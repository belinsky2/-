package ru.punchline.data

import android.content.Context
import java.io.File
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
import ru.punchline.vault.BlobStore

/**
 * Всё, что слой данных отдаёт наружу.
 *
 * Room, DAO и сама база остаются внутри модуля: приложение работает
 * с репозиториями и не должно знать, чем именно они подкреплены. Заодно это
 * снимает Room с компиляционного пути `:app` — и делает переезд слоя данных
 * на Kotlin Multiplatform в v2 вопросом одного модуля, а не всего проекта.
 */
class DataLayer(
    context: Context,
    clock: Clock,
    deviceId: DeviceId,
    dateKeys: DateKeys,
    appVersion: String,
    blobRoot: File,
) {
    private val appContext = context.applicationContext
    private val database = PunchlineDatabase.open(appContext)

    val blobs: BlobStore = BlobStore(blobRoot)
    val sink: MutationSink = MutationSink(clock, deviceId)

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
        appVersion = appVersion,
    )
}
