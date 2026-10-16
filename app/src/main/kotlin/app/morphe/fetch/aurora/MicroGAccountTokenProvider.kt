package app.morphe.fetch.aurora

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.Activity
import android.content.Context
import app.morphe.fetch.loadHelperSettings
import app.morphe.fetch.saveHelperSettings
import app.morphe.fetch.updateAuroraToken
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.data.models.UserProfile
import com.aurora.gplayapi.data.providers.DeviceInfoProvider
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.network.IHttpClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        const val OFFICIAL_MICROG_ACCOUNT_TYPE = "com.google"
        val SUPPORTED_ACCOUNT_TYPES = arrayOf(REVANCED_ACCOUNT_TYPE, OFFICIAL_MICROG_ACCOUNT_TYPE, "org.microg.gms")
        const val GOOGLE_PLAY_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/googleplay"
        const val PACKAGE_NAME_PLAY_STORE = "com.android.vending"
        const val DISPENSER_URL = "https://auroraoss.com/api/auth"
        val DEFAULT_DISPENSERS = listOf(
            DISPENSER_URL,
            "https://auroraoss.in/api/auth"
        )

        const val VALIDATION_PACKAGE = "com.google.android.apps.maps"

        const val GOOGLE_PLAY_CERT =
            "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5YTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK"
    }

    @Volatile
    private var cachedAuthData: AuthData? = null

    /** Uncached client wrapping GPlayHttpClient with no-cache header (matches Aurora nightly) */
    private val uncachedHttpClient = object : IHttpClient by httpClient {
        override fun get(
            url: String,
            headers: Map<String, String>,
            params: Map<String, String>
        ): PlayResponse = httpClient.get(url, headers + ("Cache-Control" to "no-cache"), params)
    }

    /**
     * Session validation matching Aurora nightly:
     * - Uses no-cache header to prevent stale cached responses from vouching for expired sessions.
     * - Probes actual Play Store API connectivity via AppDetailsHelper for VALIDATION_PACKAGE.
     * - Retains valid credentials on IOException (network unreachable / timeouts) rather than invalidating good sessions.
     */
    fun isSavedAuthDataValid(authData: AuthData): Boolean = try {
        val app = AppDetailsHelper(authData).using(uncachedHttpClient)
            .getAppByPackageName(VALIDATION_PACKAGE)
        app.packageName == VALIDATION_PACKAGE && app.displayName.isNotEmpty()
    } catch (exception: IOException) {
        Log.w(TAG, "Network unreachable while checking session validity, preserving cached session: ${exception.message}")
        true
    } catch (_: Exception) {
        false
    }

    /** Clears the cached auth session so the next call to [getAuthData] fetches a fresh one. */
    fun invalidateAuth() {
        cachedAuthData = null
    }

    /**
     * Invalidates any cached token for the given account/email in Android's AccountManager
     * and clears the cached session, so subsequent authentication requests fetch a fresh token from Google.
     */
    fun invalidateToken(email: String? = null, token: String? = null) {
        cachedAuthData = null
        val accountManager = AccountManager.get(context)
        if (!token.isNullOrBlank()) {
            for (type in SUPPORTED_ACCOUNT_TYPES) {
                runCatching { accountManager.invalidateAuthToken(type, token) }
            }
        }
        val settings = context.loadHelperSettings()
        val targetEmail = email ?: settings.auroraEmail
        val targetToken = token ?: settings.auroraAuthToken
        if (!targetToken.isNullOrBlank()) {
            for (type in SUPPORTED_ACCOUNT_TYPES) {
                runCatching { accountManager.invalidateAuthToken(type, targetToken) }
            }
        }
        // Wipe expired token from SharedPreferences so we do not read it again
        context.updateAuroraToken(token = null, email = targetEmail)
    }

    fun getSupportedAccountTypes(): Array<String> = SUPPORTED_ACCOUNT_TYPES

    fun getAvailableAccounts(): List<Account> {
        val accountManager = AccountManager.get(context)
        // 1. Prime account visibility via MicroG's AccountContentProvider
        val providerAuthorities = listOf(
            "app.revanced.android.gms.auth.accounts",
            "org.microg.gms.auth.accounts",
            "com.google.android.gms.auth.accounts"
        )
        val providerAccounts = mutableListOf<Account>()
        for (authority in providerAuthorities) {
            for (type in SUPPORTED_ACCOUNT_TYPES) {
                runCatching {
                    val uri = android.net.Uri.parse("content://$authority")
                    val bundle = context.contentResolver.call(uri, "get_accounts", type, null)
                    val parcelables = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        bundle?.getParcelableArray("accounts", Account::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        bundle?.getParcelableArray("accounts")?.filterIsInstance<Account>()?.toTypedArray()
                    }
                    if (!parcelables.isNullOrEmpty()) {
                        providerAccounts.addAll(parcelables)
                    }
                }
            }
        }

        val accounts = buildList {
            addAll(providerAccounts)
            for (type in SUPPORTED_ACCOUNT_TYPES) {
                addAll(runCatching { accountManager.getAccountsByType(type) }.getOrNull().orEmpty())
            }
        }.distinctBy { "${it.type}:${it.name.lowercase(Locale.ROOT)}" }
        Log.i(TAG, "getAvailableAccounts found ${accounts.size} account(s): ${accounts.map { "${it.name} (${it.type})" }}")
        return accounts
    }

    /**
     * Re-authenticates the configured account by requesting a fresh OAuth2 token
     * from MicroG, updating SharedPreferences, and rebuilding AuthData.
     */
    suspend fun refreshAccountToken(activity: Activity? = null): AuthData = withContext(Dispatchers.IO) {
        cachedAuthData = null
        val settings = context.loadHelperSettings()
        val email = settings.auroraEmail?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No account configured to refresh.")

        Log.i(TAG, "Refreshing Google Play token via MicroG for account $email...")
        val freshToken = fetchTokenForEmail(email, activity, settings.auroraTokenType)
        context.updateAuroraToken(token = freshToken, email = email, tokenType = "AUTH")

        val deviceProps = NativeDeviceProfileProvider.getDeviceProperties(context)
        val locale = Locale.getDefault()
        val authData = AuthHelper.build(
            email = email,
            token = freshToken,
            tokenType = AuthHelper.Token.AUTH,
            properties = deviceProps,
            locale = locale
        )
        cachedAuthData = authData
        Log.i(TAG, "Successfully refreshed Google Play session for $email")
        authData
    }

    suspend fun getAuthData(activity: Activity? = null): AuthData = withContext(Dispatchers.IO) {
        cachedAuthData?.takeIf { isSavedAuthDataValid(it) }?.let {
            return@withContext it
        }

        val settings = context.loadHelperSettings()
        val configuredToken = settings.auroraAuthToken?.trim()
        val configuredEmail = settings.auroraEmail?.trim()?.takeIf { it.isNotBlank() }
        val deviceProps = NativeDeviceProfileProvider.getDeviceProperties(context)
        val locale = Locale.getDefault()

        // 1. If an account is logged in / configured, prioritize it
        if (configuredEmail != null) {
            if (!configuredToken.isNullOrBlank()) {
                val tokenType = if (settings.auroraTokenType.equals("AAS", ignoreCase = true)) {
                    AuthHelper.Token.AAS
                } else {
                    AuthHelper.Token.AUTH
                }
                try {
                    val authData = AuthHelper.build(
                        email = configuredEmail,
                        token = configuredToken,
                        tokenType = tokenType,
                        properties = deviceProps,
                        locale = locale
                    )
                    if (isSavedAuthDataValid(authData)) {
                        cachedAuthData = authData
                        Log.i(TAG, "Successfully built and validated Google Play session from configured settings ($configuredEmail)")
                        return@withContext authData
                    } else {
                        Log.w(TAG, "Cached token for $configuredEmail failed validation, auto-refreshing via MicroG...")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cached token for $configuredEmail is invalid, auto-refreshing via MicroG...", e)
                }
            }

            // Token is absent or expired -> auto-refresh from MicroG
            try {
                return@withContext refreshAccountToken(activity)
            } catch (e: Exception) {
                Log.w(TAG, "Could not auto-refresh token for $configuredEmail: ${e.message}. Falling back to anonymous session.")
                if (activity != null) {
                    throw IllegalStateException(
                        "Google Play session for $configuredEmail has expired. Please re-authenticate in Settings.",
                        e
                    )
                }
            }
        }

        // 2. Anonymous mode: fetch session using custom or default dispenser endpoints
        val dispensersToTry = buildList {
            settings.auroraCustomDispenser?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(DEFAULT_DISPENSERS)
        }.distinct()

        for (dispenser in dispensersToTry) {
            for (attempt in 1..2) {
                try {
                    val authData = fetchAnonymousAuthData(dispenser, deviceProps, locale)
                    cachedAuthData = authData
                    Log.i(TAG, "Successfully authenticated with Aurora dispenser ($dispenser) on attempt $attempt")
                    return@withContext authData
                } catch (e: Exception) {
                    Log.w(TAG, "Aurora dispenser ($dispenser) attempt $attempt failed: ${e.message}")
                    if (attempt < 2) {
                        kotlinx.coroutines.delay(800L * attempt)
                    }
                }
            }
        }

        // 3. Fallback to on-device MicroG accounts if available
        val accounts = getAvailableAccounts()
        if (accounts.isNotEmpty()) {
            val account = accounts.first()
            try {
                val token = fetchAccountToken(account, activity)
                val authData = AuthHelper.build(
                    email = account.name,
                    token = token,
                    tokenType = AuthHelper.Token.AUTH,
                    properties = deviceProps,
                    locale = locale
                )
                context.updateAuroraToken(token = token, email = account.name, tokenType = "AUTH")
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
        // Invalidate any previously cached token for this account so we don't get a stale/expired token
        invalidateToken(email = email)
        return fetchAccountToken(account, activity)
    }

    fun clearCachedSession() {
        cachedAuthData = null
    }

    private suspend fun fetchAccountToken(account: Account, activity: Activity?): String {
        val timeoutMs = if (activity != null) 30000L else 8000L
        return withTimeout(timeoutMs) {
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

                            val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                result.getParcelable(AccountManager.KEY_INTENT, android.content.Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                result.getParcelable(AccountManager.KEY_INTENT) as? android.content.Intent
                            }

                            if (intent != null) {
                                if (activity != null) {
                                    Log.i(TAG, "Launching MicroG consent intent for ${account.name}...")
                                    runCatching { activity.startActivity(intent) }
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            IllegalStateException("Please grant MicroG permission in the opened prompt, then tap login again.")
                                        )
                                    }
                                    return
                                } else {
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            IllegalStateException("MicroG consent required for ${account.name}. Please select this account in Morphe Fetch Settings to authorize.")
                                        )
                                    }
                                    return
                                }
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
                                val msg = if (e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true) {
                                    "Google Play Services does not permit direct login for third-party apps. Please stay with Anonymous session or use MicroG-RE."
                                } else {
                                    e.message ?: "Authentication failed"
                                }
                                continuation.resumeWithException(IllegalStateException(msg, e))
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
        }
    }

    private fun fetchAnonymousAuthData(dispenserUrl: String, deviceProps: Properties, locale: Locale): AuthData {
        val jsonProps = JsonObject()
        for (name in deviceProps.stringPropertyNames()) {
            jsonProps.addProperty(name, deviceProps.getProperty(name))
        }

        val playResponse = httpClient.postAuth(
            dispenserUrl,
            jsonProps.toString().toByteArray(Charsets.UTF_8)
        )
        if (!playResponse.isSuccessful) {
            throw IOException("Dispenser ($dispenserUrl) request failed with HTTP ${playResponse.code}")
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
            tokenDispenserUrl = json.get("tokenDispenserUrl")?.asString ?: dispenserUrl,
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
