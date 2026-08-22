package ru.punchline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.punchline.model.BitStatus

/**
 * Живые записи — те, у которых нет надгробия. Ни один экран не должен
 * запрашивать таблицу без этого условия, иначе удалённое всплывёт обратно.
 */
@Dao
interface TopicDao {
    @Upsert suspend fun upsert(topic: TopicEntity)

    @Query("SELECT * FROM topics WHERE deleted_at IS NULL ORDER BY is_core DESC, passion_score DESC, title")
    fun observeAlive(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun byId(id: String): TopicEntity?

    @Query("SELECT COUNT(*) FROM topics WHERE is_core = 1 AND deleted_at IS NULL")
    suspend fun coreCount(): Int
}

@Dao
interface BitDao {
    @Upsert suspend fun upsert(bit: BitEntity)

    @Upsert suspend fun upsertAll(bits: List<BitEntity>)

    @Query("SELECT * FROM bits WHERE id = :id")
    suspend fun byId(id: String): BitEntity?

    @Query("SELECT * FROM bits WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAlive(): Flow<List<BitEntity>>

    @Query("SELECT * FROM bits WHERE deleted_at IS NULL")
    suspend fun alive(): List<BitEntity>

    @Query("SELECT * FROM bits WHERE deleted_at IS NULL AND status = :status ORDER BY updated_at DESC")
    fun observeByStatus(status: BitStatus): Flow<List<BitEntity>>

    @Query("SELECT * FROM bits WHERE deleted_at IS NULL AND topic_id = :topicId ORDER BY updated_at DESC")
    fun observeByTopic(topicId: String): Flow<List<BitEntity>>

    @Query("SELECT * FROM bits WHERE deleted_at IS NULL AND status = 'SEED' ORDER BY updated_at DESC")
    fun observeInbox(): Flow<List<BitEntity>>

    @Query(
        """
        SELECT * FROM bits
        WHERE deleted_at IS NULL
          AND (title LIKE '%' || :query || '%'
               OR premise LIKE '%' || :query || '%'
               OR punch_text LIKE '%' || :query || '%'
               OR act_out_text LIKE '%' || :query || '%')
        ORDER BY updated_at DESC
        """
    )
    fun search(query: String): Flow<List<BitEntity>>
}

@Dao
interface BitVersionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(version: BitVersionEntity)

    @Query("SELECT * FROM bit_versions WHERE bit_id = :bitId ORDER BY created_at DESC")
    fun observeForBit(bitId: String): Flow<List<BitVersionEntity>>

    @Query("SELECT created_at FROM bit_versions WHERE bit_id = :bitId ORDER BY created_at DESC LIMIT 1")
    suspend fun lastSnapshotAt(bitId: String): Long?
}

@Dao
interface SetListDao {
    @Upsert suspend fun upsert(setList: SetListEntity)
    @Upsert suspend fun upsertItem(item: SetListItemEntity)
    @Upsert suspend fun upsertItems(items: List<SetListItemEntity>)

    @Query("SELECT * FROM set_lists WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAlive(): Flow<List<SetListEntity>>

    @Query("SELECT * FROM set_lists WHERE id = :id")
    suspend fun byId(id: String): SetListEntity?

    @Query("SELECT * FROM set_list_items WHERE set_list_id = :setListId AND deleted_at IS NULL ORDER BY item_order")
    fun observeItems(setListId: String): Flow<List<SetListItemEntity>>

    @Query("SELECT * FROM set_list_items WHERE set_list_id = :setListId AND deleted_at IS NULL ORDER BY item_order")
    suspend fun items(setListId: String): List<SetListItemEntity>

    /** Шутки, стоящие хоть в одном сет-листе: «в сете» — вычисляемое состояние, а не колонка. */
    @Query(
        """
        SELECT DISTINCT bit_id FROM set_list_items
        WHERE deleted_at IS NULL
        """
    )
    fun observeScheduledBitIds(): Flow<List<String>>
}

@Dao
interface GigDao {
    @Upsert suspend fun upsert(gig: GigEntity)

    @Query("SELECT * FROM gigs WHERE deleted_at IS NULL ORDER BY date_millis DESC")
    fun observeAlive(): Flow<List<GigEntity>>

    @Query("SELECT * FROM gigs WHERE id = :id")
    suspend fun byId(id: String): GigEntity?

