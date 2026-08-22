package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.punchline.data.db.TopicDao
import ru.punchline.model.Clock
import ru.punchline.model.Id
import ru.punchline.model.Topic

class TopicRepository(
    private val topics: TopicDao,
    private val sink: MutationSink,
    private val clock: Clock,
) {
    /** Сколько тем одновременно могут быть главными — упражнение 9. */
    val coreLimit: Int get() = CORE_LIMIT

    fun observeAll(): Flow<List<Topic>> =
        topics.observeAlive().map { list -> list.map { it.toDomain() } }

    suspend fun titleOf(id: Id?): Topic? = id?.let { topics.byId(it.value)?.toDomain() }

    suspend fun add(title: String): Id {
        val id = Id.generate(clock)
        topics.upsert(
            Topic(id = id, title = title.trim(), meta = sink.stamp().toDomain()).toEntity()
        )
        return id
    }

    suspend fun setPassion(id: Id, score: Int) {
        val current = topics.byId(id.value) ?: return
        topics.upsert(current.copy(passionScore = score.coerceIn(0, 10), sync = sink.stamp()))
    }

    /**
     * Отметить тему главной. Ограничение в три темы — мягкое: Картер требует
     * сузиться до трёх, но запрещать автору четвёртую не дело приложения.
     * Вызывающий код получает признак превышения и решает, предупреждать ли.
     */
    suspend fun setCore(id: Id, isCore: Boolean): CoreResult {
        val current = topics.byId(id.value) ?: return CoreResult.NotFound
        topics.upsert(current.copy(isCore = isCore, sync = sink.stamp()))
        val count = topics.coreCount()
        return if (isCore && count > CORE_LIMIT) CoreResult.OverLimit(count) else CoreResult.Ok
    }

    suspend fun delete(id: Id) {
        val current = topics.byId(id.value) ?: return
        topics.upsert(current.copy(sync = sink.tombstone()))
    }

    private companion object { const val CORE_LIMIT = 3 }
}

sealed interface CoreResult {
    data object Ok : CoreResult
    data object NotFound : CoreResult
    data class OverLimit(val count: Int) : CoreResult
}
