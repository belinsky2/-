package ru.punchline.vault

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Хранилище аудио с адресацией по содержимому.
 *
 * Имя файла — SHA-256 его байтов. Отсюда бесплатно следует три вещи:
 * повторная запись того же файла не создаёт дубликат, целостность бэкапа
 * проверяется пересчётом хеша, а слияние двух устройств не может дать
 * конфликт имён — совпало имя, значит совпало содержимое.
 */
class BlobStore(private val root: File) {

    init { root.mkdirs() }

    /** Кладёт поток в хранилище и возвращает его хеш. */
    fun put(source: InputStream): StoredBlob {
        val temp = File.createTempFile("blob", ".part", root)
        val digest = MessageDigest.getInstance(ALGORITHM)
        var size = 0L
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    size += read
                }
            }
            val hash = digest.digest().toHex()
            val target = fileFor(hash)
            if (target.exists()) {
                // Такой файл уже есть — второй копии не нужно.
                temp.delete()
            } else {
                target.parentFile?.mkdirs()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            return StoredBlob(hash, size)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun put(file: File): StoredBlob = file.inputStream().use(::put)

    fun fileFor(hash: String): File = File(File(root, hash.take(SHARD_LENGTH)), hash)

    fun exists(hash: String): Boolean = fileFor(hash).exists()

    /** Проверка целостности: содержимое файла обязано соответствовать его имени. */
    fun verify(hash: String): Boolean {
        val file = fileFor(hash)
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance(ALGORITHM)
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex() == hash
    }

    fun allHashes(): List<String> =
        root.listFiles()
            ?.filter { it.isDirectory && it.name.length == SHARD_LENGTH }
            ?.flatMap { shard -> shard.listFiles()?.map { it.name }.orEmpty() }
            .orEmpty()

    /** Удаляет файлы, на которые не ссылается ни одна запись. Только по явной команде. */
    fun deleteUnreferenced(referenced: Set<String>): Int =
        allHashes().filterNot { it in referenced }.count { fileFor(it).delete() }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val BUFFER_BYTES = 64 * 1024

        /** Первые два символа хеша — подкаталог: тысячи файлов в одной папке тормозят. */
        const val SHARD_LENGTH = 2

        fun ByteArray.toHex(): String {
            val hex = "0123456789abcdef"
            val out = StringBuilder(size * 2)
            for (b in this) {
                val v = b.toInt() and 0xFF
                out.append(hex[v ushr 4]).append(hex[v and 0x0F])
            }
            return out.toString()
        }
    }
}

data class StoredBlob(val hash: String, val sizeBytes: Long)
