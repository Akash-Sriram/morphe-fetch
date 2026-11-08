package app.morphe.fetch

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_DOWNLOAD_ATTEMPTS = 3
private const val DOWNLOAD_RETRY_BASE_MS = 2_000L
private const val HTTP_PARTIAL_CONTENT = 206
internal const val MULTI_MIN_BYTES = 8 * 1024 * 1024L // 8 MiB

internal data class DownloadResult(
    val finalUrl: String,
    val contentDisposition: String?
)

internal class ApkDownloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val apkMirrorClient: OkHttpClient = MorpheHttpClient.apkMirrorDownloadClient,
    private val onRetry: (attempt: Int, delayMs: Long) -> Unit = { _, _ -> }
) {
    private val activeCalls = Collections.synchronizedSet(mutableSetOf<Call>())

    fun cancelCurrent() {
        synchronized(activeCalls) {
            activeCalls.forEach { runCatching { it.cancel() } }
            activeCalls.clear()
        }
    }

    suspend fun downloadToFile(
        download: CandidateDownloadFile,
        target: File,
        multiThreadEnabled: Boolean = true,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult {
        var attempt = 0
        while (true) {
            try {
                return performDownloadAttempt(download, target, multiThreadEnabled, onProgress)
            } catch (error: Throwable) {
                if (error is CancellationException || error.message == "Canceled") throw error
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS - 1 || !isTransientDownloadError(error)) throw error
                attempt++
                val delayMs = DOWNLOAD_RETRY_BASE_MS * (1L shl (attempt - 1))
                onRetry(attempt, delayMs)
                delay(delayMs)
            }
        }
    }

    private suspend fun performDownloadAttempt(
        download: CandidateDownloadFile,
        target: File,
        multiThreadEnabled: Boolean,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult {
        val url = download.url.normalizedHttpUrlOrNull()
            ?: error("Source returned an invalid download URL.".withManualModeHint())
        val partial = if (target.exists()) target.length() else 0L

        // If target has no partial content, multiThread is enabled, and file is either unknown size or >= 8MB, attempt segmented download
        if (partial == 0L && multiThreadEnabled && (download.size == null || download.size >= MULTI_MIN_BYTES)) {
            val multiResult = runCatching {
                attemptSegmentedDownload(download, url, target, onProgress)
            }
            val result = multiResult.getOrNull()
            if (result != null) {
                return result
            }
            val exception = multiResult.exceptionOrNull()
            if (exception is CancellationException || exception?.message == "Canceled") {
                target.delete()
                throw exception
            }
            // Clean up partial segment file on probe skip or non-cancellation error before fallback
            target.delete()
        }

        return performSingleStreamDownload(download, url, target, onProgress)
    }

    private suspend fun attemptSegmentedDownload(
        download: CandidateDownloadFile,
        url: String,
        target: File,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult? {
        val downloadClient = downloadClientFor(url)
        val probeBuilder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
        download.referer?.let { probeBuilder.header("Referer", it) }
        download.cookieHeader?.takeIf { it.isNotBlank() }?.let { probeBuilder.header("Cookie", it) }

        val probeCall = downloadClient.newCall(probeBuilder.build())
        activeCalls.add(probeCall)
        val probeResponse = try {
            probeCall.execute()
        } finally {
            activeCalls.remove(probeCall)
        }

        val total: Long
        val finalUrl: String
        val contentDisposition: String?
        probeResponse.use { response ->
            if (response.code != HTTP_PARTIAL_CONTENT) return null

            val encoding = response.header("Content-Encoding")
            if (!encoding.isNullOrEmpty() && !encoding.equals("identity", ignoreCase = true)) {
                return null
            }

            val contentType = response.header("Content-Type")
            if (contentType != null && contentType.contains("text/html", ignoreCase = true)) {
                return null
            }

            total = parseContentRangeTotal(response.header("Content-Range"))
            if (total < MULTI_MIN_BYTES) return null

            finalUrl = response.request.url.toString()
            contentDisposition = response.header("Content-Disposition")
        }

        val segments = if (total < 20 * 1024 * 1024L) 2 else 4

        target.delete()
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(total)
        }

        val copiedBytes = AtomicLong(0L)
        try {
            coroutineScope {
                val jobs = (0 until segments).map { i ->
                    val start = i * total / segments
                    val end = if (i == segments - 1) total - 1 else (i + 1) * total / segments - 1
                    async(Dispatchers.IO) {
                        downloadSegment(
                            downloadClient = downloadClient,
                            segmentUrl = finalUrl,
                            referer = download.referer,
                            cookieHeader = download.cookieHeader,
                            target = target,
                            start = start,
                            end = end,
                            total = total,
                            copiedBytes = copiedBytes,
                            onProgress = onProgress
                        )
                    }
                }
                jobs.awaitAll()
            }
        } catch (e: Throwable) {
            synchronized(activeCalls) {
                activeCalls.forEach { runCatching { it.cancel() } }
            }
            throw e
        }

        if (target.length() != total) {
            throw IOException("Segmented download size mismatch: expected $total bytes, got ${target.length()} bytes")
        }

        // Validate zip magic bytes
        RandomAccessFile(target, "r").use { raf ->
            if (raf.length() >= 2) {
                raf.seek(0)
                val b0 = raf.readByte()
                val b1 = raf.readByte()
                if (b0 != 'P'.code.toByte() || b1 != 'K'.code.toByte()) {
                    throw IOException("Segmented download result is not a valid zip archive")
                }
            }
        }

        return DownloadResult(finalUrl, contentDisposition)
    }

    private fun downloadSegment(
        downloadClient: OkHttpClient,
        segmentUrl: String,
        referer: String?,
        cookieHeader: String?,
        target: File,
        start: Long,
        end: Long,
        total: Long,
        copiedBytes: AtomicLong,
        onProgress: (copied: Long, total: Long) -> Unit
    ) {
        val expected = end - start + 1
        var written = 0L
        val reqBuilder = Request.Builder()
            .url(segmentUrl)
            .header("Range", "bytes=$start-$end")
            .header("Accept-Encoding", "identity")
        referer?.let { reqBuilder.header("Referer", it) }
        cookieHeader?.takeIf { it.isNotBlank() }?.let { reqBuilder.header("Cookie", it) }

        val call = downloadClient.newCall(reqBuilder.build())
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                if (response.code != HTTP_PARTIAL_CONTENT) {
                    throw IOException("HTTP ${response.code} for segment $start-$end")
                }
                val encoding = response.header("Content-Encoding")
                if (!encoding.isNullOrEmpty() && !encoding.equals("identity", ignoreCase = true)) {
                    throw IOException("Unexpected Content-Encoding $encoding for segment $start-$end")
                }
                val body = response.body
                RandomAccessFile(target, "rw").use { raf ->
                    raf.seek(start)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            raf.write(buffer, 0, read)
                            written += read
                            val current = copiedBytes.addAndGet(read.toLong())
                            onProgress(current, total)
                        }
                    }
                }
            }
            if (written != expected) {
                throw IOException("Short segment: expected $expected bytes, got $written bytes")
            }
        } catch (error: Throwable) {
            copiedBytes.addAndGet(-written)
            throw error
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun performSingleStreamDownload(
        download: CandidateDownloadFile,
        url: String,
        target: File,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult {
        val partial = if (target.exists()) target.length() else 0L
        val builder = Request.Builder().url(url)
        download.referer?.let { builder.header("Referer", it) }
        download.cookieHeader?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        if (partial > 0L) builder.header("Range", "bytes=$partial-")

        val call = downloadClientFor(url).newCall(builder.build())
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val finalUrl = response.request.url.toString()
                val contentDisposition = response.header("Content-Disposition")
                val resumed = response.code == HTTP_PARTIAL_CONTENT && partial > 0L
                val total: Long = if (resumed) {
                    partial + response.body.contentLength().coerceAtLeast(0L)
                } else {
                    download.size ?: response.body.contentLength().coerceAtLeast(0L)
                }
                val contentType = response.body.contentType()?.toString()
                response.body.byteStream().use { input ->
                    val source = if (resumed) {
                        input
                    } else {
                        validateApkLikeStream(input, contentType)
                    }
                    FileOutputStream(target, resumed).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = if (resumed) partial else 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                    }
                }
                val contentEncoding = response.header("Content-Encoding")
                val isIdentity = contentEncoding.isNullOrEmpty() || contentEncoding.equals("identity", ignoreCase = true)
                if (isIdentity && total > 0L && target.length() < total) {
                    throw IOException(
                        "Truncated download for ${target.name}: expected $total bytes, received ${target.length()} bytes"
                    )
                }
                return DownloadResult(finalUrl, contentDisposition)
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun downloadClientFor(url: String): OkHttpClient =
        when {
            url.contains("apkpure", ignoreCase = true) -> apkPureClient
            url.contains("apkmirror", ignoreCase = true) -> apkMirrorClient
            url.contains("uptodown.com", ignoreCase = true) || url.contains("uptodown.net", ignoreCase = true) -> MorpheHttpClient.uptodownDownloadClient
            else -> client
        }

    private fun isTransientDownloadError(error: Throwable): Boolean {
        if (error is java.io.IOException) return true
        val message = error.message.orEmpty()
        return Regex("""HTTP (408|429|5\d\d)\b""").containsMatchIn(message)
    }
}

internal fun parseContentRangeTotal(contentRange: String?): Long {
    if (contentRange == null) return -1L
    val slash = contentRange.lastIndexOf('/')
    if (slash < 0 || slash == contentRange.length - 1) return -1L
    return contentRange.substring(slash + 1).trim().toLongOrNull() ?: -1L
}

private fun validateApkLikeStream(
    input: java.io.InputStream,
    contentType: String?
): java.io.InputStream {
    val header = ByteArray(4)
    val headerSize = input.read(header)
    val isZip = headerSize >= 2 &&
        header[0] == 'P'.code.toByte() &&
        header[1] == 'K'.code.toByte()

    check(isZip) {
        val type = contentType?.let { " ($it)" }.orEmpty()
        "Source did not return a valid APK/APKS/XAPK$type."
    }

    return SequenceInputStream(ByteArrayInputStream(header, 0, headerSize), input)
}
