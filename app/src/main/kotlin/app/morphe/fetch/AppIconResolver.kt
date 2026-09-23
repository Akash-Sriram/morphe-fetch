package app.morphe.fetch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

internal object AppIconResolver {
    private const val TAG = "AppIconResolver"
    private const val ICON_SIZE = 176

    private val maxMemoryBytes = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(4 * 1024 * 1024)
    private val memoryCache = object : LruCache<String, ImageBitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return value.width * value.height * 4
        }
    }

    private val httpClient get() = MorpheHttpClient.iconClient

    private fun diskCacheDir(context: Context): File {
        return File(context.cacheDir, "app_icons").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun diskCacheFile(context: Context, packageName: String): File {
        val safeName = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(diskCacheDir(context), "$safeName.png")
    }

    fun getCached(packageName: String): ImageBitmap? {
        return memoryCache.get(packageName)
    }

    fun cacheFromApkFile(context: Context, packageName: String, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) return
        runCatching {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            packageInfo?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                val drawable = appInfo.loadIcon(pm)
                val bitmap = drawable.toBitmap(width = ICON_SIZE, height = ICON_SIZE)
                saveToDisk(context, packageName, bitmap)
                memoryCache.put(packageName, bitmap.asImageBitmap())
            }
        }.onFailure {
            Log.w(TAG, "Failed to cache icon from APK file for $packageName", it)
        }
    }

    private fun saveToDisk(context: Context, packageName: String, bitmap: Bitmap) {
        runCatching {
            val file = diskCacheFile(context, packageName)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            Log.w(TAG, "Failed to save icon to disk for $packageName", it)
        }
    }

    private fun loadFromDisk(context: Context, packageName: String): ImageBitmap? {
        val file = diskCacheFile(context, packageName)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            imageBitmap
        }.getOrNull()
    }

    private fun loadFromInstalled(context: Context, packageName: String): ImageBitmap? {
        return runCatching {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap(width = ICON_SIZE, height = ICON_SIZE)
            saveToDisk(context, packageName, bitmap)
            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            imageBitmap
        }.getOrNull()
    }

    private fun loadFromApkUri(context: Context, packageName: String, uriString: String): ImageBitmap? {
        return runCatching {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("file://") || uri.scheme == null) {
                val path = uri.path ?: uriString
                val file = File(path)
                if (file.exists()) {
                    cacheFromApkFile(context, packageName, file)
                    return memoryCache.get(packageName)
                }
            } else if (uriString.startsWith("content://")) {
                val tempFile = File(context.cacheDir, "temp_icon_read_${System.currentTimeMillis()}.apk")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        cacheFromApkFile(context, packageName, tempFile)
                        return memoryCache.get(packageName)
                    }
                } finally {
                    tempFile.delete()
                }
            }
            null
        }.getOrNull()
    }

    private fun fetchBitmapFromUrl(url: String): Bitmap? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun fetchGooglePlayIcon(packageName: String): Bitmap? {
        return runCatching {
            val url = "https://play.google.com/store/apps/details?id=$packageName&hl=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
            if (html.isBlank()) return null

            val doc = Jsoup.parse(html)
            var iconUrl = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
            if (iconUrl.isNullOrBlank()) {
                iconUrl = doc.select("meta[name=twitter:image]").attr("content").takeIf { it.isNotBlank() }
            }
            if (iconUrl.isNullOrBlank()) {
                iconUrl = doc.select("img[itemprop=image]").attr("src").takeIf { it.isNotBlank() }
            }
            if (iconUrl.isNullOrBlank()) {
                iconUrl = doc.select("img[alt*='Icon image' i]").attr("src").takeIf { it.isNotBlank() }
            }
            if (iconUrl.isNullOrBlank()) {
                val match = Regex("""https://play-lh\.googleusercontent\.com/[a-zA-Z0-9_\-=]+""").find(html)
                iconUrl = match?.value
            }

            if (!iconUrl.isNullOrBlank()) {
                fetchBitmapFromUrl(iconUrl)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchAptoideIcon(packageName: String): Bitmap? {
        return runCatching {
            val encodedPkg = java.net.URLEncoder.encode(packageName, "UTF-8")
            val url = "https://ws75.aptoide.com/api/7/apps/search?query=$encodedPkg&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MorpheHttpClient.browserUserAgent)
                .build()
            val json = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
            val match = Regex(""""icon"\s*:\s*"([^"]+)"""").find(json)
            val iconUrl = match?.groupValues?.get(1)?.replace("\\/", "/")
            if (!iconUrl.isNullOrBlank()) {
                fetchBitmapFromUrl(iconUrl)
            } else null
        }.getOrNull()
    }

    private fun fetchFDroidIcon(packageName: String): Bitmap? {
        return runCatching {
            val url = "https://f-droid.org/en/packages/$packageName/"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
            if (html.isBlank()) return null
            val doc = Jsoup.parse(html)
            val iconSrc = doc.select(".package-icon").attr("src").takeIf { it.isNotBlank() }
                ?: doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
            if (!iconSrc.isNullOrBlank()) {
                val fullUrl = if (iconSrc.startsWith("http")) iconSrc else "https://f-droid.org$iconSrc"
                fetchBitmapFromUrl(fullUrl)
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun resolveIcon(
        context: Context,
        packageName: String,
        iconUrl: String? = null,
        apkUri: String? = null,
        isInstalled: Boolean? = null
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext null

        // 1. In-memory cache
        memoryCache.get(packageName)?.let { return@withContext it }

        // 2. Persistent disk cache
        loadFromDisk(context, packageName)?.let { return@withContext it }

        // 3. Local installed app
        if (isInstalled != false) {
            loadFromInstalled(context, packageName)?.let { return@withContext it }
        }

        // 4. Local APK file/URI if provided
        if (!apkUri.isNullOrBlank()) {
            loadFromApkUri(context, packageName, apkUri)?.let { return@withContext it }
        }

        // 5. Explicit iconUrl if provided
        if (!iconUrl.isNullOrBlank()) {
            val bitmap = fetchBitmapFromUrl(iconUrl)
            if (bitmap != null) {
                saveToDisk(context, packageName, bitmap)
                val imageBitmap = bitmap.asImageBitmap()
                memoryCache.put(packageName, imageBitmap)
                return@withContext imageBitmap
            }
        }

        // 6. Online Web Resolver: Google Play Store
        val playBitmap = fetchGooglePlayIcon(packageName)
        if (playBitmap != null) {
            saveToDisk(context, packageName, playBitmap)
            val imageBitmap = playBitmap.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            return@withContext imageBitmap
        }

        // 7. Online Web Resolver: Aptoide
        val aptoideBitmap = fetchAptoideIcon(packageName)
        if (aptoideBitmap != null) {
            saveToDisk(context, packageName, aptoideBitmap)
            val imageBitmap = aptoideBitmap.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            return@withContext imageBitmap
        }

        // 8. Online Web Resolver: F-Droid fallback
        val fdroidBitmap = fetchFDroidIcon(packageName)
        if (fdroidBitmap != null) {
            saveToDisk(context, packageName, fdroidBitmap)
            val imageBitmap = fdroidBitmap.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            return@withContext imageBitmap
        }

        null
    }

    suspend fun resolveUrl(
        context: Context,
        url: String
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val cacheKey = "url_" + url.hashCode().toString()
        memoryCache.get(cacheKey)?.let { return@withContext it }
        loadFromDisk(context, cacheKey)?.let { return@withContext it }
        val bitmap = fetchBitmapFromUrl(url)
        if (bitmap != null) {
            saveToDisk(context, cacheKey, bitmap)
            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(cacheKey, imageBitmap)
            imageBitmap
        } else {
            null
        }
    }
}
