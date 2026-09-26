package app.morphe.fetch

import android.net.Uri
import android.util.Log
import java.net.URLEncoder
import java.util.Locale
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** App-listing slugs that carry a different version stream than the canonical app. */
private val APKMIRROR_EDITION_SLUG_REGEX = Regex(
    """(amazon|fire-tablet|fire-tv|androidtv|wear|go-edition|lite|beta|alpha|enterprise|kids|headunit|auto)""",
    RegexOption.IGNORE_CASE
)

internal data class ApkMirrorHistoryItem(
    val url: String,
    val versionName: String?,
    val releaseDate: String?,
    val releaseSuffix: String?
)

internal class ApkMirrorParser(private val ctx: SourceParserContext) : ApkSourceParser {
    override val source: DownloadSource = DownloadSource.APK_MIRROR

    override suspend fun findCandidates(
        request: HelperRequest,
        option: CandidateOption
    ): List<DownloadCandidate> = when (option) {
        CandidateOption.REQUESTED -> findApkMirrorRequested(request)
        CandidateOption.LATEST -> findApkMirrorLatest(request)
        CandidateOption.MANUAL -> emptyList()
    }

    override fun searchUrl(packageName: String): String? = apkMirrorBrowserSearchUrl(packageName)
    override fun searchUrl(packageName: String, appName: String?): String? =
        apkMirrorBrowserSearchUrl(packageName, appName = appName)

    fun apkMirrorBrowserSearchUrl(
        packageName: String,
        versionName: String? = null,
        appName: String? = null
    ): String {
        val baseQuery = if (!appName.isNullOrBlank() && !appName.equals(packageName, ignoreCase = true)) {
            appName.trim()
        } else {
            packageName.trim()
        }
        val query = if (!versionName.isNullOrBlank()) "$baseQuery $versionName" else baseQuery
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=$encoded"
    }

    override fun requestedFallbackCandidate(request: HelperRequest): DownloadCandidate =
        apkMirrorRequested(request)

    override fun latestFallbackCandidate(request: HelperRequest): DownloadCandidate =
        apkMirrorLatest(request)

    private val historyCache = HashMap<String, List<DownloadCandidate>>()

    override suspend fun resolveHistory(request: HelperRequest): List<DownloadCandidate> {
        historyCache[request.packageName]?.let { return it }
        val result = resolveHistoryUncached(request)
        historyCache[request.packageName] = result
        return result
    }

    private suspend fun resolveHistoryUncached(request: HelperRequest): List<DownloadCandidate> {
        val searchDoc = fetchDocument(apkMirrorPackageSearchUrl(request.packageName))
        val appPageUrl = resolveApkMirrorAppPage(searchDoc, request) ?: return emptyList()
        val category = appPageUrl.trimEnd('/').substringAfterLast('/').takeIf(String::isNotBlank)
            ?: return emptyList()

        val historyItems = mutableListOf<ApkMirrorHistoryItem>()
        for (page in 1..3) {
            val pageUrl = if (page == 1) {
                apkMirrorUploadsUrl(appPageUrl)
            } else {
                "https://www.apkmirror.com/uploads/page/$page/?appcategory=$category"
            }
            val doc: Document = try {
                fetchDocument(pageUrl, referer = appPageUrl)
            } catch (e: HttpRateLimitedException) {
                break
            } catch (e: Exception) {
                continue
            }
            // Filter by the app page path: the uploads page also carries
            // trending/sidebar release links for other apps, which must not
            // leak into this app's version history.
            val items = apkMirrorHistoryItems(doc, appPageUrl)
            if (items.isEmpty()) break
            historyItems += items
        }

        return historyItems
            .distinctBy { it.url }
            .mapNotNull { item ->
                val versionName = item.versionName ?: apkMirrorVersionFromReleaseUrl(item.url, appPageUrl) ?: return@mapNotNull null
                DownloadCandidate(
                    source = DownloadSource.APK_MIRROR,
                    name = request.appName,
                    packageName = request.packageName,
                    versionName = versionName,
                    versionCode = null,
                    url = item.url,
                    fileKind = "web",
                    option = CandidateOption.LATEST,
                    directDownload = false,
                    versionStatus = request.versionStatus(versionName, null),
                    formatMatches = true,
                    note = null,
                    releaseDate = item.releaseDate,
                    releaseSuffix = item.releaseSuffix
                )
            }
            .sortedWith { left, right ->
                val versionCmp = compareVersionNames(right.versionName, left.versionName)
                if (versionCmp != 0) {
                    versionCmp
                } else {
                    val leftSuffixRank = if (left.releaseSuffix == null) 0 else 1
                    val rightSuffixRank = if (right.releaseSuffix == null) 0 else 1
                    leftSuffixRank.compareTo(rightSuffixRank)
                }
            }
    }

    override suspend fun resolveHistoryCandidate(
        request: HelperRequest,
        candidate: DownloadCandidate
    ): DownloadCandidate? {
        // History rows carry the release-page URL. Resolve it the same way the
        // Recommended/Latest tabs do; return null when no direct download can be
        // produced so the UI can offer an "Open link" action instead.
        return runCatching {
            apkMirrorCandidatesFromReleaseUrl(
                request = request,
                releaseUrl = candidate.url,
                versionName = candidate.versionName,
                option = candidate.option
            )
        }
            .getOrNull()
            ?.firstOrNull { it.directDownload }
    }

