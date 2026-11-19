package com.alibi.spoofer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
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

/**
 * Geometry for one leg.
 * @param speedsMps optional per-segment average speed from OSRM (size = points.size - 1).
 * @param isFallback true when routing failed and this is just a straight line.
 */
data class RoutePolyline(
    val points: List<LatLng>,
    val speedsMps: List<Double>? = null,
    val isFallback: Boolean = false
)

object RouteCalculator {

    private val routeCache = ConcurrentHashMap<String, RoutePolyline>()

    fun clearCache() {
        routeCache.clear()
    }

    internal fun putInCache(key: String, polyline: RoutePolyline) {
        routeCache[key] = polyline
    }

    fun buildCacheKey(start: LatLng, end: LatLng, mode: RouteMode): String {
        return "${mode}_${start.latitude.round(5)}_${start.longitude.round(5)}_${end.latitude.round(5)}_${end.longitude.round(5)}"
    }

    /**
     * Builds a one-point-per-second playback route for [waypoints].
     * Waits are returned as holds (see [SimulatedRoute.holdsAtIndex]), not repeated points.
     * The last stop's wait is ignored: playback stays at the destination until stopped.
     */
    suspend fun calculateComplexRoute(
        waypoints: List<Waypoint>,
        isSmartDrive: Boolean = false
    ): SimulatedRoute = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext SimulatedRoute.EMPTY

        val points = ArrayList<LatLng>()
        val holds = HashMap<Int, Int>()
        val fallbackLegs = HashSet<Int>()
        var distance = 0.0

        for ((legIndex, pair) in waypoints.zipWithNext().withIndex()) {
            ensureActive()
            val (from, to) = pair

            val leg = fetchPolyline(from.location, to.location, from.modeToNext)
            if (leg.isFallback) fallbackLegs += legIndex
            distance += polylineLength(leg.points)

            val sampled = if (isSmartDrive || from.isSmartDrive) {
                sampleSmartPolyline(leg.points, from.modeToNext, leg.speedsMps)
            } else {
                samplePolylineDynamically(leg.points, from.speedKmHToNext)
            }
            if (sampled.isEmpty()) continue

            if (points.isEmpty()) {
                points.addAll(sampled)
            } else {
                points.addAll(sampled.drop(1))
            }

            if (legIndex == 0 && from.waitTimeSeconds > 0) {
                holds[0] = from.waitTimeSeconds.coerceAtMost(Waypoint.MAX_WAIT_SECONDS)
            }
            val isLastStop = legIndex == waypoints.size - 2
            if (!isLastStop && to.waitTimeSeconds > 0) {
                holds[points.lastIndex] = to.waitTimeSeconds.coerceAtMost(Waypoint.MAX_WAIT_SECONDS)
            }
        }

