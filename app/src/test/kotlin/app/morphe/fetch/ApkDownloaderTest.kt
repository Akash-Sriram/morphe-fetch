package app.morphe.fetch

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkDownloaderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var downloader: ApkDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        downloader = ApkDownloader(client, client, client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parseContentRangeTotal_parsesValidRanges() {
        assertEquals(12345678L, parseContentRangeTotal("bytes 0-0/12345678"))
        assertEquals(8388608L, parseContentRangeTotal("bytes 0-1023/8388608"))
        assertEquals(-1L, parseContentRangeTotal("bytes 0-0/*"))
        assertEquals(-1L, parseContentRangeTotal(null))
        assertEquals(-1L, parseContentRangeTotal("invalid"))
    }

    @Test
    fun downloadToFile_singleStreamDownloadsValidZip() = runBlocking {
        val zipBytes = createSampleZipBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(Buffer().write(zipBytes))
        )

        val target = tempDir.newFile("test_target.apk")
        val candidate = CandidateDownloadFile(
            url = server.url("/app.apk").toString(),
            fileName = "test_target.apk",
            size = zipBytes.size.toLong()
        )

        var progressReported = 0L
        val result = downloader.downloadToFile(candidate, target, multiThreadEnabled = false) { copied, _ ->
            progressReported = copied
        }

        assertTrue(target.exists())
        assertEquals(zipBytes.size.toLong(), target.length())
        assertEquals(zipBytes.size.toLong(), progressReported)
        assertEquals(server.url("/app.apk").toString(), result.finalUrl)
    }

    @Test
    fun downloadToFile_segmentedDownloadSucceedsOn206() = runBlocking {
        // Create an 8 MiB + 1024 bytes dummy zip payload
        val totalSize = 8 * 1024 * 1024 + 1024
        val dummyData = ByteArray(totalSize)
        // Set PK magic bytes
        dummyData[0] = 'P'.code.toByte()
        dummyData[1] = 'K'.code.toByte()
        dummyData[2] = 0x03
        dummyData[3] = 0x04

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range") ?: return MockResponse().setResponseCode(400)
                if (rangeHeader == "bytes=0-0") {
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-0/$totalSize")
                        .setHeader("Content-Type", "application/vnd.android.package-archive")
                        .setBody(Buffer().write(dummyData, 0, 1))
                }
                val match = Regex("""bytes=(\d+)-(\d+)""").find(rangeHeader)
                if (match != null) {
                    val start = match.groupValues[1].toInt()
                    val end = match.groupValues[2].toInt()
                    val length = end - start + 1
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                        .setHeader("Content-Type", "application/vnd.android.package-archive")
                        .setBody(Buffer().write(dummyData, start, length))
                }
                return MockResponse().setResponseCode(400)
            }
        }

        val target = File(tempDir.root, "segmented_target.apk")
        val candidate = CandidateDownloadFile(
            url = server.url("/large_app.apk").toString(),
            fileName = "segmented_target.apk",
            size = totalSize.toLong()
        )

        var lastProgress = 0L
        val result = downloader.downloadToFile(candidate, target, multiThreadEnabled = true) { copied, _ ->
            lastProgress = copied
        }

        assertTrue(target.exists())
        assertEquals(totalSize.toLong(), target.length())
        assertEquals(totalSize.toLong(), lastProgress)
        assertEquals(server.url("/large_app.apk").toString(), result.finalUrl)
    }

    private fun createSampleZipBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