    private fun apkMirrorRequested(request: HelperRequest) = DownloadCandidate(
        source = DownloadSource.APK_MIRROR,
        name = request.appName,
        packageName = request.packageName,
        versionName = request.versionName,
        versionCode = request.versionCode ?: request.versionCodes.singleOrNull(),
        url = request.sourceHintUrlsFor(DownloadSource.APK_MIRROR).firstOrNull()
            ?: apkMirrorBrowserSearchUrl(request.packageName, request.versionName, request.appName),
        fileKind = request.requestedFormatLabel,
        option = CandidateOption.REQUESTED,
        directDownload = false,
        versionStatus = VersionStatus.REQUESTED,
        formatMatches = true
    )

    private fun apkMirrorLatest(request: HelperRequest): DownloadCandidate =
        apkMirrorLatestWebCandidate(request, runCatching {
            resolveApkMirrorLatestInfo(request, apkMirrorPackageSearchUrl(request.packageName))
        }.getOrNull())

    private fun findApkMirrorLatest(request: HelperRequest): List<DownloadCandidate> {
        val latestInfo = resolveApkMirrorLatestInfo(
            request = request,
            searchUrl = apkMirrorPackageSearchUrl(request.packageName)
        )
        val latestReleaseUrl = latestInfo
            ?.openUrl
            ?.takeIf(::apkMirrorLooksLikeReleaseUrl)
        val directCandidates = latestReleaseUrl?.let { releaseUrl ->
            apkMirrorCandidatesFromReleaseUrl(
                request = request,
                releaseUrl = releaseUrl,
                versionName = latestInfo.versionName ?: apkMirrorVersionFromReleaseUrl(releaseUrl),
                option = CandidateOption.LATEST
            )
        }

        return directCandidates.orEmpty()
            .ifEmpty { listOf(apkMirrorLatestWebCandidate(request, latestInfo)) }
    }

    private fun apkMirrorLatestWebCandidate(
        request: HelperRequest,
        latestInfo: ApkMirrorLatestInfo?
    ): DownloadCandidate {
        val searchUrl = latestInfo?.openUrl
            ?: apkMirrorBrowserSearchUrl(request.packageName, latestInfo?.versionName, request.appName)
        return DownloadCandidate(
            source = DownloadSource.APK_MIRROR,
            name = request.appName,
            packageName = request.packageName,
            versionName = latestInfo?.versionName,
            versionCode = null,
            url = searchUrl,
            fileKind = "web",
            option = CandidateOption.LATEST,
            directDownload = false,
            versionStatus = VersionStatus.LATEST,
            formatMatches = true
        )
    }

    private fun findApkMirrorRequested(request: HelperRequest): List<DownloadCandidate> {
        if (!request.hasKnownVersionRequest) return emptyList()

        // 1. Fast path: search directly for the specific release query (e.g. "Google Photos 7.93.0.982110057")
        val directSearchUrl = apkMirrorBrowserSearchUrl(
            packageName = request.packageName,
            versionName = request.requestedVersionNames.firstOrNull(),
            appName = request.appName
        )
        val directCandidates = runCatching {
            val directDoc = fetchDocument(directSearchUrl)
            val releaseUrl = apkMirrorReleaseLinks(directDoc).firstOrNull { relUrl ->
                val v = request.requestedVersionNames.firstOrNull()
                v == null || apkMirrorReleaseUrlMatchesVersion(relUrl, v)
            }
            releaseUrl?.let { relUrl ->
                apkMirrorCandidatesFromReleaseUrl(
                    request = request,
                    releaseUrl = relUrl,
                    versionName = request.requestedVersionNames.firstOrNull()
                        ?: apkMirrorVersionFromReleaseUrl(relUrl),
                    option = CandidateOption.REQUESTED
                )
            }
        }.getOrNull()

        if (!directCandidates.isNullOrEmpty()) {
            return directCandidates
        }

        // 2. Fall back to app page and uploads list crawl
        val searchDoc = fetchDocument(apkMirrorPackageSearchUrl(request.packageName))
        val appPageUrl = resolveApkMirrorAppPage(
            searchDoc = searchDoc,
            request = request
        ) ?: throw SourceAppNotFoundException(request.packageName)
        val appDoc = fetchDocument(appPageUrl)
        val requested = apkMirrorRequestedReleaseUrl(
            request = request,
            appPageUrl = appPageUrl,
            appDoc = appDoc
        )?.let { releaseUrl ->
            apkMirrorCandidatesFromReleaseUrl(
                request = request,
                releaseUrl = releaseUrl,
                versionName = request.requestedVersionNames.firstOrNull()
                    ?: apkMirrorVersionFromReleaseUrl(releaseUrl),
                option = CandidateOption.REQUESTED
            )
        }

        return requested.orEmpty()
    }

    private fun apkMirrorPackageSearchUrl(packageName: String): String {
        val query = URLEncoder.encode(packageName, "UTF-8")
        return "https://www.apkmirror.com/?post_type=app_release&searchtype=app&s=$query"
    }

