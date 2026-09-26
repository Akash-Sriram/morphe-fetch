package app.morphe.fetch

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import okhttp3.Cache
import okhttp3.CipherSuite
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bridges OkHttp requests with Android's system [CookieManager].
 * This synchronizes cookies between in-app WebViews (e.g. where Cloudflare Turnstile
 * is solved) and OkHttp background requests (scrapers & downloaders).
 */
internal object WebKitCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        runCatching { cookieManager.flush() }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyList()
        val rawHeaders = buildList {
            cookieManager.getCookie(url.toString())?.takeIf { it.isNotBlank() }?.let(::add)
            val host = url.host.lowercase(Locale.US)
            val baseDomains = listOf("apkmirror.com", "uptodown.com", "apkcombo.com", "apkpure.com")
            for (domain in baseDomains) {
                if (host.endsWith(domain)) {
                    cookieManager.getCookie("https://www.$domain/")?.takeIf { it.isNotBlank() }?.let(::add)
                    cookieManager.getCookie("https://$domain/")?.takeIf { it.isNotBlank() }?.let(::add)
                    if (domain == "uptodown.com") {
                        cookieManager.getCookie("https://en.uptodown.com/")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                    break
                }
            }
        }
        if (rawHeaders.isEmpty()) return emptyList()

        val parsedCookies = mutableMapOf<String, Cookie>()
        for (header in rawHeaders) {
            header.split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val name = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(url.host)
                            .build()
                    }.getOrNull()?.let { cookie ->
                        parsedCookies[name] = cookie
                    }
                }
            }
        }
        return parsedCookies.values.toList()
    }
}

internal fun sanitizeToChromeUserAgent(rawUa: String): String {
    val chromeMatch = Regex("""Chrome/([\d.]+)""").find(rawUa)
    val chromeVersion = chromeMatch?.groupValues?.getOrNull(1) ?: "131.0.6778.200"
    return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
}

internal fun derivedSecChUa(userAgent: String): String {
    val major = Regex("""Chrome/(\d+)""").find(userAgent)?.groupValues?.getOrNull(1) ?: "131"
    return "\"Google Chrome\";v=\"$major\", \"Chromium\";v=\"$major\", \"Not_A Brand\";v=\"24\""
}

internal fun buildAcceptLanguage(): String {
    val locale = Locale.getDefault()
    val tag = locale.toLanguageTag()
    val lang = locale.language
    val tags = linkedSetOf<String>()
    if (tag.isNotBlank()) tags.add(tag)
    if (lang.isNotBlank()) tags.add(lang)
    tags.add("en-US")
    tags.add("en")

    var q = 1.0
    val parts = mutableListOf<String>()
    for (t in tags) {
        if (q >= 1.0) {
            parts.add(t)
        } else {
            parts.add("$t;q=${String.format(Locale.US, "%.1f", q)}")
        }
        q -= 0.1
        if (q < 0.1) q = 0.1
    }
    return parts.joinToString(",")
}

/**
 * Centralized OkHttpClient factory and connection pooling.
 *
 * Sharing a single [ConnectionPool] and dispatcher avoids socket starvation,
 * minimizes thread pool churn, enables HTTP/2 connection multiplexing,
 * and saves significant heap memory.
 */
internal object MorpheHttpClient {

    val connectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    @Volatile
    var browserUserAgent: String =
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private set

