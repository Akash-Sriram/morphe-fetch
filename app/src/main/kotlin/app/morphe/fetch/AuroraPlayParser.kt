package app.morphe.fetch

import android.content.Context
import android.util.Log
import app.morphe.fetch.aurora.ExodusVersionResolver
import app.morphe.fetch.aurora.GPlayHttpClient
import app.morphe.fetch.aurora.MicroGAccountTokenProvider
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.exceptions.GooglePlayException
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import com.google.gson.Gson
import java.util.Locale

internal class AuroraPlayParser(
    private val context: Context,
    okHttpClient: OkHttpClient = MorpheHttpClient.gplayClient,
    private val gPlayClient: GPlayHttpClient = GPlayHttpClient(okHttpClient),
    private val tokenProvider: MicroGAccountTokenProvider = MicroGAccountTokenProvider(
        context,
        gPlayClient
    ),
    private val exodusResolver: ExodusVersionResolver = ExodusVersionResolver(okHttpClient),
    private val apkPureApi: ApkPureApi? = null
) : ApkSourceParser {

    companion object {
        private const val TAG = "AuroraPlayParser"

        private val KNOWN_ABI_SUBSTRINGS = listOf(
            "arm64_v8a" to "arm64-v8a",
            "arm64-v8a" to "arm64-v8a",
            "armeabi_v7a" to "armeabi-v7a",
            "armeabi-v7a" to "armeabi-v7a",
            "armeabi" to "armeabi",
            "x86_64" to "x86_64",
            "x86" to "x86"
        )

        fun isCompatibleSplit(fileName: String, availableAbis: List<String>): Boolean {
            val lower = fileName.lowercase(Locale.US)
            val matchedAbi = KNOWN_ABI_SUBSTRINGS.firstOrNull { (sub, _) -> lower.contains(sub) }?.second
            if (matchedAbi != null) {
                return availableAbis.any { it.equals(matchedAbi, ignoreCase = true) }
            }
            return true
        }

        /**
         * Returns true if two version name strings share the same major.minor prefix
         * (first two dot-separated numeric segments), e.g.:
         *   "7.94.0.984908898" and "7.94.1.600123456" → true (both "7.94")
         *   "7.94.0.984908898" and "7.95.0.12345678"  → false
         */
        fun versionsShareMajorMinor(a: String, b: String): Boolean {
            fun majorMinor(v: String): String? {
                val parts = v.trim().split('.')
                if (parts.size < 2) return null
                val major = parts[0].toLongOrNull() ?: return null
                val minor = parts[1].toLongOrNull() ?: return null
                return "$major.$minor"
            }
            val ma = majorMinor(a) ?: return false
            val mb = majorMinor(b) ?: return false
            return ma == mb
        }
    }


    override val source: DownloadSource = DownloadSource.AURORA

    override fun searchUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    override fun requestedFallbackCandidate(request: HelperRequest): DownloadCandidate? = null

    override fun latestFallbackCandidate(request: HelperRequest): DownloadCandidate? = null

    override suspend fun findCandidates(
        request: HelperRequest,
        option: CandidateOption
    ): List<DownloadCandidate> = withContext(Dispatchers.IO) {
        val authData = tokenProvider.getAuthData()

        when (option) {
            CandidateOption.REQUESTED -> {
                var targetVersionCode: Long? = request.versionCode
                    ?: request.requestedVersionCodes.firstOrNull()

                var resolvedVersionName: String? = request.requestedVersionName

                // Try APKMirror cache if already discovered
                if (targetVersionCode == null && !request.requestedVersionName.isNullOrBlank()) {
                    targetVersionCode = app.morphe.fetch.aurora.ApkMirrorVersionResolver.getCachedVersionCode(
                        request.packageName,
                        request.requestedVersionName!!
                    )
                    if (targetVersionCode != null) {
                        Log.i(TAG, "Resolved version code from APKMirror cache: ${request.requestedVersionName} (vc=$targetVersionCode) for ${request.packageName}")
                    }
                }

                // Try Exodus Privacy to map version name → version code
                if (targetVersionCode == null && !request.requestedVersionName.isNullOrBlank()) {
                    targetVersionCode = exodusResolver.resolveVersionCode(
                        request.packageName,
                        request.requestedVersionName!!
                    )
                }

                if (targetVersionCode == null) {
                    // Try Play Store app details for version code resolution
                    try {
                        val details = AppDetailsHelper(authData).using(gPlayClient)
                            .getAppByPackageName(request.packageName)
                        when {
                            // Strict match: Play's latest version is exactly what was requested
                            request.matchesRequestedVersion(details.versionName, details.versionCode) -> {
                                targetVersionCode = details.versionCode
                                resolvedVersionName = details.versionName
                            }
                            // Fuzzy prefix match: e.g., requested "7.94.0.984908898" and Play has "7.94.x.y"
                            // Both share the same major.minor — assume same effective version
                            !request.requestedVersionName.isNullOrBlank() &&
                            versionsShareMajorMinor(request.requestedVersionName!!, details.versionName) -> {
                                Log.i(TAG, "Fuzzy version match: requested '${request.requestedVersionName}' ≈ Play '${details.versionName}' (vc=${details.versionCode}) for ${request.packageName}")
                                targetVersionCode = details.versionCode
                                resolvedVersionName = details.versionName
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to query app details for ${request.packageName}", e)
                    }
                }

                // Fallback: Query APKPure API index if Play details did not match
                if (targetVersionCode == null && apkPureApi != null) {
                    try {
                        val resp = apkPureApi.getAppUpdate(
                            header = Gson().toJson(ApkPureDeviceHeader()),
                            request = ApkPureUpdateRequest(
                                app_info_for_update = listOf(
                                    ApkPureAppInfo(package_name = request.packageName, version_code = 0L)
                                )
                            )
                        )
                        val match = resp.app_update_response.firstOrNull { it.package_name == request.packageName }
                        if (match != null && match.version_code > 0L) {
                            val reqVName = request.requestedVersionName
                            if (reqVName == null ||
                                request.matchesRequestedVersion(match.version_name, match.version_code) ||
                                versionsShareMajorMinor(reqVName, match.version_name)
                            ) {
                                Log.i(TAG, "Resolved version code via APKPure index: ${match.version_name} (vc=${match.version_code}) for ${request.packageName}")
                                targetVersionCode = match.version_code
                                if (resolvedVersionName == null) resolvedVersionName = match.version_name
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to resolve version code from APKPure index for ${request.packageName}: ${e.message}")
                    }
                }

                // Fallback: Query APKMirror to resolve version code for requested version
                if (targetVersionCode == null && !request.requestedVersionName.isNullOrBlank()) {
                    try {
                        targetVersionCode = app.morphe.fetch.aurora.ApkMirrorVersionResolver.resolveVersionCode(
                            packageName = request.packageName,
                            versionName = request.requestedVersionName!!,
                            appName = request.appName
                        )
                        if (targetVersionCode != null) {
                            Log.i(TAG, "Resolved version code via APKMirror: ${request.requestedVersionName} (vc=$targetVersionCode) for ${request.packageName}")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to resolve version code from APKMirror for ${request.packageName}: ${e.message}")
                    }
                }

                if (targetVersionCode == null) {
                    Log.w(TAG, "Could not resolve version code for ${request.packageName} version='${request.requestedVersionName}'")
                    return@withContext emptyList()
                }

                val candidate = purchaseAndBuildCandidate(
                    request = request,
                    versionCode = targetVersionCode,
                    versionName = resolvedVersionName,
                    option = CandidateOption.REQUESTED,
                    gPlayClient = gPlayClient,
                    authData = authData
                )
                listOfNotNull(candidate)
            }


            CandidateOption.LATEST -> {
                val details = try {
                    AppDetailsHelper(authData).using(gPlayClient)
                        .getAppByPackageName(request.packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get latest app details for ${request.packageName}", e)
                    return@withContext emptyList()
                }

                var latestVersionCode = details.versionCode
                var latestVersionName = details.versionName

                // If external index reports a newer version code (e.g. during phased Google Play rollouts)
                if (apkPureApi != null) {
                    try {
                        val resp = apkPureApi.getAppUpdate(
                            header = Gson().toJson(ApkPureDeviceHeader()),
                            request = ApkPureUpdateRequest(
                                app_info_for_update = listOf(
                                    ApkPureAppInfo(package_name = request.packageName, version_code = details.versionCode)
                                )
                            )
                        )
                        val match = resp.app_update_response.firstOrNull { it.package_name == request.packageName }
                        if (match != null && match.version_code > latestVersionCode) {
                            Log.i(TAG, "Indexed newer version code for ${request.packageName}: ${match.version_name} (vc=${match.version_code} > Play vc=$latestVersionCode)")
                            latestVersionCode = match.version_code
                            latestVersionName = match.version_name
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Index check for newer version code skipped: ${e.message}")
                    }
                }

                var candidate = purchaseAndBuildCandidate(
                    request = request,
                    versionCode = latestVersionCode,
                    versionName = latestVersionName,
                    option = CandidateOption.LATEST,
                    gPlayClient = gPlayClient,
                    authData = authData
                )
                if (candidate == null && latestVersionCode != details.versionCode && details.versionCode > 0) {
                    Log.i(TAG, "Purchase for indexed vc=$latestVersionCode failed, falling back to Play details vc=${details.versionCode}")
                    candidate = purchaseAndBuildCandidate(
                        request = request,
                        versionCode = details.versionCode,
                        versionName = details.versionName,
                        option = CandidateOption.LATEST,
                        gPlayClient = gPlayClient,
                        authData = authData
                    )
                }
                listOfNotNull(candidate)
            }

            CandidateOption.MANUAL -> emptyList()
        }
    }

    override suspend fun resolveHistory(request: HelperRequest): List<DownloadCandidate> =
        withContext(Dispatchers.IO) {
            val reports = exodusResolver.getVersions(request.packageName)
            if (reports.isEmpty()) return@withContext emptyList()

            reports.map { item ->
                DownloadCandidate(
                    source = DownloadSource.AURORA,
                    name = request.appName,
                    packageName = request.packageName,
                    versionName = item.versionName,
                    versionCode = item.versionCode,
                    url = searchUrl(request.packageName),
                    fileKind = "apks",
                    option = CandidateOption.LATEST,
                    directDownload = false,
                    versionStatus = request.versionStatus(item.versionName, item.versionCode),
                    formatMatches = true,
                    releaseDate = item.date
                )
            }
        }

    override suspend fun resolveHistoryCandidate(
        request: HelperRequest,
        candidate: DownloadCandidate
    ): DownloadCandidate? = withContext(Dispatchers.IO) {
        val targetVersionCode = candidate.versionCode ?: return@withContext null
        val authData = try {
            tokenProvider.getAuthData()
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed during history resolve", e)
            return@withContext null
        }
        purchaseAndBuildCandidate(
            request = request,
            versionCode = targetVersionCode,
            versionName = candidate.versionName,
            option = CandidateOption.LATEST,
            gPlayClient = gPlayClient,
            authData = authData
        )
    }

    private suspend fun purchaseAndBuildCandidate(
        request: HelperRequest,
        versionCode: Long,
        versionName: String?,
        option: CandidateOption,
        gPlayClient: GPlayHttpClient,
        authData: com.aurora.gplayapi.data.models.AuthData
    ): DownloadCandidate? {
        val maxAttempts = 3
        var currentAuthData = authData

        for (attempt in 1..maxAttempts) {
            val playFiles: List<PlayFile> = try {
                PurchaseHelper(currentAuthData).using(gPlayClient)
                    .purchase(request.packageName, versionCode, 1)
            } catch (e: GooglePlayException.Unknown) {
                if (e.code == 429) {
                    val backoffMs = attempt * 2000L
                    Log.w(TAG, "Play delivery 429 for ${request.packageName} vc=$versionCode (attempt $attempt/$maxAttempts) — retrying in ${backoffMs}ms")
                    if (attempt < maxAttempts) {
                        delay(backoffMs)
                        continue
                    } else {
                        Log.e(TAG, "Play delivery 429 — all retries exhausted for ${request.packageName} vc=$versionCode")
                        return null
                    }
                }
                Log.w(TAG, "Google Play purchase/delivery failed for ${request.packageName} vc=$versionCode", e)
                return null
            } catch (e: GooglePlayException.AuthException) {
                Log.w(TAG, "Auth error during purchase for ${request.packageName} — invalidating session and retrying (attempt $attempt/$maxAttempts)", e)
                tokenProvider.invalidateAuth()
                if (attempt < maxAttempts) {
                    try {
                        currentAuthData = tokenProvider.getAuthData()
                    } catch (authEx: Exception) {
                        Log.e(TAG, "Re-auth failed, giving up", authEx)
                        return null
                    }
                    continue
                }
                return null
            } catch (e: Exception) {
                Log.w(TAG, "Google Play purchase/delivery failed for ${request.packageName} vc=$versionCode", e)
                return null
            }

            if (playFiles.isEmpty()) return null

            val availableAbis = request.availableAbis
            val filteredPlayFiles = playFiles.filter { isCompatibleSplit(it.name, availableAbis) }
            val effectiveFiles = filteredPlayFiles.ifEmpty { playFiles }

            val downloadFiles = effectiveFiles.map { file ->
                CandidateDownloadFile(
                    url = file.url,
                    fileName = file.name,
                    size = file.size,
                    expectedSha256 = file.sha256.takeIf { it.isNotBlank() }
                )
            }

            val isSplit = downloadFiles.size > 1
            val fileKind = if (isSplit) "apks" else "apk"

            return DownloadCandidate(
                source = DownloadSource.AURORA,
                name = request.appName,
                packageName = request.packageName,
                versionName = versionName,
                versionCode = versionCode,
                url = downloadFiles.first().url,
                fileKind = fileKind,
                option = option,
                directDownload = true,
                versionStatus = request.versionStatus(versionName, versionCode),
                formatMatches = request.acceptsFormat(fileKind),
                variantLabel = if (isSplit) "Split bundle" else "Standalone APK",
                files = downloadFiles
            )
        }
        return null
    }
}
