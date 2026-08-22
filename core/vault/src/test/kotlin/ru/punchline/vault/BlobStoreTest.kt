package ru.punchline.vault

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlobStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private fun store() = BlobStore(temp.newFolder("blobs"))

    @Test
    fun `identical content is stored once`() {
        val store = store()
        val a = store.put("одна и та же запись".byteInputStream())
        val b = store.put("одна и та же запись".byteInputStream())

        assertEquals(a.hash, b.hash)
        assertEquals(
            "повторная запись не должна создавать второй файл",
            1,
            store.allHashes().size,
        )
    }

    @Test
    fun `different content gets different names`() {
        val store = store()
        assertNotEquals(
            store.put("рант про тёщу".byteInputStream()).hash,
            store.put("рант про метро".byteInputStream()).hash,
        )
    }

    @Test
    fun `size is reported for the whole stream`() {
        val store = store()
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(200_000L, store.put(payload.inputStream()).sizeBytes)
    }

    @Test
    fun `verify catches a tampered file`() {
        val store = store()
        val stored = store.put("оригинал".byteInputStream())
        assertTrue(store.verify(stored.hash))

        store.fileFor(stored.hash).writeText("подменили")
        assertFalse("имя файла — его контрольная сумма", store.verify(stored.hash))
    }

    @Test
    fun `verify of a missing blob is false, not a crash`() {
        assertFalse(store().verify("0".repeat(64)))
    }

    @Test
    fun `unreferenced blobs are removed only on demand`() {
        val store = store()
        val kept = store.put("нужное".byteInputStream())
        val orphan = store.put("забытое".byteInputStream())

        assertEquals(1, store.deleteUnreferenced(setOf(kept.hash)))
        assertTrue(store.exists(kept.hash))
        assertFalse(store.exists(orphan.hash))
    }

    @Test
    fun `files are sharded so one directory never holds thousands`() {
        val store = store()
        val stored = store.put("что угодно".byteInputStream())
        val file: File = store.fileFor(stored.hash)
        assertEquals(stored.hash.take(2), file.parentFile.name)
        assertEquals(stored.hash, file.name)
    }
}
