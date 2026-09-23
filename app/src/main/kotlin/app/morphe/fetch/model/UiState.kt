package app.morphe.fetch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

internal sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Ready(val result: CandidateResult) : UiState
    data class CheckingPickedFile(val candidate: DownloadCandidate) : UiState
    data class Downloading(
        val candidate: DownloadCandidate,
        val percent: Int,
        val speedBytesPerSec: Double = 0.0,
        val etaMs: Long? = null,
        val statusMessage: String? = null,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L
    ) : UiState
    data class Completed(
        val result: PendingDownloadResult
    ) : UiState
    data class Error(
        val message: String,
        val candidate: DownloadCandidate? = null
    ) : UiState
}

internal data class PrimaryAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val run: () -> Unit
)

internal enum class SourceSubTab(
    val label: String,
    val icon: ImageVector
) {
    Manual("Manual", Icons.Outlined.Tune),
    Recommended("Recommended", Icons.Outlined.CheckCircle),
    Latest("Latest", Icons.Outlined.Star),
    History("History", Icons.Outlined.History)
}

internal val DownloadSource.supportsRecommended: Boolean
    get() = when (this) {
        DownloadSource.PLAY -> false
        else -> true
    }

internal val DownloadSource.supportsHistory: Boolean
    get() = when (this) {
        DownloadSource.PLAY -> false
        else -> true
    }

/** A previously downloaded file offered for reuse, with its size on disk. */
internal data class ReuseOption(
    val entry: DownloadHistoryEntry,
    val sizeBytes: Long
)
