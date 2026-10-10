package app.morphe.fetch.aurora

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

internal object NativeDeviceProfileProvider {

    private const val GMS_PACKAGE = "com.google.android.gms"
    private const val VENDING_PACKAGE = "com.android.vending"

    // Certified defaults tested and working across Google Play API
    private const val DEFAULT_GSF_VERSION = "203019037"
    private const val DEFAULT_VENDING_VERSION = "82151710"
    private const val DEFAULT_VENDING_VERSION_STRING = "21.5.17-21 [0] [PR] 326734551"
    private const val DEFAULT_GL_VERSION = "196610" // OpenGL ES 3.2

    fun getDeviceProperties(context: Context): Properties {
        val properties = Properties()

        // 1. Base certified defaults to guarantee critical keys exist
        fillCertifiedBaseline(properties)

        // 2. Hardware configuration & input capabilities
        runCatching {
            val config = context.resources.configuration
            properties.setProperty("TouchScreen", "${config.touchscreen}")
            properties.setProperty("Keyboard", "${config.keyboard}")
            properties.setProperty("Navigation", "${config.navigation}")
            properties.setProperty("ScreenLayout", "${config.screenLayout and 15}")
            properties.setProperty("HasHardKeyboard", "${config.keyboard == Configuration.KEYBOARD_QWERTY}")
            properties.setProperty(
                "HasFiveWayNavigation",
                "${config.navigation == Configuration.NAVIGATIONHIDDEN_YES}"
            )
        }

        // 3. Screen display metrics
        runCatching {
            val metrics = context.resources.displayMetrics
            properties.setProperty("Screen.Density", "${metrics.densityDpi}")
            properties.setProperty("Screen.Width", "${metrics.widthPixels}")
            properties.setProperty("Screen.Height", "${metrics.heightPixels}")
        }

        // 4. Supported ABIs / Platforms
        val deviceAbis = runCatching {
            Build.SUPPORTED_ABIS?.filter { !it.isNullOrBlank() }?.joinToString(",")
        }.getOrNull().orEmpty()
        if (deviceAbis.isNotBlank()) {
            properties.setProperty("Platforms", deviceAbis)
        }

        // 5. System features & shared libraries
        runCatching {
            val features = context.packageManager.systemAvailableFeatures
                .mapNotNull { it.name }
                .filter { it.isNotBlank() }
            if (features.isNotEmpty()) {
                properties.setProperty("Features", features.joinToString(","))
            }
        }
        runCatching {
            val sharedLibs = context.packageManager.systemSharedLibraryNames
                ?.filter { !it.isNullOrBlank() }
            if (!sharedLibs.isNullOrEmpty()) {
                properties.setProperty("SharedLibraries", sharedLibs.joinToString(","))
            }
        }

        // 6. Locales & TimeZone
        val locales = runCatching {
            context.assets.locales.mapNotNull { it.replace("-", "_") }
                .filter { it.isNotBlank() }
                .joinToString(",")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toString()
        properties.setProperty("Locales", locales)

        val tzId = TimeZone.getDefault().id
        if (!tzId.isNullOrBlank()) {
            properties.setProperty("TimeZone", tzId)
        }

        // 7. Dynamic OpenGL ES version and extensions
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val reqGl = am?.deviceConfigurationInfo?.reqGlEsVersion
            if (reqGl != null && reqGl > 0) {
                properties.setProperty("GL.Version", reqGl.toString())
            }
        }
        runCatching {
            val glExts = EglExtensionProvider.eglExtensions
            if (glExts.isNotEmpty()) {
                properties.setProperty("GL.Extensions", glExts.joinToString(","))
            }
        }

        // 8. Dynamic Google Play Services (GMS) & Play Store (Vending) inspection
        runCatching {
            val pm = context.packageManager
            runCatching {
                val gmsInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(GMS_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(GMS_PACKAGE, 0)
                }
                val gmsCode = PackageInfoCompat.getLongVersionCode(gmsInfo)
                if (gmsCode > 0) {
                    properties.setProperty("GSF.version", gmsCode.toString())
                }
            }
            runCatching {
                val vendingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(VENDING_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(VENDING_PACKAGE, 0)
                }
                val vendingCode = PackageInfoCompat.getLongVersionCode(vendingInfo)
                if (vendingCode > 0) {
                    properties.setProperty("Vending.version", vendingCode.toString())
                }
                vendingInfo.versionName?.takeIf { it.isNotBlank() }?.let {
                    properties.setProperty("Vending.versionString", it)
                }
            }
        }

        // 9. Real device build & OS identifiers
        properties.setProperty("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString())
        Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.VERSION.RELEASE", it)
        }
        Build.MODEL?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.MODEL", it)
            properties.setProperty("UserReadableName", "${Build.MANUFACTURER.orEmpty()} $it".trim())
        }
        Build.MANUFACTURER?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.MANUFACTURER", it)
        }
        Build.BRAND?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.BRAND", it)
        }
        Build.DEVICE?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.DEVICE", it)
        }
        Build.PRODUCT?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.PRODUCT", it)
        }
        Build.HARDWARE?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.HARDWARE", it)
        }
        Build.ID?.takeIf { it.isNotBlank() }?.let {
            properties.setProperty("Build.ID", it)
        }
        Build.BOOTLOADER?.takeIf { it.isNotBlank() && it != "unknown" }?.let {
            properties.setProperty("Build.BOOTLOADER", it)
        }
        runCatching { Build.getRadioVersion() }.getOrNull()?.takeIf { !it.isNullOrBlank() }?.let {
            properties.setProperty("Build.RADIO", it)
        }
        Build.FINGERPRINT?.takeIf { it.isNotBlank() && !it.contains("test-keys", ignoreCase = true) }?.let {
            properties.setProperty("Build.FINGERPRINT", it)
        }

        // 10. Safeguard for uncertified / Huawei devices (prevent Google Play block)
        val isHuawei = Build.MANUFACTURER.equals("huawei", ignoreCase = true) ||
            Build.BRAND.equals("huawei", ignoreCase = true) ||
            Build.BRAND.equals("honor", ignoreCase = true)
        if (isHuawei) {
            spoofCertifiedPixelBaseline(properties)
        }

        return properties
    }

    private fun fillCertifiedBaseline(properties: Properties) {
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
            setProperty("GL.Version", DEFAULT_GL_VERSION)
            setProperty("Screen.Density", "440")
            setProperty("Screen.Width", "1080")
            setProperty("Screen.Height", "2160")
            setProperty("Platforms", "arm64-v8a,armeabi-v7a,armeabi")
            setProperty("GSF.version", DEFAULT_GSF_VERSION)
            setProperty("Vending.version", DEFAULT_VENDING_VERSION)
            setProperty("Vending.versionString", DEFAULT_VENDING_VERSION_STRING)
            setProperty("CellOperator", "40411")
            setProperty("SimOperator", "40411")
            setProperty("TimeZone", "Asia/Kolkata")
            setProperty("Roaming", "mobile-notroaming")
            setProperty("Client", "android-google")
        }
    }

    private fun spoofCertifiedPixelBaseline(properties: Properties) {
        properties["Build.HARDWARE"] = "lynx"
        properties["Build.BOOTLOADER"] = "lynx-1.0-9716681"
        properties["Build.BRAND"] = "google"
        properties["Build.DEVICE"] = "lynx"
        properties["Build.MODEL"] = "Pixel 7a"
        properties["Build.MANUFACTURER"] = "Google"
        properties["Build.PRODUCT"] = "lynx"
        properties["Build.ID"] = "TQ2A.230505.002"
        properties["Build.FINGERPRINT"] = "google/lynx/lynx:13/TQ2A.230505.002/9880290:user/release-keys"
    }
}
