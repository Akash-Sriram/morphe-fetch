package app.morphe.fetch.aurora

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

internal data class ExodusReportItem(
    val versionName: String,
    val versionCode: Long,
    val date: String?
)

internal class ExodusVersionResolver(
    private val client: OkHttpClient,
    private val baseUrl: String = EXODUS_SEARCH_URL
) {
    companion object {
        private const val TAG = "ExodusVersionResolver"
        const val EXODUS_SEARCH_URL = "https://reports.exodus-privacy.eu.org/api/search/"
        private const val EXODUS_API_KEY = "bbe6ebae4ad45a9cbacb17d69739799b8df2c7ae"
    }

    private val cache = ConcurrentHashMap<String, List<ExodusReportItem>>()

    suspend fun getVersions(packageName: String): List<ExodusReportItem> = withContext(Dispatchers.IO) {
        cache[packageName]?.let { return@withContext it }

        val url = "$baseUrl$packageName"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Token $EXODUS_API_KEY")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Exodus request for $packageName returned HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body.string()
                val parsed = parseExodusReports(body, packageName)
                if (parsed.isNotEmpty()) {
                    cache[packageName] = parsed
                }
                parsed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve versions from Exodus for $packageName", e)
            emptyList()
        }
    }

    suspend fun resolveVersionCode(packageName: String, versionName: String): Long? {
        val versions = getVersions(packageName)
        val normalized = versionName.trim()
        val exactMatch = versions.firstOrNull { it.versionName.equals(normalized, ignoreCase = true) }
        if (exactMatch != null) return exactMatch.versionCode

        // Also check with leading 'v' stripped or minor difference
        val clean = normalized.removePrefix("v").removePrefix("V")
        return versions.firstOrNull {
            it.versionName.removePrefix("v").removePrefix("V").equals(clean, ignoreCase = true)
        }?.versionCode
    }

    private fun parseExodusReports(jsonStr: String, packageName: String): List<ExodusReportItem> {
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val pkgObj = root.getAsJsonObject(packageName) ?: return emptyList()
        val reportsArray = pkgObj.getAsJsonArray("reports") ?: return emptyList()

        val results = mutableListOf<ExodusReportItem>()
        for (elem in reportsArray) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val vName = obj.get("version")?.asString?.trim().orEmpty()
            val vCodeStr = obj.get("version_code")?.asString?.trim().orEmpty()
            val vCode = vCodeStr.toLongOrNull() ?: continue
            val date = obj.get("creation_date")?.asString?.take(10)
            if (vCode > 0L) {
                results.add(ExodusReportItem(versionName = vName, versionCode = vCode, date = date))
            }
        }
        return results.sortedByDescending { it.versionCode }
    }
}
