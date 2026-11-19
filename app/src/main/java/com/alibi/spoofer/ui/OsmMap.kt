package com.alibi.spoofer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.alibi.spoofer.createActiveMockLocationDrawable
import com.alibi.spoofer.createIntermediateRouteDrawable
import com.alibi.spoofer.createRealLocationDrawable
import com.alibi.spoofer.createRouteDestinationDrawable
import com.alibi.spoofer.createRouteStartDrawable
import com.alibi.spoofer.createSingleTargetDrawable
import com.alibi.spoofer.util.Http
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.SimulatedRoute
import com.alibi.spoofer.util.Waypoint
import com.alibi.spoofer.util.toGeoPoint
import com.alibi.spoofer.util.toLatLng
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.events.MapEventsReceiver
import java.io.File

/** Imperative handle so the screen can move the camera in response to ViewModel commands. */
class MapController internal constructor(private val map: MapView) {
    fun centerOn(latLng: LatLng, zoom: Double? = null) {
        map.controller.animateTo(latLng.toGeoPoint(), zoom ?: map.zoomLevelDouble, 600L)
    }

    fun fit(points: List<LatLng>) {
        if (points.size < 2) return
        try {
            val box = BoundingBox.fromGeoPoints(points.map { it.toGeoPoint() })
            // Pad so the route isn't hidden under the search bar or bottom sheet.
            map.post { map.zoomToBoundingBox(box.increaseByScale(1.35f), true) }
        } catch (_: Exception) {
            // Degenerate box (e.g. all points identical)
        }
    }

    val center: LatLng get() = (map.mapCenter as GeoPoint).toLatLng()
}

private class Overlays(
    val route: Polyline,
    val singleTarget: Marker,
    val mock: Marker,
    val real: Marker,
    val stops: MutableList<Marker> = mutableListOf()
)

@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMap(
    modifier: Modifier,
    mode: SpooferMode,
    singleTarget: LatLng?,
    waypoints: List<Waypoint>,
    route: SimulatedRoute,
    mockLocation: LatLng?,
    realLocation: LatLng?,
    onTap: (LatLng) -> Unit,
    onUserPan: () -> Unit,
    onCenterChanged: (LatLng) -> Unit,
    onReady: (MapController) -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

    val onTapState = rememberUpdatedState(onTap)
    val onUserPanState = rememberUpdatedState(onUserPan)
    val onCenterState = rememberUpdatedState(onCenterChanged)

    val icons = remember {
        object {
            val start = createRouteStartDrawable(context)
            val end = createRouteDestinationDrawable(context)
            val mid = createIntermediateRouteDrawable(context)
            val single = createSingleTargetDrawable(context)
            val mock = createActiveMockLocationDrawable(context)
            val real = createRealLocationDrawable(context)
        }
    }

    val mapView = remember {
        configureOsmDroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isHorizontalMapRepetitionEnabled = true
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 3.0
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(20.0, 0.0))
        }
    }

    val overlays = remember {
        fun marker(title: String, iconDrawable: android.graphics.drawable.Drawable) = Marker(mapView).apply {
            this.title = title
            icon = iconDrawable
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
            isEnabled = false
        }

        val tapReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onTapState.value(p.toLatLng()); return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                onTapState.value(p.toLatLng()); return true
            }
        }
        val routeLine = Polyline(mapView).apply {
            outlinePaint.color = 0xFF4A4FD1.toInt()
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            setOnClickListener { _, _, _ -> false }
            isEnabled = false
        }
        Overlays(
            route = routeLine,
            singleTarget = marker("Target", icons.single),
            mock = marker("Mock", icons.mock),
            real = marker("Real", icons.real)
        ).also {
            mapView.overlays.add(MapEventsOverlay(tapReceiver))
            mapView.overlays.add(it.route)
            mapView.overlays.add(it.real)
            mapView.overlays.add(it.singleTarget)
            mapView.overlays.add(it.mock)
        }
    }

    // Lifecycle, listeners, and cleanup
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        mapView.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                onCenterState.value((mapView.mapCenter as GeoPoint).toLatLng()); return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean = false
        }, 400))
        mapView.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_MOVE) onUserPanState.value()
            false
        }
        onReady(MapController(mapView))

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Dark mode: invert tiles so the map doesn't glare at night.
    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mapView.invalidate()
    }

    // Each overlay updates only when its own data changes.
    LaunchedEffect(route, mode) {
        val show = mode == SpooferMode.ROUTE && route.points.size >= 2
        if (show) overlays.route.setPoints(route.points.map { it.toGeoPoint() })
        overlays.route.isEnabled = show
        mapView.invalidate()
    }

    LaunchedEffect(waypoints, mode) {
        overlays.stops.forEach { mapView.overlays.remove(it) }
        overlays.stops.clear()
        if (mode == SpooferMode.ROUTE) {
            waypoints.forEachIndexed { i, wp ->
                val m = Marker(mapView).apply {
                    position = wp.location.toGeoPoint()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = when (i) {
                        0 -> icons.start
                        waypoints.lastIndex -> icons.end
                        else -> icons.mid
                    }
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                }
                // Insert below the live mock marker so it stays on top.
                mapView.overlays.add(mapView.overlays.indexOf(overlays.singleTarget).coerceAtLeast(0), m)
                overlays.stops.add(m)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(singleTarget, mode) {
        val show = mode == SpooferMode.SINGLE && singleTarget != null
        if (singleTarget != null) overlays.singleTarget.position = singleTarget.toGeoPoint()
        overlays.singleTarget.isEnabled = show
        mapView.invalidate()
    }

    LaunchedEffect(mockLocation) {
        if (mockLocation != null) overlays.mock.position = mockLocation.toGeoPoint()
        overlays.mock.isEnabled = mockLocation != null
        mapView.invalidate()
    }

    LaunchedEffect(realLocation) {
        if (realLocation != null) overlays.real.position = realLocation.toGeoPoint()
        overlays.real.isEnabled = realLocation != null
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}

private var osmConfigured = false

private fun configureOsmDroid(context: Context) {
    if (osmConfigured) return
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // OSM tile policy: identify the app honestly; no fake Referer.
    config.userAgentValue = Http.USER_AGENT
    config.cacheMapTileCount = 12
    val base = File(context.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
    osmConfigured = true
}
