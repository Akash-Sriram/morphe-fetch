package app.morphe.fetch

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.SequenceInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_DOWNLOAD_ATTEMPTS = 3
private const val DOWNLOAD_RETRY_BASE_MS = 2_000L
private const val HTTP_PARTIAL_CONTENT = 206

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
    private var activeCall: Call? = null

    fun cancelCurrent() {
        activeCall?.cancel()
    }

    suspend fun downloadToFile(
        download: CandidateDownloadFile,
        target: File,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult {
        var attempt = 0
        while (true) {
            try {
                return performDownloadAttempt(download, target, onProgress)
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

    private fun performDownloadAttempt(
        download: CandidateDownloadFile,
        target: File,
        onProgress: (copied: Long, total: Long) -> Unit
    ): DownloadResult {
        val url = download.url.normalizedHttpUrlOrNull()
            ?: error("Source returned an invalid download URL.".withManualModeHint())
        val partial = if (target.exists()) target.length() else 0L
        val builder = Request.Builder().url(url)
        download.referer?.let { builder.header("Referer", it) }
        download.cookieHeader?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        if (partial > 0L) builder.header("Range", "bytes=$partial-")

        val call = downloadClientFor(url).newCall(builder.build())
        activeCall = call
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
                return DownloadResult(finalUrl, contentDisposition)
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    private fun downloadClientFor(url: String): OkHttpClient =
        when {
            url.contains("apkpure", ignoreCase = true) -> apkPureClient
            url.contains("apkmirror", ignoreCase = true) -> apkMirrorClient
            else -> client
        }

    private fun isTransientDownloadError(error: Throwable): Boolean {
        if (error is java.io.IOException) return true
        val message = error.message.orEmpty()
        return Regex("""HTTP (408|429|5\d\d)\b""").containsMatchIn(message)
    }
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
