package app.morphe.fetch

import android.util.Log
import okhttp3.OkHttpClient

internal object MorpheLinkParser {
    private const val TAG = "MorpheLinkParser"

    fun canParse(url: String): Boolean {
        val lower = url.lowercase()
        return GitHubReleaseResolver.looksLikeGitHubRelease(lower) ||
            lower.endsWith(".apk") || lower.endsWith(".xapk") || lower.endsWith(".apkm") ||
            lower.contains("apkmirror.com") || lower.contains("apkpure.com") || lower.contains("uptodown.com")
    }

    suspend fun parseLink(
        url: String,
        request: HelperRequest,
        option: CandidateOption,
        client: OkHttpClient,
        parsers: Map<DownloadSource, ApkSourceParser>
    ): List<DownloadCandidate> {
        val lower = url.lowercase()
        if (GitHubReleaseResolver.looksLikeGitHubRelease(url)) {
            return GitHubReleaseResolver.resolve(client, url, request, option)
        }

        val directKind = when {
            lower.endsWith(".xapk") || lower.contains(".xapk?") -> "xapk"
            lower.endsWith(".apkm") || lower.contains(".apkm?") -> "apkm"
            lower.endsWith(".apks") || lower.contains(".apks?") -> "apks"
            lower.endsWith(".apk") || lower.contains(".apk?") -> "apk"
            else -> null
        }
        if (directKind != null) {
            val fileName = runCatching { java.net.URI(url).path.substringAfterLast('/') }
                .getOrDefault("${request.packageName}.$directKind")
            return listOf(
                DownloadCandidate(
                    source = morpheResolvedSource(url) ?: DownloadSource.AURORA,
                    name = request.appName,
                    packageName = request.packageName,
                    versionName = request.requestedVersionName,
                    versionCode = request.versionCode ?: request.requestedVersionCodes.firstOrNull(),
                    url = url,
                    fileKind = directKind,
                    option = option,
                    directDownload = true,
                    versionStatus = if (option == CandidateOption.REQUESTED) VersionStatus.REQUESTED else VersionStatus.LATEST,
                    formatMatches = request.acceptsFormat(directKind),
                    files = listOf(
                        CandidateDownloadFile(
                            url = url,
                            fileName = fileName.sanitizeFileName()
                        )
                    )
                )
            )
        }

        val source = morpheResolvedSource(url) ?: return emptyList()
        val parser = parsers[source] ?: return emptyList()
        return runCatching {
            parser.findCandidatesFromResolvedUrl(request, url, option).orEmpty()
        }.onFailure { Log.w(TAG, "Failed to resolve link with ${source.label} parser: $url", it) }.getOrDefault(emptyList())
    }
}
