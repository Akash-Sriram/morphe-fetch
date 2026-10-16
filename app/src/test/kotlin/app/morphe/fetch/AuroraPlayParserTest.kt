package app.morphe.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraPlayParserTest {

    @Test
    fun isCompatibleSplit_filtersIncompatibleAbis() {
        val supportedAbis = listOf("arm64-v8a")

        // Compatible splits
        assertTrue(AuroraPlayParser.isCompatibleSplit("base.apk", supportedAbis))
        assertTrue(AuroraPlayParser.isCompatibleSplit("split_config.arm64_v8a.apk", supportedAbis))
        assertTrue(AuroraPlayParser.isCompatibleSplit("split_config.xxhdpi.apk", supportedAbis))
        assertTrue(AuroraPlayParser.isCompatibleSplit("split_config.en.apk", supportedAbis))

        // Incompatible architecture splits
        assertFalse(AuroraPlayParser.isCompatibleSplit("split_config.armeabi_v7a.apk", supportedAbis))
        assertFalse(AuroraPlayParser.isCompatibleSplit("split_config.x86_64.apk", supportedAbis))
        assertFalse(AuroraPlayParser.isCompatibleSplit("split_config.x86.apk", supportedAbis))
    }

    @Test
    fun isCompatibleSplit_supportsMultipleAbis() {
        val supportedAbis = listOf("arm64-v8a", "armeabi-v7a")

        assertTrue(AuroraPlayParser.isCompatibleSplit("split_config.arm64_v8a.apk", supportedAbis))
        assertTrue(AuroraPlayParser.isCompatibleSplit("split_config.armeabi_v7a.apk", supportedAbis))
        assertFalse(AuroraPlayParser.isCompatibleSplit("split_config.x86_64.apk", supportedAbis))
    }

    @Test
    fun searchUrl_returnsStandardPlayStoreLink() {
        val request = testRequest(packageName = "com.google.android.youtube")
        val parser = AuroraPlayParser(
            context = android.app.Application(),
            okHttpClient = okhttp3.OkHttpClient()
        )

        assertEquals(
            "https://play.google.com/store/apps/details?id=com.google.android.youtube",
            parser.searchUrl(request.packageName)
        )
        // Verify fallback candidates are null so Morphe Fetch never redirects to Google Play
        org.junit.Assert.assertNull(parser.requestedFallbackCandidate(request))
        org.junit.Assert.assertNull(parser.latestFallbackCandidate(request))
    }

    @Test
    fun versionsShareMajorMinor_matchesSameMajorMinor() {
        assertTrue(AuroraPlayParser.versionsShareMajorMinor("7.94.0.984908898", "7.94.1.123456"))
        assertTrue(AuroraPlayParser.versionsShareMajorMinor("7.94.0.984908898", "7.94"))
        assertTrue(AuroraPlayParser.versionsShareMajorMinor("1.0.0", "1.0.9"))
        assertFalse(AuroraPlayParser.versionsShareMajorMinor("7.94.0.984908898", "7.93.0.123456"))
        assertFalse(AuroraPlayParser.versionsShareMajorMinor("7.94.0.984908898", "8.94.0"))
    }

    @Test
    fun looksLikeApkDownload_recognizesApkMirrorAndStandardUrls() {
        // APKMirror dynamic download PHP key URLs
        assertTrue("https://www.apkmirror.com/wp-content/themes/responsive/download.php?key=123456789".looksLikeApkDownload())
        assertTrue("https://www.apkmirror.com/apk/google-inc/youtube/download/?key=abcdef123".looksLikeApkDownload())
        assertTrue("https://www.apkmirror.com/download.php".looksLikeApkDownload())

        // Standard extension and CDN URLs
        assertTrue("https://example.com/app.apk".looksLikeApkDownload())
        assertTrue("https://example.com/app.apks".looksLikeApkDownload())
        assertTrue("https://example.com/app.apkm".looksLikeApkDownload())
        assertTrue("https://example.com/app.xapk".looksLikeApkDownload())
        assertTrue("https://dw.uptodown.com/dwn/abc123xyz".looksLikeApkDownload())

        // Uptodown store installer stub should be rejected
        assertFalse("https://dw.uptodown.com/dwn/uptodown-com.reddit.frontpage.apk".looksLikeApkDownload())
        assertFalse("https://www.apkmirror.com/apk/google-inc/youtube/".looksLikeApkDownload())
    }

    @Test
    fun helperSettings_persistsCustomDispenser() {
        val settings = HelperSettings(
            auroraEmail = "test@example.com",
            auroraAuthToken = "token123",
            auroraCustomDispenser = "https://custom-dispenser.example.com/api/auth"
        )
        assertEquals("https://custom-dispenser.example.com/api/auth", settings.auroraCustomDispenser)
    }
}