    private fun resolveApkMirrorLatestInfo(
        request: HelperRequest,
        searchUrl: String
    ): ApkMirrorLatestInfo? {
        val searchDoc = fetchDocument(searchUrl)
        val appPageUrl = resolveApkMirrorAppPage(
            searchDoc = searchDoc,
            request = request
        ) ?: throw SourceAppNotFoundException(request.packageName)
        val searchVersion = apkMirrorSearchVersionForApp(searchDoc, appPageUrl)
            ?: apkMirrorVersions(searchDoc.html()).firstOrNull()
        val appDoc = fetchDocument(appPageUrl)
        val uploadsDoc = runCatching { fetchDocument(apkMirrorUploadsUrl(appPageUrl), referer = appPageUrl) }
            .getOrNull()
        val latestReleaseUrl = apkMirrorLatestReleaseUrl(
            (
                uploadsDoc?.let { apkMirrorReleaseLinks(it, appPageUrl) }.orEmpty() +
                    apkMirrorReleaseLinks(appDoc, appPageUrl)
                ).distinct()
        )
        val latestVersion = latestReleaseUrl?.let { apkMirrorVersionFromReleaseUrl(it, appPageUrl) }
            ?: searchVersion
            ?: apkMirrorVersions(appDoc.html()).firstOrNull()

        return ApkMirrorLatestInfo(
            versionName = latestVersion,
            openUrl = latestReleaseUrl ?: appPageUrl
        )
    }

