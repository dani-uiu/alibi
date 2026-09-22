package com.alibi.spoofer

import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.RouteCalculator
import com.alibi.spoofer.util.RouteMode
import com.alibi.spoofer.util.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class RouteCalculatorTest {

    @Test
    fun testRouteModesDefaultAndRanges() {
        assertEquals("Simple Line", RouteMode.SIMPLE_LINE.displayName)
        assertEquals(50f, RouteMode.SIMPLE_LINE.defaultSpeedKmH)
        assertEquals(10f, RouteMode.SIMPLE_LINE.minSpeedKmH)
        assertEquals(150f, RouteMode.SIMPLE_LINE.maxSpeedKmH)

        assertEquals("Car Ride", RouteMode.CAR_RIDE.displayName)
        assertEquals(50f, RouteMode.CAR_RIDE.defaultSpeedKmH)
        assertEquals(20f, RouteMode.CAR_RIDE.minSpeedKmH)
        assertEquals(120f, RouteMode.CAR_RIDE.maxSpeedKmH)

        assertEquals("Walking", RouteMode.WALKING.displayName)
        assertEquals(5f, RouteMode.WALKING.defaultSpeedKmH)
        assertEquals(1f, RouteMode.WALKING.minSpeedKmH)
        assertEquals(15f, RouteMode.WALKING.maxSpeedKmH)
    }

    @Test
    fun testCalculateComplexRouteSimpleLine() = runBlocking {
        val start = LatLng(37.7749, -122.4194)
        val end = LatLng(37.7849, -122.4094)

        val waypoints = listOf(
            Waypoint(location = start, modeToNext = RouteMode.SIMPLE_LINE, speedKmHToNext = 50.0),
            Waypoint(location = end)
        )

        val route = RouteCalculator.calculateComplexRoute(waypoints)

        assertTrue(route.isNotEmpty())
        assertEquals(start.latitude, route.first().latitude, 0.0001)
        assertEquals(end.latitude, route.last().latitude, 0.0001)
    }

    @Test
    fun testSampleSmartPolyline() {
        val p1 = LatLng(37.7749, -122.4194)
        val p2 = LatLng(37.7760, -122.4180)
        val p3 = LatLng(37.7800, -122.4100)
        val polyline = listOf(p1, p2, p3)

        val sampled = RouteCalculator.sampleSmartPolyline(polyline)

        assertTrue(sampled.isNotEmpty())
        assertEquals(p1.latitude, sampled.first().latitude, 0.0001)
        assertEquals(p3.latitude, sampled.last().latitude, 0.0001)
    }

    @Test
    fun testCalculateComplexRouteWithSmartDrive() = runBlocking {
        val start = LatLng(37.7749, -122.4194)
        val end = LatLng(37.7849, -122.4094)

        val waypoints = listOf(
            Waypoint(location = start, modeToNext = RouteMode.SIMPLE_LINE, isSmartDrive = true),
            Waypoint(location = end)
        )

        val route = RouteCalculator.calculateComplexRoute(waypoints, isSmartDrive = true)

        assertTrue(route.isNotEmpty())
        assertEquals(start.latitude, route.first().latitude, 0.0001)
        assertEquals(end.latitude, route.last().latitude, 0.0001)
    }

    @Test
    fun testSampleSmartWalkPolyline() {
        val p1 = LatLng(37.7749, -122.4194)
        val p2 = LatLng(37.7760, -122.4180)
        val p3 = LatLng(37.7800, -122.4100)
        val polyline = listOf(p1, p2, p3)

        val sampled = RouteCalculator.sampleSmartWalkPolyline(polyline)

        assertTrue(sampled.isNotEmpty())
        assertEquals(p1.latitude, sampled.first().latitude, 0.0001)
        assertEquals(p3.latitude, sampled.last().latitude, 0.0001)
    }

    @Test
    fun testCalculateComplexRouteWalkingWithSmartDrive() = runBlocking {
        val start = LatLng(37.7749, -122.4194)
        val end = LatLng(37.7849, -122.4094)

        val waypoints = listOf(
            Waypoint(location = start, modeToNext = RouteMode.WALKING, isSmartDrive = true),
            Waypoint(location = end)
        )

        val route = RouteCalculator.calculateComplexRoute(waypoints, isSmartDrive = true)

        assertTrue(route.isNotEmpty())
        assertEquals(start.latitude, route.first().latitude, 0.0001)
        assertEquals(end.latitude, route.last().latitude, 0.0001)
    }

    @Test
    fun testDecodePolyline() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val decoded = RouteCalculator.decodePolyline(encoded)

        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 0.001)
        assertEquals(-120.2, decoded[0].longitude, 0.001)
        assertEquals(40.7, decoded[1].latitude, 0.001)
        assertEquals(-120.95, decoded[1].longitude, 0.001)
        assertEquals(43.252, decoded[2].latitude, 0.001)
        assertEquals(-126.453, decoded[2].longitude, 0.001)
    }

    @Test
    fun testWaitTimesAtStops() = runBlocking {
        val w1Loc = LatLng(37.7749, -122.4194)
        val w2Loc = LatLng(37.7760, -122.4180)
        val w3Loc = LatLng(37.7800, -122.4100)

        val waypoints = listOf(
            Waypoint(location = w1Loc, modeToNext = RouteMode.SIMPLE_LINE, waitTimeSeconds = 3),
            Waypoint(location = w2Loc, modeToNext = RouteMode.SIMPLE_LINE, waitTimeSeconds = 5),
            Waypoint(location = w3Loc, waitTimeSeconds = 2)
        )

        val route = RouteCalculator.calculateComplexRoute(waypoints)

        assertTrue(route.size > 10)
        assertEquals(w1Loc.latitude, route[0].latitude, 0.0001)
        assertEquals(w1Loc.latitude, route[1].latitude, 0.0001)
        assertEquals(w1Loc.latitude, route[2].latitude, 0.0001)
        assertEquals(w3Loc.latitude, route[route.size - 1].latitude, 0.0001)
        assertEquals(w3Loc.latitude, route[route.size - 2].latitude, 0.0001)
    }

    @Test
    fun testMixedSegmentModes() = runBlocking {
        val w1Loc = LatLng(37.7749, -122.4194)
        val w2Loc = LatLng(37.7760, -122.4180)
        val w3Loc = LatLng(37.7800, -122.4100)

        val waypoints = listOf(
            Waypoint(location = w1Loc, modeToNext = RouteMode.WALKING, speedKmHToNext = 5.0),
            Waypoint(location = w2Loc, modeToNext = RouteMode.SIMPLE_LINE, speedKmHToNext = 50.0),
            Waypoint(location = w3Loc)
        )

        val route = RouteCalculator.calculateComplexRoute(waypoints)

        assertTrue(route.isNotEmpty())
        assertEquals(w1Loc.latitude, route.first().latitude, 0.0001)
        assertEquals(w3Loc.latitude, route.last().latitude, 0.0001)
    }

    @Test
    fun testCacheKeyKeyGenerationAndRounding() {
        val start = LatLng(37.7749281, -122.4194182)
        val end = LatLng(37.7849219, -122.4094111)

        val key = RouteCalculator.buildCacheKey(start, end, RouteMode.CAR_RIDE)
        assertEquals("CAR_RIDE_37.77493_-122.41942_37.78492_-122.40941", key)
    }

    @Test
    fun testRouteCacheAndFallback() = runBlocking {
        RouteCalculator.clearCache()

        val start = LatLng(37.7749, -122.4194)
        val end = LatLng(37.7849, -122.4094)
        val key = RouteCalculator.buildCacheKey(start, end, RouteMode.CAR_RIDE)
        val mockPolyline = listOf(start, LatLng(37.7800, -122.4140), end)

        // When cache is empty and network is unavailable in unit test environment, fetchOsrmPolyline returns null
        RouteCalculator.fetchOsrmPolyline(start, end, RouteMode.CAR_RIDE)
        // Now populate cache as if a previous request succeeded
        val cacheField = RouteCalculator::class.java.getDeclaredField("routeCache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(RouteCalculator) as ConcurrentHashMap<String, List<LatLng>>
        cache[key] = mockPolyline

        // Fetch again - must return cached polyline immediately without network
        val fetchedFromCache = RouteCalculator.fetchOsrmPolyline(start, end, RouteMode.CAR_RIDE)
        assertEquals(mockPolyline, fetchedFromCache)

        val fetchedPolyline = RouteCalculator.fetchPolyline(start, end, RouteMode.CAR_RIDE)
        assertEquals(mockPolyline, fetchedPolyline)
    }
}
