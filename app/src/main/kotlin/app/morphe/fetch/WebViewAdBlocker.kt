package app.morphe.fetch

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Lightweight, zero-dependency ad and tracker blocker for Morphe Fetch's in-app WebView.
 *
 * Drops network requests to ad/tracking networks before sockets open,
 * whitelists critical Cloudflare and APK source domains, and injects cosmetic CSS
 * to hide ad placeholders and deceptive banners.
 */
internal object WebViewAdBlocker {

    private val EMPTY_BYTES = ByteArray(0)

    private val WHITELISTED_DOMAINS = hashSetOf(
        "cloudflare.com",
        "challenges.cloudflare.com",
        "static.cloudflareinsights.com",
        "cloudflareinsights.com",
        "apkmirror.com",
        "uptodown.com",
        "apkpure.com",
        "apkcombo.com",
        "aptoide.com",
        "github.com",
        "githubusercontent.com"
    )

    private val AD_DOMAINS = hashSetOf(
        // Google Ad / Tracking Networks
        "googlesyndication.com",
        "doubleclick.net",
        "googleadservices.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "afs.googlesyndication.com",

        // Major Ad Exchanges & SSPs / DSPs
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "sharethrough.com",
        "bidswitch.net",
        "casalemedia.com",
        "moatads.com",
        "serving-sys.com",
        "adform.net",
        "advertising.com",
        "adsystem.com",
        "adroll.com",
        "creativecdn.com",
        "adkernel.com",
        "contextweb.com",
        "sovrn.com",
        "lijit.com",
        "yieldmo.com",
        "teads.tv",
        "media.net",
        "exponential.com",

        // Aggressive Pop, Mobile & Redirect Ad Networks
        "adsterra.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "exoclick.com",
        "inmobi.com",
        "unityads.unity3d.com",
        "applovin.com",
        "adcolony.com",
        "ironsrc.com",
        "vungle.com",
        "trafficjunky.com",
        "clickadu.com",
        "hilltopads.net",
        "monetag.com",
        "richpush.com",
        "zeroredirect1.com",
        "zeroredirect2.com",
        "revcontent.com",
        "mgid.com",
        "zedo.com",

        // Telemetry & Fingerprinting Trackers
        "scorecardresearch.com",
        "quantserve.com",
        "chartbeat.com"
    )

    /**
     * Checks whether the given URI corresponds to a known ad, tracker, or malware domain.
     */
    fun isAd(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase(Locale.US) ?: return false
        return isAdHost(host)
    }

    /**
     * Checks whether a hostname matches ad or tracker domains, respecting whitelisted domains.
     */
    fun isAdHost(rawHost: String): Boolean {
        val host = rawHost.lowercase(Locale.US).trim()
        if (host.isBlank()) return false

        // Check whitelist first
        if (isWhitelisted(host)) return false

        // Direct match
        if (AD_DOMAINS.contains(host)) return true

        // Subdomain matching (e.g. pagead2.googlesyndication.com matches googlesyndication.com)
        for (adDomain in AD_DOMAINS) {
            if (host.endsWith(".$adDomain")) {
                return true
            }
        }

        return false
    }

    private fun isWhitelisted(host: String): Boolean {
        for (whitelisted in WHITELISTED_DOMAINS) {
            if (host == whitelisted || host.endsWith(".$whitelisted")) {
                return true
            }
        }
        return false
    }

    /**
     * Creates an empty HTTP 200 response to cleanly satisfy blocked requests without errors.
     */
    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(EMPTY_BYTES)
        )
    }

    /**
     * Comprehensive CSS snippet injected into pages to hide ad containers, empty slots,
     * floating overlays, and deceptive fake download buttons.
     */
    val COSMETIC_HIDING_CSS = """
        [id*='google_ads'], [class*='adsbygoogle'], [class*='ad-container'], [class*='ad_container'],
        [id*='taboola'], [id*='outbrain'], .advertisement, .ad-banner, .app-ad, [class*='criteo'],
        [class*='pubmatic'], div[id^='dfp-ad-'], div[id^='gpt-ad-'], div[id*='div-gpt-ad'],
        .ad-wrapper, .ad_wrapper, .ad-placement, .ad_placement, .ad-slot, .ad_slot,
        [id*='ad-banner'], [class*='ad-banner'], iframe[src*='doubleclick.net'],
        iframe[src*='googlesyndication.com'], iframe[src*='google'], iframe[src*='adnxs.com'],
        iframe[src*='amazon-adsystem'], .apkmirror-ad, .apkmirror_ad, #ad-bottom, #ad-top,
        .app_ad_unit, .sticky-ad, .floating-ad, .interstitial-ad,
        div[class*='sponsored'], div[id*='sponsored'] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
            margin: 0 !important;
            padding: 0 !important;
        }
    """.trimIndent()

    /**
     * JavaScript code to inject the cosmetic stylesheet into the DOM.
     */
    val INJECT_CSS_JS = """
        (function() {
            var styleId = 'morphe-adblock-style';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.type = 'text/css';
                style.appendChild(document.createTextNode(${javaScriptStringLiteral(COSMETIC_HIDING_CSS)}));
                (document.head || document.documentElement).appendChild(style);
            }
        })();
    """.trimIndent()

    private fun javaScriptStringLiteral(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "\"$escaped\""
    }
}
