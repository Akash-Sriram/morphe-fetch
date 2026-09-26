package app.morphe.fetch

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.fetch.updater.AppUpdater
import app.morphe.fetch.updater.UpdateInfo
import app.morphe.fetch.updater.UpdateState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class HelperViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val APKMIRROR_REQUEST_GAP_MS = 1000L
    }

    init {
        MorpheHttpClient.init(application)
        viewModelScope.launch(Dispatchers.IO) {
            if (!ApkMirrorSessionWarmer.hasValidCfClearance()) {
                ApkMirrorSessionWarmer.warmSessionSync(application)
            }
        }
    }

    private val client get() = MorpheHttpClient.baseClient
    private val apkPureClient get() = MorpheHttpClient.apkPureClient
    private val apkMirrorClient get() = MorpheHttpClient.apkMirrorClient
    private val gplayClient get() = MorpheHttpClient.gplayClient

    private val apkPureApi by lazy {
        Retrofit.Builder()
            .client(apkPureClient)
            .baseUrl("https://tapi.pureapk.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApkPureApi::class.java)
    }


    private val parsers: Map<DownloadSource, ApkSourceParser> by lazy {
        val parserContext = SourceParserContext(
            fetcher = OkHttpSourceTextFetcher(
                client,
                hostGapsMillis = mapOf("apkmirror.com" to APKMIRROR_REQUEST_GAP_MS, "uptodown.com" to 500L)
            ),
            apkPureApi = apkPureApi
        )
        val apkMirrorParserContext = SourceParserContext(
            fetcher = OkHttpSourceTextFetcher(
                apkMirrorClient,
                hostGapsMillis = mapOf("apkmirror.com" to APKMIRROR_REQUEST_GAP_MS, "uptodown.com" to 500L)
            ),
            apkPureApi = apkPureApi
        )
        listOf(
            AuroraPlayParser(application, gplayClient, apkPureApi = apkPureApi),
            ApkMirrorParser(apkMirrorParserContext),
            UptodownParser(parserContext),
            ApkPureParser(parserContext),
            ApkComboParser(parserContext)
        ).associateBy { it.source }
    }

    private val candidateResolver by lazy { CandidateResolver(parsers) }

    var request by mutableStateOf<HelperRequest?>(null)
        private set
    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set
    var helperSettings by mutableStateOf(HelperSettings())
        private set
    var installedPackageRefreshToken by mutableIntStateOf(0)
        private set
    var selectedPagerPage by mutableIntStateOf(0)
    var pendingDownload: PendingDownload? = null
        private set
    var captchaBrowser by mutableStateOf<DownloadCandidate?>(null)
        private set
    var reuseOffer by mutableStateOf<List<ReuseOption>?>(null)
        private set

    val appUpdater by lazy { AppUpdater() }
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private var requestIntentExtras: Bundle? = null

    private val _finishEvents = Channel<Intent>()
    val finishEvents = _finishEvents.receiveAsFlow()

    private val effectiveDisabledSources: Set<DownloadSource>
        get() {
            val disabled = helperSettings.disabledSources
            return if (disabled.size >= DownloadSource.entries.size) {
                disabled - DownloadSource.APK_PURE
            } else {
                disabled
            }
        }

    init {
        helperSettings = application.loadHelperSettings()
        viewModelScope.launch(Dispatchers.IO) {
            application.cleanupTemporaryDownloads(helperSettings)
            runCatching { MorpheArchive.getOrFetchIndex() }
        }
        viewModelScope.launch {
            DownloadJobManager.events.collect(::handleDownloadEvent)
        }
    }

    fun handleIntent(intent: Intent) {
        requestIntentExtras = intent.extras
        request = HelperRequest.from(intent)
        startRequestLog(request)
        val active = request
        if (active != null) {
            offerExistingDownloadIfPresent(active)
            loadCandidates()
            if (helperSettings.autoDownloadBestMatch && active.callerPackage.isNotBlank() && active.hasRequestedVersionRequest) {
                startAutoResolveAndDownload(active)
            }
        } else {
            uiState = UiState.Idle
        }
    }

    fun searchPackage(
        packageName: String,
        appName: String? = null,
        targetSource: DownloadSource? = null
    ) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return
        val catalogMatch = MorpheArchive.findBestMatch(cleanPkg)
        val resolvedPkg = catalogMatch?.packageName ?: cleanPkg
        val cleanName = appName?.trim()?.takeIf(String::isNotBlank)
            ?: catalogMatch?.name
            ?: resolvedPkg.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.US) }
        val searchReq = HelperRequest(
            callerPackage = "",
            packageName = resolvedPkg,
            appName = cleanName,
            versionName = null,
            versionCode = null,
            versionCodes = emptySet(),
            compatibleVersionNames = emptySet(),
            compatibleVersionCodes = emptySet(),
            supportedAbis = emptyList(),
            requestedFileType = null,
            allowSplitArchive = true,
            stockInstallRequired = false,
            fallbackWebUrl = "",
            sourceHintUrls = emptyList()
        )
        request = searchReq
        startRequestLog(searchReq)
        offerExistingDownloadIfPresent(searchReq)

        val effectivePreferred = targetSource ?: helperSettings.preferredSource
        val initialResult = candidateResolver.initialCandidateResult(
            request = searchReq,
            effectiveDisabledSources = effectiveDisabledSources,
            preferredSource = effectivePreferred
        )
        uiState = UiState.Ready(initialResult)

        appendLog(
            "Ready. Manual links prepared for " +
                "${DownloadSource.entries.count { it !in effectiveDisabledSources }} sources."
        )

        if (targetSource != null) {
            val targetIdx = initialResult.sourceGroups.indexOfFirst { it.source == targetSource }
            selectedPagerPage = if (targetIdx >= 0) 1 + targetIdx else 0
            resolveCandidates(targetSource, CandidateOption.LATEST)
        } else {
            selectedPagerPage = 0
            initialResult.sourceGroups.forEach { group ->
                resolveCandidates(group.source, CandidateOption.LATEST)
            }
        }
    }

    fun clearRequest() {
        request = null
        uiState = UiState.Idle
        selectedPagerPage = 0
        AppLog.clear()
        AppLog.setRequestSummary(null)
    }

    fun updateSelectedPagerPage(page: Int) {
        selectedPagerPage = page
    }

    fun loadCandidates() {
        val activeRequest = request ?: return
        val initial = initialCandidateResult(activeRequest)
        uiState = UiState.Ready(initial)
        selectedPagerPage = 0
        appendLog(
            "Ready. Manual links prepared for " +
                "${DownloadSource.entries.count { it !in effectiveDisabledSources }} sources."
        )
        initial.sourceGroups.forEach { group ->
            val option = if (group.source.supportsRecommended && activeRequest.hasRequestedVersionRequest) {
                CandidateOption.REQUESTED
            } else {
                CandidateOption.LATEST
            }
            resolveCandidates(group.source, option)
        }
    }

    fun resolveCandidates(source: DownloadSource, option: CandidateOption) {
        val activeRequest = request ?: return
        val context = getApplication<Application>()
        helperSettings.networkPolicy.blockReason(context)?.let { message ->
            appendLog(message, LogLevel.Warning)
            updateResolveState(source, option, ResolveState.Error(message))
            return
        }
        appendLog("Checking ${option.labelForLogs} from ${source.label}.")
        updateResolveState(source, option, ResolveState.Loading)
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                resolveSourceSection(activeRequest, source, option)
            }
            logResolveOutcome(source, option, resolved)

            updateResolveState(
                source = source,
                option = option,
                state = resolved.notFoundMessage
                    ?.takeIf { resolved.candidates.isEmpty() }
                    ?.let { ResolveState.Done(emptyList()) }
                    ?: resolved.errorMessage
                        ?.takeIf { resolved.candidates.isEmpty() }
                        ?.let { message ->
                            ResolveState.Error(
                                message = message,
                                fallbackCandidate = resolved.fallbackCandidate
                            )
                        }
                    ?: ResolveState.Done(resolved.candidates)
            )

            if (source != DownloadSource.AURORA && option == CandidateOption.REQUESTED && resolved.candidates.isNotEmpty()) {
                val candidateWithCode = resolved.candidates.firstOrNull { it.versionCode != null }
                val discoveredCode = candidateWithCode?.versionCode
                val discoveredVersionName = candidateWithCode?.versionName ?: activeRequest.requestedVersionName
                if (discoveredCode != null && !discoveredVersionName.isNullOrBlank()) {
                    app.morphe.fetch.aurora.ApkMirrorVersionResolver.cacheVersionCode(
                        activeRequest.packageName,
                        discoveredVersionName,
                        discoveredCode
                    )
                    val currentGroup = (uiState as? UiState.Ready)?.result?.sourceGroups?.firstOrNull { it.source == DownloadSource.AURORA }
                    val auroraEmpty = currentGroup?.recommended is ResolveState.Done &&
                        (currentGroup.recommended as ResolveState.Done).candidates.isEmpty()
                    if (auroraEmpty) {
                        appendLog("Discovered version code $discoveredCode from ${source.label}. Querying Aurora Google Play CDN...", LogLevel.Info)
                        resolveCandidates(DownloadSource.AURORA, CandidateOption.REQUESTED)
                    }
                }
            }
        }
    }

    private fun updateResolveState(
        source: DownloadSource,
        option: CandidateOption,
        state: ResolveState
    ) {
        val activeRequest = request ?: return
        val current = (uiState as? UiState.Ready)?.result ?: initialCandidateResult(activeRequest)
        uiState = UiState.Ready(current.withResolveState(source, option, state))
    }

    private var autoDownloadJob: Job? = null

    private fun startAutoResolveAndDownload(activeRequest: HelperRequest) {
        autoDownloadJob?.cancel()
        autoDownloadJob = viewModelScope.launch {
            appendLog("Auto-fetch: Resolving indexed link and direct sources for ${activeRequest.packageName}...")
            val indexedUrl = withContext(Dispatchers.IO) {
                MorpheApiResolver.resolve(
                    packageName = activeRequest.packageName,
                    version = activeRequest.versionName,
                    abi = activeRequest.availableAbis.firstOrNull()
                )
            }
            val currentReq = if (!indexedUrl.isNullOrBlank() && activeRequest == request) {
                appendLog("Auto-fetch: Resolved Morphe indexed mirror link: $indexedUrl")
                val updated = activeRequest.copy(
                    sourceHintUrls = (activeRequest.sourceHintUrls + indexedUrl).distinct()
                )
                request = updated
                updated
            } else {
                activeRequest
            }

            val preferred = helperSettings.preferredSource
            val defaultOrder = listOf(
                DownloadSource.AURORA,
                DownloadSource.APK_PURE,
                DownloadSource.APK_MIRROR,
                DownloadSource.UPTODOWN,
                DownloadSource.APK_COMBO
            )
            val sourcesToTry = (listOfNotNull(preferred) + defaultOrder)
                .distinct()
                .filter { it !in effectiveDisabledSources }

            for (src in sourcesToTry) {
                if (uiState is UiState.Downloading || uiState is UiState.Completed) return@launch
                updateResolveState(src, CandidateOption.REQUESTED, ResolveState.Loading)
                val outcome = withContext(Dispatchers.IO) {
                    resolveSourceSection(currentReq, src, CandidateOption.REQUESTED)
                }
                logResolveOutcome(src, CandidateOption.REQUESTED, outcome)
                updateResolveState(
                    source = src,
                    option = CandidateOption.REQUESTED,
                    state = if (outcome.candidates.isNotEmpty()) {
                        ResolveState.Done(outcome.candidates)
                    } else if (outcome.errorMessage != null) {
                        ResolveState.Error(outcome.errorMessage, outcome.fallbackCandidate)
                    } else {
                        ResolveState.Done(emptyList())
                    }
                )

                var directMatch = outcome.candidates
                    .filter { candidate ->
                        candidate.directDownload &&
                            candidate.formatMatches &&
                            currentReq.isRequestedMatch(candidate)
                    }
                    .minByOrNull { candidate ->
                        val arch = candidate.variantLabel?.lowercase(Locale.US) ?: ""
                        val idx = currentReq.availableAbis.indexOfFirst { it.equals(arch, ignoreCase = true) }
                        if (idx >= 0) idx else if (arch.isUniversalArchLabel()) 10 else 999
                    }

                if (directMatch == null && src == DownloadSource.UPTODOWN) {
                    val indirectMatch = outcome.candidates
                        .filter { candidate ->
                            !candidate.directDownload &&
                                candidate.formatMatches &&
                                currentReq.isRequestedMatch(candidate)
                        }
                        .minByOrNull { candidate ->
                            val arch = candidate.variantLabel?.lowercase(Locale.US) ?: ""
                            val idx = currentReq.availableAbis.indexOfFirst { it.equals(arch, ignoreCase = true) }
                            if (idx >= 0) idx else if (arch.isUniversalArchLabel()) 10 else 999
                        }
                    if (indirectMatch != null) {
                        appendLog("Auto-fetch: Attempting background verification for Uptodown (${indirectMatch.versionDisplay})...")
                        val context = getApplication<Application>()
                        val targetUrl = indirectMatch.captchaUrl ?: indirectMatch.url
                        val resolvedUrl = UptodownHeadlessResolver.resolveDownloadUrl(context, targetUrl, timeoutMs = 9500L)
                        if (resolvedUrl != null) {
                            appendLog("Auto-fetch: Background verification passed! Starting download...")
                            directMatch = indirectMatch.copy(
                                url = resolvedUrl,
                                directDownload = true,
                                files = listOf(
                                    CandidateDownloadFile(
                                        url = resolvedUrl,
                                        fileName = capturedDownloadFileName(indirectMatch, resolvedUrl, indirectMatch.fileKind),
                                        referer = targetUrl,
                                        cookieHeader = CookieManager.getInstance().getCookie(targetUrl)
                                    )
                                )
                            )
                        }
                    }
                }

                if (directMatch != null) {
                    appendLog("Auto-fetch: Verified match on ${src.label} (${directMatch.versionDisplay}). Starting download...")
                    downloadAndReturn(directMatch)
                    return@launch
                }
            }
        }
    }

    fun loadVersionHistory(source: DownloadSource) {
        val activeRequest = request ?: return
        val context = getApplication<Application>()
        helperSettings.networkPolicy.blockReason(context)?.let { message ->
            appendLog(message, LogLevel.Warning)
            updateHistoryState(source, VersionHistoryState.Error(message))
            return
        }
        updateHistoryState(source, VersionHistoryState.Loading)
        appendLog("Loading ${source.label} version history.")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { resolveVersionHistory(activeRequest, source) }
            }
            result
                .onSuccess { candidates ->
                    appendLog("${source.label} version history loaded: ${candidates.size} versions.")
                    updateHistoryState(source, VersionHistoryState.Done(candidates))
                }
                .onFailure { error ->
                    val message = sourceFailureMessage(source, error)
                    appendLog(message, LogLevel.Error)
                    updateHistoryState(source, VersionHistoryState.Error(message))
                }
        }
    }

    private fun updateHistoryState(source: DownloadSource, state: VersionHistoryState) {
        val activeRequest = request ?: return
        val current = (uiState as? UiState.Ready)?.result ?: initialCandidateResult(activeRequest)
        uiState = UiState.Ready(current.withHistoryState(source, state))
    }

    fun downloadVersion(candidate: DownloadCandidate) {
        val activeRequest = request ?: return
        val currentResult = (uiState as? UiState.Ready)?.result
            ?: initialCandidateResult(activeRequest)
        appendLog("Resolving ${candidate.versionDisplay} from ${candidate.source.label} for download...")
        uiState = UiState.Loading
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching { resolveHistoryCandidate(activeRequest, candidate) }
            }
            resolved
                .onSuccess { direct ->
                    if (direct == null) {
                        val message =
                            "No direct download was available for ${candidate.versionDisplay} " +
                                "on ${candidate.source.label}. Open the version page manually."
                        appendLog(message, LogLevel.Warning)
                        uiState = UiState.Ready(
                            currentResult.markHistoryCandidateNoDirectDownload(
                                source = candidate.source,
                                candidateKey = candidate.identityKey()
                            )
                        )
                    } else {
                        downloadAndReturn(direct)
                    }
                }
                .onFailure { error ->
                    val message = downloadFailureMessage(candidate, error)
                    appendLog(message, LogLevel.Error)
                    uiState = UiState.Error(message)
                }
        }
    }

    private suspend fun resolveVersionHistory(
        request: HelperRequest,
        source: DownloadSource
    ): List<DownloadCandidate> =
        parsers[source]?.resolveHistory(request).orEmpty()

    private suspend fun resolveHistoryCandidate(
        request: HelperRequest,
        candidate: DownloadCandidate
    ): DownloadCandidate? =
        parsers[candidate.source]?.resolveHistoryCandidate(request, candidate)

    private fun startRequestLog(request: HelperRequest?) {
        AppLog.clear()
        if (request == null) {
            AppLog.setRequestSummary(null)
            appendLog("Opened without a Morphe request.", LogLevel.Warning)
        } else {
            AppLog.setRequestSummary(
                buildString {
                    append("App: ${request.appName}\n")
                    append("Package: ${request.packageName}\n")
                    append("Version: ${request.requestedVersionName ?: "any compatible"}\n")
                    append("Build: ${request.versionCodeSummary ?: "any"}\n")
                    append("Format: ${request.requestedFormatLabel.ifBlank { "any" }}\n")
                    if (request.availableAbis.isNotEmpty()) {
                        append("ABI: ${request.abiSummary}\n")
                    }
                    request.sourceHintUrls.takeIf { it.isNotEmpty() }?.let { urls ->
                        append("Source hints: ${urls.joinToString()}\n")
                    }
                }.trimEnd()
            )
            appendLog(
                "Request for ${request.appName} (${request.packageName}), " +
                    "version ${request.versionName ?: "any compatible"}, " +
                    "build ${request.versionCodeSummary ?: "any"}, format ${request.requestedFormatLabel}."
            )
        }
    }

    private fun logResolveOutcome(
        source: DownloadSource,
        option: CandidateOption,
        outcome: ResolveOutcome
    ) {
        when {
            outcome.notFoundMessage != null -> {
                appendLog("${source.label} ${option.labelForLogs}: ${outcome.notFoundMessage}.", LogLevel.Warning)
            }
            outcome.errorMessage != null && outcome.candidates.isEmpty() -> {
                appendLog("${source.label} ${option.labelForLogs} failed: ${outcome.errorMessage}", LogLevel.Error)
            }
            outcome.candidates.isEmpty() -> {
                appendLog("${source.label} ${option.labelForLogs} found no candidates.", LogLevel.Warning)
            }
            else -> {
                val summary = outcome.candidates.joinToString(limit = 3, truncated = "…") { candidate ->
                    candidate.versionDisplay
                }
                appendLog("${source.label} ${option.labelForLogs} found ${outcome.candidates.size}: $summary")
            }
        }
    }

    fun appendLog(message: String, level: LogLevel = LogLevel.Info) {
        AppLog.record(level, message)
    }

    fun changeRequestedFileType(kind: String) {
        val active = request ?: return
        if (active.requestedFileType?.equals(kind, ignoreCase = true) == true) return
        request = active.copy(
            requestedFileType = kind,
            allowSplitArchive = false
        )
        appendLog("Request format narrowed to ${kind.uppercase(Locale.US)}.", LogLevel.Info)
        loadCandidates()
    }

    fun updateHelperSettings(settings: HelperSettings) {
        val sourcesChanged = helperSettings.disabledSources != settings.disabledSources
        val preferredChanged = helperSettings.preferredSource != settings.preferredSource
        helperSettings = settings
        val context = getApplication<Application>()
        context.saveHelperSettings(settings)
        if ((sourcesChanged || preferredChanged) && request != null && uiState is UiState.Ready) {
            selectedPagerPage = 0
            uiState = UiState.Ready(initialCandidateResult(request!!))
        }
        viewModelScope.launch(Dispatchers.IO) {
            context.cleanupTemporaryDownloads(settings)
        }
    }

    private fun initialCandidateResult(request: HelperRequest): CandidateResult =
        candidateResolver.initialCandidateResult(request, effectiveDisabledSources, helperSettings.preferredSource)

    private suspend fun resolveSourceSection(
        request: HelperRequest,
        source: DownloadSource,
        option: CandidateOption
    ): ResolveOutcome =
        candidateResolver.resolveSourceSection(request, source, option, effectiveDisabledSources)

    fun downloadAndReturn(candidate: DownloadCandidate) {
        val activeRequest = request ?: return
        val context = getApplication<Application>()
        helperSettings.networkPolicy.blockReason(context)?.let { message ->
            appendLog(message, LogLevel.Warning)
            uiState = UiState.Error(message)
            return
        }
        appendLog(
            "Downloading ${candidate.source.label} ${candidate.option.labelForLogs} " +
                "${candidate.versionDisplay} (${candidate.fileKind.uppercase(Locale.US)})."
        )
        uiState = UiState.Downloading(candidate, 0)
        pendingDownload = PendingDownload(activeRequest, candidate, helperSettings)
        startPendingDownload()
    }

    fun startPendingDownload() {
        val pending = pendingDownload ?: return
        pendingDownload = null
        DownloadJobManager.start(
            DownloadJobManager.DownloadJob(
                request = pending.request,
                candidate = pending.candidate,
                settings = pending.settings,
                requestIntentExtras = requestIntentExtras
            )
        )
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, DownloadService::class.java).setAction(ACTION_START_DOWNLOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun cancelDownload() {
        appendLog("Cancelling download…", LogLevel.Warning)
        val context = getApplication<Application>()
        runCatching {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL_DOWNLOAD)
            )
        }.onFailure {
            appendLog("Could not cancel the download.", LogLevel.Error)
        }
    }

    fun openCaptchaBrowser(candidate: DownloadCandidate) {
        if (candidate.source == DownloadSource.UPTODOWN) {
            viewModelScope.launch {
                appendLog(
                    "Uptodown: Attempting background verification for ${candidate.versionDisplay}...",
                    LogLevel.Info
                )
                val context = getApplication<Application>()
                val targetUrl = candidate.captchaUrl ?: candidate.url
                val resolvedUrl = UptodownHeadlessResolver.resolveDownloadUrl(context, targetUrl, timeoutMs = 9500L)
                if (resolvedUrl != null) {
                    appendLog(
                        "Uptodown: Background verification passed! Starting download...",
                        LogLevel.Info
                    )
                    val capture = BrowserDownloadCapture(
                        downloadUrl = resolvedUrl,
                        refererUrl = targetUrl,
                        cookieHeader = CookieManager.getInstance().getCookie(targetUrl)
                    )
                    handleCapturedDownload(candidate, capture)
                    return@launch
                }
                appendLog(
                    "Uptodown: Background verification inconclusive. Opening in-app browser.",
                    LogLevel.Info
                )
                captchaBrowser = candidate
            }
            return
        }

        appendLog(
            "Opening in-app browser for ${candidate.source.label} to solve the captcha.",
            LogLevel.Info
        )
        captchaBrowser = candidate
    }

    fun closeCaptchaBrowser() {
        captchaBrowser = null
    }

    fun onBrowserDownloadCaptured(capture: BrowserDownloadCapture) {
        val candidate = captchaBrowser ?: return
        captchaBrowser = null
        handleCapturedDownload(candidate, capture)
    }

    private fun handleCapturedDownload(candidate: DownloadCandidate, capture: BrowserDownloadCapture) {
        val detectedKind = fileKindFromUrl(capture.downloadUrl)
        val fileKind = if (detectedKind != "apk") detectedKind else candidate.fileKind.takeIf { it.isNotBlank() } ?: "apk"
        val referer = capture.refererUrl
            ?.takeIf(String::isNotBlank)
            ?: candidate.captchaUrl
            ?: candidate.url
        val cookieHeader = capture.cookieHeader
            ?: CookieManager.getInstance().getCookie(referer)
        appendLog(
            "Captured download link from ${candidate.source.label} " +
                "($fileKind): ${capture.downloadUrl}",
            LogLevel.Info
        )
        downloadAndReturn(
            candidate.copy(
                url = capture.downloadUrl,
                fileKind = fileKind,
                directDownload = true,
                note = null,
                files = listOf(
                    CandidateDownloadFile(
                        url = capture.downloadUrl,
                        fileName = capturedDownloadFileName(candidate, capture.downloadUrl, fileKind),
                        referer = referer,
                        cookieHeader = cookieHeader
                    )
                )
            )
        )
    }

    private fun handleDownloadEvent(event: DownloadJobManager.Event?) {
        when (event) {
            null -> Unit
            is DownloadJobManager.Event.Progress -> {
                uiState = UiState.Downloading(
                    candidate = event.candidate,
                    percent = event.percent,
                    speedBytesPerSec = event.speedBytesPerSec,
                    etaMs = event.etaMs,
                    bytesDownloaded = event.bytesDownloaded,
                    totalBytes = event.totalBytes
                )
            }
            is DownloadJobManager.Event.PostDownloadStatus -> {
                uiState = UiState.Downloading(
                    event.candidate,
                    percent = 100,
                    statusMessage = event.status
                )
            }
            is DownloadJobManager.Event.Completed -> {
                if (event.result.belongsToCurrentSession(request, event.epoch)) {
                    appendLog("Download validated: ${event.result.fileName}.")
                    val isFromMorphe = request?.callerPackage?.isNotBlank() == true
                    if (isFromMorphe) {
                        deliverPendingResult(event.result)
                    } else {
                        cancelCompletionNotification()
                        uiState = UiState.Completed(event.result)
                    }
                }
            }
            is DownloadJobManager.Event.Failed -> {
                val candidate = event.candidate
                val isHtmlOrChallenge = event.message.contains("text/html", ignoreCase = true) ||
                    event.message.contains("cloudflare", ignoreCase = true) ||
                    event.message.contains("captcha", ignoreCase = true) ||
                    event.message.contains("bot protection", ignoreCase = true)

                if (isHtmlOrChallenge && candidate != null) {
                    appendLog("${candidate.source.label} requires browser verification. Opening in-app browser...", LogLevel.Warning)
                    openCaptchaBrowser(candidate)
                } else {
                    val message = event.message.withManualModeHint()
                    appendLog(message, LogLevel.Error)
                    uiState = UiState.Error(message, candidate)
                }
            }
            is DownloadJobManager.Event.Cancelled -> {
                appendLog("Download canceled.")
                loadCandidates()
            }
            is DownloadJobManager.Event.ValidationMismatch -> {
                event.file.delete()
                val message = "Downloaded APK version code did not match requested."
                appendLog(message, LogLevel.Error)
                uiState = UiState.Error(message)
            }
        }
    }

    fun deliverPendingResult(pending: PendingDownloadResult) {
        val context = getApplication<Application>()
        val uri = Uri.parse(pending.uri)
        val resultIntent = Intent().apply {
            data = uri
            clipData = ClipData.newUri(context.contentResolver, pending.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(DownloadHelperContract.EXTRA_RESULT_PACKAGE_NAME, pending.packageName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_VERSION_NAME, pending.versionName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_SOURCE_NAME, pending.sourceName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_FILE_NAME, pending.fileName)
        }
        DownloadJobManager.clearPendingResult(context)
        cancelCompletionNotification()
        _finishEvents.trySend(resultIntent)
    }

    fun deliverPendingResultIfPresent(activity: Activity): Boolean {
        val activeRequest = request ?: return false
        val context = getApplication<Application>()
        val pending = DownloadJobManager.readPendingResult(context) ?: return false
        if (!pending.belongsTo(activeRequest)) return false
        if (activity.callingActivity == null) return false

        val uri = Uri.parse(pending.uri)
        val result = Intent().apply {
            data = uri
            clipData = ClipData.newUri(activity.contentResolver, pending.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(DownloadHelperContract.EXTRA_RESULT_PACKAGE_NAME, pending.packageName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_VERSION_NAME, pending.versionName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_SOURCE_NAME, pending.sourceName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_FILE_NAME, pending.fileName)
        }
        activity.setResult(Activity.RESULT_OK, result)
        appendLog("Delivered existing result for ${pending.packageName}.")
        DownloadJobManager.clearPendingResult(context)
        cancelCompletionNotification()
        activity.finish()
        return true
    }

    private fun cancelCompletionNotification() {
        val context = getApplication<Application>()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DONE)
    }

    private fun offerExistingDownloadIfPresent(request: HelperRequest) {
        val context = getApplication<Application>()
        val existing = DownloadHistoryStore.entries(context)
            .filter { history ->
                history.packageName == request.packageName &&
                    (request.requestedVersionName == null || request.matchesRequestedVersion(history.versionName, null))
            }
            .distinctBy { it.fileName }
            .filter { entry -> FileHandoffHelper.historyFileExists(context, entry) }
            .map { entry -> ReuseOption(entry, FileHandoffHelper.historyFileSize(context, entry)) }
        if (existing.isNotEmpty()) reuseOffer = existing
    }

    fun useReuseOffer(option: ReuseOption) {
        reuseOffer = null
        val active = request ?: return
        val entry = option.entry
        appendLog(
            "Reusing previously downloaded ${entry.fileName} for ${active.packageName}.",
            LogLevel.Info
        )
        val pending = PendingDownloadResult(
            uri = entry.uri,
            fileName = entry.fileName,
            packageName = entry.packageName,
            versionName = entry.versionName,
            sourceName = entry.sourceName,
            requestPackage = active.packageName,
            callerPackage = active.callerPackage
        )
        val context = getApplication<Application>()
        DownloadJobManager.persistPendingResult(pending, context)
        deliverPendingResult(pending)
    }

    fun dismissReuseOffer() {
        reuseOffer = null
    }

    fun returnPickedFile(candidate: DownloadCandidate, uri: Uri?) {
        val activeRequest = request ?: return
        val context = getApplication<Application>()
        val settings = helperSettings

        if (uri == null) {
            appendLog("File selection canceled.", LogLevel.Warning)
            return
        }

        appendLog("Checking manually selected file for ${candidate.source.label}.")
        uiState = UiState.CheckingPickedFile(candidate)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = FileHandoffHelper.copyPickedFileToTemporary(context, activeRequest, candidate, uri)
                    validateDownloadedArtifact(context, activeRequest, candidate, file)
                }
            }

            result
                .onSuccess { file ->
                    appendLog("Selected file validated: ${file.name} (${file.length()} bytes).")
                    runCatching {
                        val pending = FileHandoffHelper.returnPreparedFile(context, activeRequest, candidate, file, settings)
                        deliverPendingResult(pending)
                    }.onFailure { error ->
                        val message = (error.message ?: "Could not return selected APK to Morphe.")
                            .withManualModeHint()
                        appendLog(message, LogLevel.Error)
                        uiState = UiState.Error(message)
                    }
                }
                .onFailure { error ->
                    if (error is VersionCodeMismatchException) {
                        error.file.delete()
                    }
                    val message = (error.message ?: "Selected file could not be used.")
                        .withManualModeHint()
                    appendLog(message, LogLevel.Error)
                    uiState = UiState.Error(message)
                }
        }
    }

    fun returnInstalledApp(candidate: DownloadCandidate, activity: Activity) {
        val activeRequest = request ?: return
        val context = getApplication<Application>()
        if (!context.isPackageInstalled(candidate.packageName)) {
            appendLog("${candidate.packageName} is not installed yet.", LogLevel.Warning)
            installedPackageRefreshToken++
            return
        }

        val result = Intent().apply {
            putExtra(DownloadHelperContract.EXTRA_RESULT_USE_INSTALLED_APP, true)
            putExtra(DownloadHelperContract.EXTRA_RESULT_PACKAGE_NAME, candidate.packageName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_VERSION_NAME, candidate.versionName)
            putExtra(DownloadHelperContract.EXTRA_RESULT_SOURCE_NAME, candidate.source.label)
        }

        activity.setResult(Activity.RESULT_OK, result)
        appendLog("Returned installed ${candidate.packageName} to ${activeRequest.callerPackage}.")
        activity.finish()
    }

    fun openHistoryEntry(entry: DownloadHistoryEntry, activity: Activity) {
        runCatching {
            activity.startActivity(FileHandoffHelper.createOpenIntent(entry))
        }.onFailure {
            appendLog("Could not open ${entry.fileName}.", LogLevel.Warning)
        }
    }

    fun shareHistoryEntry(entry: DownloadHistoryEntry, activity: Activity) {
        runCatching {
            activity.startActivity(FileHandoffHelper.createShareIntent(activity, entry))
        }.onFailure {
            appendLog("Could not share ${entry.fileName}.", LogLevel.Warning)
        }
    }

    fun checkForUpdates(currentVersion: String = BuildConfig.VERSION_NAME) {
        updateState = UpdateState.Checking
        viewModelScope.launch {
            try {
                val info = appUpdater.checkForUpdates(currentVersion)
                updateState = if (info.isUpdateAvailable) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                updateState = UpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    fun downloadUpdate(context: Context, info: UpdateInfo) {
        viewModelScope.launch {
            try {
                updateState = UpdateState.Downloading(
                    info = info,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = info.apkSize
                )
                val targetFile = appUpdater.downloadUpdate(context, info) { progress, copied, total ->
                    updateState = UpdateState.Downloading(
                        info = info,
                        progress = progress,
                        downloadedBytes = copied,
                        totalBytes = total
                    )
                }
                updateState = UpdateState.ReadyToInstall(info, targetFile)
            } catch (e: Exception) {
                updateState = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun installUpdate(context: Context, apkFile: File) {
        runCatching {
            appUpdater.installApk(context, apkFile)
        }.onFailure { e ->
            updateState = UpdateState.Error(e.message ?: "Installation failed")
        }
    }

    fun resetUpdateState() {
        updateState = UpdateState.Idle
    }
}