    private fun resolveApkMirrorAppPage(searchDoc: Document, request: HelperRequest): String? {
        if (searchDoc.isApkMirrorNoResults()) return null
        for (candidate in apkMirrorAppPageCandidates(searchDoc, request)) {
            val candidateDoc: Document = try {
                fetchDocument(candidate)
            } catch (e: HttpRateLimitedException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            if (candidateDoc.apkMirrorMatchesPackage(request.packageName)) return candidate
        }

        return null
    }

    private fun apkMirrorAppPageCandidates(searchDoc: Document, request: HelperRequest): List<String> {
        val expectedSlugs = apkMirrorExpectedAppSlugs(request)
        return searchDoc.select("a[href]")
            .asSequence()
            .mapNotNull { apkMirrorAbsoluteUrl(it.attr("href")) }
            .filter { url ->
                runCatching {
                    java.net.URI(url).path.matches(Regex("""/apk/[^/]+/[^/]+/?"""))
                }.getOrDefault(false)
            }
            .distinct()
            .sortedBy { url -> apkMirrorAppSlugScore(url, expectedSlugs) }
            .take(20)
            .toList()
    }

    private fun apkMirrorExpectedAppSlugs(request: HelperRequest): Set<String> =
        buildSet {
            add(request.appName.slugForUrl())
            val packageParts = request.packageName.split(".")
            packageParts.takeLast(2)
                .joinToString("-")
                .slugForUrl()
                .takeIf(String::isNotBlank)
                ?.let(::add)
            packageParts.takeLast(3)
                .joinToString("-")
                .slugForUrl()
                .takeIf(String::isNotBlank)
                ?.let(::add)
        }

    private fun apkMirrorAppSlugScore(url: String, expectedSlugs: Set<String>): Int {
        val segments = runCatching {
            java.net.URI(url).path.trim('/').split('/')
        }.getOrDefault(emptyList())
        // Path layout: /apk/{developer}/{app-slug}/  the developer segment
        // alone is ambiguous when a publisher lists several editions (e.g.
        // TikTok vs its Amazon Appstore edition), so score the app segment too.
        val developerSlug = segments.getOrNull(1).orEmpty()
        val appSlug = segments.getOrNull(2).orEmpty()

        fun scoreSlug(slug: String): Int = when {
            slug in expectedSlugs -> 0
            expectedSlugs.any { slug.endsWith(it) } -> 1
            expectedSlugs.any { slug.contains(it) } -> 2
            else -> 3
        }

        // Score both the developer and the app segment (e.g. TikTok's canonical
        // listing vs its Amazon Appstore edition live under the same developer).
        var score = minOf(scoreSlug(developerSlug), scoreSlug(appSlug))

        // Prefer the canonical listing over special editions, which share the
        // package name but carry different version streams (amazon, lite, tv...).
        if (APKMIRROR_EDITION_SLUG_REGEX.containsMatchIn(appSlug)) score += 4
        return score
    }

    private fun apkMirrorSearchVersionForApp(searchDoc: Document, appPageUrl: String): String? {
        val appPath = runCatching { java.net.URI(appPageUrl).path.trimEnd('/') }.getOrNull()
            ?: return null
        val row = searchDoc.select("div.appRow")
            .firstOrNull { row ->
                row.select("a[href]")
                    .asSequence()
                    .map { it.absUrl("href") }
                    .any { url ->
                        runCatching { java.net.URI(url).path.trimEnd('/') == appPath }
                            .getOrDefault(false)
                    }
            }

        return row
            ?.select("img[alt]")
            ?.asSequence()
            ?.map { it.attr("alt") }
            ?.mapNotNull { alt -> Regex("""\b(\d+(?:\.\d+)+(?:[.\w-]*)?)\b""").findAll(alt).lastOrNull()?.value }
            ?.firstOrNull()
    }

    private fun apkMirrorVersions(html: String): List<String> =
        Regex(
            """Version:\s*</span>\s*<span[^>]*class=["'][^"']*infoSlide-value[^"']*["'][^>]*>\s*([^<]+?)\s*</span>""",
            RegexOption.IGNORE_CASE
        )
            .findAll(html)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
            .filterNot { it.contains("alpha", ignoreCase = true) || it.contains("beta", ignoreCase = true) }
            .distinct()
            .toList()

    private fun apkMirrorReleaseLinks(doc: Document, appPageUrl: String? = null): List<String> =
        apkMirrorHistoryItems(doc, appPageUrl).map { it.url }

    private fun apkMirrorHistoryItems(doc: Document, appPageUrl: String? = null): List<ApkMirrorHistoryItem> {
        val appPath = appPageUrl
            ?.let { url -> runCatching { java.net.URI(url).path.trimEnd('/') }.getOrNull() }

        val byUrl = LinkedHashMap<String, ApkMirrorHistoryItem>()
        val links = doc.select("a[href]")

        for (link in links) {
            val rawHref = link.attr("href")
            val absUrl = apkMirrorAbsoluteUrl(rawHref) ?: continue
            val path = runCatching { java.net.URI(absUrl).path }.getOrNull() ?: continue
            if (!path.startsWith("/apk/") || !path.trimEnd('/').endsWith("-release")) continue
            if (appPath != null && !path.startsWith("$appPath/")) continue

            val row = link.parents().firstOrNull { parent ->
                parent.hasClass("appRow") || parent.hasClass("table-row") || parent.tagName().equals("tr", ignoreCase = true)
            }

            val linkText = link.text().trim()
            val suffix = extractReleaseSuffix(absUrl, linkText)
            val versionName = apkMirrorVersionFromReleaseUrl(absUrl, appPageUrl)

            val rawDate = row?.selectFirst(".dateyear_utc, span[data-utcdate], span.date, .utcdate")?.text()?.trim()
                ?: row?.text()?.let { text ->
                    Regex("""\b(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \d{1,2}, \d{4}\b""", RegexOption.IGNORE_CASE)
                        .find(text)?.value
                }

            val formattedDate = rawDate?.let(::formatApkMirrorDate)?.takeIf { it.isNotBlank() }

            val existing = byUrl[absUrl]
            if (existing == null) {
                byUrl[absUrl] = ApkMirrorHistoryItem(
                    url = absUrl,
                    versionName = versionName,
                    releaseDate = formattedDate,
                    releaseSuffix = suffix
                )
            } else {
                byUrl[absUrl] = existing.copy(
                    versionName = existing.versionName ?: versionName,
                    releaseDate = existing.releaseDate ?: formattedDate,
                    releaseSuffix = existing.releaseSuffix ?: suffix
                )
            }
        }

        return byUrl.values.toList()
    }

    private fun extractReleaseSuffix(url: String, linkText: String? = null): String? {
        if (!linkText.isNullOrBlank()) {
            val parenMatch = Regex("""\(([^)]+)\)""").find(linkText)
            if (parenMatch != null) {
                val inside = parenMatch.groupValues[1].trim()
                if (inside.isNotEmpty() && !inside.all { it.isDigit() }) {
                    return normalizeSuffix(inside)
                }
            }
        }

        val path = runCatching { java.net.URI(url).path.trimEnd('/') }.getOrNull() ?: return null
        val slug = path.substringAfterLast('/').removeSuffix("-release")
        val versionMatch = Regex("""\d+(?:[-.]\d+)+""").findAll(slug).lastOrNull() ?: return null
        val remainder = slug.substring(versionMatch.range.last + 1).trim('-', '.', '_')
        if (remainder.isNotBlank() && !remainder.all { it.isDigit() }) {
            return normalizeSuffix(remainder)
        }
        return null
    }

    private fun normalizeSuffix(raw: String): String {
        val clean = raw.trim()
        return when (clean.lowercase(Locale.US)) {
            "secondary" -> "Secondary"
            "beta" -> "Beta"
            "alpha" -> "Alpha"
            "wear", "wear-os", "wearos", "wear os" -> "Wear OS"
            "tv", "android-tv", "androidtv", "android tv" -> "Android TV"
            "bundle" -> "Bundle"
            "leanback" -> "Leanback"
            "rc" -> "RC"
            "dev" -> "Dev"
            "canary" -> "Canary"
            else -> clean.replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    when (word.lowercase(Locale.US)) {
                        "os" -> "OS"
                        "tv" -> "TV"
                        "rc" -> "RC"
                        else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    }
                }
        }
    }

