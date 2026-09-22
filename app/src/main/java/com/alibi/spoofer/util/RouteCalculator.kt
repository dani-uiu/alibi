package com.alibi.spoofer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return round(this * multiplier) / multiplier
}

enum class RouteMode(
    val displayName: String,
    val defaultSpeedKmH: Float,
    val minSpeedKmH: Float,
    val maxSpeedKmH: Float,
) {
    SIMPLE_LINE("Simple Line", 50f, 10f, 150f),
    CAR_RIDE("Car Ride", 50f, 20f, 120f),
    WALKING("Walking", 5f, 1f, 15f)
}

object RouteCalculator {

    private val routeCache = ConcurrentHashMap<String, List<LatLng>>()

    fun clearCache() {
        routeCache.clear()
    }

    fun buildCacheKey(start: LatLng, end: LatLng, mode: RouteMode): String {
        return "${mode}_${start.latitude.round(5)}_${start.longitude.round(5)}_${end.latitude.round(5)}_${end.longitude.round(5)}"
    }

    suspend fun calculateComplexRoute(
        waypoints: List<Waypoint>,
        isSmartDrive: Boolean = false
    ): List<LatLng> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext emptyList()
        val fullRoute = mutableListOf<LatLng>()
        
        val pairs = waypoints.zipWithNext()
        for ((index, pair) in pairs.withIndex()) {
            val (w1, w2) = pair

            if (index == 0 && w1.waitTimeSeconds > 0) {
                repeat(w1.waitTimeSeconds) {
                    fullRoute.add(w1.location)
                }
            }

            val polyline = fetchPolyline(w1.location, w2.location, w1.modeToNext)
            
            val sampled = if (isSmartDrive || w1.isSmartDrive) {
                sampleSmartPolyline(polyline, w1.modeToNext)
            } else {
                samplePolylineDynamically(polyline, w1.speedKmHToNext)
            }
            
            if (index == 0 && fullRoute.isEmpty()) {
                fullRoute.addAll(sampled)
            } else if (index == 0) {
                fullRoute.addAll(sampled)
            } else {
                if (sampled.isNotEmpty()) {
                    fullRoute.addAll(sampled.drop(1))
                }
            }
            
            repeat(w2.waitTimeSeconds) {
                fullRoute.add(w2.location)
            }
        }
        
