package ru.punchline.model

import kotlin.random.Random

/** Управляемые часы: тест обязан уметь прожить девяносто дней мгновенно. */
class TestClock(private var now: Long = 1_700_000_000_000) : Clock {
    override fun nowMillis(): Long = now
    fun advanceDays(days: Long) { now += days * 24 * 60 * 60 * 1000 }
    fun advanceMillis(ms: Long) { now += ms }
}

val TEST_DEVICE = DeviceId("test-device")

fun meta(clock: Clock, lamport: Long = 1, deletedAt: Long? = null) =
    SyncMeta(updatedAt = clock.nowMillis(), lamport = lamport, deviceId = TEST_DEVICE, deletedAt = deletedAt)

fun bit(
    clock: Clock,
    id: String = "bit-1",
    topicId: String? = "topic-1",
    status: BitStatus = BitStatus.SEED,
    attitude: Attitude? = null,
    premise: String? = null,
    punch: Punch? = null,
    actOut: ActOut? = null,
    callbackTo: String? = null,
    durationSec: Int? = null,
) = Bit(
    id = Id(id),
    topicId = topicId?.let(::Id),
    title = id,
    status = status,
    attitude = attitude,
    elements = BitElements(
        premise = premise,
        punch = punch,
        actOut = actOut,
        callbackTo = callbackTo?.let(::Id),
    ),
    durationSec = durationSec,
    meta = meta(clock),
)

fun performance(
    clock: Clock,
    bitId: String,
    result: LaughResult,
    gigId: String = "gig-1",
    lamport: Long = 1,
) = BitPerformance(
    id = Id.generate(clock, Random(bitId.hashCode() + result.ordinal + lamport.toInt())),
    gigId = Id(gigId),
    bitId = Id(bitId),
    result = result,
    meta = meta(clock, lamport),
)
