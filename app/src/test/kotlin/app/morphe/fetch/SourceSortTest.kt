package app.morphe.fetch

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSortTest {

    private fun dummyCandidate(
        source: DownloadSource,
        directDownload: Boolean = true,
        option: CandidateOption = CandidateOption.LATEST
    ) = DownloadCandidate(
        source = source,
        name = "App",
        packageName = "com.example.app",
        versionName = "1.0.0",
        versionCode = 100L,
        url = "https://example.com/download",
        fileKind = "apk",
        option = option,
        directDownload = directDownload,
        versionStatus = VersionStatus.LATEST,
        formatMatches = true
    )

    private fun createGroup(
        source: DownloadSource,
        latestState: ResolveState = ResolveState.Idle,
        recommendedState: ResolveState = ResolveState.Idle
    ) = SourceCandidateGroup(
        source = source,
        manual = emptyList(),
        recommended = recommendedState,
        latest = latestState
    )

    @Test
    fun sortedForDisplay_placesProviderWithDirectDownloadAtTopWhenOthersHaveNoApks() {
        val request = testRequest(packageName = "in.startv.hotstar")
        val groups = listOf(
            createGroup(DownloadSource.APK_PURE, latestState = ResolveState.Error("No listing found on APKPure")),
            createGroup(DownloadSource.APK_COMBO, latestState = ResolveState.Error("No listing found on APKCombo")),
            createGroup(DownloadSource.UPTODOWN, latestState = ResolveState.Error("No listing found on Uptodown")),
            createGroup(
                DownloadSource.APK_MIRROR,
                latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_MIRROR, directDownload = true)))
            )
        )

        val sorted = groups.sortedForDisplay(request)

        assertEquals(
            listOf(
                DownloadSource.APK_MIRROR,
                DownloadSource.APK_PURE,
                DownloadSource.APK_COMBO,
                DownloadSource.UPTODOWN
            ),
            sorted.map { it.source }
        )
    }

    @Test
    fun sortedForDisplay_preservesDefaultOrderWhenAllHaveDirectDownloads() {
        val request = testRequest(packageName = "com.example.app")
        val groups = listOf(
            createGroup(DownloadSource.APK_PURE, latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_PURE)))),
            createGroup(DownloadSource.APK_COMBO, latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_COMBO)))),
            createGroup(DownloadSource.UPTODOWN, latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.UPTODOWN)))),
            createGroup(DownloadSource.APK_MIRROR, latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_MIRROR))))
        )

        val sorted = groups.sortedForDisplay(request)

        assertEquals(
            listOf(
                DownloadSource.APK_PURE,
                DownloadSource.APK_COMBO,
                DownloadSource.UPTODOWN,
                DownloadSource.APK_MIRROR
            ),
            sorted.map { it.source }
        )
    }

    @Test
    fun sortedForDisplay_placesLoadingAboveError() {
        val request = testRequest(packageName = "com.example.app")
        val groups = listOf(
            createGroup(DownloadSource.APK_PURE, latestState = ResolveState.Error("No listing found")),
            createGroup(DownloadSource.APK_COMBO, latestState = ResolveState.Loading),
            createGroup(DownloadSource.UPTODOWN, latestState = ResolveState.Error("No listing found")),
            createGroup(DownloadSource.APK_MIRROR, latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_MIRROR))))
        )

        val sorted = groups.sortedForDisplay(request)

        assertEquals(
            listOf(
                DownloadSource.APK_MIRROR,
                DownloadSource.APK_COMBO,
                DownloadSource.APK_PURE,
                DownloadSource.UPTODOWN
            ),
            sorted.map { it.source }
        )
    }

    @Test
    fun sortedForDisplay_requestedVersionFallbackToLatestDirectDownload() {
        val request = testRequest(
            packageName = "com.example.app",
            versionName = "2.0.0"
        )
        val groups = listOf(
            createGroup(
                DownloadSource.APK_PURE,
                recommendedState = ResolveState.Error("Version not found"),
                latestState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_PURE, directDownload = true)))
            ),
            createGroup(
                DownloadSource.APK_COMBO,
                recommendedState = ResolveState.Error("No listing found"),
                latestState = ResolveState.Error("No listing found")
            ),
            createGroup(
                DownloadSource.APK_MIRROR,
                recommendedState = ResolveState.Done(listOf(dummyCandidate(DownloadSource.APK_MIRROR, directDownload = true)))
            )
        )

        val sorted = groups.sortedForDisplay(request)

        assertEquals(
            listOf(
                DownloadSource.APK_MIRROR,
                DownloadSource.APK_PURE,
                DownloadSource.APK_COMBO
            ),
            sorted.map { it.source }
        )
    }
}
