package ru.punchline.data.vault

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import ru.punchline.data.db.PunchlineDatabase
import ru.punchline.model.Clock
import ru.punchline.model.DeviceId
import ru.punchline.vault.BlobStore
import ru.punchline.vault.ImportOutcome
import ru.punchline.vault.ImportPreview
import ru.punchline.vault.ImportProblem
import ru.punchline.vault.VaultExporter
import ru.punchline.vault.VaultFormat
import ru.punchline.vault.VaultImporter
import ru.punchline.vault.VaultManifest

/**
 * Экспорт и импорт хранилища на стороне Android.
 *
 * Формат и упаковка живут в :core:vault и одинаковы на всех платформах;
 * здесь только специфичное для телефона: сведение журнала базы, сбор реально
 * используемых хешей и работа с потоками, которые приходят из системного
 * диалога выбора файла.
 */
class VaultService(
    private val context: Context,
    private val database: PunchlineDatabase,
    private val blobs: BlobStore,
    private val clock: Clock,
    private val deviceId: DeviceId,
    private val appVersion: String,
) {

    private val databaseFile: File
        get() = context.getDatabasePath(PunchlineDatabase.FILE_NAME)

    suspend fun export(out: OutputStream, lamport: Long) {
        // Без сведения журнала свежие записи остались бы в файле -wal рядом
        // с базой и в архив не попали бы: экспорт молча потерял бы последние
        // правки, а это хуже, чем отсутствие экспорта вообще.
        checkpoint()

        val referenced = database.audioReferences().referencedHashes().toSet()
        val bytes = referenced.sumOf { blobs.fileFor(it).length() }

        VaultExporter(databaseFile, blobs).export(
            out = out,
            manifest = VaultManifest(
                formatVersion = VaultFormat.VERSION,
                schemaVersion = PunchlineDatabase.VERSION,
                appVersion = appVersion,
                deviceId = deviceId.value,
                exportedAt = clock.nowMillis(),
                lamport = lamport,
                blobCount = referenced.size,
                blobBytes = bytes,
            ),
            referencedHashes = referenced,
        )
    }

    fun inspect(open: () -> InputStream): ImportPreview = VaultImporter(blobs).inspect(open)

    /**
     * Разворачивает архив поверх текущих данных.
     *
     * Архив сначала осматривается и только потом применяется: непригодный
     * файл не должен доходить до подмены базы. База закрывается перед заменой
     * файла — писать в неё во время подмены значит получить битое хранилище.
     *
     * После успеха приложение обязано перезапуститься: открытые DAO указывают
     * на файл, которого больше нет.
     */
    fun import(open: () -> InputStream): ImportOutcome {
        val preview = inspect(open)
        if (!preview.readable) {
            return ImportOutcome.Failed(preview.problem ?: ImportProblem.ManifestMissing)
        }
        database.close()
        return VaultImporter(blobs).import(open, databaseFile)
    }

    private fun checkpoint() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }
}
