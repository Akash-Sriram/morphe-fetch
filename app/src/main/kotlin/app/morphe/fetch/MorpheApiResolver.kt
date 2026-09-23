package app.morphe.fetch

import android.util.Log
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Resolves indexed download URLs directly from Morphe's web-search API endpoint.
 * This mirrors the mechanism used by Morphe Manager, fetching cached/indexed mirror
 * release pages without relying on local Google searches.
 */
internal object MorpheApiResolver {
    private const val TAG = "MorpheApiResolver"
    private const val MORPHE_API_URL = "https://api.morphe.software"

    fun buildQueryUrl(packageName: String, version: String?, abi: String? = null): String {
        val resolvedAbi = abi ?: "arm64-v8a"
        val query = "$packageName~${version ?: "any"}~$resolvedAbi"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return "$MORPHE_API_URL/v2/web-search/$encodedQuery"
    }

    suspend fun resolve(packageName: String, version: String?, abi: String? = null): String? =
        withContext(Dispatchers.IO) {
            val url = buildQueryUrl(packageName, version, abi)
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", MorpheHttpClient.browserUserAgent)
                    .build()
                MorpheHttpClient.baseClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code in 300..399) {
                        val finalUrl = response.request.url.toString()
                        if (!finalUrl.contains("/v2/web-search/")) {
                            Log.d(TAG, "Resolved indexed URL: $finalUrl for $packageName $version")
                            return@use finalUrl
                        }
                    }
                    null
                }
            }.onFailure {
                Log.w(TAG, "Failed to resolve indexed URL from Morphe API: $url", it)
            }.getOrNull()
        }
}
