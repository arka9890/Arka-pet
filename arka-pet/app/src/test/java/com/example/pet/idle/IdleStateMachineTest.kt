package com.example.pet.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A [Random] that always answers 0 (prefer SIT) or nearly 1 (prefer SLEEP). */
private class FixedRandom(private val high: Boolean) : Random() {
    override fun nextBits(bitCount: Int): Int =
        if (high) -1 ushr (32 - bitCount) else 0
}

class IdleStateMachineTest {

    private val machine = IdleStateMachine()
    private val t = machine.timings
    private val preferSit = FixedRandom(high = false)
    private val preferSleep = FixedRandom(high = true)

    @Test
    fun wander_staysWanderingBeforeItsCooldown() {
        val next = machine.nextState(IdleState.WANDER, preferSit, t.wanderSeconds - 0.01f)
        assertEquals(IdleState.WANDER, next)
    }

    @Test
    fun wander_becomesIdleAfterItsCooldown() {
        assertEquals(
            IdleState.IDLE,
            machine.nextState(IdleState.WANDER, preferSit, t.wanderSeconds),
        )
    }

    @Test
    fun idle_staysIdleBeforeItsCooldown() {
        assertEquals(
            IdleState.IDLE,
            machine.nextState(IdleState.IDLE, preferSit, t.idleSeconds - 0.01f),
        )
    }

    @Test
    fun idle_becomesSitWhenRandomPicksSit() {
        assertEquals(
            IdleState.SIT,
            machine.nextState(IdleState.IDLE, preferSit, t.idleSeconds),
        )
    }

    @Test
    fun idle_becomesSleepWhenRandomPicksSleep() {
        assertEquals(
            IdleState.SLEEP,
            machine.nextState(IdleState.IDLE, preferSleep, t.idleSeconds),
        )
    }

    @Test
    fun sit_returnsToWanderAfterItsCooldown() {
        assertEquals(
            IdleState.SIT,
            machine.nextState(IdleState.SIT, preferSit, t.sitSeconds - 0.01f),
        )
        assertEquals(
            IdleState.WANDER,
            machine.nextState(IdleState.SIT, preferSit, t.sitSeconds),
        )
    }

    @Test
    fun sleep_returnsToWanderAfterItsLongerCooldown() {
        assertEquals(
            IdleState.SLEEP,
            machine.nextState(IdleState.SLEEP, preferSit, t.sleepSeconds - 0.01f),
        )
        assertEquals(
            IdleState.WANDER,
            machine.nextState(IdleState.SLEEP, preferSit, t.sleepSeconds),
        )
        assertTrue("sleep must last longer than a sit", t.sleepSeconds > t.sitSeconds)
    }

    @Test
    fun tap_opensChatFromEveryIdleState() {
        IdleState.entries
            .filter { it != IdleState.CHAT_OPEN }
            .forEach { state ->
                assertEquals(IdleState.CHAT_OPEN, machine.tap(state))
            }
    }

    @Test
    fun tap_closesChat() {
        assertEquals(IdleState.IDLE, machine.tap(IdleState.CHAT_OPEN))
    }

    @Test
    fun chat_staysOpenUntilClosed() {
        assertEquals(
            IdleState.CHAT_OPEN,
            machine.nextState(IdleState.CHAT_OPEN, preferSit, t.sleepSeconds * 10f),
        )
    }

    @Test
    fun negativeDt_isTreatedAsNoTime() {
        assertEquals(
            IdleState.WANDER,
            machine.nextState(IdleState.WANDER, preferSit, -1f),
        )
    }

    @Test
    fun timings_rejectNonPositiveDurations() {
        listOf<() -> IdleTimings>(
            { IdleTimings(wanderSeconds = 0f) },
            { IdleTimings(idleSeconds = -1f) },
            { IdleTimings(sitSeconds = 0f) },
            { IdleTimings(sleepSeconds = 0f) },
        ).forEach { make ->
            try {
                IdleStateMachine(make())
                throw AssertionError("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }
}
