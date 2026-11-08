package app.morphe.fetch

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.util.Locale

/**
 * Centralized repository for managing HTTP cookies across WebViews and OkHttp.
 *
 * Bridges Android's native system [CookieManager] with background network requests,
 * providing unified cookie header extraction, clearance verification, and persistence.
 */
internal object CookieRepository {

    internal var testCookieProvider: ((String) -> String?)? = null
    internal var testSetCookieConsumer: ((String, String) -> Unit)? = null

    private val cookieManager: CookieManager?
        get() = runCatching { CookieManager.getInstance() }.getOrNull()

    private fun getCookie(url: String): String? {
        testCookieProvider?.let { return it(url) }
        return cookieManager?.getCookie(url)
    }

    private fun setCookie(url: String, cookie: String) {
        testSetCookieConsumer?.let {
            it(url, cookie)
            return
        }
        cookieManager?.setCookie(url, cookie)
    }

    /**
     * Retrieves a combined Cookie header string for the given [url] and optional [referer],
     * including any parent domain or CDN cookies necessary for the request.
     */
    fun getCookieHeader(url: String, referer: String? = null, source: DownloadSource? = null): String? {
        if (cookieManager == null && testCookieProvider == null) return null
        val cookies = buildList {
            getCookie(url)?.takeIf(String::isNotBlank)?.let(::add)
            referer?.takeIf(String::isNotBlank)?.let { ref ->
                getCookie(ref)?.takeIf(String::isNotBlank)?.let(::add)
            }

            val combined = "$url ${referer.orEmpty()} ${source?.name.orEmpty()}".lowercase(Locale.US)
            if (combined.contains("apkmirror.com") || source == DownloadSource.APK_MIRROR) {
                getCookie("https://www.apkmirror.com/")?.takeIf(String::isNotBlank)?.let(::add)
                getCookie("https://apkmirror.com/")?.takeIf(String::isNotBlank)?.let(::add)
            }
            if (combined.contains("uptodown.com") || source == DownloadSource.UPTODOWN) {
                getCookie("https://en.uptodown.com/")?.takeIf(String::isNotBlank)?.let(::add)
                getCookie("https://uptodown.com/")?.takeIf(String::isNotBlank)?.let(::add)
                getCookie("https://dw.uptodown.com/")?.takeIf(String::isNotBlank)?.let(::add)
            }
            if (combined.contains("apkpure.com") || source == DownloadSource.APK_PURE) {
                getCookie("https://apkpure.com/")?.takeIf(String::isNotBlank)?.let(::add)
                getCookie("https://d.apkpure.com/")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }

        if (cookies.isEmpty()) return null

        val map = linkedMapOf<String, String>()
        for (header in cookies) {
            header.split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val key = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    map[key] = value
                }
            }
        }

        return if (map.isNotEmpty()) {
            map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } else null
    }

    /**
     * Loads cookies for an OkHttp [HttpUrl] request.
     */
    fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (cookieManager == null && testCookieProvider == null) return emptyList()
        val rawHeaders = buildList {
            getCookie(url.toString())?.takeIf(String::isNotBlank)?.let(::add)
            val host = url.host.lowercase(Locale.US)
            val baseDomains = listOf("apkmirror.com", "uptodown.com", "apkpure.com")
            for (domain in baseDomains) {
                if (host.endsWith(domain)) {
                    getCookie("https://www.$domain/")?.takeIf(String::isNotBlank)?.let(::add)
                    getCookie("https://$domain/")?.takeIf(String::isNotBlank)?.let(::add)
                    if (domain == "uptodown.com") {
                        getCookie("https://en.uptodown.com/")?.takeIf(String::isNotBlank)?.let(::add)
                        getCookie("https://dw.uptodown.com/")?.takeIf(String::isNotBlank)?.let(::add)
                    }
                    break
                }
            }
        }
        if (rawHeaders.isEmpty()) return emptyList()

        val parsedCookies = mutableMapOf<String, Cookie>()
        for (header in rawHeaders) {
            header.split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val name = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(url.host)
                            .build()
                    }.getOrNull()?.let { cookie ->
                        parsedCookies[name] = cookie
                    }
                }
            }
        }
        return parsedCookies.values.toList()
    }

    /**
     * Saves response cookies from OkHttp into system [CookieManager].
     */
    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookieManager == null && testSetCookieConsumer == null) return
        val urlString = url.toString()
        for (cookie in cookies) {
            setCookie(urlString, cookie.toString())
        }
        flush()
    }

    /**
     * Checks whether active clearance cookies exist for [source].
     */
    fun hasClearance(source: DownloadSource): Boolean {
        if (cookieManager == null && testCookieProvider == null) return false
        return when (source) {
            DownloadSource.APK_MIRROR -> {
                val c1 = getCookie("https://www.apkmirror.com/").orEmpty()
                val c2 = getCookie("https://apkmirror.com/").orEmpty()
                c1.contains("cf_clearance") || c2.contains("cf_clearance")
            }
            DownloadSource.UPTODOWN -> {
                val c1 = getCookie("https://uptodown.com/").orEmpty()
                val c2 = getCookie("https://en.uptodown.com/").orEmpty()
                val c3 = getCookie("https://dw.uptodown.com/").orEmpty()
                c1.contains("cf_clearance") || c2.contains("cf_clearance") ||
                    c3.contains("uptodown_next") || c1.contains("uptodown_session") || c2.contains("uptodown_session")
            }
            else -> true
        }
    }

    /**
     * Clears cookies for a specific [source].
     */
    fun clearCookies(source: DownloadSource) {
        if (cookieManager == null && testCookieProvider == null && testSetCookieConsumer == null) return
        val domains = when (source) {
            DownloadSource.APK_MIRROR -> listOf("https://www.apkmirror.com/", "https://apkmirror.com/")
            DownloadSource.UPTODOWN -> listOf("https://uptodown.com/", "https://en.uptodown.com/", "https://dw.uptodown.com/")
            DownloadSource.APK_PURE -> listOf("https://apkpure.com/", "https://d.apkpure.com/")
            else -> emptyList()
        }
        for (domain in domains) {
            val cookies = getCookie(domain)?.split(";").orEmpty()
            for (cookie in cookies) {
                val name = cookie.substringBefore("=").trim()
                if (name.isNotBlank()) {
                    setCookie(domain, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/")
                }
            }
        }
        flush()
    }

    /**
     * Flushes cookies to disk storage.
     */
    fun flush() {
        runCatching { cookieManager?.flush() }
    }
}
