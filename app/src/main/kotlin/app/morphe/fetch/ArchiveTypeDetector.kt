package app.morphe.fetch

import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Inspects zip structures of downloaded files and corrects file extensions
 * so Morphe Manager never fails due to mislabeled archives (e.g. server returning
 * .apk for an .xapk split bundle).
 */
internal object ArchiveTypeDetector {
    private const val TAG = "ArchiveTypeDetector"

    fun normalizeExtension(file: File): File {
        val detected = detect(file) ?: return file
        val lower = file.name.lowercase(Locale.US)
        if (lower.endsWith(".$detected")) {
            return file
        }
        val target = File(file.parentFile, "${file.nameWithoutExtension}.$detected")
        if (target.exists()) {
            target.delete()
        }
        if (file.renameTo(target)) {
            Log.i(TAG, "Detected ${detected.uppercase(Locale.US)} archive; renamed ${file.name} to ${target.name}")
            return target
        }
        return file
    }

    fun detect(file: File): String? {
        val lower = file.name.lowercase(Locale.US)
        if (!lower.endsWith(".apk") && !lower.endsWith(".apks") &&
            !lower.endsWith(".xapk") && !lower.endsWith(".apkm")) {
            return null
        }
        return try {
            ZipFile(file).use { zip ->
                val rootManifest = zip.getEntry("AndroidManifest.xml") != null
                val toc = zip.getEntry("toc.pb") != null
                val saiMeta = zip.getEntry("meta.sai_v2.json") != null || zip.getEntry("info.json") != null
                val xapkManifest = zip.getEntry("manifest.json") != null
                var hasApkEntry = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entryName = entries.nextElement().name.lowercase(Locale.US)
                    if (entryName.endsWith(".apk")) {
                        hasApkEntry = true
                        break
                    }
                }
                when {
                    toc -> "apks"
                    saiMeta -> "apkm"
                    xapkManifest && hasApkEntry -> "xapk"
                    rootManifest -> "apk"
                    hasApkEntry -> "apks"
                    else -> null
                }
            }
        } catch (_: IOException) {
            null
        }
    }
}
