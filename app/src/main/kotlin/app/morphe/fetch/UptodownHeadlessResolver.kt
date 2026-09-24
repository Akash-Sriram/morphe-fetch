package app.morphe.fetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless background resolver for Uptodown variant download pages.
 *
 * Uptodown uses Cloudflare Turnstile to guard download links. On genuine
 * Android devices, Turnstile passes non-interactively in a background WebView
 * in 500-1500ms. This resolver loads the variant page headlessly, lets Turnstile
 * execute, captures the generated direct CDN download URL, flushes clearance
 * cookies into [CookieManager], and returns the direct link — completely
 * avoiding opening the browser for the user.
 */
internal object UptodownHeadlessResolver {
    private const val TAG = "UptodownHeadless"

    suspend fun resolveDownloadUrl(
        context: Context,
        pageUrl: String,
        timeoutMs: Long = 10000L
    ): String? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.Main) {
            var webView: WebView? = null
            var resolvedUrl: String? = null
            val isDone = AtomicBoolean(false)

            try {
                suspendCancellableCoroutine<String?> { cont ->
                    fun finish(url: String?) {
                        if (isDone.compareAndSet(false, true)) {
                            resolvedUrl = url
                            runCatching { CookieManager.getInstance().flush() }
                            if (cont.isActive) cont.resumeWith(Result.success(url))
                            runCatching {
                                webView?.stopLoading()
                                webView?.destroy()
                                webView = null
                            }
                        }
                    }

                    cont.invokeOnCancellation {
                        finish(null)
                    }

                    class HeadlessBridge {
                        @JavascriptInterface
                        fun onFound(url: String) {
                            Handler(Looper.getMainLooper()).post {
                                val trimmed = url.trim()
                                val normalized = if (!trimmed.startsWith("http", ignoreCase = true)) {
                                    "https://dw.uptodown.com/dwn/${trimmed.trimStart('/')}"
                                } else {
                                    trimmed
                                }
                                if (!normalized.contains("uptodown-") && (normalized.looksLikeApkDownload() || normalized.contains("dw.uptodown"))) {
                                    Log.i(TAG, "Captured direct URL via JS: $normalized")
                                    finish(normalized)
                                }
                            }
                        }
                    }

                    val view = WebView(context.applicationContext)
                    webView = view
                    view.layout(0, 0, 1080, 1920)
                    view.settings.apply {
                        userAgentString = MorpheHttpClient.browserUserAgent
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(view, true)

                    view.addJavascriptInterface(HeadlessBridge(), "UptodownHeadless")

                    view.setDownloadListener { url, _, _, _, _ ->
                        val normalized = url.trim()
                        if (!normalized.contains("uptodown-") && (normalized.looksLikeApkDownload() || normalized.contains("dw.uptodown"))) {
                            Log.i(TAG, "Captured direct URL via DownloadListener: $normalized")
                            finish(normalized)
                        }
                    }

                    view.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(v, url, favicon)
                            v?.evaluateJavascript(
                                """
                                (function() {
                                  window.addEventAnalytics = window.addEventAnalytics || function() {};
                                  try {
                                    Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                                    Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
                                  } catch(e) {}
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            if (WebViewAdBlocker.isAd(uri)) return true
                            val url = uri.toString().trim()
                            if (!url.contains("uptodown-") && (url.looksLikeApkDownload() || url.contains("dw.uptodown"))) {
                                Log.i(TAG, "Captured direct URL via shouldOverrideUrlLoading: $url")
                                finish(url)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(v: WebView?, url: String?) {
                            super.onPageFinished(v, url)
                            cm.flush()
                            v?.evaluateJavascript(HEADLESS_POLL_JS, null)
                        }
                    }

                    val pollHandler = Handler(Looper.getMainLooper())
                    val pollRunnable = object : Runnable {
                        override fun run() {
                            if (isDone.get()) return
                            cm.flush()
                            view.evaluateJavascript(HEADLESS_POLL_JS, null)
                            pollHandler.postDelayed(this, 600)
                        }
                    }
                    pollHandler.postDelayed(pollRunnable, 800)

                    view.loadUrl(pageUrl)
                }
            } finally {
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
            }
        }
    }

    private const val HEADLESS_POLL_JS = """
(function() {
  try {
    if (typeof window.addEventAnalytics !== 'function') {
      window.addEventAnalytics = function() {};
    }
    try {
      Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
      Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
    } catch(e) {}

    var variantRows = document.querySelectorAll('.content .variant, div.variant');
    if (variantRows && variantRows.length > 0) {
      var target = variantRows[0].querySelector('a[href], .v-report');
      if (target && !target.getAttribute('data-headless-clicked')) {
        target.setAttribute('data-headless-clicked', 'true');
        target.click();
        return;
      }
    }

    var btn = document.querySelector('#detail-download-button, a.button.download[data-url], button#detail-download-button');
    if (btn) {
      var u = btn.getAttribute('data-url');
      if (u && u.length > 5 && u.indexOf('uptodown-') === -1) {
        if (!u.startsWith('http')) {
          u = 'https://dw.uptodown.com/dwn/' + u.replace(/^\/+/, '');
        }
        window.UptodownHeadless && window.UptodownHeadless.onFound(u);
        return;
      }

      if (window.turnstile && typeof window.onDownloadTurnstileAPILoaded === 'function' && !window._turnstileTriggered) {
        window._turnstileTriggered = true;
        try { window.onDownloadTurnstileAPILoaded(); } catch(e) {}
      }

      btn.click();
    }
  } catch(e) {}
})();
"""
}
