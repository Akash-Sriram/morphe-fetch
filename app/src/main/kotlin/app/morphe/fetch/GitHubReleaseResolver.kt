package app.morphe.fetch

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal object GitHubReleaseResolver {
    private const val TAG = "GitHubReleaseResolver"

    fun looksLikeGitHubRelease(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("github.com") && lower.contains("/releases")
    }

    suspend fun resolve(
        client: OkHttpClient,
        url: String,
        request: HelperRequest,
        option: CandidateOption
    ): List<DownloadCandidate> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = java.net.URI(url)
            val segments = uri.path.split('/').filter { it.isNotBlank() }
            if (segments.size < 2) return@withContext emptyList()
            val owner = segments[0]
            val repo = segments[1]

            val apiUrl = if (url.contains("/releases/tag/")) {
                val tag = segments.last()
                "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
            } else {
                "https://api.github.com/repos/$owner/$repo/releases/latest"
            }

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", MorpheHttpClient.MORPHE_FETCH_USER_AGENT)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body.string()
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").removePrefix("v")
                val assets = json.optJSONArray("assets") ?: JSONArray()

                val candidates = mutableListOf<DownloadCandidate>()
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val downloadUrl = asset.optString("browser_download_url")
                    val size = asset.optLong("size", 0L)
                    val kind = name.substringAfterLast('.', "").lowercase()
                    if (kind !in setOf("apk", "xapk", "apkm")) continue

                    val bestRank = archMatchRank(name, request.availableAbis)
                    if (bestRank >= ARCH_RANK_MISMATCH && request.availableAbis.isNotEmpty()) {
                        val hasAbiInName = listOf("arm64", "v7a", "x86", "universal")
                            .any { name.contains(it, ignoreCase = true) }
                        if (hasAbiInName) continue
                    }

                    candidates.add(
                        DownloadCandidate(
                            source = DownloadSource.AURORA,
                            name = request.appName.ifBlank { name },
                            packageName = request.packageName,
                            versionName = tagName.ifBlank { request.versionName },
                            versionCode = request.versionCode,
                            url = downloadUrl,
                            fileKind = kind,
                            option = option,
                            directDownload = true,
                            versionStatus = if (option == CandidateOption.REQUESTED) VersionStatus.REQUESTED else VersionStatus.LATEST,
                            formatMatches = request.acceptsFormat(kind),
                            files = listOf(
                                CandidateDownloadFile(
                                    url = downloadUrl,
                                    fileName = name.sanitizeFileName(),
                                    size = size.takeIf { it > 0 }
                                )
                            )
                        )
                    )
                }
                candidates
            }
        }.onFailure { Log.w(TAG, "Failed to resolve GitHub release for $url", it) }.getOrDefault(emptyList())
    }
}
