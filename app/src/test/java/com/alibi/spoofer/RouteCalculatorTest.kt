package com.alibi.spoofer

import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.RouteCalculator
import com.alibi.spoofer.util.RouteMode
import com.alibi.spoofer.util.RoutePolyline
import com.alibi.spoofer.util.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCalculatorTest {

    private val a = LatLng(37.7749, -122.4194)
    private val b = LatLng(37.7760, -122.4180)
    private val c = LatLng(37.7800, -122.4100)

    @Test
    fun routeModesDefaultsAndRanges() {
        assertEquals(10f, RouteMode.SIMPLE_LINE.minSpeedKmH)
        assertEquals(150f, RouteMode.SIMPLE_LINE.maxSpeedKmH)
        assertEquals(20f, RouteMode.CAR_RIDE.minSpeedKmH)
        assertEquals(120f, RouteMode.CAR_RIDE.maxSpeedKmH)
        assertEquals(1f, RouteMode.WALKING.minSpeedKmH)
        assertEquals(15f, RouteMode.WALKING.maxSpeedKmH)
    }

    @Test
    fun simpleLineRouteStartsAndEndsAtWaypoints() = runBlocking {
        val route = RouteCalculator.calculateComplexRoute(
            listOf(Waypoint(location = a, modeToNext = RouteMode.SIMPLE_LINE, speedKmHToNext = 50.0), Waypoint(location = c))
        )
        assertTrue(route.points.size > 2)
        assertEquals(a.latitude, route.points.first().latitude, 1e-4)
        assertEquals(c.latitude, route.points.last().latitude, 1e-4)
        assertTrue(route.fallbackLegs.isEmpty())
        assertTrue(route.distanceMeters > 900)
    }

    @Test
    fun pointsAreSpacedByRequestedSpeed() {
        // 36 km/h = 10 m/s → consecutive points about 10 m apart on a straight line.
        val sampled = RouteCalculator.samplePolylineDynamically(listOf(a, c), 36.0)
        val gaps = sampled.zipWithNext().dropLast(1).map { (p, q) -> RouteCalculator.computeDistanceMeters(p, q) }
        assertTrue(gaps.all { it in 9.0..11.0 })
    }

    @Test
    fun waitsAreHoldsNotRepeatedPoints() = runBlocking {
        val waypoints = listOf(
            Waypoint(location = a, modeToNext = RouteMode.SIMPLE_LINE, waitTimeSeconds = 30),
            Waypoint(location = b, modeToNext = RouteMode.SIMPLE_LINE, waitTimeSeconds = 3 * 3600),
            Waypoint(location = c, waitTimeSeconds = 600)
        )
        val route = RouteCalculator.calculateComplexRoute(waypoints)

        // A 3-hour wait must not add 10,800 points.
        assertTrue("route has ${route.points.size} points", route.points.size < 1000)
        assertEquals(30, route.holdsAtIndex[0])
        val bIndex = route.holdsAtIndex.entries.first { it.value == 3 * 3600 }.key
        assertEquals(b.latitude, route.points[bIndex].latitude, 1e-6)
        // The destination's wait is ignored: playback holds there anyway.
        assertFalse(route.holdsAtIndex.containsKey(route.points.lastIndex))
        assertEquals(route.points.size + 30 + 3 * 3600, route.durationSeconds)
    }

    @Test
    fun waitIsCappedAt24Hours() = runBlocking {
        val route = RouteCalculator.calculateComplexRoute(
            listOf(
                Waypoint(location = a, modeToNext = RouteMode.SIMPLE_LINE, waitTimeSeconds = 999 * 3600),
                Waypoint(location = c)
            )
        )
        assertEquals(Waypoint.MAX_WAIT_SECONDS, route.holdsAtIndex[0])
    }

    @Test
    fun smartDriveAndSmartWalkReachDestination() = runBlocking {
        for (mode in listOf(RouteMode.SIMPLE_LINE, RouteMode.WALKING)) {
            RouteCalculator.putInCache(
                RouteCalculator.buildCacheKey(a, c, mode),
                RoutePolyline(listOf(a, b, c))
            )
            val route = RouteCalculator.calculateComplexRoute(
                listOf(Waypoint(location = a, modeToNext = mode, isSmartDrive = true), Waypoint(location = c)),
                isSmartDrive = true
            )
            assertEquals(a.latitude, route.points.first().latitude, 1e-4)
            assertEquals(c.latitude, route.points.last().latitude, 1e-4)
        }
    }

    @Test
    fun smartDriveUsesRoutedSpeeds() {
        // A long straight road with an OSRM speed of 25 m/s (90 km/h).
        val start = LatLng(40.0, -3.0)
        val end = LatLng(40.0, -2.9) // ~8.5 km
        val sampled = RouteCalculator.sampleSmartPolyline(listOf(start, end), RouteMode.CAR_RIDE, listOf(25.0))
        val cruising = sampled.zipWithNext().drop(20).take(50).map { (p, q) -> RouteCalculator.computeDistanceMeters(p, q) }
        assertTrue(cruising.average() in 23.0..27.0)
    }

    @Test
    fun cachedRouteIsReused() = runBlocking {
        RouteCalculator.clearCache()
        val cached = RoutePolyline(listOf(a, b, c))
        RouteCalculator.putInCache(RouteCalculator.buildCacheKey(a, c, RouteMode.CAR_RIDE), cached)
        assertEquals(cached, RouteCalculator.fetchPolyline(a, c, RouteMode.CAR_RIDE))
    }

    @Test
    fun cacheKeyRoundsToFiveDecimals() {
        val key = RouteCalculator.buildCacheKey(LatLng(37.7749281, -122.4194182), LatLng(37.7849219, -122.4094111), RouteMode.CAR_RIDE)
        assertEquals("CAR_RIDE_37.77493_-122.41942_37.78492_-122.40941", key)
    }

    @Test
    fun decodePolyline() {
        val decoded = RouteCalculator.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 0.001)
        assertEquals(-120.2, decoded[0].longitude, 0.001)
        assertEquals(43.252, decoded[2].latitude, 0.001)
        assertEquals(-126.453, decoded[2].longitude, 0.001)
    }

    @Test
    fun parsesOsrmResponseWithSpeeds() {
        val json = """
            {"code":"Ok","routes":[{"geometry":"_p~iF~ps|U_ulLnnqC_mqNvxq`@",
              "legs":[{"annotation":{"speed":[10.5, 20.0]}}]}]}
        """.trimIndent()
        val parsed = RouteCalculator.parseOsrmResponse(json)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.points.size)
        assertEquals(listOf(10.5, 20.0), parsed.speedsMps)
    }

    @Test
    fun ignoresMismatchedOsrmSpeeds() {
        val json = """{"code":"Ok","routes":[{"geometry":"_p~iF~ps|U_ulLnnqC_mqNvxq`@","legs":[{"annotation":{"speed":[10.5]}}]}]}"""
        assertNull(RouteCalculator.parseOsrmResponse(json)!!.speedsMps)
    }

    @Test
    fun rejectsOsrmErrorResponse() {
        assertNull(RouteCalculator.parseOsrmResponse("""{"code":"NoRoute","routes":[]}"""))
    }

    @Test
    fun longRouteSamplingIsFast() {
        // ~5,000-node polyline: sampling used to rescan from the start each second.
        val poly = (0 until 5000).map { i -> LatLng(40.0 + i * 0.0002, -3.0 + (i % 2) * 0.0001) }
        val started = System.nanoTime()
        RouteCalculator.sampleSmartPolyline(poly, RouteMode.CAR_RIDE)
        RouteCalculator.sampleSmartWalkPolyline(poly)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $ms ms", ms < 3000)
    }
}
