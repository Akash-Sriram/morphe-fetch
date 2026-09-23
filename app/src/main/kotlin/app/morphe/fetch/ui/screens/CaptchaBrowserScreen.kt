package app.morphe.fetch

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

@Composable
internal fun CaptchaBrowserScreen(
    candidate: DownloadCandidate,
    onClose: () -> Unit,
    onDownloadCaptured: (BrowserDownloadCapture) -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var autoPilotStatus by remember { mutableStateOf<String?>(null) }
    var webViewRef: WebView? = null
    val bridge = remember {
        CaptchaCaptureBridge(
            onUrl = { url ->
                Handler(Looper.getMainLooper()).post {
                    val referer = webViewRef?.url ?: webViewRef?.originalUrl ?: candidate.captchaUrl ?: candidate.url
                    CookieManager.getInstance().flush()
                    val cookies = extractCombinedCookies(url, referer)
                    onDownloadCaptured(
                        BrowserDownloadCapture(
                            downloadUrl = url,
                            refererUrl = referer,
                            cookieHeader = cookies
                        )
                    )
                }
            },
            onLog = { message ->
                Handler(Looper.getMainLooper()).post {
                    autoPilotStatus = message
                }
            }
        )
    }
    val webView = remember {
        WebView(context).apply {
            webViewRef = this
            settings.userAgentString = MorpheHttpClient.browserUserAgent
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(bridge, CAPTCHA_BRIDGE_NAME)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (WebViewAdBlocker.isAd(uri)) {
                        return true
                    }
                    val url = uri.toString()
                    if (url.looksLikeApkDownload()) {
                        val referer = view?.url ?: view?.originalUrl ?: candidate.captchaUrl ?: candidate.url
                        CookieManager.getInstance().flush()
                        val cookies = extractCombinedCookies(url, referer)
                        onDownloadCaptured(
                            BrowserDownloadCapture(
                                downloadUrl = url,
                                refererUrl = referer,
                                cookieHeader = cookies
                            )
                        )
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url
                    if (WebViewAdBlocker.isAd(uri)) {
                        return WebViewAdBlocker.createEmptyResource()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    view?.evaluateJavascript(WebViewAdBlocker.INJECT_CSS_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    view?.evaluateJavascript(WebViewAdBlocker.INJECT_CSS_JS, null)
                    view?.evaluateJavascript(CAPTCHA_CAPTURE_JS, null)
                    if (candidate.source == DownloadSource.APK_MIRROR) {
                        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                        val targetVersion = candidate.versionName.orEmpty()
                        val autoPilotScript = buildAutoPilotJs(targetVersion, preferredAbi)
                        view?.postDelayed({
                            view.evaluateJavascript(autoPilotScript, null)
                        }, 700)
                    }
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                val referer = this.url ?: this.originalUrl ?: candidate.captchaUrl ?: candidate.url
                CookieManager.getInstance().flush()
                val cookies = extractCombinedCookies(url, referer)
                onDownloadCaptured(
                    BrowserDownloadCapture(
                        downloadUrl = url,
                        refererUrl = referer,
                        cookieHeader = cookies
                    )
                )
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(candidate.captchaUrl ?: candidate.url) {
        webView.loadUrl(candidate.captchaUrl ?: candidate.url)
    }
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val browserTitle = if (candidate.captchaUrl != null && !candidate.directDownload) {
                        "Solve captcha"
                    } else {
                        "Open in app"
                    }
                    Text(
                        text = "$browserTitle  ${candidate.source.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = candidate.versionDisplay,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HelperOutlinedButton(
                    text = "Close",
                    onClick = onClose,
                    icon = Icons.Outlined.Close,
                    modifier = Modifier.widthIn(min = MorpheDefaults.CompactButtonWidth)
                )
            }
            if (candidate.captchaUrl != null && !candidate.directDownload) {
                InfoBox(title = "Solve the captcha", icon = Icons.Outlined.Shield) {
                    Text(
                        text = "When the page starts the download, the file is captured and " +
                            "returned to Morphe automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                InfoBox(title = "Find the download link", icon = Icons.Outlined.OpenInBrowser) {
                    Text(
                        text = "The app downloads the file once the page offers it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "package \"${candidate.packageName}\" · version " +
                            "\"${candidate.versionDisplay}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            autoPilotStatus?.let { status ->
                InfoBox(title = "Auto-pilot", icon = Icons.Outlined.Download) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (progress > 0 && progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SectionCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private class CaptchaCaptureBridge(
    private val onUrl: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    @JavascriptInterface
    fun capture(url: String) {
        onUrl(url)
    }

    @JavascriptInterface
    fun log(message: String) {
        onLog(message)
    }
}

private const val CAPTCHA_BRIDGE_NAME = "Android"

private const val CAPTCHA_CAPTURE_JS = """
(function () {
  var re = /\.(apk|apks|apkm|xapk)(\?|#|$)/i;
  var dlPhp = /download\.php\?.*id=\d+/i;
  function looksLike(u) {
    if (!u) return false;
    try { u = decodeURIComponent(u); } catch (e) {}
    return re.test(u) || /filename[^.]*\.(apk|apks|apkm|xapk)/i.test(u) || dlPhp.test(u) || /download\.php\?id=\d+/i.test(u);
  }
  
  document.addEventListener('click', function (e) {
    var el = e.target;
    while (el && el !== document && !(el.tagName === 'A' && el.href)) el = el.parentNode;
    if (el && el.tagName === 'A' && looksLike(el.href)) {
      window.Android && window.Android.capture(el.href);
    }
  }, true);
  var origOpen = window.open;
  window.open = function (u) {
    if (u && looksLike(u)) { window.Android && window.Android.capture(u); return null; }
    return origOpen ? origOpen.apply(window, arguments) : null;
  };
})();
"""

private fun String.looksLikeApkDownload(): Boolean {
    val decoded = Uri.decode(this).lowercase(Locale.US)
    return Regex("""\.(apk|apks|apkm|xapk)(\?|#|$)""").containsMatchIn(decoded) ||
        Regex("""filename[^.]*\.(apk|apks|apkm|xapk)""").containsMatchIn(decoded) ||
        Regex("""download\.php\?.*id=\d+""").containsMatchIn(decoded) ||
        Regex("""download\.php\?id=\d+""").containsMatchIn(decoded)
}

private fun extractCombinedCookies(url: String, referer: String?): String? {
    val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
    val cookies = buildList {
        cm.getCookie(url)?.takeIf { it.isNotBlank() }?.let(::add)
        referer?.takeIf { it.isNotBlank() }?.let { ref ->
            cm.getCookie(ref)?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (url.contains("apkmirror.com", ignoreCase = true) || referer?.contains("apkmirror.com", ignoreCase = true) == true) {
            cm.getCookie("https://www.apkmirror.com/")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    if (cookies.isEmpty()) return null
    val map = mutableMapOf<String, String>()
    cookies.forEach { header ->
        header.split(";").forEach { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                map[key] = value
            }
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

private fun buildAutoPilotJs(targetVersion: String, preferredAbi: String): String {
    val escapedVer = targetVersion.replace("\"", "\\\"")
    val escapedAbi = preferredAbi.replace("\"", "\\\"")
    return """
(function () {
  try {
    if (document.title.indexOf('Just a moment') !== -1 || document.querySelector('iframe[src*="cloudflare"]')) {
      return;
    }

    var targetVer = "$escapedVer";
    var preferredAbi = "$escapedAbi";

    // Step 4: Final download page -> click #download-link
    var finalDl = document.querySelector('a#download-link, a.downloadButton[href*="download.php"]');
    if (finalDl && finalDl.href && !finalDl.getAttribute('data-autopilot-clicked')) {
      finalDl.setAttribute('data-autopilot-clicked', 'true');
      window.Android && window.Android.log && window.Android.log("Starting download...");
      finalDl.click();
      return;
    }

    // Step 3: Variant page -> click "Download APK" (.downloadButton)
    var dlBtn = document.querySelector('a.downloadButton, a[href*="/download/?key="]');
    if (dlBtn && dlBtn.href && !dlBtn.getAttribute('data-autopilot-clicked')) {
      dlBtn.setAttribute('data-autopilot-clicked', 'true');
      window.Android && window.Android.log && window.Android.log("Clicking Download APK...");
      dlBtn.click();
      return;
    }

    // Step 2: Release page with variants table
    var variantRows = document.querySelectorAll('div.table-row.headerFont, div.table-row');
    if (variantRows && variantRows.length > 0) {
      var bestLink = null;
      var bestScore = -1;
      for (var i = 0; i < variantRows.length; i++) {
        var row = variantRows[i];
        var badge = row.querySelector('span.apkm-badge');
        var badgeText = badge ? badge.textContent.trim().toUpperCase() : '';
        if (badgeText.indexOf('BUNDLE') !== -1) continue;

        var rowText = row.textContent.toLowerCase();
        var score = 1;
        if (preferredAbi && rowText.indexOf(preferredAbi.toLowerCase()) !== -1) {
          score += 10;
        }
        if (rowText.indexOf('nodpi') !== -1) {
          score += 2;
        }
        if (score > bestScore) {
          var link = row.querySelector('div.table-cell a[href*="-apk/"], a.downloadLink, a[href*="-apk/"]');
          if (!link) {
            var allLinks = row.querySelectorAll('a[href]');
            for (var j = 0; j < allLinks.length; j++) {
              if (allLinks[j].href.indexOf('-apk/') !== -1 || allLinks[j].href.indexOf('-download/') !== -1) {
                link = allLinks[j];
                break;
              }
            }
          }
          if (link) {
            bestScore = score;
            bestLink = link;
          }
        }
      }
      if (!bestLink) {
        for (var i = 0; i < variantRows.length; i++) {
          var row = variantRows[i];
          var link = row.querySelector('div.table-cell a[href*="-apk/"], a.downloadLink, a[href*="-download/"]');
          if (link) { bestLink = link; break; }
        }
      }
      if (bestLink && !bestLink.getAttribute('data-autopilot-clicked')) {
        bestLink.setAttribute('data-autopilot-clicked', 'true');
        window.Android && window.Android.log && window.Android.log("Selected " + preferredAbi + " APK variant...");
        bestLink.click();
        return;
      }
    }

    // Step 1: Search results page -> click matching release row
    var releaseLinks = document.querySelectorAll('a[href*="-release/"]');
    if (releaseLinks && releaseLinks.length > 0) {
      var chosenLink = null;
      for (var k = 0; k < releaseLinks.length; k++) {
        var rLink = releaseLinks[k];
        if (rLink.href.indexOf('#') !== -1) continue;
        var linkText = (rLink.textContent || '') + ' ' + (rLink.href || '');
        if (targetVer && (linkText.indexOf(targetVer) !== -1 || linkText.indexOf(targetVer.replace(/\./g, '-')) !== -1)) {
          chosenLink = rLink;
          break;
        }
      }
      if (!chosenLink && releaseLinks.length > 0) {
        for (var m = 0; m < releaseLinks.length; m++) {
          if (releaseLinks[m].href.indexOf('#') === -1) {
            chosenLink = releaseLinks[m];
            break;
          }
        }
      }
      if (chosenLink && !chosenLink.getAttribute('data-autopilot-clicked')) {
        chosenLink.setAttribute('data-autopilot-clicked', 'true');
        window.Android && window.Android.log && window.Android.log("Selecting release " + (targetVer || "") + "...");
        chosenLink.click();
        return;
      }
    }
  } catch (e) {
    window.Android && window.Android.log && window.Android.log("Auto-pilot error: " + e.message);
  }
})();
    """.trimIndent()
}

