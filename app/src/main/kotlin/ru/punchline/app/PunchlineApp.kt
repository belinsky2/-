package ru.punchline.app

import android.app.Application
import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import ru.punchline.data.DataLayer
import ru.punchline.data.repo.DateKeys
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

    val clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * Идентификатор устройства. Создаётся один раз и переживает обновления,
     * но не переезжает при импорте: новый телефон обязан считаться отдельным
     * устройством, иначе логические часы двух копий совпадут.
     */
    val deviceId: DeviceId = run {
        val prefs = this.context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_KEY, null)
        if (existing != null) {
            DeviceId(existing)
        } else {
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(DEVICE_KEY, fresh).apply()
            DeviceId(fresh)
        }
    }

    private val data = DataLayer(
        context = this.context,
        clock = clock,
        deviceId = deviceId,
        dateKeys = SystemDateKeys(),
        appVersion = BuildConfig.VERSION_NAME,
        blobRoot = File(this.context.filesDir, BLOB_DIR),
    )

    val bits get() = data.bits
    val topics get() = data.topics
    val setLists get() = data.setLists
    val gigs get() = data.gigs
    val workshop get() = data.workshop
    val streaks get() = data.streaks
    val vault get() = data.vault
    val sink get() = data.sink
    val blobs get() = data.blobs

    suspend fun seedExercises() {
        val raw = context.assets.open(EXERCISES_ASSET).bufferedReader().use { it.readText() }
        data.exercises.seed(raw)
    }

    private companion object {
        const val DEVICE_PREFS = "device"
        const val DEVICE_KEY = "device_id"
        const val BLOB_DIR = "blobs"
        const val EXERCISES_ASSET = "exercises.json"
    }
}

/** Календарные ключи в локальном поясе: «день» цепочки — это день автора. */
private class SystemDateKeys : DateKeys {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun today(nowMillis: Long): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
            .format(formatter)

    override fun previous(key: String): String =
        LocalDate.parse(key, formatter).minusDays(1).format(formatter)
}
