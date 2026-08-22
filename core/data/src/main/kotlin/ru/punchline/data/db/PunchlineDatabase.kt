package ru.punchline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = PunchlineDatabase.VERSION,
    exportSchema = true,
    entities = [
        TopicEntity::class,
        BitEntity::class,
        BitVersionEntity::class,
        SetListEntity::class,
        SetListItemEntity::class,
        GigEntity::class,
        BitPerformanceEntity::class,
        RantEntity::class,
        MorningWritingEntity::class,
        StreakDayEntity::class,
        AudioBlobEntity::class,
        ProfileEntity::class,
        FunnyScalePointEntity::class,
        ExerciseEntity::class,
        ExerciseEntryEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class PunchlineDatabase : RoomDatabase() {

    abstract fun topics(): TopicDao
    abstract fun bits(): BitDao
    abstract fun bitVersions(): BitVersionDao
    abstract fun setLists(): SetListDao
    abstract fun gigs(): GigDao
    abstract fun performances(): BitPerformanceDao
    abstract fun rants(): RantDao
    abstract fun morningWritings(): MorningWritingDao
    abstract fun streaks(): StreakDao
    abstract fun audioBlobs(): AudioBlobDao
    abstract fun profile(): ProfileDao
    abstract fun exercises(): ExerciseDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "vault.sqlite"

        /**
         * Разрушающая миграция не подключается сознательно: потерять год
         * материала из-за неаккуратной схемы — худший из возможных исходов,
         * поэтому лучше явный сбой, чем тихо стёртая база.
         */
        fun open(context: Context): PunchlineDatabase =
            Room.databaseBuilder(context.applicationContext, PunchlineDatabase::class.java, FILE_NAME)
                .build()
    }
}
