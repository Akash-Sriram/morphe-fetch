package app.morphe.fetch.updater

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.morphe.fetch.compareVersionNames
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

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

        val json = response.body?.string() ?: throw IllegalStateException("Empty response from GitHub")
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
            onProgress(1.0f, info.apkSize, info.apkSize)
            return@withContext targetFile
        }

        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "MorpheFetch-Updater")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed with HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty body from APK download")
        val contentLength = if (body.contentLength() > 0) body.contentLength() else info.apkSize

        val tempFile = File(updatesDir, "download-${System.currentTimeMillis()}.tmp")
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
            }
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        targetFile
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            throw IllegalStateException("Update file does not exist: ${apkFile.absolutePath}")
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

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
}
