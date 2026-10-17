package app.morphe.fetch.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkArchiveValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createZip(
        name: String,
        includeManifest: Boolean = true,
        includeDex: Boolean = true
    ): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            if (includeManifest) {
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write("<manifest/>".toByteArray())
                zip.closeEntry()
            }
            if (includeDex) {
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write("dex-bytecode".toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun validateApkArchive_validApk_returnsNull() {
        val apk = createZip("valid.apk", includeManifest = true, includeDex = true)
        val result = validateApkArchive(apk)
        assertNull(result)
    }

    @Test
    fun validateApkArchive_matchingSize_returnsNull() {
        val apk = createZip("valid_size.apk", includeManifest = true, includeDex = true)
        val result = validateApkArchive(apk, expectedSize = apk.length())
        assertNull(result)
    }

    @Test
    fun validateApkArchive_emptyFile_returnsError() {
        val empty = tempFolder.newFile("empty.apk")
        val result = validateApkArchive(empty)
        assertNotNull(result)
        assertTrue(result!!.contains("empty"))
    }

    @Test
    fun validateApkArchive_truncatedFile_returnsError() {
        val apk = createZip("source.apk", includeManifest = true, includeDex = true)
        val truncated = tempFolder.newFile("truncated.apk")
        // Copy only the first 20 bytes (keeps local header, loses central directory)
        apk.inputStream().use { input ->
            truncated.outputStream().use { output ->
                val buffer = ByteArray(20)
                val read = input.read(buffer)
                output.write(buffer, 0, read)
            }
        }

        val result = validateApkArchive(truncated)
        assertNotNull(result)
        assertTrue(result!!.contains("corrupted") || result.contains("incomplete"))
    }

    @Test
    fun validateApkArchive_missingManifest_returnsError() {
        val apk = createZip("no_manifest.apk", includeManifest = false, includeDex = true)
        val result = validateApkArchive(apk)
        assertNotNull(result)
        assertTrue(result!!.contains("AndroidManifest.xml"))
    }

    @Test
    fun validateApkArchive_missingDex_returnsError() {
        val apk = createZip("no_dex.apk", includeManifest = true, includeDex = false)
        val result = validateApkArchive(apk)
        assertNotNull(result)
        assertTrue(result!!.contains("DEX"))
    }

    @Test
    fun validateApkArchive_sizeMismatch_returnsError() {
        val apk = createZip("size_mismatch.apk", includeManifest = true, includeDex = true)
        val result = validateApkArchive(apk, expectedSize = apk.length() + 500L)
        assertNotNull(result)
        assertTrue(result!!.contains("size mismatch"))
    }
}
