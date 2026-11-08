package app.morphe.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateResolverTest {

    @Test
    fun morpheResolvedSource_identifiesSourcesCorrectly() {
        assertEquals(
            DownloadSource.APK_MIRROR,
            morpheResolvedSource("https://www.apkmirror.com/apk/meta/facebook/facebook-123/")
        )
        assertEquals(
            DownloadSource.UPTODOWN,
            morpheResolvedSource("https://facebook.en.uptodown.com/android/download/456")
        )
        assertEquals(
            DownloadSource.APK_PURE,
            morpheResolvedSource("https://d.apkpure.com/b/APK/com.facebook.katana")
        )
        assertNull(morpheResolvedSource("https://github.com/example/repo"))
    }

    @Test
    fun initialCandidateResult_prioritizesMorpheResolvedSource() {
        val resolver = CandidateResolver(emptyMap())
        val requestWithUptodownHint = testRequest().copy(
            sourceHintUrls = listOf("https://facebook.en.uptodown.com/android/download/456")
        )

        val result = resolver.initialCandidateResult(
            request = requestWithUptodownHint,
            effectiveDisabledSources = emptySet()
        )

        val sources = result.sourceGroups.map { it.source }
        assertEquals(DownloadSource.UPTODOWN, sources.first())
    }
}
