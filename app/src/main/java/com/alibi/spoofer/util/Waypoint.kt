package com.alibi.spoofer.util

import java.util.UUID

/**
 * A stop on a planned route. Immutable: edit with [copy] so Compose and the
 * ViewModel always see a new value and recalculate.
 */
data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    val location: LatLng,
    /** Optional human-readable name (e.g. from search results). */
    val label: String? = null,
    /** Seconds to stay at this stop before leaving (or after arriving, for the last stop). */
    val waitTimeSeconds: Int = 0,
    val modeToNext: RouteMode = RouteMode.CAR_RIDE,
    val speedKmHToNext: Double = 50.0,
    val isSmartDrive: Boolean = false
) {
    companion object {
        /** Upper bound for a single stop's wait, so a typo can't create an absurd plan. */
        const val MAX_WAIT_SECONDS = 24 * 60 * 60
    }
}

/**
 * A route ready to be played back by the service, one point per second.
 *
 * Waits are not stored as repeated points (that used to blow up memory and the
 * Intent size limit for long waits); instead [holdsAtIndex] says how many seconds
 * to stay at a given point index before moving on.
 */
data class SimulatedRoute(
    val points: List<LatLng>,
    val holdsAtIndex: Map<Int, Int> = emptyMap(),
    /** Indices of legs that fell back to a straight line because routing failed. */
    val fallbackLegs: Set<Int> = emptySet(),
    val distanceMeters: Double = 0.0
) {
    val totalHoldSeconds: Int get() = holdsAtIndex.values.sum()

    /** Approximate playback duration: one second per point plus all waits. */
    val durationSeconds: Int get() = points.size + totalHoldSeconds

    val isEmpty: Boolean get() = points.isEmpty()

    companion object {
        val EMPTY = SimulatedRoute(emptyList())
    }
}
