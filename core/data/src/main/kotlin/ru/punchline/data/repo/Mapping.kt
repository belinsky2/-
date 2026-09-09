package ru.punchline.data.repo

import ru.punchline.data.db.BitEntity
import ru.punchline.data.db.BitPerformanceEntity
import ru.punchline.data.db.GigEntity
import ru.punchline.data.db.SetListEntity
import ru.punchline.data.db.SetListItemEntity
import ru.punchline.data.db.SyncColumns
import ru.punchline.data.db.TopicEntity
import ru.punchline.model.ActOut
import ru.punchline.model.Bit
import ru.punchline.model.BitElements
import ru.punchline.model.BitPerformance
import ru.punchline.model.DeviceId
import ru.punchline.model.Gig
import ru.punchline.model.Id
import ru.punchline.model.Punch
import ru.punchline.model.SetList
import ru.punchline.model.SetListItem
import ru.punchline.model.SyncMeta
import ru.punchline.model.Topic

/**
 * Перевод между таблицами и доменом. Держится отдельно, чтобы доменные типы
 * не знали ни про Room, ни про то, что поля шутки разложены по колонкам.
 */

fun SyncColumns.toDomain(): SyncMeta =
    SyncMeta(updatedAt, lamport, DeviceId(deviceId), deletedAt)

fun SyncMeta.toColumns(): SyncColumns =
    SyncColumns(updatedAt, lamport, deviceId.value, deletedAt)

fun TopicEntity.toDomain(): Topic =
    Topic(Id(id), title, passionScore, isCore, sync.toDomain())

fun Topic.toEntity(): TopicEntity =
    TopicEntity(id.value, title, passionScore, isCore, meta.toColumns())

fun BitEntity.toDomain(): Bit = Bit(
    id = Id(id),
    topicId = topicId?.let(::Id),
    title = title,
    status = status,
    attitude = attitude,
    elements = BitElements(
        premise = premise,
        setup = setup,
        punch = punchText?.let { Punch(it, punchTechnique ?: ru.punchline.model.PunchTechnique.OTHER) },
        actOut = actOutText?.let { ActOut(it, actOutSpaceWork, actOutAudioHash) },
        tags = tags,
        callbackTo = callbackTo?.let(::Id),
    ),
    durationSec = durationSec,
    meta = sync.toDomain(),
)

fun Bit.toEntity(): BitEntity = BitEntity(
    id = id.value,
    topicId = topicId?.value,
    title = title,
    status = status,
    attitude = attitude,
    premise = elements.premise,
    setup = elements.setup,
    punchText = elements.punch?.text,
    punchTechnique = elements.punch?.technique,
    actOutText = elements.actOut?.text,
    actOutSpaceWork = elements.actOut?.hasSpaceWork ?: false,
    actOutAudioHash = elements.actOut?.audioHash,
    tags = elements.tags,
    callbackTo = elements.callbackTo?.value,
    durationSec = durationSec,
    sync = meta.toColumns(),
)

fun SetListItemEntity.toDomain(): SetListItem =
    SetListItem(Id(id), Id(bitId), order, role, plannedDurationSec)

fun SetListEntity.toDomain(items: List<SetListItemEntity>): SetList =
    SetList(Id(id), title, targetDurationSec, items.map { it.toDomain() }, sync.toDomain())

fun GigEntity.toDomain(): Gig =
    Gig(Id(id), setListId?.let(::Id), type, venue, dateMillis, actualDurationSec, sync.toDomain())

fun BitPerformanceEntity.toDomain(): BitPerformance =
    BitPerformance(Id(id), Id(gigId), Id(bitId), result, note, sync.toDomain())
