package ru.punchline.vault

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

/**
 * Перенос всего хранилища одним файлом.
 *
 * Архив, а не папка: выгрузка сотен блобов по одному через SAF занимает
 * секунды на файл и превращает бэкап в то, что никто не делает.
 *
 * Смена телефона — заявленное требование, поэтому этот код появляется сразу
 * после хранилища, а не в конце: потерять год материала из-за отложенного
 * бэкапа — худший из возможных исходов проекта.
 */
class VaultExporter(
    private val databaseFile: File,
    private val blobs: BlobStore,
    private val json: Json = Json { prettyPrint = true },
) {

    fun export(
        out: OutputStream,
        manifest: VaultManifest,
        referencedHashes: Set<String>,
    ) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(VaultFormat.MANIFEST_ENTRY))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()

            // База выгружается как есть: перед вызовом её нужно свести
            // в один файл, иначе журнал WAL останется снаружи архива.
            zip.putNextEntry(ZipEntry(VaultFormat.DATABASE_ENTRY))
            databaseFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            for (hash in referencedHashes) {
                val file = blobs.fileFor(hash)
                if (!file.exists()) continue
                zip.putNextEntry(ZipEntry(VaultFormat.BLOB_PREFIX + hash))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

/**
 * Чтение архива. Импорт разделён на два шага: сначала осмотр без единой
 * записи на диск, потом применение. Пользователь должен видеть, что именно
 * он собирается развернуть поверх текущих данных.
 */
class VaultImporter(
    private val blobs: BlobStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun inspect(open: () -> InputStream): ImportPreview {
        var manifest: VaultManifest? = null
        var hasDatabase = false
        var blobCount = 0

        readEntries(open()) { entry, stream ->
            when {
                entry.name == VaultFormat.MANIFEST_ENTRY ->
                    manifest = json.decodeFromString(stream.readBytes().decodeToString())
                entry.name == VaultFormat.DATABASE_ENTRY -> hasDatabase = true
                entry.name.startsWith(VaultFormat.BLOB_PREFIX) -> blobCount++
            }
        }

        val found = manifest
            ?: return ImportPreview(EMPTY_MANIFEST, blobCount, false, ImportProblem.ManifestMissing)
        if (found.formatVersion > VaultFormat.VERSION) {
            return ImportPreview(found, blobCount, false, ImportProblem.TooNew(found.formatVersion))
        }
        if (!hasDatabase) {
            return ImportPreview(found, blobCount, false, ImportProblem.DatabaseMissing)
        }
        return ImportPreview(found, blobCount, true, null)
    }

    /**
     * Разворачивает архив. База пишется во временный файл: подменять рабочую
     * базу на полпути нельзя — оборванный импорт оставит пользователя без данных.
     * Блобы проверяются по хешу, потому что имя файла и есть его контрольная сумма.
     */
    fun import(open: () -> InputStream, databaseTarget: File): ImportOutcome {
        val staged = File(databaseTarget.parentFile, databaseTarget.name + ".incoming")
        var restoredBlobs = 0
        var corrupt: String? = null

        try {
            readEntries(open()) { entry, stream ->
                when {
                    entry.name == VaultFormat.DATABASE_ENTRY ->
                        staged.outputStream().use { stream.copyTo(it) }

                    entry.name.startsWith(VaultFormat.BLOB_PREFIX) -> {
                        val declared = entry.name.removePrefix(VaultFormat.BLOB_PREFIX)
                        val stored = blobs.put(stream)
                        if (stored.hash != declared) {
                            corrupt = declared
                        } else {
                            restoredBlobs++
                        }
                    }
                }
            }

            val corruptHash = corrupt
            if (corruptHash != null) {
                staged.delete()
                return ImportOutcome.Failed(ImportProblem.CorruptBlob(corruptHash))
            }
            if (!staged.exists()) {
                return ImportOutcome.Failed(ImportProblem.DatabaseMissing)
            }

            // Подмена базы — последнее действие и единственное необратимое.
            if (!staged.renameTo(databaseTarget)) {
                staged.copyTo(databaseTarget, overwrite = true)
                staged.delete()
            }
            return ImportOutcome.Restored(restoredBlobs)
        } catch (t: Throwable) {
            staged.delete()
            throw t
        }
    }

    private fun readEntries(input: InputStream, handle: (ZipEntry, InputStream) -> Unit) {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    // Защита от путей вида ../: архив может прийти откуда угодно.
                    if (entry.name.contains("..")) {
                        zip.closeEntry()
                        continue
                    }
                    handle(entry, NonClosingStream(zip))
                }
                zip.closeEntry()
            }
        }
    }

    private companion object {
        val EMPTY_MANIFEST = VaultManifest(0, 0, "", "", 0, 0, 0, 0)
    }
}

sealed interface ImportOutcome {
    data class Restored(val blobCount: Int) : ImportOutcome
    data class Failed(val problem: ImportProblem) : ImportOutcome
}

/** ZipInputStream нельзя закрывать на каждой записи — иначе оборвётся весь поток. */
private class NonClosingStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun available(): Int = delegate.available()
    override fun close() = Unit
}
