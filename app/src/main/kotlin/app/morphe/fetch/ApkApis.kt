package app.morphe.fetch

import android.os.Build
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.random.Random

internal data class ApkMirrorLatestInfo(
    val versionName: String?,
    val openUrl: String
)

internal data class ApkMirrorVariant(
    val url: String,
    val type: String,
    val fileKind: String,
    val arch: String?,
    val dpi: String?,
    val isBundle: Boolean,
    val versionCode: Long? = null
)

internal data class UptodownVersionResponse(
    val data: List<UptodownVersionEntry> = emptyList()
)

internal data class UptodownVersionEntry(
    @SerializedName("fileID")
    val fileId: Long? = null,
    val version: String? = null,
    @SerializedName("kindFile")
    val kindFile: String? = null,
    @SerializedName("titleKindFile")
    val titleKindFile: String? = null,
    @SerializedName("versionURL")
    val versionUrl: UptodownVersionUrl? = null
)

internal data class UptodownVersionUrl(
    val url: String? = null,
    @SerializedName("extraURL")
    val extraUrl: String? = null,
    @SerializedName("versionID")
    val versionId: Long? = null
)

internal data class UptodownVariantResponse(
    val content: String? = null
)

internal data class UptodownVariantFile(
    val fileId: String,
    val fileKind: String,
    val archLabel: String?
)

internal interface ApkPureApi {
    suspend fun getAppUpdate(
        header: String,
        request: ApkPureUpdateRequest
    ): ApkPureUpdateResponse
}

internal class OkHttpApkPureApi(
    private val client: OkHttpClient,
    private val gson: Gson
) : ApkPureApi {
    override suspend fun getAppUpdate(
        header: String,
        request: ApkPureUpdateRequest
    ): ApkPureUpdateResponse = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://tapi.pureapk.com/v3/get_app_update")
            .header("content-type", "application/json")
            .header("ual-access-businessid", "projecta")
            .header("ual-access-projecta", header)
            .post(requestBody)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("APKPure API error: HTTP ${resp.code}")
            val json = resp.body?.string().orEmpty()
            gson.fromJson(json, ApkPureUpdateResponse::class.java)
        }
    }
}

internal data class ApkPureUpdateRequest(
    val app_info_for_update: List<ApkPureAppInfo>,
    val android_id: String = Random.nextLong().toString(16),
    val application_id: String = "com.apkpure.aegon",
    val cached_size: Long = -1
)

internal data class ApkPureAppInfo(
    val package_name: String,
    val version_code: Long,
    val is_system: Boolean = false,
    val version_id: String = "",
    val cached_size: Int = -1
)

internal data class ApkPureUpdateResponse(
    val retcode: Int = 0,
    val app_update_response: List<ApkPureAppUpdate> = emptyList()
)

internal data class ApkPureAppUpdate(
    val package_name: String = "",
    val version_code: Long = 0L,
    val version_name: String = "",
    val label: String = "",
    val asset: ApkPureAsset = ApkPureAsset()
)

internal data class ApkPureAsset(
    val type: String = "",
    val url: String = ""
)

internal data class ApkPureVersionEntry(
    val versionName: String?,
    val versionCode: Long?,
    val downloadPageUrl: String,
    val fileKind: String
)

internal data class ApkPureDeviceHeader(
    val device_info: ApkPureDeviceInfo = ApkPureDeviceInfo()
)

internal data class ApkPureDeviceInfo(
    val abis: List<String> = runCatching { Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList()),
    val android_id: String = Random.nextLong().toString(16),
    val os_ver: String = runCatching { Build.VERSION.SDK_INT.toString() }.getOrDefault(""),
    val os_ver_name: String = runCatching { Build.VERSION.RELEASE }.getOrNull() ?: "",
    val platform: Int = 1
)

