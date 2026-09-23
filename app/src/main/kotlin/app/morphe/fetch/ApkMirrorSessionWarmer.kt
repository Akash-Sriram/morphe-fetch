package app.morphe.fetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object ApkMirrorSessionWarmer {
    private const val TAG = "ApkMirrorWarmer"
    private const val TARGET_URL = "https://www.apkmirror.com/"
    private val isWarming = AtomicBoolean(false)

    fun hasValidCfClearance(): Boolean {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return false
        val cookies = cm.getCookie(TARGET_URL).orEmpty()
        return cookies.contains("cf_clearance=")
    }

    fun warmSessionSync(context: Context, timeoutSeconds: Long = 15): Boolean {
        if (hasValidCfClearance()) {
            return true
        }

        if (!isWarming.compareAndSet(false, true)) {
            val start = System.currentTimeMillis()
            while (isWarming.get() && (System.currentTimeMillis() - start) < timeoutSeconds * 1000) {
                try {
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return hasValidCfClearance()
                }
                if (hasValidCfClearance()) return true
            }
            return hasValidCfClearance()
        }

        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            var webView: WebView? = null
            var finished = false

            fun finish(ok: Boolean) {
                if (!finished) {
                    finished = true
                    success.set(ok)
                    latch.countDown()
                    runCatching {
                        webView?.stopLoading()
                        webView?.destroy()
                    }
                }
            }

            try {
                webView = WebView(context.applicationContext).apply {
                    settings.userAgentString = MorpheHttpClient.browserUserAgent
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)

                    val pollRunnable = object : Runnable {
                        override fun run() {
                            if (finished) return
                            cm.flush()
                            if (hasValidCfClearance()) {
                                Log.i(TAG, "cf_clearance detected via polling")
                                finish(true)
                                return
                            }
                            mainHandler.postDelayed(this, 500)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            cm.flush()
                            if (hasValidCfClearance()) {
                                Log.i(TAG, "cf_clearance detected onPageFinished")
                                finish(true)
                            }
                        }
                    }

                    mainHandler.postDelayed(pollRunnable, 1000)
                    loadUrl(TARGET_URL)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start WebView warmer", e)
                finish(false)
            }
        }

        try {
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            isWarming.set(false)
        }

        val result = hasValidCfClearance()
        Log.i(TAG, "Session warming completed. hasValidCfClearance = $result")
        return result
    }
}
