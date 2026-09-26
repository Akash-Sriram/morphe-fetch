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
}

