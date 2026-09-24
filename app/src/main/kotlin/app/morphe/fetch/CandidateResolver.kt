package app.morphe.fetch

import android.util.Log
import java.net.URLEncoder
import java.util.Locale

internal class CandidateResolver(
    private val parsers: Map<DownloadSource, ApkSourceParser>
) {
    companion object {
        private const val TAG = "CandidateResolver"
    }

    fun initialCandidateResult(
        request: HelperRequest,
        effectiveDisabledSources: Set<DownloadSource>,
        preferredSource: DownloadSource? = null
    ): CandidateResult {
        val manual = manualCandidates(request, effectiveDisabledSources)
        val enabled = DownloadSource.entries
            .filter { it !in effectiveDisabledSources }
        val ordered = preferredSource
            ?.takeIf { it in enabled }
            ?.let { preferred -> listOf(preferred) + enabled.filterNot { it == preferred } }
            ?: enabled
        return CandidateResult(
            sourceGroups = ordered.map { source ->
                SourceCandidateGroup(
                    source = source,
                    manual = manual.filter { it.source == source },
                    recommended = ResolveState.Idle,
                    latest = ResolveState.Idle
                )
            }
        )
    }

    suspend fun resolveSourceSection(
        request: HelperRequest,
        source: DownloadSource,
        option: CandidateOption,
        effectiveDisabledSources: Set<DownloadSource>
    ): ResolveOutcome {
        val lookup = runCatching { findSourceCandidates(request, source, option) }
            .onFailure { error ->
                if (error !is SourceAppNotFoundException) {
                    Log.w(TAG, "${source.label} ${option.name.lowercase(Locale.US)} lookup failed", error)
                }
            }
        lookup.exceptionOrNull()
            ?.takeIf { it is SourceAppNotFoundException }
            ?.let {
                return ResolveOutcome(
                    candidates = emptyList(),
                    notFoundMessage = "no listing for ${request.packageName}"
                )
            }
        lookup.exceptionOrNull()?.let { error ->
            return ResolveOutcome(
                candidates = emptyList(),
                errorMessage = sourceFailureMessage(source, error),
                fallbackCandidate = sourceErrorFallbackCandidate(request, source, option, effectiveDisabledSources, error)
            )
        }
        val sourceCandidates = lookup
            .getOrDefault(emptyList())
            .distinctBy(DownloadCandidate::identityKey)

        val candidates = when (option) {
            CandidateOption.REQUESTED -> recommendedCandidatesForSource(request, source, sourceCandidates)
            CandidateOption.LATEST -> latestCandidatesForSource(request, source, sourceCandidates, effectiveDisabledSources)
            CandidateOption.MANUAL -> emptyList()
        }

        return ResolveOutcome(
            candidates = candidates,
            errorMessage = lookup.exceptionOrNull()?.let { sourceFailureMessage(source, it) }
        )
    }

    suspend fun findSourceCandidates(
        request: HelperRequest,
        source: DownloadSource,
        option: CandidateOption
    ): List<DownloadCandidate> =
        parsers[source]?.findCandidates(request, option).orEmpty()

    fun recommendedCandidatesForSource(
        request: HelperRequest,
        source: DownloadSource,
        candidates: List<DownloadCandidate>
    ): List<DownloadCandidate> {
        if (!request.hasRequestedVersionRequest) {
            return emptyList()
        }

        return buildList {
            addAll(candidates.filter(request::isRequestedMatch))
            if (none { it.option == CandidateOption.REQUESTED }) {
                parsers[source]?.requestedFallbackCandidate(request)?.let(::add)
            }
        }
            .distinctBy(DownloadCandidate::identityKey)
            .sortedWith(
                compareBy<DownloadCandidate> { it.sortIndex }
                    .thenBy { it.archPriority(request.availableAbis) }
            )
    }

    fun latestCandidatesForSource(
        request: HelperRequest,
        source: DownloadSource,
        candidates: List<DownloadCandidate>,
        effectiveDisabledSources: Set<DownloadSource>
    ): List<DownloadCandidate> {
        return buildList {
            addAll(
                candidates
                    .filter { it.option == CandidateOption.LATEST }
                    .filter { request.requestsVariantBuild || !it.hasVariantBuildMarker }
            )
            if (none { it.option == CandidateOption.LATEST }) {
                parsers[source]?.latestFallbackCandidate(request)?.let(::add)
            }
        }
            .distinctBy(DownloadCandidate::identityKey)
            .sortedWith(
                compareBy<DownloadCandidate> { it.sortIndex }
                    .thenBy { it.archPriority(request.availableAbis) }
            )
    }

    fun sourceErrorFallbackCandidate(
        request: HelperRequest,
        source: DownloadSource,
        option: CandidateOption,
        effectiveDisabledSources: Set<DownloadSource>,
        error: Throwable? = null
    ): DownloadCandidate? {
        if (option == CandidateOption.MANUAL) return null

        val manual = manualCandidates(request, effectiveDisabledSources).firstOrNull { it.source == source } ?: return null
        val requestedVersionName = request.requestedVersionNames.firstOrNull()
        val requestedVersionCode = request.versionCode ?: request.versionCodes.singleOrNull()
        val challengeUrl = (error as? SourceChallengeException)?.challengeUrl
        val apkMirrorSearchUrl = if (source == DownloadSource.APK_MIRROR) {
            (parsers[source] as? ApkMirrorParser)?.apkMirrorBrowserSearchUrl(
                packageName = request.packageName,
                versionName = requestedVersionName,
                appName = request.appName
            )
        } else null
        val sourceSearchUrl = parsers[source]?.searchUrl(request.packageName, request.appName)
        val url = when (option) {
            CandidateOption.REQUESTED -> challengeUrl
                ?: request.sourceHintUrlsFor(source).firstOrNull()
                ?: apkMirrorSearchUrl
                ?: sourceSearchUrl
                ?: manual.url
            CandidateOption.LATEST -> challengeUrl ?: apkMirrorSearchUrl ?: sourceSearchUrl ?: manual.url
            CandidateOption.MANUAL -> manual.url
        }

        return manual.copy(
            versionName = requestedVersionName.takeIf { option == CandidateOption.REQUESTED },
            versionCode = requestedVersionCode.takeIf { option == CandidateOption.REQUESTED },
            url = url,
            fileKind = "web",
            option = option,
            directDownload = false,
            versionStatus = if (option == CandidateOption.REQUESTED) VersionStatus.REQUESTED else VersionStatus.LATEST,
            formatMatches = true,
            note = if (challengeUrl != null) "Cloudflare / Bot verification required. Solve the challenge to proceed." else null,
            captchaUrl = challengeUrl,
            variantLabel = null,
            files = emptyList()
        )
    }

    fun sourceVersionSearchUrl(request: HelperRequest, source: DownloadSource): String? =
        parsers[source]?.searchUrl(request.packageName, request.appName)


    fun manualCandidates(
        request: HelperRequest,
        effectiveDisabledSources: Set<DownloadSource>
    ): List<DownloadCandidate> =
        manualSourceUrls(request, effectiveDisabledSources).map { (source, url) ->
            DownloadCandidate(
                source = source,
                name = request.appName,
                packageName = request.packageName,
                versionName = null,
                versionCode = null,
                url = url,
                fileKind = "web",
                option = CandidateOption.MANUAL,
                directDownload = false,
                versionStatus = VersionStatus.LATEST,
                formatMatches = true
            )
        }.sortedBy { it.sortIndex }

    fun latestWebFallback(
        request: HelperRequest,
        source: DownloadSource,
        effectiveDisabledSources: Set<DownloadSource>
    ): DownloadCandidate? =
        manualSourceUrls(request, effectiveDisabledSources)
            .firstOrNull { (candidateSource, _) -> candidateSource == source }
            ?.let { (_, url) ->
                DownloadCandidate(
                    source = source,
                    name = request.appName,
                    packageName = request.packageName,
                    versionName = null,
                    versionCode = null,
                    url = url,
                    fileKind = "web",
                    option = CandidateOption.LATEST,
                    directDownload = false,
                    versionStatus = VersionStatus.LATEST,
                    formatMatches = true
                )
            }

    fun manualSourceUrls(
        request: HelperRequest,
        effectiveDisabledSources: Set<DownloadSource>
    ): List<Pair<DownloadSource, String>> =
        parsers.values
            .filter { it.source !in effectiveDisabledSources }
            .mapNotNull { parser ->
                parser.searchUrl(request.packageName, request.appName)?.let { parser.source to it }
            }
            .sortedBy { it.first.ordinal }
}

private fun DownloadCandidate.archPriority(availableAbis: List<String>): Int {
    val arch = variantLabel?.lowercase(Locale.US) ?: ""
    val idx = availableAbis.indexOfFirst { it.equals(arch, ignoreCase = true) }
    return if (idx >= 0) idx else if (arch.isUniversalArchLabel()) 10 else 999
}
