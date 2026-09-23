package app.morphe.fetch

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

internal object FileHandoffHelper {

    fun historyFileExists(context: Context, entry: DownloadHistoryEntry): Boolean {
        val uri = Uri.parse(entry.uri)
        return runCatching {
            if (entry.uri.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                File(uri.path ?: return@runCatching false).exists()
            }
        }.getOrDefault(false)
    }

    fun historyFileSize(context: Context, entry: DownloadHistoryEntry): Long {
        val uri = Uri.parse(entry.uri)
        return runCatching {
            if (entry.uri.startsWith("content://")) {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } else {
                File(uri.path ?: return@runCatching 0L).length()
            }
        }.getOrDefault(0L)
    }

    fun returnPreparedFile(
        context: Context,
        request: HelperRequest,
        candidate: DownloadCandidate,
        file: File,
        settings: HelperSettings
    ): PendingDownloadResult {
        val uri = when (settings.downloadLocation) {
            DownloadLocation.TEMPORARY -> FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
            DownloadLocation.DOWNLOADS -> context.copyToDownloads(file)
        }
        val pending = PendingDownloadResult(
            uri = uri.toString(),
            fileName = file.name,
            packageName = candidate.packageName,
            versionName = candidate.versionName,
            sourceName = candidate.source.label,
            requestPackage = request.packageName,
            callerPackage = request.callerPackage
        )
        DownloadJobManager.persistPendingResult(pending, context)
        AppIconResolver.cacheFromApkFile(context, candidate.packageName, file)
        context.recordHandOff(request, candidate, file, uri)
        if (
            settings.downloadLocation == DownloadLocation.DOWNLOADS ||
            settings.deleteTemporaryAfterHandoff
        ) {
            context.scheduleTemporaryDelete(file)
        }
        return pending
    }

    fun copyPickedFileToTemporary(
        context: Context,
        request: HelperRequest?,
        candidate: DownloadCandidate,
        uri: Uri
    ): File {
        val displayName = displayNameForUri(context, uri)
        val extension = displayName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it != displayName }
            ?: candidate.fileKind.takeUnless { it.equals("web", ignoreCase = true) }
            ?: request?.requestedFileKinds?.orderedFileKinds()?.firstOrNull()
            ?: "apk"
        val outputName = displayName
            ?.takeIf(String::isNotBlank)
            ?: "${candidate.packageName}-${candidate.versionName ?: "manual"}.$extension"
        val outputFile = context.temporaryDownloadsDir().apply { mkdirs() }.uniqueChild(outputName)

        try {
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Selected file could not be opened.")
            check(bytesCopied > 0L) { "Selected file was empty." }
            return outputFile
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        }
    }

    fun displayNameForUri(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?.takeIf(String::isNotBlank)
    }

    fun createOpenIntent(entry: DownloadHistoryEntry): Intent {
        val uri = Uri.parse(entry.uri)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, fileNameMimeType(entry.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareIntent(context: Context, entry: DownloadHistoryEntry): Intent {
        val uri = Uri.parse(entry.uri)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = fileNameMimeType(entry.fileName)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, entry.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(share, "Share ${entry.fileName}")
    }
}
