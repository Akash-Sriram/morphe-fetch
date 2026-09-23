package app.morphe.fetch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewAdBlockerTest {

    @Test
    fun isAdHost_blocksMajorAdAndTrackingNetworks() {
        val testAdHosts = listOf(
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "googleadservices.com",
            "securepubads.g.doubleclick.net",
            "stats.g.doubleclick.net",
            "criteo.com",
            "static.criteo.net",
            "taboola.com",
            "cdn.taboola.com",
            "outbrain.com",
            "adsterra.com",
            "popads.net",
            "propellerads.com",
            "amazon-adsystem.com",
            "pubmatic.com",
            "rubiconproject.com",
            "exoclick.com",
            "inmobi.com",
            "quantserve.com",
            "scorecardresearch.com"
        )

        for (host in testAdHosts) {
            assertTrue("Expected host '$host' to be classified as an ad", WebViewAdBlocker.isAdHost(host))
        }
    }

    @Test
    fun isAdHost_whitelistsCloudflareAndAppSources() {
        val whitelistedHosts = listOf(
            "challenges.cloudflare.com",
            "static.cloudflareinsights.com",
            "cloudflare.com",
            "www.cloudflare.com",
            "apkmirror.com",
            "www.apkmirror.com",
            "download.apkmirror.com",
            "uptodown.com",
            "en.uptodown.com",
            "apkpure.com",
            "apkcombo.com",
            "aptoide.com",
            "github.com",
            "raw.githubusercontent.com"
        )

        for (host in whitelistedHosts) {
            assertFalse("Expected host '$host' to be whitelisted", WebViewAdBlocker.isAdHost(host))
        }
    }

    @Test
    fun isAdHost_handlesEmptyOrBlankHostGracefully() {
        assertFalse(WebViewAdBlocker.isAdHost(""))
        assertFalse(WebViewAdBlocker.isAdHost("   "))
    }
}
