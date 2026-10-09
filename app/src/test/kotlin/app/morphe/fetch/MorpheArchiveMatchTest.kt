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
    private val facebook = ArchiveApp(
        packageName = "com.facebook.katana",
        name = "Facebook",
        patches = listOf("De-Vanced Settings", "Remove ads"),
        sources = listOf(
            ArchiveSource(repo = "RookieEnough/De-Vanced")
        )
    )

    @Before
    fun setUp() {
        MorpheArchive.cachedIndex = MorpheArchiveIndex(
            apps = listOf(youtube, ytMusic, twitter, facebook)
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

    @Test
    fun `searchCatalog matches apps by source repository name like de-vance`() {
        val results = MorpheArchive.searchCatalog("de-vance")
        assertEquals(1, results.size)
        assertEquals("Facebook", results.first().name)
    }

    @Test
    fun `searchCatalog matches apps by patch name like sponsorblock`() {
        val results = MorpheArchive.searchCatalog("sponsorblock")
        assertEquals(1, results.size)
        assertEquals("YouTube", results.first().name)
    }

    @Test
    fun `matchingReason identifies source repo match when app name does not match`() {
        val reason = facebook.matchingReason("de-vance")
        assertEquals(MatchReason.SourceRepo("RookieEnough/De-Vanced"), reason)
    }

    @Test
    fun `matchingReason identifies patch match when app name does not match`() {
        val reason = youtube.matchingReason("sponsorblock")
        assertEquals(MatchReason.Patch("sponsorblock"), reason)
    }

    @Test
    fun `matchingReason returns null when app name matches directly`() {
        val reason = youtube.matchingReason("youtube")
        assertNull(reason)
    }

    @Test
    fun `searchSources matches repo by name and repo slug`() {
        val devancedSource = ArchiveSource(
            name = "De-Vanced",
            repo = "RookieEnough/De-Vanced",
            patchCount = 75,
            apps = listOf(ArchiveSourceApp(name = "Facebook", packageName = "com.facebook.katana"))
        )
        MorpheArchive.cachedIndex = MorpheArchiveIndex(
            apps = listOf(facebook),
            repos = listOf(devancedSource)
        )

        val results = MorpheArchive.searchSources("de-vance")
        assertEquals(1, results.size)
        assertEquals("De-Vanced", results.first().name)
        assertEquals("RookieEnough/De-Vanced", results.first().repo)

        val resultsByAuthor = MorpheArchive.searchSources("rookie")
        assertEquals(1, resultsByAuthor.size)
        assertEquals("De-Vanced", resultsByAuthor.first().name)
    }
}
