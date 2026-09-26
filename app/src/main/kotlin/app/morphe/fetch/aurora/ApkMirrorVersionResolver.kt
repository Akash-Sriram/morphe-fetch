package app.morphe.fetch.aurora

import android.util.Log
import app.morphe.fetch.MorpheHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal object ApkMirrorVersionResolver {
    private const val TAG = "ApkMirrorVersionResolver"
    private val cache = ConcurrentHashMap<String, Long>()

    fun cacheVersionCode(packageName: String, versionName: String, versionCode: Long) {
        val key = cacheKey(packageName, versionName)
        cache[key] = versionCode
    }

    fun getCachedVersionCode(packageName: String, versionName: String): Long? {
        return cache[cacheKey(packageName, versionName)]
    }

    private fun cacheKey(packageName: String, versionName: String): String =
        "${packageName.trim().lowercase()}:${versionName.trim().lowercase()}"

    suspend fun resolveVersionCode(
        packageName: String,
        versionName: String,
        appName: String? = null,
        client: OkHttpClient = MorpheHttpClient.apkMirrorClient
    ): Long? = withContext(Dispatchers.IO) {
        getCachedVersionCode(packageName, versionName)?.let { return@withContext it }

        val query = buildList {
            appName?.takeIf { it.isNotBlank() }?.let(::add)
            add(versionName.trim())
            if (isEmpty()) add(packageName.trim())
        }.joinToString(" ")

        val searchUrl = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=${URLEncoder.encode(query, "UTF-8")}"

        val searchHtml = runCatching {
            val req = Request.Builder().url(searchUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body.string() else null
            }
        }.getOrNull() ?: return@withContext null

        val searchDoc = Jsoup.parse(searchHtml, searchUrl)
        val releaseLinks = searchDoc.select("a[href*=-release/]")
        val releaseUrl = releaseLinks.firstOrNull { link ->
            val href = link.attr("href")
            href.contains("-release", ignoreCase = true) && !href.contains("/apk/apkmirror/", ignoreCase = true)
        }?.absUrl("href") ?: return@withContext null

        val releaseHtml = runCatching {
            val req = Request.Builder().url(releaseUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body.string() else null
            }
        }.getOrNull() ?: return@withContext null

        val releaseDoc = Jsoup.parse(releaseHtml, releaseUrl)
        val rows = releaseDoc.select("div.variants-table div.table-row")
        for (row in rows) {
            val firstCellText = row.select("div.table-cell").firstOrNull()?.text().orEmpty()
            val rowVersionCode = Regex("""\b(\d{6,11})\b""").find(firstCellText)?.groupValues?.get(1)?.toLongOrNull()
                ?: Regex("""\b(\d{6,11})\b""").find(row.text())?.groupValues?.get(1)?.toLongOrNull()
            if (rowVersionCode != null && rowVersionCode > 0L) {
                cacheVersionCode(packageName, versionName, rowVersionCode)
                Log.i(TAG, "Resolved versionCode=$rowVersionCode for $packageName $versionName from APKMirror")
                return@withContext rowVersionCode
            }
        }
        null
    }
}
