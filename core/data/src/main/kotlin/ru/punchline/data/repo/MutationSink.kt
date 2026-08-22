package ru.punchline.data.repo

import java.util.concurrent.atomic.AtomicLong
import ru.punchline.data.db.SyncColumns
import ru.punchline.model.Clock
import ru.punchline.model.DeviceId

/**
 * Единственная точка, через которую проходит любая запись в базу.
 *
 * Сейчас она делает немного: проставляет время, логические часы и устройство.
 * Смысл в другом — когда появится синхронизация с Mac, журнал операций
 * добавляется здесь одним файлом, а не правкой полусотни мест, куда иначе
 * разбрелись бы вызовы DAO.
 */
class MutationSink(
    private val clock: Clock,
    private val deviceId: DeviceId,
    initialLamport: Long = 0,
) {
    private val lamport = AtomicLong(initialLamport)

    /** Метаданные для новой или изменённой записи. */
    fun stamp(): SyncColumns = SyncColumns(
        updatedAt = clock.nowMillis(),
        lamport = lamport.incrementAndGet(),
        deviceId = deviceId.value,
    )

    /** Надгробие вместо физического удаления: иначе запись воскреснет при слиянии. */
    fun tombstone(): SyncColumns {
        val now = clock.nowMillis()
        return SyncColumns(
            updatedAt = now,
            lamport = lamport.incrementAndGet(),
            deviceId = deviceId.value,
            deletedAt = now,
        )
    }

    /**
     * Подтягивает счётчик выше чужого значения. Вызывается при импорте и,
     * позже, при слиянии: логические часы обязаны обгонять всё увиденное,
     * иначе свежая локальная правка проиграет чужой уже устаревшей.
     */
    fun observe(foreignLamport: Long) {
        while (true) {
            val current = lamport.get()
            if (current > foreignLamport) return
            if (lamport.compareAndSet(current, foreignLamport + 1)) return
        }
    }

    fun current(): Long = lamport.get()
}
