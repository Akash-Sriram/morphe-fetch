package app.morphe.fetch.aurora

import android.content.Context
import android.os.Build
import java.io.InputStream
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

internal object NativeDeviceProfileProvider {

    fun getDeviceProperties(context: Context): Properties {
        val properties = Properties()

        // 1. Certified Google Play phone profile baseline.
        // Google Play restricts apps like Instagram, Netflix, etc. on Tablets (e.g. SM-X216B)
        // and uncertified OS builds (Android 16 preview), returning AppNotSupported (code=2).
        // A certified phone profile baseline guarantees full hardware features, OpenGL extensions,
        // and a certified fingerprint recognized by Google Play.
        val resourceName = when {
            Build.DEVICE?.contains("beryllium", ignoreCase = true) == true ||
                Build.MODEL?.contains("POCO F1", ignoreCase = true) == true -> "gplayapi_poco_f1"
            Build.MANUFACTURER?.contains("samsung", ignoreCase = true) == true -> "gplayapi_sm_s20_plus"
            else -> "gplayapi_px_9a"
        }

        val stream: InputStream? = runCatching {
            val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resId != 0) context.resources.openRawResource(resId) else null
        }.getOrNull() ?: runCatching {
            val fallbackId = context.resources.getIdentifier("gplayapi_poco_f1", "raw", context.packageName)
            if (fallbackId != 0) context.resources.openRawResource(fallbackId) else null
        }.getOrNull() ?: runCatching {
            NativeDeviceProfileProvider::class.java.classLoader?.getResourceAsStream("res/raw/$resourceName.properties")
                ?: NativeDeviceProfileProvider::class.java.classLoader?.getResourceAsStream("res/raw/gplayapi_poco_f1.properties")
        }.getOrNull()

        if (stream != null) {
            try {
                stream.use { properties.load(it) }
            } catch (_: Exception) {
            }
        }

        if (properties.isEmpty) {
            fillMinimalCertifiedProperties(properties)
        }

        // 2. Adapt native device architecture, locale, and timezone dynamically
        val deviceAbis = runCatching {
            Build.SUPPORTED_ABIS?.filter { !it.isNullOrBlank() }?.joinToString(separator = ",")
        }.getOrNull().orEmpty()
        if (deviceAbis.isNotBlank()) {
            properties.setProperty("Platforms", deviceAbis)
        }

        val locales = runCatching {
            context.assets.locales.mapNotNull { it.replace("-", "_") }.filter { it.isNotBlank() }.joinToString(",")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toString()
        properties.setProperty("Locales", locales)

        val tzId = TimeZone.getDefault().id
        if (!tzId.isNullOrBlank()) {
            properties.setProperty("TimeZone", tzId)
        }

        return properties
    }

    private fun fillMinimalCertifiedProperties(properties: Properties) {
        properties.apply {
            setProperty("UserReadableName", "POCO F1")
            setProperty("Build.HARDWARE", "qcom")
            setProperty("Build.RADIO", "4.0.c2.6-00335-0724_2053_3c8fca6")
            setProperty("Build.BOOTLOADER", "unknown")
            setProperty("Build.FINGERPRINT", "google/sunfish/sunfish:11/RP1A.200720.010/6722941:user/release-keys")
            setProperty("Build.BRAND", "Xiaomi")
            setProperty("Build.DEVICE", "beryllium")
            setProperty("Build.VERSION.SDK_INT", "30")
            setProperty("Build.MODEL", "POCO F1")
            setProperty("Build.MANUFACTURER", "Xiaomi")
            setProperty("Build.PRODUCT", "reloaded_beryllium")
            setProperty("Build.ID", "RP1A.200720.011")
            setProperty("Build.VERSION.RELEASE", "11")
            setProperty("TouchScreen", "3")
            setProperty("Keyboard", "1")
            setProperty("Navigation", "1")
            setProperty("ScreenLayout", "2")
            setProperty("HasHardKeyboard", "false")
            setProperty("HasFiveWayNavigation", "false")
            setProperty("GL.Version", "196610")
            setProperty("Screen.Density", "440")
            setProperty("Screen.Width", "1080")
            setProperty("Screen.Height", "2160")
            setProperty("Platforms", "arm64-v8a,armeabi-v7a,armeabi")
            setProperty("GSF.version", "202414022")
            setProperty("Vending.version", "81971200")
            setProperty("Vending.versionString", "19.7.12-all [0] [PR] 305919187")
            setProperty("CellOperator", "40411")
            setProperty("SimOperator", "40411")
            setProperty("TimeZone", "Asia/Kolkata")
            setProperty("Roaming", "mobile-notroaming")
            setProperty("Client", "android-google")
        }
    }
}
