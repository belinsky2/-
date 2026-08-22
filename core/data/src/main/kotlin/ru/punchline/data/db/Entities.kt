package ru.punchline.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.punchline.model.Attitude
import ru.punchline.model.BitStatus
import ru.punchline.model.GigType
import ru.punchline.model.LaughResult
import ru.punchline.model.PunchTechnique
import ru.punchline.model.SetListRole

/**
 * Поля, без которых слияние двух устройств невозможно. Встраиваются в каждую
 * таблицу — включая те, что сегодня кажутся неважными: добавить их потом
 * означает миграцию живой базы с уже разъехавшимися данными.
 */
data class SyncColumns(
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "lamport") val lamport: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "topics", indices = [Index("is_core"), Index("deleted_at")])
data class TopicEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "passion_score") val passionScore: Int,
    @ColumnInfo(name = "is_core") val isCore: Boolean,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "bits",
    indices = [Index("topic_id"), Index("status"), Index("deleted_at"), Index("callback_to")],
)
data class BitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "topic_id") val topicId: String?,
    val title: String,
    val status: BitStatus,
    val attitude: Attitude?,
    val premise: String?,
    val setup: String?,
    @ColumnInfo(name = "punch_text") val punchText: String?,
    @ColumnInfo(name = "punch_technique") val punchTechnique: PunchTechnique?,
    @ColumnInfo(name = "act_out_text") val actOutText: String?,
    @ColumnInfo(name = "act_out_space_work") val actOutSpaceWork: Boolean,
    @ColumnInfo(name = "act_out_audio_hash") val actOutAudioHash: String?,
    val tags: List<String>,
    @ColumnInfo(name = "callback_to") val callbackTo: String?,
    @ColumnInfo(name = "duration_sec") val durationSec: Int?,
    @Embedded val sync: SyncColumns,
)

/**
 * Снимок шутки. Кроме ручной истории сюда же ляжет версия, проигравшая
 * слияние: терять текст шутки нельзя, показать «была и такая» — нормально.
 */
@Entity(tableName = "bit_versions", indices = [Index("bit_id")])
data class BitVersionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bit_id") val bitId: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "set_lists", indices = [Index("deleted_at")])
data class SetListEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "target_duration_sec") val targetDurationSec: Int,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "set_list_items", indices = [Index("set_list_id"), Index("bit_id")])
data class SetListItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "set_list_id") val setListId: String,
    @ColumnInfo(name = "bit_id") val bitId: String,
    @ColumnInfo(name = "item_order") val order: Int,
    val role: SetListRole,
    @ColumnInfo(name = "planned_duration_sec") val plannedDurationSec: Int?,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "gigs", indices = [Index("date_millis"), Index("deleted_at")])
data class GigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "set_list_id") val setListId: String?,
    val type: GigType,
    val venue: String,
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    @ColumnInfo(name = "actual_duration_sec") val actualDurationSec: Int?,
    @ColumnInfo(name = "audio_hash") val audioHash: String?,
    val notes: String?,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "bit_performances", indices = [Index("gig_id"), Index("bit_id")])
data class BitPerformanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "gig_id") val gigId: String,
    @ColumnInfo(name = "bit_id") val bitId: String,
    val result: LaughResult,
    val note: String?,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "rants", indices = [Index("topic_id")])
data class RantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "topic_id") val topicId: String?,
    @ColumnInfo(name = "audio_hash") val audioHash: String,
    val transcript: String?,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "morning_writings", indices = [Index("date_millis")])
data class MorningWritingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "prompt_id") val promptId: Int?,
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    val text: String,
    @ColumnInfo(name = "audio_hash") val audioHash: String?,
    @Embedded val sync: SyncColumns,
)

/** День цепочки Сайнфелда. Ключ — календарная дата, чтобы день не засчитался дважды. */
@Entity(tableName = "streak_days")
data class StreakDayEntity(
    @PrimaryKey @ColumnInfo(name = "date_key") val dateKey: String,
    @ColumnInfo(name = "did_write") val didWrite: Boolean,
    @ColumnInfo(name = "did_perform") val didPerform: Boolean,
    @ColumnInfo(name = "minutes_written") val minutesWritten: Int,
    @Embedded val sync: SyncColumns,
)

/**
 * Аудиофайл, адресуемый по содержимому. Одна и та же запись, попавшая в базу
 * дважды, даёт одну строку и один файл; при слиянии двух устройств совпадающее
 * имя означает совпадающее содержимое, то есть конфликта нет по построению.
 */
@Entity(tableName = "audio_blobs")
data class AudioBlobEntity(
    @PrimaryKey val hash: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_sec") val durationSec: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Профиль. Живёт в базе, а не в DataStore: всё, что создал пользователь,
 * обязано попасть в экспорт и пережить смену телефона.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "comedy_vision") val comedyVision: String?,
    @ColumnInfo(name = "goal_minutes") val goalMinutes: Int,
    @ColumnInfo(name = "morning_writing_minute_of_day") val morningWritingMinuteOfDay: Int?,
    @Embedded val sync: SyncColumns,
)

/** Точка на графике шкалы смешного (упражнение 1). */
@Entity(tableName = "funny_scale_points")
data class FunnyScalePointEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    val score: Int,
    val note: String?,
    @Embedded val sync: SyncColumns,
)

/**
 * Упражнение из тетради. Текстов Картер здесь нет — только номер, рабочий
 * ярлык и страница книги. Флаг [userEdited] защищает правки пользователя
 * от затирания при обновлении приложения.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val number: Int,
    val part: String,
    @ColumnInfo(name = "short_title") val shortTitle: String,
    @ColumnInfo(name = "book_page") val bookPage: Int?,
    @ColumnInfo(name = "input_schema") val inputSchema: String,
    @ColumnInfo(name = "user_edited") val userEdited: Boolean = false,
)

@Entity(tableName = "exercise_entries", indices = [Index("exercise_number")])
data class ExerciseEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exercise_number") val exerciseNumber: Int,
    @ColumnInfo(name = "answers_json") val answersJson: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @Embedded val sync: SyncColumns,
)
