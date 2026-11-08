package app.morphe.fetch

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorpheArchiveModelTest {

    private val gson = Gson()

    @Test
    fun deserializesEnrichedArchiveAppAndSource() {
        val sampleJson = """
        {
          "generatedAt": "2026-11-08 22:45 UTC",
          "appCount": 1,
          "repoCount": 1,
          "patchCount": 5,
          "apps": [
            {
              "packageName": "com.twitter.android",
              "name": "Twitter",
              "category": "Social & Communication",
              "isFeatured": true,
              "iconUrl": "https://play-lh.googleusercontent.com/sample_icon.png",
              "patchCount": 5,
              "patches": ["Hide Ads", "Download Media"],
              "sources": [
                {
                  "repo": "test/repo",
                  "host": "github.com",
                  "stars": 42,
                  "hotRank": 12,
                  "bundleIconUrl": "https://example.com/bundle.png"
                }
              ]
            }
          ],
          "repos": []
        }
        """.trimIndent()

        val parsed = gson.fromJson(sampleJson, MorpheArchiveData::class.java)
        assertNotNull(parsed)
        assertEquals(1, parsed.apps.size)

        val app = parsed.apps[0]
        assertEquals("com.twitter.android", app.packageName)
        assertEquals("Twitter", app.name)
        assertEquals("Social & Communication", app.category)
        assertTrue(app.isFeatured)
        assertEquals("https://play-lh.googleusercontent.com/sample_icon.png", app.iconUrl)

        val source = app.sources[0]
        assertEquals("test/repo", source.repo)
        assertEquals(42, source.stars)
        assertEquals(12, source.hotRank)
        assertEquals("https://example.com/bundle.png", source.bundleIconUrl)
    }
}
