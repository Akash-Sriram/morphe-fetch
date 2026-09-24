package app.morphe.fetch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MorpheArchiveMatchTest {

    private val youtube = ArchiveApp(
        packageName = "com.google.android.youtube",
        name = "YouTube",
        patches = listOf("ad-block", "sponsorblock")
    )
    private val ytMusic = ArchiveApp(
        packageName = "com.google.android.apps.youtube.music",
        name = "YouTube Music",
        patches = listOf("ad-block")
    )
    private val twitter = ArchiveApp(
        packageName = "com.twitter.android",
        name = "Twitter",
        patches = listOf("dynamic-color")
    )

    @Before
    fun setUp() {
        MorpheArchive.cachedIndex = MorpheArchiveIndex(
            apps = listOf(youtube, ytMusic, twitter)
        )
    }

    @After
    fun tearDown() {
        MorpheArchive.cachedIndex = null
    }

    @Test
    fun `findExactMatch matches by package name exactly case insensitive`() {
        val match = MorpheArchive.findExactMatch("COM.GOOGLE.ANDROID.YOUTUBE")
        assertNotNull(match)
        assertEquals("YouTube", match?.name)
    }

    @Test
    fun `findExactMatch matches by app name exactly case insensitive`() {
        val match = MorpheArchive.findExactMatch("youtube")
        assertNotNull(match)
        assertEquals("com.google.android.youtube", match?.packageName)
    }

    @Test
    fun `findBestMatch returns null when multiple apps match query`() {
        val match = MorpheArchive.findBestMatch("you")
        assertNull(match)
    }

    @Test
    fun `findBestMatch returns the single matching app when unambiguous`() {
        val match = MorpheArchive.findBestMatch("twitter")
        assertNotNull(match)
        assertEquals("com.twitter.android", match?.packageName)
    }

    @Test
    fun `findBestMatch returns exact match even if other apps share prefix`() {
        val match = MorpheArchive.findBestMatch("YouTube")
        assertNotNull(match)
        assertEquals("com.google.android.youtube", match?.packageName)
    }

    @Test
    fun `findBestMatch returns null for unknown package query with dot`() {
        val match = MorpheArchive.findBestMatch("org.videolan.vlc")
        assertNull(match)
    }
}
