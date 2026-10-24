package com.example.pet.idle

import kotlin.random.Random

/**
 * What the pet is doing. [CHAT_OPEN] is entered and left by [IdleStateMachine.tap],
 * not by elapsed time.
 */
enum class IdleState { WANDER, IDLE, SIT, SLEEP, CHAT_OPEN }

/**
 * How long each state lasts, in seconds, plus how often a finished IDLE becomes
 * [IdleState.SIT] instead of [IdleState.SLEEP]. The durations double as cooldowns:
 * a state is never left before its duration has elapsed.
 */
data class IdleTimings(
    val wanderSeconds: Float = 5f,
    val idleSeconds: Float = 4f,
    val sitSeconds: Float = 8f,
    val sleepSeconds: Float = 25f,
    val sitChance: Float = 0.6f,
) {
    init {
        require(wanderSeconds.isFinite() && wanderSeconds > 0f) { "wanderSeconds must be > 0" }
        require(idleSeconds.isFinite() && idleSeconds > 0f) { "idleSeconds must be > 0" }
        require(sitSeconds.isFinite() && sitSeconds > 0f) { "sitSeconds must be > 0" }
        require(sleepSeconds.isFinite() && sleepSeconds > 0f) { "sleepSeconds must be > 0" }
        require(sitChance.isFinite() && sitChance in 0f..1f) { "sitChance must be in 0..1" }
    }
}

/**
 * Stateless idle behavior: WANDER → IDLE → SIT/SLEEP → WANDER.
 * No Android or clock dependencies, so it runs on the JVM.
 *
 * [nextState] takes `secondsInState` as the seconds elapsed since `current` became active, not the
 * last frame's delta. Callers reset their timer whenever the returned state changes.
 * Negative or NaN `secondsInState` counts as zero, matching the renderers' update contract.
 */
class IdleStateMachine(val timings: IdleTimings = IdleTimings()) {

    /** The state after [secondsInState] seconds in [current]. */
    fun nextState(current: IdleState, random: Random, secondsInState: Float): IdleState {
        val elapsed = if (secondsInState.isNaN()) 0f else secondsInState.coerceAtLeast(0f)
        return when (current) {
            IdleState.WANDER ->
                if (elapsed >= timings.wanderSeconds) IdleState.IDLE else current
            IdleState.IDLE -> when {
                elapsed < timings.idleSeconds -> current
                random.nextFloat() < timings.sitChance -> IdleState.SIT
                else -> IdleState.SLEEP
            }
            IdleState.SIT ->
                if (elapsed >= timings.sitSeconds) IdleState.WANDER else current
            IdleState.SLEEP ->
                if (elapsed >= timings.sleepSeconds) IdleState.WANDER else current
            IdleState.CHAT_OPEN -> current
        }
    }

    /** Tap interrupts idle behavior: opens chat, or closes it if already open. */
    fun tap(current: IdleState): IdleState =
        if (current == IdleState.CHAT_OPEN) IdleState.IDLE else IdleState.CHAT_OPEN
}
