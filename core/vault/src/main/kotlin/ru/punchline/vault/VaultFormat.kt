package ru.punchline.vault

import kotlinx.serialization.Serializable

/**
 * Манифест архива. Он же — контракт обмена с будущим приложением на Mac,
 * поэтому у него собственная версия, независимая от версии схемы базы.
 */
@Serializable
data class VaultManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val deviceId: String,
    val exportedAt: Long,
    val lamport: Long,
    val blobCount: Int,
    val blobBytes: Long,
)

object VaultFormat {
    /** Версия формата архива. Меняется, только когда старый архив перестаёт читаться. */
    const val VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_ENTRY = "vault.sqlite"
    const val BLOB_PREFIX = "blobs/"
}

/** Что именно приехало при импорте. UI показывает это до того, как что-то менять. */
data class ImportPreview(
    val manifest: VaultManifest,
    val blobsInArchive: Int,
    val readable: Boolean,
    val problem: ImportProblem?,
)

sealed interface ImportProblem {
    /** Архив собран более новой версией приложения — читать его вслепую опасно. */
    data class TooNew(val formatVersion: Int) : ImportProblem
    data object ManifestMissing : ImportProblem
    data object DatabaseMissing : ImportProblem
    data class CorruptBlob(val hash: String) : ImportProblem
}
