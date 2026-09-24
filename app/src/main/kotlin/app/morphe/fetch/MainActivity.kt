package app.morphe.fetch

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<HelperViewModel>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.startPendingDownload()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncNightModeWithSystem(viewModel.helperSettings.themeMode)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (viewModel.deliverPendingResultIfPresent(this)) {
            return
        }

        viewModel.handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finishEvents.collect { resultIntent ->
                    val uri = resultIntent.data
                    val fileName = resultIntent.getStringExtra(DownloadHelperContract.EXTRA_RESULT_FILE_NAME)
                    if (uri != null) {
                        deliverHandoffToMorphe(uri, fileName)
                    } else if (callingActivity != null) {
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        }

        setContent {
            HelperTheme(themeMode = viewModel.helperSettings.themeMode) {
                val captchaCandidate = viewModel.captchaBrowser
                if (captchaCandidate != null) {
                    CaptchaBrowserScreen(
                        candidate = captchaCandidate,
                        onClose = viewModel::closeCaptchaBrowser,
                        onDownloadCaptured = viewModel::onBrowserDownloadCaptured
                    )
                } else {
                    HelperScreen(
                        request = viewModel.request,
                        state = viewModel.uiState,
                        settings = viewModel.helperSettings,
                        logs = AppLog.entries,
                        installedPackageRefreshToken = viewModel.installedPackageRefreshToken,
                        selectedPagerPage = viewModel.selectedPagerPage,
                        onPagerPageChanged = viewModel::updateSelectedPagerPage,
                        onSettingsChange = viewModel::updateHelperSettings,
                        onRefresh = viewModel::loadCandidates,
                        onResolve = viewModel::resolveCandidates,
                        onDownload = ::handleDownload,
                        onPickDownloadedFile = viewModel::returnPickedFile,
                        onUseInstalledApp = { candidate -> viewModel.returnInstalledApp(candidate, this) },
                        onVersionHistory = viewModel::loadVersionHistory,
                        onDownloadVersion = viewModel::downloadVersion,
                        onOpenHistoryEntry = { entry -> viewModel.openHistoryEntry(entry, this) },
                        onShareHistoryEntry = { entry -> viewModel.shareHistoryEntry(entry, this) },
                        onClearHistory = { DownloadHistoryStore.clear(this) },
                        onClearLogs = { AppLog.clear() },
                        onCancel = { finish() },
                        onCancelDownload = viewModel::cancelDownload,
                        onOpenMorphe = ::openMorpheManager,
                        onSendApkToMorphe = { entry -> deliverHandoffToMorphe(Uri.parse(entry.uri), entry.fileName) },
                        onSolveCaptcha = viewModel::openCaptchaBrowser,
                        onRequestFileTypeChange = viewModel::changeRequestedFileType,
                        onSearchPackage = { pkg, src -> viewModel.searchPackage(pkg, targetSource = src) },
                        onClearRequest = viewModel::clearRequest,
                        onDeliverPendingResult = viewModel::deliverPendingResult,
                        updateState = viewModel.updateState,
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        onDownloadUpdate = { info -> viewModel.downloadUpdate(this, info) },
                        onInstallUpdate = { file -> viewModel.installUpdate(this, file) },
                        onDismissUpdate = viewModel::resetUpdateState
                    )

                    viewModel.reuseOffer?.let { offer ->
                        ReuseOfferDialog(
                            options = offer,
                            onUseExisting = viewModel::useReuseOffer,
                            onDownloadNew = viewModel::dismissReuseOffer
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (viewModel.deliverPendingResultIfPresent(this)) {
            return
        }
        viewModel.handleIntent(intent)
    }

    private fun handleDownload(candidate: DownloadCandidate) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.downloadAndReturn(candidate)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.downloadAndReturn(candidate)
        }
    }

    private fun isHelperInvocation(): Boolean {
        val caller = viewModel.request?.callerPackage
        return (!caller.isNullOrBlank()) || (callingActivity != null) ||
                (intent.action == DownloadHelperContract.ACTION_DOWNLOAD_ORIGINAL_APK)
    }

    private fun resolveShareableUri(uri: Uri, fileName: String?): Uri {
        val cleanName = (fileName ?: uri.lastPathSegment ?: "app.apk").removeSuffix(".zip")
        // Check Download/Morphe Fetch public directory
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Morphe Fetch")
        val directFile = File(downloadsDir, cleanName)
        if (directFile.exists()) {
            return FileProvider.getUriForFile(this, "${packageName}.files", directFile)
        }
        val zipFile = File(downloadsDir, "$cleanName.zip")
        if (zipFile.exists()) {
            if (zipFile.renameTo(directFile)) {
                return FileProvider.getUriForFile(this, "${packageName}.files", directFile)
            }
            return FileProvider.getUriForFile(this, "${packageName}.files", zipFile)
        }
        // Check temporary downloads dir
        val tempDir = temporaryDownloadsDir()
        val tempFile = File(tempDir, cleanName)
        if (tempFile.exists()) {
            return FileProvider.getUriForFile(this, "${packageName}.files", tempFile)
        }
        // If content uri, try to copy it to temporary dir so it has clean name and FileProvider authority
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                val destFile = File(tempDir, cleanName)
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    return FileProvider.getUriForFile(this, "${packageName}.files", destFile)
                }
            }
        }
        return uri
    }

    private fun deliverHandoffToMorphe(uri: Uri, fileName: String?) {
        val shareableUri = resolveShareableUri(uri, fileName)
        val cleanFileName = (fileName ?: shareableUri.lastPathSegment ?: "app.apk").removeSuffix(".zip")
        val caller = viewModel.request?.callerPackage?.takeIf { it.isNotBlank() } ?: "app.morphe.manager"

        if (isHelperInvocation()) {
            val resultIntent = Intent().apply {
                data = shareableUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(DownloadHelperContract.EXTRA_RESULT_FILE_NAME, cleanFileName)
                viewModel.request?.packageName?.let {
                    putExtra(DownloadHelperContract.EXTRA_RESULT_PACKAGE_NAME, it)
                }
            }
            grantUriPermission(caller, shareableUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            sendApkToMorphe(shareableUri, cleanFileName)
        }
    }

    private fun sendApkToMorphe(uri: Uri, fileName: String? = null) {
        val cleanFileName = (fileName ?: uri.lastPathSegment ?: "app.apk").removeSuffix(".zip")
        val shareableUri = if (uri.authority == "${packageName}.files") uri else resolveShareableUri(uri, cleanFileName)
        val detectedType = fileNameMimeType(cleanFileName)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = detectedType
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            clipData = ClipData.newUri(contentResolver, cleanFileName, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val managerPackage = MORPHE_MANAGER_PACKAGES.firstOrNull { pkg ->
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
        if (managerPackage != null) {
            grantUriPermission(managerPackage, shareableUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sendIntent.setPackage(managerPackage)
            viewModel.appendLog("Sending APK to Morphe Manager ($managerPackage).")
            val started = runCatching {
                startActivity(sendIntent)
                true
            }.getOrElse {
                runCatching {
                    val fallbackIntent = Intent(sendIntent).apply {
                        type = "application/vnd.android.package-archive"
                    }
                    startActivity(fallbackIntent)
                    true
                }.getOrElse {
                    runCatching {
                        val fallbackIntent = Intent(sendIntent).apply {
                            type = "application/octet-stream"
                        }
                        startActivity(fallbackIntent)
                        true
                    }.getOrDefault(false)
                }
            }
            if (!started) {
                runCatching {
                    startActivity(Intent.createChooser(sendIntent, "Send to Morphe"))
                }.onFailure { openMorpheManager() }
            }
        } else {
            runCatching { startActivity(Intent.createChooser(sendIntent, "Send to Morphe")) }
                .onFailure { openMorpheManager() }
        }
    }

    private fun openMorpheManager() {
        val launchIntent = MORPHE_MANAGER_PACKAGES
            .asSequence()
            .mapNotNull { packageName -> packageManager.getLaunchIntentForPackage(packageName) }
            .firstOrNull()
        if (launchIntent != null) {
            viewModel.appendLog("Opening Morphe Manager (${launchIntent.component?.packageName ?: "Morphe Manager"}).")
            runCatching {
                startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                viewModel.appendLog("Could not open Morphe Manager.", LogLevel.Error)
            }
        } else {
            viewModel.appendLog("Morphe Manager is not installed — opening morphe.software.", LogLevel.Warning)
            val releases = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(MORPHE_MANAGER_SITE_URL)
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { startActivity(releases) }
                .onFailure {
                    viewModel.appendLog("Could not open Morphe Manager releases page.", LogLevel.Error)
                }
        }
    }
}
