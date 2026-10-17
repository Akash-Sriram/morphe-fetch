package app.morphe.fetch.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.morphe.fetch.compareVersionNames
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

internal data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

internal data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("browser_download_url") val downloadUrl: String = "",
    @SerializedName("content_type") val contentType: String = ""
)

internal data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String?,
    val changelog: String?,
    val apkUrl: String,
    val apkSize: Long,
    val isUpdateAvailable: Boolean
)

internal sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

internal class AppUpdater(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val gson: Gson = Gson(),
    private val repoOwner: String = "Akash-Sriram",
    private val repoName: String = "morphe-fetch"
) {
    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MorpheFetch-Updater")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to check for updates: HTTP ${response.code}")
        }

        val json = response.body.string()
        val release = gson.fromJson(json, GitHubRelease::class.java)

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalStateException("No APK artifact found in release ${release.tagName}")

        val latestVersion = release.tagName.removePrefix("v").trim()
        val isNewer = compareVersionNames(latestVersion, currentVersion.removePrefix("v").trim()) > 0

        UpdateInfo(
            tagName = release.tagName,
            versionName = latestVersion,
            releaseTitle = release.name ?: release.tagName,
            changelog = release.body,
            apkUrl = apkAsset.downloadUrl,
            apkSize = apkAsset.size,
            isUpdateAvailable = isNewer
        )
    }

    suspend fun downloadUpdate(
        context: Context,
        info: UpdateInfo,
        onProgress: (progress: Float, copied: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "morphe-fetch-${info.tagName}.apk")

        if (targetFile.exists() && targetFile.length() == info.apkSize && info.apkSize > 0) {
            if (validateApkArchive(targetFile, info.apkSize) == null) {
                onProgress(1.0f, info.apkSize, info.apkSize)
                return@withContext targetFile
            } else {
                targetFile.delete()
            }
        }

        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "MorpheFetch-Updater")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed with HTTP ${response.code}")
        }

        val body = response.body
        val contentLength = if (body.contentLength() > 0) body.contentLength() else info.apkSize

        val tempFile = File(updatesDir, "download-${System.currentTimeMillis()}.tmp")
        try {
            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = if (contentLength > 0) {
                            (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(progress, totalBytesRead, contentLength)
                    }
                    output.flush()

                    if (contentLength > 0 && totalBytesRead != contentLength) {
                        throw IOException(
                            "Truncated download: expected $contentLength bytes, received $totalBytesRead bytes"
                        )
                    }
                }
            }

            val archiveError = validateApkArchive(tempFile, contentLength)
            if (archiveError != null) {
                throw IllegalStateException("Downloaded update failed integrity check: $archiveError")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            targetFile
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            throw IllegalStateException("Update file does not exist: ${apkFile.absolutePath}")
        }

        try {
            validateUpdateApk(context, apkFile)
        } catch (e: Throwable) {
            apkFile.delete()
            throw e
        }

        val authority = "${context.packageName}.files"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    fun validateUpdateApk(context: Context, apkFile: File, expectedSize: Long = 0L) {
        val archiveError = validateApkArchive(apkFile, expectedSize)
        if (archiveError != null) {
            throw IllegalStateException("Invalid update archive: $archiveError")
        }

        val pm = context.packageManager
        val archive = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        } catch (e: Exception) {
            null
        } ?: throw IllegalStateException("Could not parse Android package information from update file.")

        if (archive.packageName != context.packageName) {
            throw IllegalStateException(
                "Package mismatch: expected ${context.packageName}, got ${archive.packageName}"
            )
        }

        val installedSignatures = getSignatures(context, context.packageName, null)
        val updateSignatures = getSignatures(context, null, apkFile.absolutePath)

        if (installedSignatures.isEmpty()) {
            throw IllegalStateException("Installed application signing certificates could not be verified.")
        }
        if (updateSignatures.isEmpty()) {
            throw IllegalStateException("Downloaded update contains no readable signing certificates.")
        }

        val matches = updateSignatures.any { updateSig ->
            installedSignatures.any { installedSig ->
                updateSig.contentEquals(installedSig)
            }
        }
        if (!matches) {
            throw IllegalStateException("Signature mismatch: update package was signed with a different key.")
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun showUpdateNotification(context: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for available Morphe Fetch updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_UPDATE", true)
        } ?: return

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeMb = if (info.apkSize > 0) {
            " (%.1f MB)".format(java.util.Locale.US, info.apkSize / (1024f * 1024f))
        } else ""

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Morphe Fetch ${info.tagName} Available")
            .setContentText("A new version is available$sizeMb. Tap to update.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Morphe Fetch ${info.tagName} is available on GitHub.\nTap to review and install the update."
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelUpdateNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 2001
    }
}

internal fun validateApkArchive(apkFile: File, expectedSize: Long = 0L): String? {
    if (!apkFile.isFile || apkFile.length() <= 0L) {
        return "the file is empty or does not exist"
    }

    if (expectedSize > 0L && apkFile.length() != expectedSize) {
        return "size mismatch: expected $expectedSize bytes, got ${apkFile.length()} bytes"
    }

    val entries = try {
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
    } catch (e: Exception) {
        return "corrupted or incomplete archive: ${e.message}"
    }

    if (!entries.contains("AndroidManifest.xml")) {
        return "not an APK: AndroidManifest.xml is missing"
    }

    if (entries.none { it.startsWith("classes") && it.endsWith(".dex") }) {
        return "not an APK: DEX executable code is missing"
    }

    return null
}

@Suppress("DEPRECATION")
internal fun getSignatures(context: Context, packageName: String?, apkPath: String?): List<ByteArray> {
    val pm = context.packageManager
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val packageInfo = if (apkPath != null) {
                pm.getPackageArchiveInfo(apkPath, flags)
            } else if (packageName != null) {
                pm.getPackageInfo(packageName, flags)
            } else null

            val signingInfo = packageInfo?.signingInfo
            val signatures = when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
            val result = signatures?.map { it.toByteArray() }
            if (!result.isNullOrEmpty()) {
                return result
            }
        }

        val flags = PackageManager.GET_SIGNATURES
        val packageInfo = if (apkPath != null) {
            pm.getPackageArchiveInfo(apkPath, flags)
        } else if (packageName != null) {
            pm.getPackageInfo(packageName, flags)
        } else null
        packageInfo?.signatures?.map { it.toByteArray() } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}


