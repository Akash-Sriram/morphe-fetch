package app.morphe.fetch.aurora

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal class GPlayHttpClient(
    val okHttpClient: OkHttpClient
) : IHttpClient {

    private companion object {
        const val POST = "POST"
        const val GET = "GET"
        const val USER_AGENT = "com.aurora.store-4.6.0-70"
    }

    private val _responseCode = MutableStateFlow(100)
    override val responseCode: StateFlow<Int> = _responseCode.asStateFlow()

    @Throws(IOException::class)
    fun call(url: String, headers: Map<String, String> = emptyMap()): Response {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .headers(headers.toHeaders())
            .build()
        return okHttpClient.newCall(request).execute()
    }

    @Throws(IOException::class)
    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .post("".toRequestBody(null))
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray
    ): PlayResponse {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .headers(headers.toHeaders())
            .post(body.toRequestBody(null, 0, body.size))
            .build()
        return processRequest(request)
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json"
        )
        val requestBody = body.toRequestBody("application/json".toMediaType(), 0, body.size)
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .headers(headers.toHeaders())
            .post(requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun get(url: String, headers: Map<String, String>): PlayResponse =
        get(url, headers, emptyMap())

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .get()
            .build()
        return processRequest(request)
    }

    override fun getAuth(url: String): PlayResponse {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json"
        )
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .headers(headers.toHeaders())
            .get()
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        paramString: String
    ): PlayResponse {
        val request = Request.Builder()
            .url("$url$paramString".toHttpUrl())
            .headers(headers.toHeaders())
            .get()
            .build()
        return processRequest(request)
    }

    private fun processRequest(request: Request): PlayResponse {
        _responseCode.value = 0
        val call = okHttpClient.newCall(request)
        return buildPlayResponse(call.execute())
    }

    private fun buildUrl(url: String, params: Map<String, String>): HttpUrl {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        return urlBuilder.build()
    }

    private fun buildPlayResponse(response: Response): PlayResponse {
        val bytes = response.body.bytes()
        _responseCode.value = response.code
        return PlayResponse(
            isSuccessful = response.isSuccessful,
            code = response.code,
            responseBytes = bytes,
            errorString = if (!response.isSuccessful) response.message else ""
        )
    }
}
