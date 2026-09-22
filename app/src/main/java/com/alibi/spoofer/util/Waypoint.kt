package com.alibi.spoofer.util

import java.util.UUID

data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    val location: LatLng,
    var waitTimeSeconds: Int = 0,
    var modeToNext: RouteMode = RouteMode.CAR_RIDE,
    var speedKmHToNext: Double = 50.0,
    var isSmartDrive: Boolean = false
)
