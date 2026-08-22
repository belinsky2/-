package ru.punchline.model

import kotlin.random.Random

/**
 * Часы как зависимость, а не как глобальное состояние: тесты жизненного цикла
 * шутки должны уметь «прожить» девяносто дней за миллисекунду.
 */
fun interface Clock {
    fun nowMillis(): Long
}

/**
 * Идентификатор записи. UUIDv7: первые 48 бит — метка времени, поэтому значения
 * упорядочены по возрастанию и не разрушают локальность индекса, в отличие от v4.
 * Уникальность обеспечивается устройством, а не базой, — иначе два устройства
 * создадут запись с одним номером и при слиянии одна затрёт другую.
 */
@JvmInline
value class Id(val value: String) {
    override fun toString(): String = value

    companion object {
        private const val HEX = "0123456789abcdef"

        fun generate(clock: Clock, random: Random = Random.Default): Id {
            val ms = clock.nowMillis()
            val bytes = ByteArray(16)
            // 48 бит времени, big-endian
            for (i in 0 until 6) {
                bytes[i] = ((ms shr (8 * (5 - i))) and 0xFF).toByte()
            }
            random.nextBytes(bytes, 6, 16)
            // версия 7 в старших четырёх битах седьмого байта
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
            // вариант RFC 4122 в старших двух битах девятого байта
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
            return Id(bytes.toUuidString())
        }

        fun of(raw: String): Id = Id(raw)

        private fun ByteArray.toUuidString(): String {
            val sb = StringBuilder(36)
            for (i in indices) {
                if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
                val v = this[i].toInt() and 0xFF
                sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
            }
            return sb.toString()
        }
    }
}

/** Идентификатор устройства. Создаётся один раз при первом запуске и не меняется. */
@JvmInline
value class DeviceId(val value: String)

/**
 * Метаданные, без которых слияние двух устройств невозможно.
 *
 * [lamport] — логические часы: настенное время на телефоне и на Mac разъезжается,
 * и при одинаковом [updatedAt] нужен детерминированный победитель.
 * [deletedAt] — надгробие: физическое удаление не переживает слияния, потому что
 * на втором устройстве запись просто выглядела бы новой.
 */
data class SyncMeta(
    val updatedAt: Long,
    val lamport: Long,
    val deviceId: DeviceId,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /**
     * Кто побеждает при расхождении. Сравнение по логическим часам, затем по
     * настенному времени, затем по идентификатору устройства — последнее нужно лишь
     * чтобы результат не зависел от порядка обхода.
     */
    fun wins(other: SyncMeta): Boolean = when {
        lamport != other.lamport -> lamport > other.lamport
        updatedAt != other.updatedAt -> updatedAt > other.updatedAt
        else -> deviceId.value > other.deviceId.value
    }
}
