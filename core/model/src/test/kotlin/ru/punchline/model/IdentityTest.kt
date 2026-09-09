package ru.punchline.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {

    @Test
    fun `id has uuid shape and version 7`() {
        val id = Id.generate(TestClock()).value
        assertEquals(36, id.length)
        assertEquals('-', id[8]); assertEquals('-', id[13])
        assertEquals('-', id[18]); assertEquals('-', id[23])
        assertEquals("версия UUID должна быть 7", '7', id[14])
        assertTrue("вариант RFC 4122", id[19] in "89ab")
    }

    @Test
    fun `ids generated later sort after earlier ones`() {
        val clock = TestClock()
        val first = Id.generate(clock, Random(1)).value
        clock.advanceMillis(5)
        val second = Id.generate(clock, Random(2)).value
        assertTrue("UUIDv7 должен быть упорядочен по времени: $first !< $second", first < second)
    }

    @Test
    fun `two devices at the same instant do not collide`() {
        val clock = TestClock()
        val a = Id.generate(clock, Random(1))
        val b = Id.generate(clock, Random(2))
        assertNotEquals(a, b)
    }

    @Test
    fun `lamport clock decides the winner before wall time`() {
        val loser = SyncMeta(updatedAt = 5_000, lamport = 1, deviceId = DeviceId("phone"))
        // Часы на втором устройстве отстают, но логически изменение более позднее.
        val winner = SyncMeta(updatedAt = 1_000, lamport = 2, deviceId = DeviceId("mac"))
        assertTrue(winner.wins(loser))
        assertTrue(!loser.wins(winner))
    }

    @Test
    fun `identical clocks resolve deterministically by device`() {
        val a = SyncMeta(updatedAt = 1_000, lamport = 1, deviceId = DeviceId("aaa"))
        val b = SyncMeta(updatedAt = 1_000, lamport = 1, deviceId = DeviceId("bbb"))
        assertTrue(b.wins(a))
        assertTrue(!a.wins(b))
    }

    @Test
    fun `tombstone is visible through meta`() {
        val alive = SyncMeta(updatedAt = 1, lamport = 1, deviceId = TEST_DEVICE)
        assertTrue(!alive.isDeleted)
        assertTrue(alive.copy(deletedAt = 2).isDeleted)
    }
}
