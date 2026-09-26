package app.morphe.fetch

import android.app.Application
import app.morphe.fetch.aurora.ApkMirrorVersionResolver
import app.morphe.fetch.aurora.NativeDeviceProfileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDeviceProfileProviderTest {

    @Test
    fun getDeviceProperties_populatesRequiredFields() {
        val context = Application()
        val props = NativeDeviceProfileProvider.getDeviceProperties(context)

        assertNotNull(props.getProperty("Build.DEVICE"))
        assertNotNull(props.getProperty("Build.MODEL"))
        assertNotNull(props.getProperty("Build.MANUFACTURER"))
        assertNotNull(props.getProperty("Build.FINGERPRINT"))
        assertNotNull(props.getProperty("Vending.version"))
        assertNotNull(props.getProperty("GSF.version"))
        assertNotNull(props.getProperty("Platforms"))
        assertNotNull(props.getProperty("Screen.Density"))
        assertNotNull(props.getProperty("Screen.Width"))
        assertNotNull(props.getProperty("Screen.Height"))
        assertNotNull(props.getProperty("Locales"))
        assertNotNull(props.getProperty("TimeZone"))

        assertTrue(props.getProperty("Platforms").isNotBlank())
        assertTrue(props.getProperty("Vending.version").toLong() > 0L)
        assertTrue(props.getProperty("GSF.version").toLong() > 0L)
    }

    @Test
    fun apkMirrorVersionResolver_cacheWorks() {
        val pkg = "com.google.android.youtube"
        val vName = "21.39.522"
        val vCode = 1561299901L

        ApkMirrorVersionResolver.cacheVersionCode(pkg, vName, vCode)
        val cached = ApkMirrorVersionResolver.getCachedVersionCode(pkg, vName)

        assertEquals(vCode, cached)
    }
}
