package app.morphe.fetch

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe memory cache for resolved Uptodown variant direct download URLs.
 *
 * Once Turnstile verification produces a direct CDN download URL (`dw.uptodown.com/dwn/...`),
 * caching it allows candidate resolvers and the UI to immediately present "Download and return"
 * and proceed directly without re-triggering Turnstile verification.
 */
internal object UptodownResolvedUrlCache {

    data class Entry(
        val downloadUrl: String,
        val cookieHeader: String?,
        val referer: String?,
        val expectedSha256: String?,
        val resolvedAtMs: Long = System.currentTimeMillis()
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - resolvedAtMs > TTL_MS
    }

    private const val TTL_MS = 30 * 60 * 1000L // 30 minutes

    private val cache = ConcurrentHashMap<String, Entry>()

    fun put(
        pageUrl: String,
        downloadUrl: String,
        cookieHeader: String? = null,
        referer: String? = null,
        expectedSha256: String? = null
    ) {
        val key = normalizeKey(pageUrl)
        if (key.isNotBlank()) {
            cache[key] = Entry(
                downloadUrl = downloadUrl,
                cookieHeader = cookieHeader,
                referer = referer ?: pageUrl,
                expectedSha256 = expectedSha256
            )
        }
    }

    fun get(pageUrl: String): Entry? {
        val key = normalizeKey(pageUrl)
        val entry = cache[key] ?: return null
        if (entry.isExpired) {
            cache.remove(key)
            return null
        }
        return entry
    }

    fun clear() {
        cache.clear()
    }

    private fun normalizeKey(url: String): String =
        url.substringBefore('?').substringBefore('#').trimEnd('/')
}
