package ru.punchline.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.punchline.data.db.ExerciseDao
import ru.punchline.data.db.ExerciseEntity

/** Одна запись указателя по книге: номер, ярлык, страница, тип формы. */
@Serializable
data class ExerciseSeed(
    val number: Int,
    val part: String,
    val shortTitle: String,
    val bookPage: Int? = null,
    val inputSchema: String,
)

@Serializable
data class ExerciseSeedFile(
    val schemaVersion: Int,
    val exercises: List<ExerciseSeed>,
    val note: String? = null,
)

/**
 * Засев справочника упражнений при первом запуске и при обновлении приложения.
 *
 * Ключевое правило: запись, которую пользователь правил руками, не трогается.
 * Семь названий восстановить по открытым источникам не удалось, и вписывать их
 * будет он сам — обновление приложения не должно стирать эту работу.
 */
class ExerciseCatalog(
    private val dao: ExerciseDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(raw: String): List<ExerciseSeed> =
        json.decodeFromString<ExerciseSeedFile>(raw).exercises

    suspend fun seed(raw: String) {
        val seeds = parse(raw)
        dao.insertMissing(
            seeds.map {
                ExerciseEntity(
                    number = it.number,
                    part = it.part,
                    shortTitle = it.shortTitle,
                    bookPage = it.bookPage,
                    inputSchema = it.inputSchema,
                    userEdited = false,
                )
            }
        )
        // Существующие записи обновляются, только если пользователь их не менял.
        seeds.forEach { dao.refreshUnedited(it.number, it.shortTitle, it.bookPage, it.inputSchema) }
    }

    /** Правка ярлыка пользователем: с этого момента засев эту запись не трогает. */
    suspend fun rename(number: Int, title: String, current: ExerciseEntity) {
        dao.update(current.copy(number = number, shortTitle = title, userEdited = true))
    }
}
