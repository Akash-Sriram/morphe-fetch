package app.morphe.fetch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal object ApkPureDirect {
    fun directUrl(packageName: String, versionCode: Long? = null, fileKind: String = "apk"): String {
        val kindPath = if (fileKind.equals("xapk", ignoreCase = true)) "XAPK" else "APK"
        val query = if (versionCode != null && versionCode > 0) "versionCode=$versionCode" else "version=latest"
        return "https://d.apkpure.com/b/$kindPath/$packageName?$query"
    }

    suspend fun resolveRedirect(client: OkHttpClient, url: String): String? = withContext(Dispatchers.IO) {
        val candidateClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MorpheHttpClient.MORPHE_FETCH_USER_AGENT)
                .head()
                .build()
            candidateClient.newCall(req).execute().use { resp ->
                if (resp.isRedirect) {
                    resp.header("Location")
                } else if (resp.isSuccessful) {
                    resp.request.url.toString().takeIf { it != url }
                } else {
                    null
                }
            }
        }.getOrNull() ?: runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MorpheHttpClient.MORPHE_FETCH_USER_AGENT)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            candidateClient.newCall(req).execute().use { resp ->
                if (resp.isRedirect) {
                    resp.header("Location")
                } else if (resp.isSuccessful || resp.code == 206) {
                    resp.request.url.toString().takeIf { it != url }
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
