package app.morphe.fetch

import android.Manifest
import android.app.Activity
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
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
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