    const val APKPURE_USER_AGENT = "APKPure/3.19.39 (Aegon)"

    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    private var cacheDir: File? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "http_cache")
        }
        runCatching {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { ua ->
            browserUserAgent = sanitizeToChromeUserAgent(ua)
        }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
        }
    }

    private fun createBaseClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .cookieJar(WebKitCookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        cacheDir?.let { dir ->
            runCatching {
                builder.cache(Cache(dir, 20L * 1024L * 1024L))
            }
        }
        return builder
    }

    val baseClient: OkHttpClient by lazy {
        createBaseClientBuilder()
            .connectionSpecs(listOf(chromeConnectionSpec, ConnectionSpec.CLEARTEXT))
            .addInterceptor { chain ->
                val req = chain.request()
                val referer = req.header("Referer")
                val reqHost = req.url.host

                val fetchSite = when {
                    referer.isNullOrBlank() -> "none"
                    runCatching {
                        val refHost = java.net.URI(referer).host
                        refHost != null && (refHost == reqHost || refHost.endsWith(reqHost.substringAfter('.')))
                    }.getOrDefault(false) -> "same-origin"
                    else -> "cross-site"
                }

                val reqBuilder = req.newBuilder()
                    .header("User-Agent", browserUserAgent)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                    )
                    .header("Accept-Language", buildAcceptLanguage())
                    .header("sec-ch-ua", derivedSecChUa(browserUserAgent))
                    .header("sec-ch-ua-mobile", "?1")
                    .header("sec-ch-ua-platform", "\"Android\"")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Site", fetchSite)
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Priority", "u=0, i")

                if (req.header("Cache-Control") == null) {
                    reqBuilder.header("Cache-Control", "max-age=0")
                }

                chain.proceed(reqBuilder.build())
            }
            .addInterceptor(httpLoggingInterceptor("Default"))
            .build()
    }

    private val chromeConnectionSpec by lazy {
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .cipherSuites(
                CipherSuite.TLS_AES_128_GCM_SHA256,
                CipherSuite.TLS_AES_256_GCM_SHA384,
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
            )
            .build()
    }

    val apkMirrorClient: OkHttpClient by lazy {
        createBaseClientBuilder()
            .connectionSpecs(listOf(chromeConnectionSpec, ConnectionSpec.CLEARTEXT))
            .addInterceptor { chain ->
                val req = chain.request()
                val referer = req.header("Referer")
                val reqHost = req.url.host

                val fetchSite = when {
                    referer.isNullOrBlank() -> "none"
                    runCatching {
                        val refHost = java.net.URI(referer).host
                        refHost != null && (refHost == reqHost || refHost.endsWith(".apkmirror.com", ignoreCase = true))
                    }.getOrDefault(false) -> "same-origin"
                    else -> "cross-site"
                }

                val reqBuilder = req.newBuilder()
                    .header("User-Agent", browserUserAgent)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                    )
                    .header("Accept-Language", buildAcceptLanguage())
                    .header("sec-ch-ua", derivedSecChUa(browserUserAgent))
                    .header("sec-ch-ua-mobile", "?1")
                    .header("sec-ch-ua-platform", "\"Android\"")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Site", fetchSite)
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Priority", "u=0, i")

                if (req.header("Cache-Control") == null) {
                    reqBuilder.header("Cache-Control", "max-age=0")
                }

                val builtRequest = reqBuilder.build()
                val response = chain.proceed(builtRequest)
                if (response.code == 403 && reqHost.contains("apkmirror.com", ignoreCase = true)) {
                    val ctx = appContext
                    if (ctx != null) {
                        val warmed = ApkMirrorSessionWarmer.warmSessionSync(ctx)
                        if (warmed) {
                            response.close()
                            return@addInterceptor chain.proceed(builtRequest)
                        }
                    }
                }
                response
            }
            .addInterceptor(httpLoggingInterceptor("APKMirror"))
            .build()
    }

    val apkMirrorDownloadClient: OkHttpClient by lazy {
        apkMirrorClient.newBuilder()
            .connectionPool(connectionPool)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val apkPureClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .connectionPool(connectionPool)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", APKPURE_USER_AGENT)
                        .build()
                )
            }
            .addInterceptor(httpLoggingInterceptor("APKPure"))
            .build()
    }

    val downloadClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .connectionPool(connectionPool)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(httpLoggingInterceptor("Download"))
            .build()
    }

    val iconClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .connectionPool(connectionPool)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val gplayClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(httpLoggingInterceptor("Aurora"))
            .build()
    }
}
