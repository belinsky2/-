package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import ru.punchline.data.db.ExerciseDao
import ru.punchline.data.db.ExerciseEntity
import ru.punchline.data.db.ExerciseEntryEntity
import ru.punchline.data.db.MorningWritingDao
import ru.punchline.data.db.MorningWritingEntity
import ru.punchline.model.Clock
import ru.punchline.model.Id

/** Упражнение вместе с тем, что по нему уже сделано. */
data class ExerciseCard(
    val number: Int,
    val part: String,
    val title: String,
    val bookPage: Int?,
    val inputSchema: String,
    val userEdited: Boolean,
    val answers: Map<String, String>,
    val completedAt: Long?,
) {
    val isDone: Boolean get() = completedAt != null

    /**
     * Семь упражнений восстановить по открытым источникам не удалось.
     * Такой ярлык пользователь впишет сам, глядя в книгу.
     */
    val needsTitle: Boolean get() = title.isBlank()
}

class PracticeRepository(
    private val exercises: ExerciseDao,
    private val morning: MorningWritingDao,
    private val sink: MutationSink,
    private val clock: Clock,
    private val json: Json = Json,
) {

    fun observeExercises(): Flow<List<ExerciseCard>> = combine(
        exercises.observeAll(),
        exercises.observeEntries(),
    ) { catalog, entries ->
        val byNumber = entries.associateBy { it.exerciseNumber }
        catalog.map { row -> row.toCard(byNumber[row.number]) }
    }

    suspend fun saveAnswers(number: Int, answers: Map<String, String>, done: Boolean) {
        val existing = exercises.entryFor(number)
        exercises.upsertEntry(
            ExerciseEntryEntity(
                id = existing?.id ?: Id.generate(clock).value,
                exerciseNumber = number,
                answersJson = json.encodeToString(answers),
                completedAt = when {
                    done -> existing?.completedAt ?: clock.nowMillis()
                    else -> null
                },
                sync = sink.stamp(),
            )
        )
    }

    /** Пользователь вписывает название упражнения — с этого момента засев его не трогает. */
    suspend fun renameExercise(row: ExerciseCard, title: String) {
        exercises.update(
            ExerciseEntity(
                number = row.number,
                part = row.part,
                shortTitle = title.trim(),
                bookPage = row.bookPage,
                inputSchema = row.inputSchema,
                userEdited = true,
            )
        )
    }

    fun observeMorningWritings(): Flow<List<MorningWritingEntity>> = morning.observeAlive()

    suspend fun saveMorningWriting(text: String, audioHash: String?, promptId: Int?): Id {
        val id = Id.generate(clock)
        morning.upsert(
            MorningWritingEntity(
                id = id.value,
                promptId = promptId,
                dateMillis = clock.nowMillis(),
                text = text,
                audioHash = audioHash,
                sync = sink.stamp(),
            )
        )
        return id
    }

    private fun ExerciseEntity.toCard(entry: ExerciseEntryEntity?) = ExerciseCard(
        number = number,
        part = part,
        title = shortTitle,
        bookPage = bookPage,
        inputSchema = inputSchema,
        userEdited = userEdited,
        answers = entry?.answersJson?.let(::decodeAnswers).orEmpty(),
        completedAt = entry?.completedAt,
    )

    /**
     * Ответы читаются терпимо к формату: запись, сделанная более старой версией
     * приложения, не должна ронять экран практики.
     */
    private fun decodeAnswers(raw: String): Map<String, String> = runCatching {
        json.decodeFromString<Map<String, JsonPrimitive>>(raw).mapValues { it.value.jsonPrimitive.content }
    }.getOrElse { emptyMap() }
}
