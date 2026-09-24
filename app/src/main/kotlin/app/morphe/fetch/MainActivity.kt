package app.morphe.fetch

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
                    if (callingActivity != null) {
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } else {
                        val uri = resultIntent.data
                        if (uri != null) {
                            sendApkToMorphe(uri, resultIntent.getStringExtra(DownloadHelperContract.EXTRA_RESULT_FILE_NAME))
                        }
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
                        onSendApkToMorphe = { entry -> sendApkToMorphe(Uri.parse(entry.uri), entry.fileName) },
                        onSolveCaptcha = viewModel::openCaptchaBrowser,
                        onRequestFileTypeChange = viewModel::changeRequestedFileType,
                        onSearchPackage = viewModel::searchPackage,
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

    private fun sendApkToMorphe(uri: Uri, fileName: String? = null) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = fileName?.let { fileNameMimeType(it) } ?: "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, fileName ?: "app.apk", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val managerPackage = MORPHE_MANAGER_PACKAGES.firstOrNull { pkg ->
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
        if (managerPackage != null) {
            sendIntent.setPackage(managerPackage)
            viewModel.appendLog("Sending APK to Morphe Manager ($managerPackage).")
            runCatching { startActivity(sendIntent) }
                .onFailure {
                    startActivity(Intent.createChooser(sendIntent, "Send to Morphe"))
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
