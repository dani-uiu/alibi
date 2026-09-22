package com.alibi.spoofer.util

import org.osmdroid.util.GeoPoint

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