        fullRoute
    }

    suspend fun fetchPolyline(
        start: LatLng,
        end: LatLng,
        mode: RouteMode
    ): List<LatLng> = withContext(Dispatchers.IO) {
        when (mode) {
            RouteMode.SIMPLE_LINE -> listOf(start, end)
            RouteMode.CAR_RIDE, RouteMode.WALKING -> {
                fetchOsrmPolyline(start, end, mode) ?: listOf(start, end)
            }
        }
    }

    fun fetchOsrmPolyline(start: LatLng, end: LatLng, mode: RouteMode): List<LatLng>? {
        if (mode == RouteMode.SIMPLE_LINE) return listOf(start, end)

        val cacheKey = buildCacheKey(start, end, mode)
        val cached = routeCache[cacheKey]
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        val (primaryUrl, fallbackUrl) = when (mode) {
            RouteMode.WALKING -> Pair(
                "https://routing.openstreetmap.de/routed-foot/route/v1/foot/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline",
                "https://router.project-osrm.org/route/v1/foot/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline"
            )
            RouteMode.CAR_RIDE -> Pair(
                "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline"
            )
            RouteMode.SIMPLE_LINE -> return listOf(start, end)
        }

        val polyline = fetchPolylineFromUrl(primaryUrl) ?: fetchPolylineFromUrl(fallbackUrl)
        if (polyline != null && polyline.size >= 2) {
            routeCache[cacheKey] = polyline
            return polyline
        }

        return routeCache[cacheKey]
    }

    fun fetchOsrmPolyline(start: LatLng, end: LatLng, profile: String): List<LatLng>? {
        val mode = if (profile == "foot" || profile == "walking") RouteMode.WALKING else RouteMode.CAR_RIDE
        return fetchOsrmPolyline(start, end, mode)
    }

    private fun fetchPolylineFromUrl(urlString: String): List<LatLng>? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Alibi/1.0 (Android; com.alibi.spoofer)")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return null

            val inputStream = connection.inputStream
            val bodyString = inputStream.bufferedReader().use { it.readText() }
            parseOsrmResponse(bodyString)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOsrmResponse(jsonString: String): List<LatLng>? {
        return try {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "Ok") return null
            val routes = root.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val firstRoute = routes.getJSONObject(0)

            val geometryObj = firstRoute.opt("geometry") ?: return null

            val points = when (geometryObj) {
                is String -> decodePolyline(geometryObj)
                is JSONObject -> {
                    val coordinates = geometryObj.optJSONArray("coordinates") ?: return null
                    val list = ArrayList<LatLng>(coordinates.length())
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lng = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        list.add(LatLng(lat, lng))
                    }
                    list
                }
                else -> return null
            }

            if (points.size >= 2) points else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(LatLng(pLat, pLng))
        }
        return poly
    }

    fun samplePolylineDynamically(
        polyline: List<LatLng>,
        baseSpeedKmH: Double
    ): List<LatLng> {
        if (polyline.size < 2) return polyline

        val segmentDistances = DoubleArray(polyline.size - 1)
        val cumDistances = DoubleArray(polyline.size)
        cumDistances[0] = 0.0

        for (i in 0 until (polyline.size - 1)) {
            val dist = computeDistanceMeters(polyline[i], polyline[i + 1])
            segmentDistances[i] = dist
            cumDistances[i + 1] = cumDistances[i] + dist
        }

        val totalDistanceMeters = cumDistances.last()
        val baseSpeedMps = (baseSpeedKmH * 1000.0) / 3600.0

        if (totalDistanceMeters <= 0.0 || baseSpeedMps <= 0.0) {
            return listOf(polyline.first(), polyline.last())
        }

        // Calculate bearings for segments to detect sharp turns
        val segmentBearings = FloatArray(polyline.size - 1)
        for (i in 0 until (polyline.size - 1)) {
            segmentBearings[i] = calculateBearing(polyline[i], polyline[i + 1])
        }

        val sampledRoute = ArrayList<LatLng>()
        sampledRoute.add(polyline.first())

        var currentDistance = 0.0
        var currentSegmentIndex = 0
        val random = Random()

        while (currentDistance < totalDistanceMeters) {
            // Find current segment
            while (currentSegmentIndex < segmentDistances.size - 1 &&
                currentDistance >= cumDistances[currentSegmentIndex + 1]
            ) {
                currentSegmentIndex++
            }
            
            // Calculate distance to next turn
            var nextTurnDistance = Double.MAX_VALUE
            for (i in currentSegmentIndex until segmentDistances.size - 1) {
                val turnAngle = abs(segmentBearings[i] - segmentBearings[i+1])
                val normalizedTurnAngle = if (turnAngle > 180) 360 - turnAngle else turnAngle
                if (normalizedTurnAngle > 30) {
                    nextTurnDistance = cumDistances[i+1] - currentDistance
                    break
                }
            }

            // Determine dynamic speed
            var currentSpeedMps = baseSpeedMps
            if (nextTurnDistance < baseSpeedMps * 3) { // Approaching turn in less than 3 seconds
                currentSpeedMps = baseSpeedMps * 0.45 // 45% speed for turn
            } else {
                // Straightaway: +/- 5% random noise
                val noise = 1.0 + (random.nextDouble() * 0.10 - 0.05)
                currentSpeedMps = baseSpeedMps * noise
            }

            currentDistance += currentSpeedMps

            if (currentDistance >= totalDistanceMeters) {
                sampledRoute.add(polyline.last())
                break
            }

            // Re-find segment in case we jumped over one
            var targetSegment = currentSegmentIndex
            while (targetSegment < segmentDistances.size - 1 &&
                currentDistance >= cumDistances[targetSegment + 1]
            ) {
                targetSegment++
            }

            val segStartDist = cumDistances[targetSegment]
            val segLength = segmentDistances[targetSegment]

            val fraction = if (segLength > 0.0) {
                ((currentDistance - segStartDist) / segLength).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            val p1 = polyline[targetSegment]
            val p2 = polyline[targetSegment + 1]

            val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
            val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
            sampledRoute.add(LatLng(lat, lng))
        }

        return sampledRoute
    }

    fun computeDistanceMeters(start: LatLng, end: LatLng): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun calculateBearing(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val initialBearing = atan2(y, x)
        val compassBearing = (Math.toDegrees(initialBearing) + 360) % 360
        return compassBearing.toFloat()
    }

    fun sampleSmartWalkPolyline(polyline: List<LatLng>): List<LatLng> {
        if (polyline.size < 2) return polyline

        val segmentDistances = DoubleArray(polyline.size - 1)
        val cumDistances = DoubleArray(polyline.size)
        cumDistances[0] = 0.0

        for (i in 0 until (polyline.size - 1)) {
            val dist = computeDistanceMeters(polyline[i], polyline[i + 1])
            segmentDistances[i] = dist
            cumDistances[i + 1] = cumDistances[i] + dist
        }

        val totalDistanceMeters = cumDistances.last()
        if (totalDistanceMeters <= 0.0) return polyline

        val segmentBearings = FloatArray(polyline.size - 1)
        for (i in 0 until (polyline.size - 1)) {
            segmentBearings[i] = calculateBearing(polyline[i], polyline[i + 1])
        }

        val turnAngles = FloatArray(polyline.size)
        turnAngles[0] = 0f
        turnAngles[polyline.size - 1] = 0f
        for (i in 1 until (polyline.size - 1)) {
            val diff = abs(segmentBearings[i] - segmentBearings[i - 1])
            val normalizedDiff = if (diff > 180f) 360f - diff else diff
            turnAngles[i] = normalizedDiff
        }

        // Identify street crosswalks / intersection turns (> 35° turn)
        data class CrosswalkWait(val nodeIndex: Int, val distanceMeters: Double, val waitDurationSec: Int)
        val crosswalkEvents = mutableListOf<CrosswalkWait>()
        val random = Random(42)

        for (i in 1 until (polyline.size - 1)) {
            if (turnAngles[i] > 35.0f) {
                val nodeDist = cumDistances[i]
                if (nodeDist > 15.0 && (totalDistanceMeters - nodeDist) > 15.0) {
                    val lastEventDist = crosswalkEvents.lastOrNull()?.distanceMeters ?: -1000.0
                    if (nodeDist - lastEventDist >= 30.0) {
                        val waitTime = 5 + random.nextInt(11) // 5 to 15 seconds wait
                        crosswalkEvents.add(CrosswalkWait(i, nodeDist, waitTime))
                    }
                }
            }
        }

        val sampledRoute = ArrayList<LatLng>()
        sampledRoute.add(polyline.first())

        var currentDistance = 0.0
        var crosswalkIndex = 0
        var inCrosswalkWait = 0
        var briskCrossingRemainingMeters = 0.0

        var nextMicroPauseDist = 200.0 + random.nextDouble() * 150.0 // micro-pause every 200..350m
        var inMicroPause = 0

        var stepCount = 0
        val maxSteps = 100_000

        while (currentDistance < totalDistanceMeters && stepCount < maxSteps) {
            stepCount++

            // 1. Crosswalk Wait Pause
            if (inCrosswalkWait > 0) {
                inCrosswalkWait--
                val basePt = getPointAtDistance(polyline, cumDistances, segmentDistances, currentDistance)
                sampledRoute.add(basePt)

                if (inCrosswalkWait == 0) {
                    // Step briskly across street (5.8 - 6.5 km/h) for ~12 meters
                    briskCrossingRemainingMeters = 12.0
                }
                continue
            }

            // 2. Micro-Pause (phone checking, shoe tying, window shopping)
            if (inMicroPause > 0) {
                inMicroPause--
                val basePt = getPointAtDistance(polyline, cumDistances, segmentDistances, currentDistance)
                sampledRoute.add(basePt)
                continue
            }

            // Check if hitting next Crosswalk Wait
            if (crosswalkIndex < crosswalkEvents.size) {
                val nextCrosswalk = crosswalkEvents[crosswalkIndex]
                if (abs(currentDistance - nextCrosswalk.distanceMeters) < 1.5 || currentDistance >= nextCrosswalk.distanceMeters) {
                    if (currentDistance < nextCrosswalk.distanceMeters + 2.0) {
                        currentDistance = nextCrosswalk.distanceMeters
                        inCrosswalkWait = nextCrosswalk.waitDurationSec
                        crosswalkIndex++
                        val basePt = getPointAtDistance(polyline, cumDistances, segmentDistances, currentDistance)
                        sampledRoute.add(basePt)
                        continue
                    }
                }
            }

            // Check if triggering a Micro-Pause (every 200..350m)
            if (currentDistance >= nextMicroPauseDist && (totalDistanceMeters - currentDistance) > 30.0 && inCrosswalkWait == 0) {
                inMicroPause = 3 + random.nextInt(6) // 3 to 8 seconds pause
                nextMicroPauseDist = currentDistance + 200.0 + random.nextDouble() * 150.0
                val basePt = getPointAtDistance(polyline, cumDistances, segmentDistances, currentDistance)
                sampledRoute.add(basePt)
                continue
            }

            // 3. Determine Base Target Speed (km/h)
            var currentSegIdx = 0
            while (currentSegIdx < segmentDistances.size - 1 && currentDistance >= cumDistances[currentSegIdx + 1]) {
                currentSegIdx++
            }

            var nextTurnDist = Double.MAX_VALUE
            for (i in currentSegIdx until (polyline.size - 1)) {
                if (turnAngles[i + 1] > 25f) {
                    nextTurnDist = cumDistances[i + 1] - currentDistance
                    break
                }
            }

            val targetSpeedKmH: Double = when {
                briskCrossingRemainingMeters > 0.0 -> 5.8 + random.nextDouble() * 0.7 // brisk cross: 5.8..6.5 km/h
                nextTurnDist in 0.0..6.0 -> 3.2 + random.nextDouble() * 0.6 // slow on sharp turn: 3.2..3.8 km/h
                else -> 4.5 + random.nextDouble() * 0.7 // normal sidewalk pace: 4.5..5.2 km/h
            }

            val baseSpeedMps = targetSpeedKmH / 3.6

            // Stride speed variation (+/- 8%)
            val strideVariation = 1.0 + (random.nextDouble() * 0.16 - 0.08)
            val actualSpeedMps = baseSpeedMps * strideVariation

            val stepDist = maxOf(0.4, actualSpeedMps)
            currentDistance += stepDist

            if (briskCrossingRemainingMeters > 0.0) {
                briskCrossingRemainingMeters -= stepDist
            }

            if (currentDistance >= totalDistanceMeters) {
                sampledRoute.add(polyline.last())
                break
            }

            // 4. Calculate position with Lateral Gait Sway (~0.3m)
            val basePt = getPointAtDistance(polyline, cumDistances, segmentDistances, currentDistance)

            var segIdx = 0
            while (segIdx < segmentDistances.size - 1 && cumDistances[segIdx + 1] < currentDistance) {
                segIdx++
            }
            val segBearing = segmentBearings[segIdx]

            // Oscillate lateral sway: ~0.3m amplitude
            val swayMeters = 0.3 * sin(stepCount * Math.PI * 0.5)

            val perpBearingRad = Math.toRadians((segBearing + 90.0) % 360.0)
            val deltaLat = (swayMeters * cos(perpBearingRad)) / 111000.0
            val deltaLng = (swayMeters * sin(perpBearingRad)) / (111000.0 * cos(Math.toRadians(basePt.latitude)))

            val swayedPt = LatLng(basePt.latitude + deltaLat, basePt.longitude + deltaLng)
            sampledRoute.add(swayedPt)
        }

        return sampledRoute
    }

    fun sampleSmartPolyline(
        polyline: List<LatLng>,
        mode: RouteMode = RouteMode.CAR_RIDE
    ): List<LatLng> {
        if (mode == RouteMode.WALKING) {
            return sampleSmartWalkPolyline(polyline)
        }
        if (polyline.size < 2) return polyline

        val segmentDistances = DoubleArray(polyline.size - 1)
        val cumDistances = DoubleArray(polyline.size)
        cumDistances[0] = 0.0

        for (i in 0 until (polyline.size - 1)) {
            val dist = computeDistanceMeters(polyline[i], polyline[i + 1])
            segmentDistances[i] = dist
            cumDistances[i + 1] = cumDistances[i] + dist
        }

        val totalDistanceMeters = cumDistances.last()
        if (totalDistanceMeters <= 0.0) return polyline

        // Calculate segment bearings and turn angles
        val segmentBearings = FloatArray(polyline.size - 1)
        for (i in 0 until (polyline.size - 1)) {
            segmentBearings[i] = calculateBearing(polyline[i], polyline[i + 1])
        }

        val turnAngles = FloatArray(polyline.size) // angle at node i
        turnAngles[0] = 0f
        turnAngles[polyline.size - 1] = 0f
        for (i in 1 until (polyline.size - 1)) {
            val diff = abs(segmentBearings[i] - segmentBearings[i - 1])
            val normalizedDiff = if (diff > 180f) 360f - diff else diff
            turnAngles[i] = normalizedDiff
        }

        // Determine base target speed per segment based on node stretch length
        val segmentRoadSpeedsKmH = DoubleArray(polyline.size - 1)
        for (i in 0 until (polyline.size - 1)) {
            val d = segmentDistances[i]
            segmentRoadSpeedsKmH[i] = when {
                d > 150.0 -> (80.0 + ((d - 150.0) / 10.0).coerceAtMost(30.0))
                d < 60.0 -> (30.0 + (d / 60.0) * 15.0)
                else -> (45.0 + ((d - 60.0) / 90.0) * 35.0)
            }
        }

        // Identify Traffic Light Stops & Major Intersections
        data class StopEvent(val distanceMeters: Double, val durationSeconds: Int)
        val stopEvents = mutableListOf<StopEvent>()
        val random = Random(42)

        var urbanDistSinceStop = 0.0
        for (i in 1 until (polyline.size - 1)) {
            val dPrev = segmentDistances[i - 1]
            val nodeDist = cumDistances[i]
            val angle = turnAngles[i]

            val isMajorIntersection = angle > 40.0f
            if (dPrev < 80.0) {
                urbanDistSinceStop += dPrev
            }

            val isPeriodicUrbanStop = urbanDistSinceStop >= 450.0

            if ((isMajorIntersection || isPeriodicUrbanStop) && nodeDist > 50.0 && (totalDistanceMeters - nodeDist) > 50.0) {
                val lastStopDist = stopEvents.lastOrNull()?.distanceMeters ?: -1000.0
                if (nodeDist - lastStopDist >= 100.0) {
                    val stopTime = 12 + random.nextInt(14) // 12 to 25 seconds
                    stopEvents.add(StopEvent(nodeDist, stopTime))
                    urbanDistSinceStop = 0.0
                }
            }
        }

        val sampledRoute = ArrayList<LatLng>()
        sampledRoute.add(polyline.first())

        var currentDistance = 0.0
        var currentSpeedMps = 0.0 // start from rest
        var stopEventIndex = 0

        var inStopCountdown = 0
        var activeStopDist = -1.0

        var isAccelerating = true
        var accelElapsed = 0.0
        var accelTotalSec = 6.0 // 5-7 seconds

        var isDecelerating = false
        var decelElapsed = 0.0
        var decelTotalSec = 5.0 // 4-6 seconds
        var decelStartSpeedMps = 0.0

        val getPointAtDist = { targetDist: Double ->
            getPointAtDistance(polyline, cumDistances, segmentDistances, targetDist)
        }

        val maxSteps = 100_000
        var stepCount = 0

        while (currentDistance < totalDistanceMeters && stepCount < maxSteps) {
            stepCount++

            // 1. If currently at a full traffic light stop
            if (inStopCountdown > 0) {
                inStopCountdown--
                sampledRoute.add(getPointAtDist(activeStopDist))

                if (inStopCountdown == 0) {
                    isAccelerating = true
                    accelElapsed = 0.0
                    accelTotalSec = 5.0 + random.nextDouble() * 2.0
                    currentSpeedMps = 0.0
                }
                continue
            }

            // 2. Determine target road cruising speed at current distance
            var targetSegIdx = 0
            while (targetSegIdx < segmentDistances.size - 1 && currentDistance >= cumDistances[targetSegIdx + 1]) {
                targetSegIdx++
            }
            val roadSpeedKmH = segmentRoadSpeedsKmH[targetSegIdx]
            var targetSpeedMps = roadSpeedKmH / 3.6

            // Check if approaching sharp turn (< 30m ahead)
            var nextTurnDist = Double.MAX_VALUE
            for (i in targetSegIdx until (polyline.size - 1)) {
                if (turnAngles[i + 1] > 30f) {
                    nextTurnDist = cumDistances[i + 1] - currentDistance
                    break
                }
            }
            if (nextTurnDist in 0.0..30.0) {
                val turnSpeedMps = 20.0 / 3.6
                targetSpeedMps = minOf(targetSpeedMps, turnSpeedMps)
            }

            // 3. Check for upcoming Traffic Light Stop
            if (stopEventIndex < stopEvents.size) {
                val nextStop = stopEvents[stopEventIndex]
                val distToStop = nextStop.distanceMeters - currentDistance

                val neededDecelDist = (currentSpeedMps * 5.0 / 2.0) + 5.0

                if (distToStop in 0.0..neededDecelDist && !isDecelerating) {
                    isDecelerating = true
                    isAccelerating = false
                    decelStartSpeedMps = maxOf(currentSpeedMps, 5.0)
                    decelElapsed = 0.0
                    decelTotalSec = 4.0 + random.nextDouble() * 2.0
                    activeStopDist = nextStop.distanceMeters
                }
            }

            // 4. Calculate current step speed using S-curve or Cruising Noise
            if (isDecelerating) {
                decelElapsed += 1.0
                val progress = (decelElapsed / decelTotalSec).coerceIn(0.0, 1.0)
                val factor = 0.5 * (1.0 + cos(Math.PI * progress))
                currentSpeedMps = decelStartSpeedMps * factor

                if (progress >= 1.0 || (currentDistance + currentSpeedMps) >= activeStopDist) {
                    currentDistance = activeStopDist
                    currentSpeedMps = 0.0
                    inStopCountdown = stopEvents[stopEventIndex].durationSeconds
                    stopEventIndex++
                    isDecelerating = false
                    sampledRoute.add(getPointAtDist(activeStopDist))
                    continue
                }
            } else if (isAccelerating) {
                accelElapsed += 1.0
                val progress = (accelElapsed / accelTotalSec).coerceIn(0.0, 1.0)
                val factor = 0.5 * (1.0 - cos(Math.PI * progress))
                currentSpeedMps = targetSpeedMps * factor

                if (progress >= 1.0) {
                    isAccelerating = false
                }
            } else {
                val noise = 1.0 + (random.nextDouble() * 0.06 - 0.03)
                currentSpeedMps = targetSpeedMps * noise
            }

            currentDistance += maxOf(0.5, currentSpeedMps)

            if (currentDistance >= totalDistanceMeters) {
                sampledRoute.add(polyline.last())
                break
            }

            sampledRoute.add(getPointAtDist(currentDistance))
        }

        return sampledRoute
    }

    private fun getPointAtDistance(
        polyline: List<LatLng>,
        cumDistances: DoubleArray,
        segmentDistances: DoubleArray,
        targetDist: Double
    ): LatLng {
        if (targetDist <= 0.0) return polyline.first()
        if (targetDist >= cumDistances.last()) return polyline.last()

        var segIdx = 0
        while (segIdx < segmentDistances.size - 1 && cumDistances[segIdx + 1] < targetDist) {
            segIdx++
        }

        val segStartDist = cumDistances[segIdx]
        val segLength = segmentDistances[segIdx]

        val fraction = if (segLength > 0.0) {
            ((targetDist - segStartDist) / segLength).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val p1 = polyline[segIdx]
        val p2 = polyline[segIdx + 1]

        val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
        val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
        return LatLng(lat, lng)
    }
}
