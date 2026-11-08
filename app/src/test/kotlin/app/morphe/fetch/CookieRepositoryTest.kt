package app.morphe.fetch

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CookieRepositoryTest {

    private val cookieStore = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        cookieStore.clear()
        CookieRepository.testCookieProvider = { url -> cookieStore[url] }
        CookieRepository.testSetCookieConsumer = { url, cookie ->
            val existing = cookieStore[url]
            val cookiePart = cookie.substringBefore(";")
            if (cookie.contains("Expires=Thu, 01 Jan 1970")) {
                val name = cookiePart.substringBefore("=").trim()
                if (existing != null) {
                    val remaining = existing.split(";")
                        .map(String::trim)
                        .filter { !it.startsWith("$name=") }
                        .joinToString("; ")
                    if (remaining.isBlank()) {
                        cookieStore.remove(url)
                    } else {
                        cookieStore[url] = remaining
                    }
                }
            } else {
                if (existing == null) {
                    cookieStore[url] = cookiePart
                } else {
                    cookieStore[url] = "$existing; $cookiePart"
                }
            }
        }
    }

    @After
    fun tearDown() {
        CookieRepository.testCookieProvider = null
        CookieRepository.testSetCookieConsumer = null
        cookieStore.clear()
    }

    @Test
    fun getCookieHeader_combinesTargetAndDomainCookies() {
        cookieStore["https://dw.uptodown.com/dwn/abc"] = "token=123"
        cookieStore["https://en.uptodown.com/"] = "uptodown_session=xyz"
        cookieStore["https://uptodown.com/"] = "cf_clearance=cleared"

        val header = CookieRepository.getCookieHeader(
            url = "https://dw.uptodown.com/dwn/abc",
            referer = "https://facebook.en.uptodown.com/android/download/123",
            source = DownloadSource.UPTODOWN
        )

        assertNotNull(header)
        assertTrue(header!!.contains("token=123"))
        assertTrue(header.contains("uptodown_session=xyz"))
        assertTrue(header.contains("cf_clearance=cleared"))
    }

    @Test
    fun hasClearance_detectsCloudflareAndSessionCookies() {
        assertFalse(CookieRepository.hasClearance(DownloadSource.UPTODOWN))
        assertFalse(CookieRepository.hasClearance(DownloadSource.APK_MIRROR))

        cookieStore["https://uptodown.com/"] = "uptodown_session=abc"
        assertTrue(CookieRepository.hasClearance(DownloadSource.UPTODOWN))

        cookieStore.clear()
        cookieStore["https://en.uptodown.com/"] = "cf_clearance=cf123"
        assertTrue(CookieRepository.hasClearance(DownloadSource.UPTODOWN))

        cookieStore.clear()
        cookieStore["https://www.apkmirror.com/"] = "cf_clearance=mirror123"
        assertTrue(CookieRepository.hasClearance(DownloadSource.APK_MIRROR))
    }

    @Test
    fun clearCookies_clearsDomainCookiesForSource() {
        cookieStore["https://en.uptodown.com/"] = "uptodown_session=abc; cf_clearance=cf123"
        cookieStore["https://uptodown.com/"] = "uptodown_session=abc"
        cookieStore["https://www.apkmirror.com/"] = "cf_clearance=mirror123"

        CookieRepository.clearCookies(DownloadSource.UPTODOWN)

        assertNull(cookieStore["https://en.uptodown.com/"])
        assertNull(cookieStore["https://uptodown.com/"])
        assertEquals("cf_clearance=mirror123", cookieStore["https://www.apkmirror.com/"])
    }

    @Test
    fun loadForRequest_returnsParsedCookiesMatchingHost() {
        cookieStore["https://www.apkmirror.com/"] = "cf_clearance=test_clearance"

        val cookies = CookieRepository.loadForRequest("https://www.apkmirror.com/apk/meta/facebook/".toHttpUrl())

        assertEquals(1, cookies.size)
        assertEquals("cf_clearance", cookies[0].name)
        assertEquals("test_clearance", cookies[0].value)
        assertEquals("www.apkmirror.com", cookies[0].domain)
    }
}
