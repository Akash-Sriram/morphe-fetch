package app.morphe.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveTypeDetectorTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun createZipWithEntries(fileName: String, vararg entries: String): File {
        val file = tempDir.newFile(fileName)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun detect_detectsApkWithRootManifest() {
        val file = createZipWithEntries("app.apk", "AndroidManifest.xml", "classes.dex")
        assertEquals("apk", ArchiveTypeDetector.detect(file))
    }

    @Test
    fun detect_detectsApksWithToc() {
        val file = createZipWithEntries("app.apks", "toc.pb", "splits/base-master.apk")
        assertEquals("apks", ArchiveTypeDetector.detect(file))
    }

    @Test
    fun detect_detectsApkmWithSaiMeta() {
        val file = createZipWithEntries("app.apkm", "meta.sai_v2.json", "base.apk")
        assertEquals("apkm", ArchiveTypeDetector.detect(file))
    }

    @Test
    fun detect_detectsXapkWithManifestAndApk() {
        val file = createZipWithEntries("app.xapk", "manifest.json", "com.example.apk")
        assertEquals("xapk", ArchiveTypeDetector.detect(file))
    }

    @Test
    fun normalizeExtension_renamesMislabeledXapkToXapk() {
        val file = createZipWithEntries("wrong_label.apk", "manifest.json", "com.example.apk")
        val normalized = ArchiveTypeDetector.normalizeExtension(file)
        assertEquals("wrong_label.xapk", normalized.name)
        assertTrue(normalized.exists())
    }

    @Test
    fun normalizeExtension_leavesCorrectApkUntouched() {
        val file = createZipWithEntries("correct.apk", "AndroidManifest.xml", "classes.dex")
        val normalized = ArchiveTypeDetector.normalizeExtension(file)
        assertEquals("correct.apk", normalized.name)
        assertTrue(normalized.exists())
    }

    @Test
    fun detect_returnsNullForNonZipOrUnknownExtension() {
        val txtFile = tempDir.newFile("sample.txt")
        txtFile.writeText("hello")
        assertNull(ArchiveTypeDetector.detect(txtFile))
    }
}
