package app.morphe.fetch

import java.net.URLEncoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UptodownParserTest {

    private val packageName = "com.example.app"
    private val searchUrl = "https://en.uptodown.com/android/search?query=${URLEncoder.encode(packageName, "UTF-8")}"
    // Subdomain must match the parser's [a-z0-9-]+ site regex (no dots).
    private val detailUrl = "https://example-app.en.uptodown.com/android"

    private val downloadPageHtml = """
        <html><body>
          <div id="detail-app-name" data-code="12345">Example App</div>
          <table><tr><th>Package Name</th><td>$packageName</td></tr></table>
        </body></html>
    """.trimIndent()

    private val versionsJson = """
        {
          "data": [
            {
              "fileID": 111,
              "version": "1.0.0",
              "kindFile": "APK",
              "versionURL": {
                "url": "$detailUrl",
                "extraURL": "download",
                "versionID": 111
              }
            },
            {
              "fileID": 222,
              "version": "2.0.0",
              "kindFile": "APK",
              "versionURL": {
                "url": "$detailUrl",
                "extraURL": "download",
                "versionID": 222
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun resolveHistory_listsVersionsNewestFirst() = runBlocking {
        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to
                        """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to downloadPageHtml,
                    "$detailUrl/apps/12345/versions/1" to versionsJson
                )
            )
        )

        val candidates = parser.resolveHistory(testRequest(packageName = packageName))

        assertEquals(2, candidates.size)
        assertEquals("2.0.0", candidates[0].versionName)
        assertEquals("1.0.0", candidates[1].versionName)
        assertEquals("$detailUrl/download/222", candidates[0].url)
        assertEquals("apk", candidates[0].fileKind)
        assertEquals(DownloadSource.UPTODOWN, candidates[0].source)
        assertTrue(!candidates[0].directDownload)
    }

    @Test
    fun resolveLatest_ignoresPlayStoreDataUrlExt() = runBlocking {
        val playDownloadPage = """
            <html><body>
              <div id="detail-app-name" data-code="12345">Example App</div>
              <div class="detail"><div class="info"><div class="version">2.0.0</div></div></div>
              <table><tr><th>Package Name</th><td>$packageName</td></tr></table>
              <button id="detail-download-button" class="button download external"
                data-url-ext="https://play.google.com/store/apps/details?id=$packageName"></button>
            </body></html>
        """.trimIndent()
        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to playDownloadPage
                )
            )
        )

        val candidates = parser.findCandidates(
            testRequest(packageName = packageName),
            CandidateOption.LATEST
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        // A Play Store listing is not a downloadable file  the row must fall
        // back to opening the Uptodown page instead of offering a download.
        assertTrue(!candidate.directDownload)
        assertEquals("$detailUrl/download", candidate.url)
        assertTrue(candidate.files.isEmpty())
        assertEquals("2.0.0", candidate.versionName)
    }

    @Test
    fun resolveLatest_keepsRealExternalApkDataUrlExt() = runBlocking {
        val externalApkUrl = "https://example.com/apps/example.apk"
        val externalDownloadPage = """
            <html><body>
              <div id="detail-app-name" data-code="12345">Example App</div>
              <div class="detail"><div class="info"><div class="version">2.0.0</div></div></div>
              <table><tr><th>Package Name</th><td>$packageName</td></tr></table>
              <button id="detail-download-button" class="button download external"
                data-url-ext="$externalApkUrl"></button>
            </body></html>
        """.trimIndent()
        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to externalDownloadPage
                )
            )
        )

        val candidates = parser.findCandidates(
            testRequest(packageName = packageName),
            CandidateOption.LATEST
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        // A genuine externally-hosted APK is still a valid direct download.
        assertTrue(candidate.directDownload)
        assertEquals(externalApkUrl, candidate.url)
        assertEquals(externalApkUrl, candidate.files.single().url)
    }

    @Test
    fun resolveHistoryCandidate_extractsDirectDownloadUrl() = runBlocking {
        val versionPageUrl = "$detailUrl/download/222"
        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    versionPageUrl to
                        """<html><body><a id="detail-download-button" data-url="xyz-abc"></a></body></html>"""
                )
            )
        )
        val candidate = DownloadCandidate(
            source = DownloadSource.UPTODOWN,
            name = "Example App",
            packageName = packageName,
            versionName = "2.0.0",
            versionCode = null,
            url = versionPageUrl,
            fileKind = "apk",
            option = CandidateOption.LATEST,
            directDownload = false,
            versionStatus = VersionStatus.LATEST,
            formatMatches = true
        )

        val resolved = parser.resolveHistoryCandidate(testRequest(packageName = packageName), candidate)

        assertNotNull(resolved)
        assertEquals("https://dw.uptodown.com/dwn/xyz-abc", resolved!!.url)
        assertTrue(resolved.directDownload)
        assertEquals(1, resolved.files.size)
        assertEquals("https://dw.uptodown.com/dwn/xyz-abc", resolved.files[0].url)
        assertEquals(versionPageUrl, resolved.files[0].referer)
    }

    @Test
    fun findCandidates_unhostedPackageThrowsSourceAppNotFoundException() = runBlocking {
        val unhostedPkg = "in.startv.hotstar"
        val unhostedSearchUrl = "https://en.uptodown.com/android/search?query=${URLEncoder.encode(unhostedPkg, "UTF-8")}"
        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    unhostedSearchUrl to """<html><body><a href="https://other-app.en.uptodown.com/android">Other App</a></body></html>""",
                    "https://other-app.en.uptodown.com/android/download" to """
                        <html><body><table><tr><th>Package Name</th><td>com.other.app</td></tr></table></body></html>
                    """.trimIndent()
                )
            )
        )

        val result = runCatching {
            parser.findCandidates(testRequest(packageName = unhostedPkg), CandidateOption.LATEST)
        }
        assertTrue(result.exceptionOrNull() is SourceAppNotFoundException)
    }

    @Test
    fun resolveHistory_prefersAndFiltersApkOverXapk() = runBlocking {
        val mixedVersionsJson = """
            {
              "data": [
                {
                  "fileID": 301,
                  "version": "3.0.0",
                  "kindFile": "XAPK",
                  "versionURL": {
                    "url": "$detailUrl",
                    "extraURL": "download",
                    "versionID": 301
                  }
                },
                {
                  "fileID": 302,
                  "version": "3.0.0",
                  "kindFile": "APK",
                  "versionURL": {
                    "url": "$detailUrl",
                    "extraURL": "download",
                    "versionID": 302
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to downloadPageHtml,
                    "$detailUrl/apps/12345/versions/1" to mixedVersionsJson
                )
            )
        )

        val candidates = parser.resolveHistory(
            testRequest(packageName = packageName, requestedFileType = "APK", allowSplitArchive = false)
        )

        assertEquals(1, candidates.size)
        assertEquals("3.0.0", candidates[0].versionName)
        assertEquals("apk", candidates[0].fileKind)
        assertTrue(candidates[0].formatMatches)
        assertEquals("$detailUrl/download/302", candidates[0].url)
    }

    @Test
    fun resolveRequested_prefersApkEntryOverXapk() = runBlocking {
        val mixedVersionsJson = """
            {
              "data": [
                {
                  "fileID": 401,
                  "version": "4.0.0",
                  "kindFile": "XAPK",
                  "versionURL": {
                    "url": "$detailUrl",
                    "extraURL": "download",
                    "versionID": 401
                  }
                },
                {
                  "fileID": 402,
                  "version": "4.0.0",
                  "kindFile": "APK",
                  "versionURL": {
                    "url": "$detailUrl",
                    "extraURL": "download",
                    "versionID": 402
                  }
                }
              ]
            }
        """.trimIndent()

        val versionPage402Html = """
            <html><body>
              <div id="detail-app-name" data-code="12345">Example App</div>
              <table>
                <tr><th>Package Name</th><td>$packageName</td></tr>
                <tr><th>File type</th><td>APK</td></tr>
              </table>
              <a id="detail-download-button" data-url="token-402">Download</a>
            </body></html>
        """.trimIndent()

        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to downloadPageHtml,
                    "$detailUrl/versions" to downloadPageHtml,
                    "$detailUrl/apps/12345/versions/1" to mixedVersionsJson,
                    "$detailUrl/download/402" to versionPage402Html
                )
            )
        )

        val candidates = parser.findCandidates(
            testRequest(packageName = packageName, versionName = "4.0.0", requestedFileType = "APK", allowSplitArchive = false),
            CandidateOption.REQUESTED
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("4.0.0", candidate.versionName)
        assertEquals("apk", candidate.fileKind)
        assertTrue(candidate.formatMatches)
        assertEquals("https://dw.uptodown.com/dwn/token-402", candidate.url)
    }

    @Test
    fun findCandidates_prefersArm64OverArmeabiVariants() = runBlocking {
        val downloadPageWithVariants = """
            <html><body>
              <div id="detail-app-name" data-code="12345">Example App</div>
              <div class="detail"><div class="info"><div class="version">5.0.0</div></div></div>
              <table><tr><th>Package Name</th><td>$packageName</td></tr></table>
              <button class="button variants" data-version="500"></button>
            </body></html>
        """.trimIndent()

        val variantContentHtml = """
            <div class="content">
              <div>arm64-v8a</div>
              <div class="variant">
                <div class="v-report" data-file-id="991"></div>
                <div class="v-file"><span>APK</span></div>
              </div>
              <div>armeabi-v7a</div>
              <div class="variant">
                <div class="v-report" data-file-id="992"></div>
                <div class="v-file"><span>APK</span></div>
              </div>
            </div>
        """.trimIndent()

        val variantResponseJson = """
            {
              "content": ${com.google.gson.Gson().toJson(variantContentHtml)}
            }
        """.trimIndent()

        val parser = UptodownParser(
            testParserContext(
                pages = mapOf(
                    searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                    "$detailUrl/download" to downloadPageWithVariants,
                    "https://example-app.en.uptodown.com/app/12345/version/500/files" to variantResponseJson,
                    "$detailUrl/download/991-x" to """<html><body><button id="detail-download-button"></button></body></html>"""
                )
            )
        )

        val request = testRequest(packageName = packageName).copy(
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        val candidates = parser.findCandidates(request, CandidateOption.LATEST)

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("arm64-v8a", candidate.variantLabel)
        assertEquals("$detailUrl/download/991-x", candidate.url)
    }

    @Test
    fun findCandidates_usesCachedDirectUrlFromUptodownResolvedUrlCache() = runBlocking {
        val downloadPageWithVariants = """
            <html><body>
              <div id="detail-app-name" data-code="12345">Example App</div>
              <div class="detail"><div class="info"><div class="version">5.0.0</div></div></div>
              <table><tr><th>Package Name</th><td>$packageName</td></tr></table>
              <button class="button variants" data-version="500"></button>
            </body></html>
        """.trimIndent()

        val variantContentHtml = """
            <div class="content">
              <div>arm64-v8a</div>
              <div class="variant">
                <div class="v-report" data-file-id="991"></div>
                <div class="v-file"><span>APK</span></div>
              </div>
            </div>
        """.trimIndent()

        val variantResponseJson = """
            {
              "content": ${com.google.gson.Gson().toJson(variantContentHtml)}
            }
        """.trimIndent()

        UptodownResolvedUrlCache.put("$detailUrl/download/991-x", "https://dw.uptodown.com/dwn/cached-cdn-991")

        try {
            val parser = UptodownParser(
                testParserContext(
                    pages = mapOf(
                        searchUrl to """<html><body><a href="$detailUrl">Example App</a></body></html>""",
                        "$detailUrl/download" to downloadPageWithVariants,
                        "https://example-app.en.uptodown.com/app/12345/version/500/files" to variantResponseJson
                    )
                )
            )

            val candidates = parser.findCandidates(testRequest(packageName = packageName), CandidateOption.LATEST)

            assertEquals(1, candidates.size)
            val candidate = candidates.single()
            assertTrue(candidate.directDownload)
            assertEquals("https://dw.uptodown.com/dwn/cached-cdn-991", candidate.url)
            assertEquals(null, candidate.captchaUrl)
        } finally {
            UptodownResolvedUrlCache.clear()
        }
    }
}
