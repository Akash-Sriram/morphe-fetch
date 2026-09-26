package app.morphe.fetch.aurora

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.Activity
import android.content.Context
import app.morphe.fetch.loadHelperSettings
import app.morphe.fetch.saveHelperSettings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.UserProfile
import com.aurora.gplayapi.data.providers.DeviceInfoProvider
import com.aurora.gplayapi.helpers.AuthHelper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.Properties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class MicroGAccountTokenProvider(
    private val context: Context,
    private val httpClient: GPlayHttpClient
) {
    companion object {
        private const val TAG = "MicroGTokenProvider"
        const val REVANCED_ACCOUNT_TYPE = "app.revanced"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        val SUPPORTED_ACCOUNT_TYPES = arrayOf(REVANCED_ACCOUNT_TYPE, GOOGLE_ACCOUNT_TYPE)
        const val GOOGLE_PLAY_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/googleplay"
        const val PACKAGE_NAME_PLAY_STORE = "com.android.vending"
        const val DISPENSER_URL = "https://auroraoss.com/api/auth"

        const val GOOGLE_PLAY_CERT =
            "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK"
    }

    @Volatile
    private var cachedAuthData: AuthData? = null

    /** Clears the cached auth session so the next call to [getAuthData] fetches a fresh one. */
    fun invalidateAuth() {
        cachedAuthData = null
    }

    fun getSupportedAccountTypes(): Array<String> {
        val accountManager = AccountManager.get(context)
        val hasRevanced = runCatching { accountManager.getAccountsByType(REVANCED_ACCOUNT_TYPE) }
            .getOrNull().orEmpty().isNotEmpty()
        return if (hasRevanced) {
            arrayOf(REVANCED_ACCOUNT_TYPE)
        } else {
            arrayOf(GOOGLE_ACCOUNT_TYPE)
        }
    }

    fun getAvailableAccounts(): List<Account> {
        val accountManager = AccountManager.get(context)
        val revanced = runCatching { accountManager.getAccountsByType(REVANCED_ACCOUNT_TYPE) }
            .getOrNull()?.toList().orEmpty()
        if (revanced.isNotEmpty()) {
            return revanced
        }
        return runCatching { accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE) }
            .getOrNull()?.toList().orEmpty()
    }

    suspend fun getAuthData(activity: Activity? = null): AuthData = withContext(Dispatchers.IO) {
        cachedAuthData?.takeIf { AuthHelper.using(httpClient).isValid(it) }?.let {
            return@withContext it
        }

        val settings = context.loadHelperSettings()
        val configuredToken = settings.auroraAuthToken?.trim()
        val configuredEmail = settings.auroraEmail?.trim()?.takeIf { it.isNotBlank() }
        val deviceProps = NativeDeviceProfileProvider.getDeviceProperties(context)
        val locale = Locale.getDefault()

        // 1. If user entered / saved a token in Settings, use it directly
        if (!configuredToken.isNullOrBlank()) {
            val tokenType = if (settings.auroraTokenType.equals("AAS", ignoreCase = true)) {
                AuthHelper.Token.AAS
            } else {
                AuthHelper.Token.AUTH
            }
            val email = configuredEmail ?: "user@gmail.com"
            try {
                val authData = AuthHelper.using(httpClient).build(
                    email = email,
                    token = configuredToken,
                    tokenType = tokenType,
                    properties = deviceProps,
                    locale = locale
                )
                cachedAuthData = authData
                Log.i(TAG, "Successfully built Google Play session from configured settings ($email)")
                return@withContext authData
            } catch (e: Exception) {
                Log.w(TAG, "Configured Google Play token is invalid: ${e.message}", e)
            }
        }

        // 2. Automatically fetch session using Aurora dispenser infrastructure
        for (attempt in 1..3) {
            try {
                val authData = fetchAnonymousAuthData(deviceProps, locale)
                cachedAuthData = authData
                Log.i(TAG, "Successfully authenticated with Aurora dispenser infrastructure on attempt $attempt")
                return@withContext authData
            } catch (e: Exception) {
                Log.w(TAG, "Aurora dispenser auth attempt $attempt failed: ${e.message}")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(1000L * attempt)
                }
            }
        }

        // 3. Fallback to on-device Google account
        val accounts = getAvailableAccounts()
        if (accounts.isNotEmpty()) {
            val account = accounts.firstOrNull { it.name.equals(configuredEmail, ignoreCase = true) }
                ?: accounts.first()
            try {
                val token = fetchAccountToken(account, activity)
                val authData = AuthHelper.using(httpClient).build(
                    email = account.name,
                    token = token,
                    tokenType = AuthHelper.Token.AUTH,
                    properties = deviceProps,
                    locale = locale
                )
                cachedAuthData = authData
                Log.i(TAG, "Successfully authenticated with device account: ${account.name}")
                return@withContext authData
            } catch (e: Exception) {
                Log.w(TAG, "Failed to authenticate via AccountManager for ${account.name}: ${e.message}")
            }
        }

        throw IllegalStateException(
            "Could not authenticate with Google Play. Please check your internet connection or enter an account token in Settings."
        )
    }

    suspend fun fetchTokenForEmail(
        email: String,
        activity: Activity?,
        accountType: String? = null
    ): String {
        val accounts = getAvailableAccounts()
        // Always prefer MicroG-RE (app.revanced) because only it supports package override without UnregisteredOnApiConsole
        val revancedMatch = accounts.firstOrNull { it.name.equals(email, ignoreCase = true) && it.type == REVANCED_ACCOUNT_TYPE }
        val account = revancedMatch
            ?: if (!accountType.isNullOrBlank()) {
                accounts.firstOrNull { it.name.equals(email, ignoreCase = true) && it.type == accountType }
                    ?: Account(email, accountType)
            } else {
                accounts.firstOrNull { it.name.equals(email, ignoreCase = true) }
                    ?: Account(email, REVANCED_ACCOUNT_TYPE)
            }
        return fetchAccountToken(account, activity)
    }

    fun clearCachedSession() {
        cachedAuthData = null
    }

    private suspend fun fetchAccountToken(account: Account, activity: Activity?): String =
        suspendCancellableCoroutine { continuation ->
            val accountManager = AccountManager.get(context)
            val options = Bundle().apply {
                putString("overridePackage", PACKAGE_NAME_PLAY_STORE)
                putByteArray("overrideCertificate", Base64.decode(GOOGLE_PLAY_CERT, Base64.DEFAULT))
            }
            val handler = Handler(Looper.getMainLooper())

            val callback = object : AccountManagerCallback<Bundle> {
                var retried = false

                override fun run(future: AccountManagerFuture<Bundle>) {
                    try {
                        val result = future.result
                        val token = result.getString(AccountManager.KEY_AUTHTOKEN)
                        if (!token.isNullOrBlank()) {
                            if (continuation.isActive) continuation.resume(token)
                            return
                        }

                        // MicroG-RE AskPackageOverrideActivity returns "retry" upon user granting consent
                        val isRetry = result.getBoolean("retry", false) || result.containsKey("retry")
                        if (!retried && (isRetry || activity != null)) {
                            retried = true
                            Log.i(TAG, "MicroG consent/retry received for ${account.name} (${account.type}), requesting token again...")
                            accountManager.getAuthToken(
                                account,
                                GOOGLE_PLAY_AUTH_TOKEN_TYPE,
                                options,
                                activity,
                                this,
                                handler
                            )
                            return
                        }

                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("AccountManager returned null auth token for ${account.name}")
                            )
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            if (activity != null) {
                accountManager.getAuthToken(
                    account,
                    GOOGLE_PLAY_AUTH_TOKEN_TYPE,
                    options,
                    activity,
                    callback,
                    handler
                )
            } else {
                accountManager.getAuthToken(
                    account,
                    GOOGLE_PLAY_AUTH_TOKEN_TYPE,
                    options,
                    true,
                    callback,
                    handler
                )
            }
        }

    private fun fetchAnonymousAuthData(deviceProps: Properties, locale: Locale): AuthData {
        val jsonProps = JsonObject()
        for (name in deviceProps.stringPropertyNames()) {
            jsonProps.addProperty(name, deviceProps.getProperty(name))
        }

        val playResponse = httpClient.postAuth(
            DISPENSER_URL,
            jsonProps.toString().toByteArray(Charsets.UTF_8)
        )
        if (!playResponse.isSuccessful) {
            throw IOException("Dispenser request failed with HTTP ${playResponse.code}")
        }

        val jsonString = String(playResponse.responseBytes, Charsets.UTF_8)
        val json = JsonParser.parseString(jsonString).asJsonObject
        val email = json.get("email")?.asString ?: throw IOException("Dispenser response missing email")
        val token = json.get("authToken")?.asString
            ?: json.get("auth")?.asString
            ?: throw IOException("Dispenser response missing auth token")

        return AuthData.Builder(
            email = email,
            aasToken = json.get("aasToken")?.asString.orEmpty(),
            authToken = token,
            isAnonymous = json.get("isAnonymous")?.asBoolean ?: true,
            gsfId = json.get("gsfId")?.asString.orEmpty(),
            tokenDispenserUrl = json.get("tokenDispenserUrl")?.asString ?: DISPENSER_URL,
            ac2dmToken = json.get("ac2dmToken")?.asString.orEmpty(),
            androidCheckInToken = json.get("androidCheckInToken")?.asString.orEmpty(),
            deviceCheckInConsistencyToken = json.get("deviceCheckInConsistencyToken")?.asString.orEmpty(),
            deviceConfigToken = json.get("deviceConfigToken")?.asString.orEmpty(),
            experimentsConfigToken = json.get("experimentsConfigToken")?.asString.orEmpty(),
            gcmToken = json.get("gcmToken")?.asString.orEmpty(),
            oAuthLoginToken = json.get("oAuthLoginToken")?.asString.orEmpty(),
            dfeCookie = json.get("dfeCookie")?.asString.orEmpty(),
            locale = locale,
            deviceInfoProvider = DeviceInfoProvider(deviceProps, locale.toString()),
            userProfile = UserProfile("Anonymous", email, Artwork())
        ).build()
    }
}