    private fun formatApkMirrorDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val patterns = listOf(
            "MMMM d, yyyy",
            "MMM d, yyyy",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US)
                val date = java.time.LocalDate.parse(trimmed, formatter)
                val outputFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
                return date.format(outputFormatter)
            } catch (_: Exception) {}
        }
        return trimmed
    }

    internal fun apkMirrorLatestReleaseUrl(releaseLinks: List<String>): String? {
        // "-SECONDARY" is an alternate package of the same version Morphe
        // cannot patch, so it must never be offered as "latest".
        val patchable = releaseLinks.filterNot { it.hasVariantBuildMarker() }
        if (patchable.isEmpty()) return null
        return patchable
            .filterNot { it.contains("alpha", ignoreCase = true) || it.contains("beta", ignoreCase = true) }
            .ifEmpty { patchable }
            .maxWithOrNull { left, right ->
                compareVersionNames(
                    apkMirrorVersionFromReleaseUrl(left),
                    apkMirrorVersionFromReleaseUrl(right)
                )
            }
    }

    private fun apkMirrorRequestedReleaseUrl(
        request: HelperRequest,
        appPageUrl: String,
        appDoc: Document
    ): String? {
        val requestedVersions = request.requestedVersionNames
        if (requestedVersions.isEmpty()) return null

        request.sourceHintUrlsFor(DownloadSource.APK_MIRROR)
            .asSequence()
            .mapNotNull(::apkMirrorAbsoluteUrl)
            .filter(::apkMirrorLooksLikeReleaseUrl)
            .toList()
            .let { urls -> apkMirrorPreferredReleaseUrl(urls, requestedVersions) }
            ?.let { return it }

        apkMirrorPreferredReleaseUrl(apkMirrorReleaseLinks(appDoc, appPageUrl), requestedVersions)
            ?.let { return it }

        val category = appPageUrl.trimEnd('/').substringAfterLast('/').takeIf(String::isNotBlank)
            ?: return null
        val uploadsUrl = "https://www.apkmirror.com/uploads/?appcategory=$category"
        for (page in 1..5) {
            val pageUrl = if (page == 1) {
                uploadsUrl
            } else {
                "https://www.apkmirror.com/uploads/page/$page/?appcategory=$category"
            }
            val doc: Document = try {
                fetchDocument(pageUrl, referer = appPageUrl)
            } catch (e: HttpRateLimitedException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "APKMirror uploads page resolve failed: $pageUrl", e)
                continue
            }

            apkMirrorPreferredReleaseUrl(apkMirrorReleaseLinks(doc), requestedVersions)
                ?.let { return it }
        }

        return null
    }

    /**
     * Among release URLs matching one of [requestedVersions], prefer the normal
     * build. APKMirror publishes alternate "-SECONDARY" builds of the same
     * version which Morphe cannot patch; they parse to the same version number,
     * so without this the first-match race hands one to the caller.
     */
    internal fun apkMirrorPreferredReleaseUrl(
        urls: List<String>,
        requestedVersions: List<String>
    ): String? {
        val matching = urls.filter { url ->
            requestedVersions.any { version -> apkMirrorReleaseUrlMatchesVersion(url, version) }
        }
        return matching.firstOrNull { !it.hasVariantBuildMarker() } ?: matching.firstOrNull()
    }

    private fun apkMirrorUploadsUrl(appPageUrl: String): String {
        val category = runCatching {
            Uri.parse(appPageUrl).path
                ?.trim('/')
                ?.substringAfterLast('/')
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
            ?: appPageUrl.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
        return "https://www.apkmirror.com/uploads/?appcategory=$category"
    }

    private fun apkMirrorCandidatesFromReleaseUrl(
        request: HelperRequest,
        releaseUrl: String,
        versionName: String?,
        option: CandidateOption
    ): List<DownloadCandidate> {
        val directCandidates = runCatching {
            apkMirrorDirectCandidatesFromReleaseUrl(
                request = request,
                releaseUrl = releaseUrl,
                versionName = versionName,
                option = option
            )
        }
            .onFailure { Log.w(TAG, "APKMirror direct resolve failed: $releaseUrl", it) }
        val directFailureMessage = directCandidates.exceptionOrNull()
            ?.let { sourceFailureMessage(DownloadSource.APK_MIRROR, it) }
        val resolvedDirectCandidates = directCandidates.getOrDefault(emptyList())

        return resolvedDirectCandidates.ifEmpty {
            listOf(
                apkMirrorReleaseFallbackCandidate(
                    request = request,
                    releaseUrl = releaseUrl,
                    versionName = versionName,
                    option = option,
                    note = directFailureMessage
                )
            )
        }
    }

    private fun apkMirrorDirectCandidatesFromReleaseUrl(
        request: HelperRequest,
        releaseUrl: String,
        versionName: String?,
        option: CandidateOption
    ): List<DownloadCandidate> {
        val releaseDoc = fetchDocument(releaseUrl)
        if (releaseDoc.isCloudflareChallenge()) {
            throw SourceChallengeException(
                "APKMirror showed a Cloudflare browser-verification page instead of the release.",
                challengeUrl = releaseUrl
            )
        }

        val variants = apkMirrorVariants(releaseDoc)
        val selectedVariants = if (variants.isEmpty()) {
            listOf(null)
        } else {
            apkMirrorSelectableVariants(request, variants)
        }
        if (selectedVariants.isEmpty()) return emptyList()

        return selectedVariants
            .mapNotNull { variant ->
                apkMirrorDirectCandidateFromReleaseUrl(
                    request = request,
                    releaseUrl = releaseUrl,
                    versionName = versionName,
                    option = option,
                    variant = variant
                )
            }
            .distinctBy(DownloadCandidate::identityKey)
    }

    private fun apkMirrorDirectCandidateFromReleaseUrl(
        request: HelperRequest,
        releaseUrl: String,
        versionName: String?,
        option: CandidateOption,
        variant: ApkMirrorVariant?
    ): DownloadCandidate? {
        val variantPageUrl = variant?.url ?: releaseUrl
        val variantDoc = if (variant == null) {
            fetchDocument(releaseUrl)
        } else {
            fetchDocument(variantPageUrl, referer = releaseUrl)
        }
        if (variantDoc.isCloudflareChallenge()) {
            throw SourceChallengeException(
                "APKMirror showed a Cloudflare browser-verification page instead of the variant.",
                challengeUrl = variantPageUrl
            )
        }

        val isBundle = variant?.isBundle ?: apkMirrorPageLooksBundle(variantDoc)
        val downloadButtonUrl = apkMirrorDownloadButtonUrl(variantDoc, isBundle) ?: return null
        val downloadDoc = fetchDocument(downloadButtonUrl, referer = variantPageUrl)
        if (downloadDoc.isCloudflareChallenge()) {
            throw SourceChallengeException(
                "APKMirror showed a Cloudflare browser-verification page instead of the download page.",
                challengeUrl = downloadButtonUrl
            )
        }

        val finalUrl = apkMirrorFinalDownloadUrl(downloadDoc) ?: return null
        val resolvedVersion = versionName
            ?: apkMirrorVersionFromReleaseUrl(releaseUrl)
        val fileKind = variant?.fileKind ?: if (isBundle) "apkm" else fileKindFromUrl(finalUrl)
        val variantLabel = variant?.displayLabel()
        val variantFileSuffix = variantLabel.variantFileSuffix()
        // APKMirror publishes the file's SHA-256 in the variant page's safe-
        // download modal, under the "APK file hashes" block. Single APKs carry
        // it; bundles don't (there is no single file to hash).
        val expectedSha256 = if (isBundle) null else apkMirrorVariantFileSha256(variantDoc)
        val extractedVersionCode = variant?.versionCode ?: apkMirrorExtractVersionCode(variantDoc)
        if (extractedVersionCode != null && !resolvedVersion.isNullOrBlank()) {
            app.morphe.fetch.aurora.ApkMirrorVersionResolver.cacheVersionCode(
                request.packageName,
                resolvedVersion,
                extractedVersionCode
            )
        }

        return DownloadCandidate(
            source = DownloadSource.APK_MIRROR,
            name = request.appName,
            packageName = request.packageName,
            versionName = resolvedVersion,
            versionCode = extractedVersionCode,
            url = finalUrl,
            fileKind = fileKind,
            option = option,
            directDownload = true,
            captchaUrl = downloadButtonUrl,
            versionStatus = request.versionStatus(resolvedVersion, extractedVersionCode),
            formatMatches = request.acceptsFormat(fileKind),
            variantLabel = variantLabel,
            files = listOf(
                CandidateDownloadFile(
                    url = finalUrl,
                    fileName = "${request.packageName}-${resolvedVersion ?: option.name.lowercase(Locale.US)}-apkmirror$variantFileSuffix.$fileKind"
                        .sanitizeFileName(),
                    referer = downloadButtonUrl,
                    expectedSha256 = expectedSha256
                )
            )
        )
    }

    /**
     * The file's SHA-256 from the variant page's safe-download modal.
     *
     * The modal lists hashes under two labels: "APK file hashes" (the file's
     * MD5/SHA-1/SHA-256) and "APK certificate fingerprints" (the signer's
     * SHA-1/SHA-256). The file hash is the SHA-256 immediately following the
     * "APK file hashes" label, not the page's first SHA-256 (which would be
     * the certificate's).
     */
    private fun apkMirrorVariantFileSha256(doc: Document): String? {
        val modal = doc.selectFirst("#safeDownload .modal-body")
            ?: doc.selectFirst(".safeDownload .modal-body")
            ?: return null
        val hashesBlock = modal.select("div")
            .firstOrNull { it.text().contains("APK file hashes", ignoreCase = true) }
        val blockText = hashesBlock?.text() ?: modal.text()
        val fileSection = blockText.substringAfter("APK file hashes", "")
            .substringBefore("APK certificate fingerprints")
        val hash = Regex("[0-9a-fA-F]{64}")
            .find(fileSection)
            ?.value
        Log.d(TAG, "APKMirror variant file SHA-256 extracted: ${hash ?: "none"}")
        return hash
    }

    private fun apkMirrorReleaseFallbackCandidate(
        request: HelperRequest,
        releaseUrl: String,
        versionName: String?,
        option: CandidateOption,
        note: String? = null
    ) = DownloadCandidate(
        source = DownloadSource.APK_MIRROR,
        name = request.appName,
        packageName = request.packageName,
        versionName = versionName ?: apkMirrorVersionFromReleaseUrl(releaseUrl),
        versionCode = null,
        url = releaseUrl,
        fileKind = "web",
        option = option,
        directDownload = false,
        versionStatus = request.versionStatus(versionName ?: apkMirrorVersionFromReleaseUrl(releaseUrl), null),
        formatMatches = true,
        note = note
    )

    private fun apkMirrorVariants(releaseDoc: Document): List<ApkMirrorVariant> =
        releaseDoc.select("div.table-row.headerFont")
            .mapNotNull(::apkMirrorVariantFromRow)

    private fun apkMirrorSelectableVariants(
        request: HelperRequest,
        variants: List<ApkMirrorVariant>
    ): List<ApkMirrorVariant> {
        val wantedKinds = apkMirrorWantedVariantKinds(request)
        for (kind in wantedKinds) {
            val typedVariants = variants.filter {
                it.fileKind == kind &&
                    request.acceptsFormat(it.fileKind)
            }
            typedVariants.filter { apkMirrorDpiMatches(it.dpi) }
                .takeIf(List<ApkMirrorVariant>::isNotEmpty)
                ?.let { return it }
            typedVariants.takeIf(List<ApkMirrorVariant>::isNotEmpty)
                ?.let { return it }
        }

        val acceptedVariants = variants.filter { request.acceptsFormat(it.fileKind) }
        val dpiPreferred = acceptedVariants.filter { apkMirrorDpiMatches(it.dpi) }
        val dpiAnyFormat = variants.filter { apkMirrorDpiMatches(it.dpi) }
        return when {
            dpiPreferred.isNotEmpty() -> dpiPreferred
            acceptedVariants.isNotEmpty() -> acceptedVariants
            dpiAnyFormat.isNotEmpty() -> dpiAnyFormat
            // No variant matches the requested format (e.g. APKS requested but the
            // release is only offered as an APKM bundle): offer the best variant
            // anyway and let the UI flag the format mismatch, like other sources do.
            else -> variants
        }
    }

    private fun apkMirrorExtractVersionCode(doc: Document): Long? {
        val specRows = doc.select(".appspec-row, div.appspec-value, tr, div.notesWrap")
        for (row in specRows) {
            val text = row.text()
            if (text.contains("Version code", ignoreCase = true)) {
                val digits = Regex("""\b(\d{6,11})\b""").find(text)?.groupValues?.get(1)
                digits?.toLongOrNull()?.let { return it }
            }
        }
        val title = doc.selectFirst("h1, h2, .version-title, .app-title")?.text().orEmpty()
        Regex("""\((\d{6,11})\)""").find(title)?.groupValues?.get(1)?.toLongOrNull()?.let {
            return it
        }
        val bodyText = doc.body().text()
        val codeMatch = Regex("""(?i)Version\s*code\s*[:\s]+(\d{6,11})""").find(bodyText)
        return codeMatch?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun apkMirrorVariantFromRow(row: Element): ApkMirrorVariant? {
        val url = row.selectFirst("div.table-cell:nth-child(1) a[href]")
            ?.attr("href")
            ?.let(::apkMirrorAbsoluteUrl)
            ?: return null
        val type = row.selectFirst("div.table-cell:nth-child(1) span.apkm-badge")
            ?.text()
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf(String::isNotBlank)
            ?: "APK"
        val fileKind = apkMirrorVariantFileKind(type)
        val cells = row.select("div.table-cell")
        val arch = cells.getOrNull(1)?.text()?.trim()?.takeIf(String::isNotBlank)
        val dpi = cells.getOrNull(3)?.text()?.trim()?.takeIf(String::isNotBlank)

        val firstCellText = cells.firstOrNull()?.text().orEmpty()
        val rowVersionCode = Regex("""\b(\d{6,11})\b""").find(firstCellText)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("""\b(\d{6,11})\b""").find(row.text())?.groupValues?.get(1)?.toLongOrNull()

        return ApkMirrorVariant(
            url = url,
            type = type,
            fileKind = fileKind,
            arch = arch,
            dpi = dpi,
            isBundle = fileKind != "apk",
            versionCode = rowVersionCode
        )
    }

    private fun apkMirrorWantedVariantKinds(request: HelperRequest): List<String> {
        val requestedKinds = request.requestedFileKinds
        return DOWNLOAD_FILE_KIND_ORDER
            .filter { it in requestedKinds }
            .ifEmpty { DOWNLOAD_FILE_KIND_ORDER }
    }

    private fun apkMirrorVariantFileKind(type: String): String {
        val normalized = type.lowercase(Locale.US)
        return when {
            "apks" in normalized -> "apks"
            "xapk" in normalized -> "xapk"
            "apkm" in normalized || "bundle" in normalized -> "apkm"
            else -> "apk"
        }
    }

    private fun ApkMirrorVariant.displayLabel(): String? {
        val archLabel = arch?.archDisplayLabel()
        val dpiLabel = dpi
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { it.equals("nodpi", ignoreCase = true) || it.equals("anydpi", ignoreCase = true) }
        return listOfNotNull(archLabel, dpiLabel)
            .joinToString(" / ")
            .takeIf(String::isNotBlank)
    }

    private fun apkMirrorDpiMatches(dpi: String?): Boolean {
        val normalized = dpi?.lowercase(Locale.US)?.takeIf(String::isNotBlank) ?: return true
        return normalized == "nodpi" || normalized == "anydpi"
    }

    private fun apkMirrorDownloadButtonUrl(doc: Document, isBundle: Boolean): String? {
        val urls = (
            doc.select("a.downloadButton[href]").map { it.attr("href") } +
                doc.select("a[href*=/download/?key][href]").map { it.attr("href") }
            )
            .mapNotNull(::apkMirrorAbsoluteUrl)
            .distinct()

        return if (isBundle) {
            urls.firstOrNull { !it.contains("forcebaseapk", ignoreCase = true) } ?: urls.firstOrNull()
        } else {
            urls.firstOrNull { it.contains("forcebaseapk", ignoreCase = true) } ?: urls.firstOrNull()
        }
    }

    private fun apkMirrorFinalDownloadUrl(doc: Document): String? =
        doc.selectFirst("a#download-link[href]")
            ?.attr("href")
            ?.let(::apkMirrorAbsoluteUrl)
            ?: doc.select("a[href]")
                .asSequence()
                .map { it.attr("href") }
                .firstOrNull { href ->
                    href.contains("download.php", ignoreCase = true) ||
                        href.contains("/download/?key=", ignoreCase = true)
                }
                ?.let(::apkMirrorAbsoluteUrl)

    private fun apkMirrorPageLooksBundle(doc: Document): Boolean =
        doc.select("span.apkm-badge, .apkm-badge")
            .any { it.text().contains("bundle", ignoreCase = true) }

internal fun apkMirrorVersionFromReleaseUrl(url: String, appPageUrl: String? = null): String? {
    val path = runCatching { java.net.URI(url).path.trimEnd('/') }.getOrNull() ?: return null
    var slug = path.substringAfterLast('/').removeSuffix("-release")
    // A release slug is "<app-slug>-<version-slug>". The app slug can itself
    // end in a digit (e.g. "file-manager-7" in "file-manager-7-3-5-4-release"),
    // which would otherwise leak into the version ("7.3.5.4" instead of
    // "3.5.4") and make Fast Mode "Latest" think an older build is newer than
    // the request. Strip the app slug using the app page URL when available.
    val appSlug = appPageUrl
        ?.let { runCatching { java.net.URI(it).path.trimEnd('/').substringAfterLast('/') }.getOrNull() }
        ?.takeIf(String::isNotBlank)
    if (appSlug != null && slug.startsWith("$appSlug-")) {
        slug = slug.removePrefix("$appSlug-")
    }
    return Regex("""\d+(?:[-.]\d+)+(?:[-.](?:alpha|beta|rc)\d*)?""", RegexOption.IGNORE_CASE)
        .findAll(slug)
        .lastOrNull()
        ?.value
        ?.replace("-", ".")
}

private fun apkMirrorReleaseUrlMatchesVersion(url: String, version: String): Boolean =
        apkMirrorLooksLikeReleaseUrl(url) &&
            (
                apkMirrorVersionFromReleaseUrl(url).versionNameEquals(version) ||
                    runCatching { java.net.URI(url).path.lowercase(Locale.US) }
                        .getOrDefault(url.lowercase(Locale.US))
                        .contains(version.apkMirrorVersionSlug())
                )

    private fun apkMirrorLooksLikeReleaseUrl(url: String): Boolean =
        runCatching {
            val path = java.net.URI(url).path
            path.startsWith("/apk/") && path.trimEnd('/').endsWith("-release")
        }.getOrDefault(false)

    private fun apkMirrorAbsoluteUrl(url: String): String? {
        val normalized = url.substringBefore("#").trim().replace("&amp;", "&")
        if (normalized.isBlank()) return null
        return when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> normalized
            normalized.startsWith("//") -> "https:$normalized"
            normalized.startsWith("/") -> "https://www.apkmirror.com$normalized"
            else -> "https://www.apkmirror.com/$normalized"
        }.normalizedHttpUrlOrNull()
    }

    private fun Document.isCloudflareChallenge(): Boolean =
        title().contains("Just a moment", ignoreCase = true) ||
            text().contains("Enable JavaScript and cookies to continue", ignoreCase = true) ||
            text().contains("Attention Required! | Cloudflare", ignoreCase = true) ||
            select("div[id*='challenge']").isNotEmpty() ||
            select("iframe[src*='cloudflare']").isNotEmpty()

    private fun Document.isApkMirrorNoResults(): Boolean =
        select("p").any { it.text().contains("No results found matching your query", ignoreCase = true) }

    private fun Document.apkMirrorMatchesPackage(packageName: String): Boolean {
        val hasPackageId = select("[id]").any { it.id() == packageName }
        val hasPlayPackage = select("a[href]")
            .asSequence()
            .map { it.absUrl("href").ifBlank { it.attr("href") } }
            .any { url ->
                url.contains("play.google.com", ignoreCase = true) &&
                    runCatching { Uri.parse(url).getQueryParameter("id") == packageName }
                        .getOrDefault(false)
            }
        val tablePackage = parseInfoTableValue(this, "Package Name") == packageName
        return hasPackageId || hasPlayPackage || tablePackage
    }

    private fun fetchDocument(url: String, referer: String? = null): Document {
        val doc = ctx.document(url, referer)
        if (doc.isCloudflareChallenge()) {
            throw SourceChallengeException(
                "APKMirror requires Cloudflare browser verification.",
                challengeUrl = url
            )
        }
        return doc
    }
}