    @Query("SELECT COUNT(*) FROM gigs WHERE deleted_at IS NULL AND date_millis >= :sinceMillis")
    fun observeCountSince(sinceMillis: Long): Flow<Int>
}

@Dao
interface BitPerformanceDao {
    @Upsert suspend fun upsert(performance: BitPerformanceEntity)
    @Upsert suspend fun upsertAll(performances: List<BitPerformanceEntity>)

    @Query("SELECT * FROM bit_performances WHERE deleted_at IS NULL")
    fun observeAll(): Flow<List<BitPerformanceEntity>>

    @Query("SELECT * FROM bit_performances WHERE gig_id = :gigId AND deleted_at IS NULL")
    fun observeForGig(gigId: String): Flow<List<BitPerformanceEntity>>

    @Query("SELECT * FROM bit_performances WHERE bit_id = :bitId AND deleted_at IS NULL")
    suspend fun forBit(bitId: String): List<BitPerformanceEntity>
}

@Dao
interface RantDao {
    @Upsert suspend fun upsert(rant: RantEntity)

    @Query("SELECT * FROM rants WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAlive(): Flow<List<RantEntity>>
}

@Dao
interface MorningWritingDao {
    @Upsert suspend fun upsert(entry: MorningWritingEntity)

    @Query("SELECT * FROM morning_writings WHERE deleted_at IS NULL ORDER BY date_millis DESC")
    fun observeAlive(): Flow<List<MorningWritingEntity>>
}

@Dao
interface StreakDao {
    @Upsert suspend fun upsert(day: StreakDayEntity)

    @Query("SELECT * FROM streak_days ORDER BY date_key DESC")
    fun observeAll(): Flow<List<StreakDayEntity>>

    @Query("SELECT * FROM streak_days WHERE date_key = :dateKey")
    suspend fun byDate(dateKey: String): StreakDayEntity?
}

@Dao
interface AudioBlobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(blob: AudioBlobEntity)

    @Query("SELECT * FROM audio_blobs WHERE hash = :hash")
    suspend fun byHash(hash: String): AudioBlobEntity?

    @Query("SELECT * FROM audio_blobs")
    suspend fun all(): List<AudioBlobEntity>
}

@Dao
interface ProfileDao {
    @Upsert suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Upsert suspend fun upsertFunnyScalePoint(point: FunnyScalePointEntity)

    @Query("SELECT * FROM funny_scale_points WHERE deleted_at IS NULL ORDER BY date_millis")
    fun observeFunnyScale(): Flow<List<FunnyScalePointEntity>>
}

@Dao
interface ExerciseDao {
    /**
     * Засев справочника при обновлении приложения. Записи, которые пользователь
     * правил руками, не трогаются — иначе апдейт стирал бы внесённые им названия.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercises SET short_title = :title, book_page = :page, input_schema = :schema " +
        "WHERE number = :number AND user_edited = 0")
    suspend fun refreshUnedited(number: Int, title: String, page: Int?, schema: String)

    @Update suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises ORDER BY number")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Upsert suspend fun upsertEntry(entry: ExerciseEntryEntity)

    @Query("SELECT * FROM exercise_entries WHERE deleted_at IS NULL")
    fun observeEntries(): Flow<List<ExerciseEntryEntity>>

    @Query("SELECT * FROM exercise_entries WHERE exercise_number = :number AND deleted_at IS NULL LIMIT 1")
    suspend fun entryFor(number: Int): ExerciseEntryEntity?
}

/**
 * Хеши аудио, на которые ссылается хоть одна живая запись.
 *
 * Собирается одним запросом по всем таблицам: перебор в коде означал бы,
 * что добавленная позже таблица с аудио тихо выпадет из бэкапа.
 */
@Dao
interface AudioReferenceDao {
    @Query(
        """
        SELECT act_out_audio_hash AS hash FROM bits
            WHERE deleted_at IS NULL AND act_out_audio_hash IS NOT NULL
        UNION
        SELECT audio_hash AS hash FROM rants
            WHERE deleted_at IS NULL
        UNION
        SELECT audio_hash AS hash FROM morning_writings
            WHERE deleted_at IS NULL AND audio_hash IS NOT NULL
        UNION
        SELECT audio_hash AS hash FROM gigs
            WHERE deleted_at IS NULL AND audio_hash IS NOT NULL
        """
    )
    suspend fun referencedHashes(): List<String>
}
