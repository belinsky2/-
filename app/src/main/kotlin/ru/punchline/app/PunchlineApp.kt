package ru.punchline.app

import android.app.Application
import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import ru.punchline.vault.BlobStore
import ru.punchline.data.db.PunchlineDatabase
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.DateKeys
import ru.punchline.data.repo.ExerciseCatalog
import ru.punchline.data.repo.GigRepository
import ru.punchline.data.repo.MutationSink
import ru.punchline.data.repo.SetListRepository
import ru.punchline.data.repo.StreakRepository
import ru.punchline.data.repo.TopicRepository
import ru.punchline.data.vault.VaultService
import ru.punchline.model.Clock
import ru.punchline.model.DeviceId

class PunchlineApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Ручная сборка зависимостей вместо Hilt.
 *
 * Приложение на одного пользователя не окупает генерацию кода, лишний
 * процессор аннотаций и ещё одну точку отказа в сборке. Здесь всё видно
 * глазами и ломается в одном месте.
 */
class AppContainer(context: Context) {

    /** Контекст приложения: живёт столько же, сколько процесс, и не течёт. */
    val context: Context = context.applicationContext

    private val appContext = this.context

    val clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * Идентификатор устройства. Создаётся один раз и переживает обновления,
     * но не переезжает при импорте на новый телефон: тот должен считаться
     * отдельным устройством, иначе логические часы двух копий совпадут.
     */
    val deviceId: DeviceId = run {
        val prefs = appContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_KEY, null)
        if (existing != null) {
            DeviceId(existing)
        } else {
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(DEVICE_KEY, fresh).apply()
            DeviceId(fresh)
        }
    }

    val database: PunchlineDatabase = PunchlineDatabase.open(appContext)

    val blobs: BlobStore = BlobStore(File(appContext.filesDir, BLOB_DIR))

    val sink: MutationSink = MutationSink(clock, deviceId)

    private val dateKeys: DateKeys = SystemDateKeys()

    val bits = BitRepository(
        bits = database.bits(),
        versions = database.bitVersions(),
        performances = database.performances(),
        setLists = database.setLists(),
        sink = sink,
        clock = clock,
    )

    val topics = TopicRepository(database.topics(), sink, clock)

    val setLists = SetListRepository(database.setLists(), database.bits(), sink, clock)

    val gigs = GigRepository(database.gigs(), database.performances(), database.bits(), sink, clock)

    val streaks = StreakRepository(database.streaks(), sink, clock, dateKeys)

    val exercises = ExerciseCatalog(database.exercises())

    val vault = VaultService(
        context = appContext,
        database = database,
        blobs = blobs,
        clock = clock,
        deviceId = deviceId,
        appVersion = BuildConfig.VERSION_NAME,
    )

    suspend fun seedExercises() {
        val raw = appContext.assets.open(EXERCISES_ASSET).bufferedReader().use { it.readText() }
        exercises.seed(raw)
    }

    private companion object {
        const val DEVICE_PREFS = "device"
        const val DEVICE_KEY = "device_id"
        const val BLOB_DIR = "blobs"
        const val EXERCISES_ASSET = "exercises.json"
    }
}

/** Календарные ключи в локальном поясе: «день» для цепочки — это день автора. */
private class SystemDateKeys : DateKeys {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun today(nowMillis: Long): String =
        LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
            .format(formatter)

    override fun previous(key: String): String =
        LocalDate.parse(key, formatter).minusDays(1).format(formatter)
}