        SimulatedRoute(points, holds, fallbackLegs, distance)
    }

    suspend fun fetchPolyline(
        start: LatLng,
        end: LatLng,
        mode: RouteMode
    ): RoutePolyline = withContext(Dispatchers.IO) {
        when (mode) {
            RouteMode.SIMPLE_LINE -> RoutePolyline(listOf(start, end))
            RouteMode.CAR_RIDE, RouteMode.WALKING ->
                fetchOsrmPolyline(start, end, mode) ?: RoutePolyline(listOf(start, end), isFallback = true)
        }
    }

    fun fetchOsrmPolyline(start: LatLng, end: LatLng, mode: RouteMode): RoutePolyline? {
        if (mode == RouteMode.SIMPLE_LINE) return RoutePolyline(listOf(start, end))

        val cacheKey = buildCacheKey(start, end, mode)
        routeCache[cacheKey]?.let { if (it.points.size >= 2) return it }

        // Always format with a dot decimal and no scientific notation.
        val coords = String.format(
            Locale.US, "%.6f,%.6f;%.6f,%.6f",
            start.longitude, start.latitude, end.longitude, end.latitude
        )
        val query = "?overview=full&geometries=polyline&annotations=speed"
        val urls = when (mode) {
            RouteMode.WALKING -> listOf(
                "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords$query",
                "https://router.project-osrm.org/route/v1/foot/$coords$query"
            )
            else -> listOf(
                "https://router.project-osrm.org/route/v1/driving/$coords$query",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coords$query"
            )
        }

        for (url in urls) {
            val res = Http.get(url)
            if (res is HttpResult.Ok) {
                val parsed = parseOsrmResponse(res.body)
                if (parsed != null) {
                    routeCache[cacheKey] = parsed
                    return parsed
                }
            }
        }
        return null
    }

    internal fun parseOsrmResponse(jsonString: String): RoutePolyline? {
        return try {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "Ok") return null
            val routes = root.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val firstRoute = routes.getJSONObject(0)

            val points = when (val geometry = firstRoute.opt("geometry")) {
                is String -> decodePolyline(geometry)
                is JSONObject -> {
                    val coordinates = geometry.optJSONArray("coordinates") ?: return null
                    (0 until coordinates.length()).map { i ->
                        val c = coordinates.getJSONArray(i)
                        LatLng(c.getDouble(1), c.getDouble(0))
                    }
                }
                else -> return null
            }
            if (points.size < 2) return null

            // Per-segment speeds from all legs, if present and consistent with the geometry.
            val speeds = ArrayList<Double>()
            val legs = firstRoute.optJSONArray("legs")
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val arr = legs.getJSONObject(l).optJSONObject("annotation")?.optJSONArray("speed") ?: continue
                    for (i in 0 until arr.length()) speeds += arr.optDouble(i, Double.NaN)
                }
            }
            val usableSpeeds = speeds.takeIf { it.size == points.size - 1 && it.none { s -> s.isNaN() } }

            RoutePolyline(points, usableSpeeds)
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

            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    fun polylineLength(points: List<LatLng>): Double =
        points.zipWithNext().sumOf { (a, b) -> computeDistanceMeters(a, b) }

    /** Constant-speed sampling (with slow-down before sharp turns), one point per second. */
    fun samplePolylineDynamically(
        polyline: List<LatLng>,
        baseSpeedKmH: Double
    ): List<LatLng> {
        if (polyline.size < 2) return polyline
        val index = PolylineIndex(polyline)
        val baseSpeedMps = baseSpeedKmH / 3.6

        if (index.totalDistance <= 0.0 || baseSpeedMps <= 0.0) {
            return listOf(polyline.first(), polyline.last())
        }

        val sampled = ArrayList<LatLng>()
        sampled.add(polyline.first())
        val random = Random()
        var distance = 0.0

        while (distance < index.totalDistance) {
            val nextTurn = index.distanceToNextTurn(distance, 30f)
            val speed = if (nextTurn < baseSpeedMps * 3) {
                baseSpeedMps * 0.45
            } else {
                baseSpeedMps * (1.0 + (random.nextDouble() * 0.10 - 0.05))
            }
            distance += speed
            if (distance >= index.totalDistance) {
                sampled.add(polyline.last())
                break
            }
            sampled.add(index.pointAt(distance))
        }
        return sampled
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
        val compassBearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
        return compassBearing.toFloat()
    }

    fun sampleSmartWalkPolyline(polyline: List<LatLng>): List<LatLng> {
        if (polyline.size < 2) return polyline
        val index = PolylineIndex(polyline)
        val total = index.totalDistance
        if (total <= 0.0) return polyline

        // Crosswalk / intersection waits at turns > 35°
        data class CrosswalkWait(val distanceMeters: Double, val waitDurationSec: Int)
        val crosswalkEvents = mutableListOf<CrosswalkWait>()
        val random = Random(42)

        for (i in 1 until polyline.size - 1) {
            if (index.turnAngles[i] > 35.0f) {
                val nodeDist = index.cumDistances[i]
                if (nodeDist > 15.0 && (total - nodeDist) > 15.0) {
                    val lastEventDist = crosswalkEvents.lastOrNull()?.distanceMeters ?: -1000.0
                    if (nodeDist - lastEventDist >= 30.0) {
                        crosswalkEvents.add(CrosswalkWait(nodeDist, 5 + random.nextInt(11)))
                    }
                }
            }
        }

        val sampled = ArrayList<LatLng>()
        sampled.add(polyline.first())

        var distance = 0.0
        var crosswalkIndex = 0
        var inCrosswalkWait = 0
        var briskCrossingRemainingMeters = 0.0
        var nextMicroPauseDist = 200.0 + random.nextDouble() * 150.0
        var inMicroPause = 0
        var stepCount = 0
        val maxSteps = 200_000

        while (distance < total && stepCount < maxSteps) {
            stepCount++

            if (inCrosswalkWait > 0) {
                inCrosswalkWait--
                sampled.add(index.pointAt(distance))
                if (inCrosswalkWait == 0) briskCrossingRemainingMeters = 12.0
                continue
            }

            if (inMicroPause > 0) {
                inMicroPause--
                sampled.add(index.pointAt(distance))
                continue
            }

            if (crosswalkIndex < crosswalkEvents.size) {
                val next = crosswalkEvents[crosswalkIndex]
                if ((abs(distance - next.distanceMeters) < 1.5 || distance >= next.distanceMeters) &&
                    distance < next.distanceMeters + 2.0
                ) {
                    distance = next.distanceMeters
                    inCrosswalkWait = next.waitDurationSec
                    crosswalkIndex++
                    sampled.add(index.pointAt(distance))
                    continue
                }
            }

            if (distance >= nextMicroPauseDist && (total - distance) > 30.0) {
                inMicroPause = 3 + random.nextInt(6)
                nextMicroPauseDist = distance + 200.0 + random.nextDouble() * 150.0
                sampled.add(index.pointAt(distance))
                continue
            }

            val nextTurnDist = index.distanceToNextTurn(distance, 25f)
            val targetSpeedKmH = when {
                briskCrossingRemainingMeters > 0.0 -> 5.8 + random.nextDouble() * 0.7
                nextTurnDist in 0.0..6.0 -> 3.2 + random.nextDouble() * 0.6
                else -> 4.5 + random.nextDouble() * 0.7
            }
            val stepDist = maxOf(0.4, targetSpeedKmH / 3.6 * (1.0 + (random.nextDouble() * 0.16 - 0.08)))
            distance += stepDist
            if (briskCrossingRemainingMeters > 0.0) briskCrossingRemainingMeters -= stepDist

            if (distance >= total) {
                sampled.add(polyline.last())
                break
            }

            // Lateral gait sway (~0.3 m)
            val basePt = index.pointAt(distance)
            val segBearing = index.bearings[index.segmentAt(distance)]
            val swayMeters = 0.3 * sin(stepCount * Math.PI * 0.5)
            val perpBearingRad = Math.toRadians((segBearing + 90.0) % 360.0)
            val deltaLat = (swayMeters * cos(perpBearingRad)) / 111000.0
            val deltaLng = (swayMeters * sin(perpBearingRad)) / (111000.0 * cos(Math.toRadians(basePt.latitude)))
            sampled.add(LatLng(basePt.latitude + deltaLat, basePt.longitude + deltaLng))
        }
        return sampled
    }

    /**
     * "Smart" sampling: accelerates from rest, slows for turns, stops at likely
     * traffic lights. When OSRM [speedsMps] are available they set the cruising
     * speed per segment; otherwise it's estimated from segment length.
     */
    fun sampleSmartPolyline(
        polyline: List<LatLng>,
        mode: RouteMode = RouteMode.CAR_RIDE,
        speedsMps: List<Double>? = null
    ): List<LatLng> {
        if (mode == RouteMode.WALKING) return sampleSmartWalkPolyline(polyline)
        if (polyline.size < 2) return polyline

        val index = PolylineIndex(polyline)
        val total = index.totalDistance
        if (total <= 0.0) return polyline

        val segmentRoadSpeedsKmH = DoubleArray(polyline.size - 1) { i ->
            val routed = speedsMps?.getOrNull(i)
            if (routed != null && routed > 0.5) {
                (routed * 3.6).coerceIn(10.0, 130.0)
            } else {
                val d = index.segmentDistances[i]
                when {
                    d > 150.0 -> 80.0 + ((d - 150.0) / 10.0).coerceAtMost(30.0)
                    d < 60.0 -> 30.0 + (d / 60.0) * 15.0
                    else -> 45.0 + ((d - 60.0) / 90.0) * 35.0
                }
            }
        }

        // Traffic light stops & major intersections
        data class StopEvent(val distanceMeters: Double, val durationSeconds: Int)
        val stopEvents = mutableListOf<StopEvent>()
        val random = Random(42)
        var urbanDistSinceStop = 0.0
        for (i in 1 until polyline.size - 1) {
            val dPrev = index.segmentDistances[i - 1]
            val nodeDist = index.cumDistances[i]
            if (dPrev < 80.0) urbanDistSinceStop += dPrev
            val isMajorIntersection = index.turnAngles[i] > 40.0f
            val isPeriodicUrbanStop = urbanDistSinceStop >= 450.0
            if ((isMajorIntersection || isPeriodicUrbanStop) && nodeDist > 50.0 && (total - nodeDist) > 50.0) {
                val lastStopDist = stopEvents.lastOrNull()?.distanceMeters ?: -1000.0
                if (nodeDist - lastStopDist >= 100.0) {
                    stopEvents.add(StopEvent(nodeDist, 12 + random.nextInt(14)))
                    urbanDistSinceStop = 0.0
                }
            }
        }

        val sampled = ArrayList<LatLng>()
        sampled.add(polyline.first())

        var distance = 0.0
        var speedMps = 0.0
        var stopEventIndex = 0
        var inStopCountdown = 0
        var activeStopDist = -1.0
        var isAccelerating = true
        var accelElapsed = 0.0
        var accelTotalSec = 6.0
        var isDecelerating = false
        var decelElapsed = 0.0
        var decelTotalSec = 5.0
        var decelStartSpeedMps = 0.0
        var stepCount = 0
        val maxSteps = 200_000

        while (distance < total && stepCount < maxSteps) {
            stepCount++

            if (inStopCountdown > 0) {
                inStopCountdown--
                sampled.add(index.pointAt(activeStopDist))
                if (inStopCountdown == 0) {
                    isAccelerating = true
                    accelElapsed = 0.0
                    accelTotalSec = 5.0 + random.nextDouble() * 2.0
                    speedMps = 0.0
                }
                continue
            }

            var targetSpeedMps = segmentRoadSpeedsKmH[index.segmentAt(distance)] / 3.6
            if (index.distanceToNextTurn(distance, 30f) in 0.0..30.0) {
                targetSpeedMps = minOf(targetSpeedMps, 20.0 / 3.6)
            }

            if (stopEventIndex < stopEvents.size && !isDecelerating) {
                val nextStop = stopEvents[stopEventIndex]
                val distToStop = nextStop.distanceMeters - distance
                if (distToStop in 0.0..((speedMps * 5.0 / 2.0) + 5.0)) {
                    isDecelerating = true
                    isAccelerating = false
                    decelStartSpeedMps = maxOf(speedMps, 5.0)
                    decelElapsed = 0.0
                    decelTotalSec = 4.0 + random.nextDouble() * 2.0
                    activeStopDist = nextStop.distanceMeters
                }
            }

            if (isDecelerating) {
                decelElapsed += 1.0
                val progress = (decelElapsed / decelTotalSec).coerceIn(0.0, 1.0)
                speedMps = decelStartSpeedMps * 0.5 * (1.0 + cos(Math.PI * progress))
                if (progress >= 1.0 || (distance + speedMps) >= activeStopDist) {
                    distance = activeStopDist
                    speedMps = 0.0
                    inStopCountdown = stopEvents[stopEventIndex].durationSeconds
                    stopEventIndex++
                    isDecelerating = false
                    sampled.add(index.pointAt(activeStopDist))
                    continue
                }
            } else if (isAccelerating) {
                accelElapsed += 1.0
                val progress = (accelElapsed / accelTotalSec).coerceIn(0.0, 1.0)
                speedMps = targetSpeedMps * 0.5 * (1.0 - cos(Math.PI * progress))
                if (progress >= 1.0) isAccelerating = false
            } else {
                speedMps = targetSpeedMps * (1.0 + (random.nextDouble() * 0.06 - 0.03))
            }

            distance += maxOf(0.5, speedMps)
            if (distance >= total) {
                sampled.add(polyline.last())
                break
            }
            sampled.add(index.pointAt(distance))
        }
        return sampled
    }

    /**
     * Precomputed distances/bearings for a polyline, with O(log n) lookups so
     * sampling long routes doesn't rescan the whole path every second.
     */
    private class PolylineIndex(val polyline: List<LatLng>) {
        val segmentDistances = DoubleArray(polyline.size - 1)
        val cumDistances = DoubleArray(polyline.size)
        val bearings = FloatArray(polyline.size - 1)
        /** Turn angle at each node (0 at the ends). */
        val turnAngles = FloatArray(polyline.size)
        private val nextTurnCache = HashMap<Float, IntArray>()

        init {
            for (i in 0 until polyline.size - 1) {
                segmentDistances[i] = computeDistanceMeters(polyline[i], polyline[i + 1])
                cumDistances[i + 1] = cumDistances[i] + segmentDistances[i]
                bearings[i] = calculateBearing(polyline[i], polyline[i + 1])
            }
            for (i in 1 until polyline.size - 1) {
                val diff = abs(bearings[i] - bearings[i - 1])
                turnAngles[i] = if (diff > 180f) 360f - diff else diff
            }
        }

        val totalDistance: Double get() = cumDistances.last()

        /** Index of the segment containing [d]. */
        fun segmentAt(d: Double): Int {
            if (d <= 0.0) return 0
            var lo = 0
            var hi = segmentDistances.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (cumDistances[mid] < d) lo = mid else hi = mid - 1
            }
            return lo
        }

        fun pointAt(d: Double): LatLng {
            if (d <= 0.0) return polyline.first()
            if (d >= totalDistance) return polyline.last()
            val seg = segmentAt(d)
            val len = segmentDistances[seg]
            val f = if (len > 0.0) ((d - cumDistances[seg]) / len).coerceIn(0.0, 1.0) else 0.0
            val p1 = polyline[seg]
            val p2 = polyline[seg + 1]
            return LatLng(
                p1.latitude + (p2.latitude - p1.latitude) * f,
                p1.longitude + (p2.longitude - p1.longitude) * f
            )
        }

        /** Distance from [d] to the next node with a turn sharper than [threshold], or MAX_VALUE. */
        fun distanceToNextTurn(d: Double, threshold: Float): Double {
            val next = nextTurnCache.getOrPut(threshold) {
                // next[i] = first node index >= i whose turn angle exceeds threshold, or -1
                IntArray(polyline.size).also { arr ->
                    var upcoming = -1
                    for (i in polyline.size - 1 downTo 0) {
                        if (turnAngles[i] > threshold) upcoming = i
                        arr[i] = upcoming
                    }
                }
            }
            val node = next[segmentAt(d) + 1]
            return if (node < 0) Double.MAX_VALUE else cumDistances[node] - d
        }
    }
}
