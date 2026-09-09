package ru.punchline.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Смена телефона — заявленное требование, и проверяется оно ровно одним
 * сценарием: выгрузить всё, стереть, развернуть обратно, убедиться,
 * что материал на месте и байт в байт тот же.
 */
class VaultRoundTripTest {

    @get:Rule val temp = TemporaryFolder()

    private fun manifest(blobCount: Int, bytes: Long, formatVersion: Int = VaultFormat.VERSION) =
        VaultManifest(
            formatVersion = formatVersion,
            schemaVersion = 1,
            appVersion = "0.1.0",
            deviceId = "phone",
            exportedAt = 1_700_000_000_000,
            lamport = 42,
            blobCount = blobCount,
            blobBytes = bytes,
        )

    @Test
    fun `everything survives export, wipe and import`() {
        val oldPhone = temp.newFolder("old")
        val oldBlobs = BlobStore(File(oldPhone, "blobs"))
        val oldDb = File(oldPhone, "vault.sqlite").apply { writeText("СОДЕРЖИМОЕ БАЗЫ") }

        val rant = oldBlobs.put("длинный рант про метро".byteInputStream())
        val actOut = oldBlobs.put("act-out голосом тёщи".byteInputStream())

        val archive = ByteArrayOutputStream()
        VaultExporter(oldDb, oldBlobs).export(archive, manifest(2, 0), setOf(rant.hash, actOut.hash))
        val bytes = archive.toByteArray()

        // Новый телефон: пустое хранилище, ничего общего со старым.
        val newPhone = temp.newFolder("new")
        val newBlobs = BlobStore(File(newPhone, "blobs"))
        val newDb = File(newPhone, "vault.sqlite")

        val preview = VaultImporter(newBlobs).inspect { ByteArrayInputStream(bytes) }
        assertTrue(preview.readable)
        assertEquals(2, preview.blobsInArchive)

        val outcome = VaultImporter(newBlobs).import({ ByteArrayInputStream(bytes) }, newDb)
        assertEquals(ImportOutcome.Restored(2), outcome)
        assertEquals("СОДЕРЖИМОЕ БАЗЫ", newDb.readText())
        assertEquals("длинный рант про метро", newBlobs.fileFor(rant.hash).readText())
        assertTrue(newBlobs.verify(actOut.hash))
    }

    @Test
    fun `blobs nothing points at stay behind`() {
        val root = temp.newFolder("phone")
        val blobs = BlobStore(File(root, "blobs"))
        val db = File(root, "vault.sqlite").apply { writeText("база") }

        val referenced = blobs.put("нужное".byteInputStream())
        val orphan = blobs.put("мусор от удалённой шутки".byteInputStream())

        val archive = ByteArrayOutputStream()
        VaultExporter(db, blobs).export(archive, manifest(1, 0), setOf(referenced.hash))

        val target = temp.newFolder("restored")
        val restored = BlobStore(File(target, "blobs"))
        VaultImporter(restored).import(
            { ByteArrayInputStream(archive.toByteArray()) },
            File(target, "vault.sqlite"),
        )
        assertTrue(restored.exists(referenced.hash))
        assertFalse("архив не должен тащить осиротевшее аудио", restored.exists(orphan.hash))
    }

    @Test
    fun `an archive from a newer app is refused, not guessed at`() {
        val root = temp.newFolder("phone")
        val blobs = BlobStore(File(root, "blobs"))
        val db = File(root, "vault.sqlite").apply { writeText("база") }

        val archive = ByteArrayOutputStream()
        VaultExporter(db, blobs).export(
            archive,
            manifest(0, 0, formatVersion = VaultFormat.VERSION + 1),
            emptySet(),
        )

        val preview = VaultImporter(BlobStore(temp.newFolder("b2")))
            .inspect { ByteArrayInputStream(archive.toByteArray()) }

        assertFalse(preview.readable)
        assertTrue(preview.problem is ImportProblem.TooNew)
    }

    @Test
    fun `an archive without a manifest is refused`() {
        val bogus = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zip.write("не то".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val preview = VaultImporter(BlobStore(temp.newFolder("b3")))
            .inspect { ByteArrayInputStream(bogus) }

        assertFalse(preview.readable)
        assertEquals(ImportProblem.ManifestMissing, preview.problem)
    }

    @Test
    fun `a corrupted blob aborts the import and leaves the database untouched`() {
        val root = temp.newFolder("phone")
        val blobs = BlobStore(File(root, "blobs"))
        val db = File(root, "vault.sqlite").apply { writeText("новая база") }
        val good = blobs.put("настоящее аудио".byteInputStream())

        val archive = ByteArrayOutputStream()
        VaultExporter(db, blobs).export(archive, manifest(1, 0), setOf(good.hash))

        // Подменяем содержимое блоба, оставляя прежнее имя.
        val tampered = repackWithTamperedBlob(archive.toByteArray(), good.hash)

        val target = temp.newFolder("restored")
        val existingDb = File(target, "vault.sqlite").apply { writeText("СТАРАЯ БАЗА") }
        val outcome = VaultImporter(BlobStore(File(target, "blobs")))
            .import({ ByteArrayInputStream(tampered) }, existingDb)

        assertTrue(outcome is ImportOutcome.Failed)
        assertEquals(
            "битый архив не должен затирать то, что уже есть на устройстве",
            "СТАРАЯ БАЗА",
            existingDb.readText(),
        )
    }

    @Test
    fun `entries trying to escape the archive are ignored`() {
        val evil = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../../escaped.txt"))
                zip.write("нет".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val preview = VaultImporter(BlobStore(temp.newFolder("b4")))
            .inspect { ByteArrayInputStream(evil) }
        assertFalse(preview.readable)
    }

    private fun repackWithTamperedBlob(source: ByteArray, hash: String): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(source)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    zip.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    if (entry.name == VaultFormat.BLOB_PREFIX + hash) {
                        zip.write("подменённые байты".toByteArray())
                    } else {
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                    input.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
