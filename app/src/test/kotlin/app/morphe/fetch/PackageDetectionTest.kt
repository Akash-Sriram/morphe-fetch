package app.morphe.fetch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageDetectionTest {

    @Test
    fun `exact package matches`() {
        val installed = setOf("com.google.android.youtube", "org.mozilla.firefox")
        assertTrue(isPackageOrPatchedInstalled("com.google.android.youtube", installed))
        assertTrue(isPackageOrPatchedInstalled("org.mozilla.firefox", installed))
        assertFalse(isPackageOrPatchedInstalled("com.twitter.android", installed))
    }

    @Test
    fun `patched package variations match correctly`() {
        val installed = setOf(
            "app.revanced.android.youtube",
            "app.morphe.android.twitter",
            "app.rvx.android.apps.photos",
            "app.morphe.reddit.frontpage"
        )
        assertTrue(isPackageOrPatchedInstalled("com.google.android.youtube", installed))
        assertTrue(isPackageOrPatchedInstalled("com.twitter.android", installed))
        assertTrue(isPackageOrPatchedInstalled("com.google.android.apps.photos", installed))
        assertTrue(isPackageOrPatchedInstalled("com.reddit.frontpage", installed))
    }

    @Test
    fun `no false positives on suffix matches such as manager`() {
        val installed = setOf("app.morphe.manager", "com.android.settings")

        assertFalse(isPackageOrPatchedInstalled("com.rs.explorer.filemanager", installed))
        assertFalse(isPackageOrPatchedInstalled("files.fileexplorer.filemanager", installed))
        assertFalse(isPackageOrPatchedInstalled("idm.internet.download.manager", installed))
        assertFalse(isPackageOrPatchedInstalled("eu.duong.picturemanager", installed))
        assertFalse(isPackageOrPatchedInstalled("com.rk.taskmanager", installed))
        assertFalse(isPackageOrPatchedInstalled("com.alphainventor.filemanager", installed))
    }

    @Test
    fun `blank or empty package returns false`() {
        val installed = setOf("app.morphe.manager", "com.google.android.youtube")
        assertFalse(isPackageOrPatchedInstalled("", installed))
        assertFalse(isPackageOrPatchedInstalled("   ", installed))
    }
}
