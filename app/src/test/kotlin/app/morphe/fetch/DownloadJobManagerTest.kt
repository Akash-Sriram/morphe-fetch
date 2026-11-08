package app.morphe.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadJobManagerTest {

    @Before
    fun setUp() {
        DownloadJobManager.retireCurrent()
    }

    @Test
    fun start_incrementsEpochAndSetsActiveJob() {
        val initialEpoch = DownloadJobManager.currentEpoch
        val request = testRequest()
        val candidate = DownloadCandidate(
            source = DownloadSource.UPTODOWN,
            name = "Test App",
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1L,
            url = "https://example.com/download",
            fileKind = "apk",
            option = CandidateOption.LATEST,
            directDownload = true,
            versionStatus = VersionStatus.LATEST,
            formatMatches = true
        )
        val job = DownloadJobManager.DownloadJob(
            request = request,
            candidate = candidate,
            settings = HelperSettings()
        )

        DownloadJobManager.start(job)

        assertEquals(initialEpoch + 1, DownloadJobManager.currentEpoch)
        assertTrue(DownloadJobManager.isLive(DownloadJobManager.currentEpoch))
        assertFalse(DownloadJobManager.isLive(initialEpoch))
        assertEquals(DownloadJobManager.currentEpoch, DownloadJobManager.activeJob?.epoch)
    }

    @Test
    fun retireCurrent_clearsActiveJob() {
        val request = testRequest()
        val candidate = DownloadCandidate(
            source = DownloadSource.UPTODOWN,
            name = "Test App",
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1L,
            url = "https://example.com/download",
            fileKind = "apk",
            option = CandidateOption.LATEST,
            directDownload = true,
            versionStatus = VersionStatus.LATEST,
            formatMatches = true
        )
        val job = DownloadJobManager.DownloadJob(
            request = request,
            candidate = candidate,
            settings = HelperSettings()
        )

        DownloadJobManager.start(job)
        val epoch = DownloadJobManager.currentEpoch
        assertTrue(DownloadJobManager.isLive(epoch))

        DownloadJobManager.retireCurrent()
        assertNull(DownloadJobManager.activeJob)
        assertFalse(DownloadJobManager.isLive(epoch))
    }
}
