package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.punchline.data.db.StreakDao
import ru.punchline.data.db.StreakDayEntity
import ru.punchline.model.Clock

/**
 * Цепочка Сайнфелда: главная метрика приложения — не количество шуток,
 * а непрерывность. День считается закрытым, если в нём было письмо
 * или выход на сцену.
 */
class StreakRepository(
    private val dao: StreakDao,
    private val sink: MutationSink,
    private val clock: Clock,
    private val dateKeys: DateKeys,
) {

    fun observeCurrentLength(): Flow<Int> = dao.observeAll().map { days ->
        val closed = days.filter { it.didWrite || it.didPerform }.map { it.dateKey }.toSet()
        var length = 0
        var cursor = dateKeys.today(clock.nowMillis())
        // Сегодня ещё не закрытый день не рвёт цепочку — она просто не выросла.
        if (cursor !in closed) cursor = dateKeys.previous(cursor)
        while (cursor in closed) {
            length++
            cursor = dateKeys.previous(cursor)
        }
        length
    }

    fun observeDays(): Flow<List<StreakDayEntity>> = dao.observeAll()

    suspend fun recordWriting(minutes: Int) {
        val key = dateKeys.today(clock.nowMillis())
        val existing = dao.byDate(key)
        dao.upsert(
            StreakDayEntity(
                dateKey = key,
                didWrite = true,
                didPerform = existing?.didPerform ?: false,
                minutesWritten = (existing?.minutesWritten ?: 0) + minutes,
                sync = sink.stamp(),
            )
        )
    }

    suspend fun recordPerformance() {
        val key = dateKeys.today(clock.nowMillis())
        val existing = dao.byDate(key)
        dao.upsert(
            StreakDayEntity(
                dateKey = key,
                didWrite = existing?.didWrite ?: false,
                didPerform = true,
                minutesWritten = existing?.minutesWritten ?: 0,
                sync = sink.stamp(),
            )
        )
    }
}

/**
 * Календарные ключи вынесены за интерфейс: «день» зависит от часового пояса,
 * а доменные правила не должны об этом знать.
 */
interface DateKeys {
    fun today(nowMillis: Long): String
    fun previous(key: String): String
}
