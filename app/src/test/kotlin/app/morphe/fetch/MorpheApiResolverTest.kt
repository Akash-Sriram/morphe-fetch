package app.morphe.fetch

import org.junit.Assert.assertTrue
import org.junit.Test

class MorpheApiResolverTest {

    @Test
    fun buildQueryUrl_encodesPackageAndVersionAndAbi() {
        val url = MorpheApiResolver.buildQueryUrl(
            packageName = "com.google.android.youtube",
            version = "19.16.39",
            abi = "arm64-v8a"
        )
        assertTrue(url.startsWith("https://api.morphe.software/v2/web-search/"))
        assertTrue(url.contains("com.google.android.youtube"))
        assertTrue(url.contains("19.16.39"))
        assertTrue(url.contains("arm64-v8a"))
    }

    @Test
    fun buildQueryUrl_handlesNullVersionGracefully() {
        val url = MorpheApiResolver.buildQueryUrl(
            packageName = "com.google.android.apps.photos",
            version = null,
            abi = "arm64-v8a"
        )
        assertTrue(url.contains("any"))
    }
}
