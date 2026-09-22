package com.alibi.spoofer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.alibi.spoofer.service.LocationSpooferService
import com.alibi.spoofer.service.SpooferState
import com.alibi.spoofer.ui.theme.AlibiTheme
import com.alibi.spoofer.util.CustomLocation
import com.alibi.spoofer.util.CustomLocationRepository
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.NominatimResult
import com.alibi.spoofer.util.NominatimSearch
import com.alibi.spoofer.util.RouteCalculator
import com.alibi.spoofer.util.RouteMode
import com.alibi.spoofer.util.Waypoint
import com.alibi.spoofer.util.toGeoPoint
import com.alibi.spoofer.util.toLatLng
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

enum class SpooferMode {
    SINGLE,
    ROUTE
}

private class MapOverlaysHolder(
    val polyline: Polyline,
    val singleTarget: Marker,
    val routeStart: Marker,
    val routeEnd: Marker,
    val activeMock: Marker,
    val realLocation: Marker,
    val waypointsMarkers: MutableList<Marker> = mutableListOf()
)

private fun configureOsmDroid(context: Context) {
    val userAgent = "Alibi/1.0 (Android; com.alibi.spoofer)"
    val config = Configuration.getInstance()
    config.userAgentValue = userAgent
    config.cacheMapTileCount = 12
    config.isMapViewHardwareAccelerated = true

    val basePath = File(context.cacheDir, "osmdroid")
    if (!basePath.exists()) {
        basePath.mkdirs()
    }
    val tileCache = File(basePath, "tiles")
    if (!tileCache.exists()) {
        tileCache.mkdirs()
    }
    config.osmdroidBasePath = basePath
    config.osmdroidTileCache = tileCache

    config.additionalHttpRequestProperties["User-Agent"] = userAgent
    config.additionalHttpRequestProperties["Referer"] = "https://github.com/alibi/spoofer"

    config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmDroid(this)
        enableEdgeToEdge()
        setContent {
            AlibiTheme {
                SpooferApp()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun SpooferApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val spooferState by LocationSpooferService.spooferState.collectAsState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var selectedMode by remember { mutableStateOf(SpooferMode.SINGLE) }
    var singleTarget by remember { mutableStateOf<LatLng?>(null) }
    val waypoints = remember { mutableStateListOf<Waypoint>() }
    var calculatedRoute by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var isGlobalSmartDrive by remember { mutableStateOf(false) }

    // Control sheet expansion state
    var isSheetExpanded by remember { mutableStateOf(false) }

    // Real-time Camera Tracking (default false during spoofing so camera isn't locked)
    var isTrackingModeEnabled by remember { mutableStateOf(false) }
    var initialLocationSet by remember { mutableStateOf(false) }

    // Search Bar State (Nominatim Geocoding)
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSearchDropdown by remember { mutableStateOf(false) }

    // Custom Saved Locations State
    var customLocations by remember { mutableStateOf(CustomLocationRepository.getCustomLocations(context)) }
    var showSaveLocationDialog by remember { mutableStateOf(false) }

    var isMockAppSet by remember { mutableStateOf(true) }
    var hasLocationPermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) ||
            (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapViewRef.value?.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapViewRef.value?.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Remember custom marker drawables
    val routeStartIcon = remember(context) { createRouteStartDrawable(context) }
    val routeDestIcon = remember(context) { createRouteDestinationDrawable(context) }
    val singleTargetIcon = remember(context) { createSingleTargetDrawable(context) }
    val activeMockIcon = remember(context) { createActiveMockLocationDrawable(context) }
    val realLocationIcon = remember(context) { createRealLocationDrawable(context) }

    var realDeviceLocation by remember { mutableStateOf<LatLng?>(null) }

    val overlaysHolder = remember { mutableStateOf<MapOverlaysHolder?>(null) }

    // Check mock app permission status
    LaunchedEffect(Unit) {
        isMockAppSet = isMockLocationEnabled(context)
    }

    // Debounce Nominatim Location Search
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            delay(400)
            isSearching = true
            val results = NominatimSearch.search(searchQuery)
            searchResults = results
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    val centerMapOn: (LatLng, Double?) -> Unit = { latLng, zoom ->
        mapViewRef.value?.let { mv ->
            mv.controller.animateTo(latLng.toGeoPoint())
            if (zoom != null) {
                mv.controller.setZoom(zoom)
            }
        }
    }

    // Observe real device location via FusedLocationProviderClient
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000L
        ).setMinUpdateIntervalMillis(2000L).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLoc = result.lastLocation
                if (lastLoc != null) {
                    val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                    realDeviceLocation = latLng
                    if (!initialLocationSet) {
                        centerMapOn(latLng, 15.0)
                        initialLocationSet = true
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {}

        onDispose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
        }
    }

    val routeCalculationKey = waypoints.map {
        "${it.id}_${it.location.latitude}_${it.location.longitude}_${it.modeToNext}_${it.speedKmHToNext}_${it.isSmartDrive}_${it.waitTimeSeconds}"
    }

    LaunchedEffect(key1 = routeCalculationKey, key2 = isGlobalSmartDrive) {
        if (waypoints.size >= 2) {
            isCalculatingRoute = true
            val calculated = RouteCalculator.calculateComplexRoute(
                waypoints = waypoints.toList(),
                isSmartDrive = isGlobalSmartDrive || waypoints.any { it.isSmartDrive }
            )
            calculatedRoute = calculated
            isCalculatingRoute = false

            if (calculated.size >= 2) {
                mapViewRef.value?.let { mv ->
                    try {
                        val bbox = BoundingBox.fromGeoPoints(calculated.map { it.toGeoPoint() })
                        mv.zoomToBoundingBox(bbox, true, 100)
                    } catch (_: Exception) {}
                }
            }
        } else {
            calculatedRoute = emptyList()
        }
    }

    val updateRouteAsync: () -> Unit = {
        coroutineScope.launch {
            if (waypoints.size >= 2) {
                isCalculatingRoute = true
                val calculated = RouteCalculator.calculateComplexRoute(
                    waypoints = waypoints.toList(),
                    isSmartDrive = isGlobalSmartDrive || waypoints.any { it.isSmartDrive }
                )
                calculatedRoute = calculated
                isCalculatingRoute = false

                if (calculated.size >= 2) {
                    mapViewRef.value?.let { mv ->
                        try {
                            val bbox = BoundingBox.fromGeoPoints(calculated.map { it.toGeoPoint() })
                            mv.zoomToBoundingBox(bbox, true, 100)
                        } catch (_: Exception) {}
                    }
                }
            } else {
                calculatedRoute = emptyList()
            }
        }
    }

    val handleUseMyLocationAsStart: () -> Unit = {
        val myLoc = realDeviceLocation
        if (myLoc != null) {
            if (selectedMode == SpooferMode.ROUTE) {
                waypoints.add(0, Waypoint(location = myLoc))
                updateRouteAsync()
                centerMapOn(myLoc, null)
                Toast.makeText(context, "Added real location as start point", Toast.LENGTH_SHORT).show()
            } else {
                singleTarget = myLoc
                waypoints.clear()
                waypoints.add(Waypoint(location = myLoc))
                centerMapOn(myLoc, null)
                Toast.makeText(context, "Set single target to real location", Toast.LENGTH_SHORT).show()
            }
        } else if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        if (selectedMode == SpooferMode.ROUTE) {
                            waypoints.add(0, Waypoint(location = latLng))
                            updateRouteAsync()
                            centerMapOn(latLng, null)
                            Toast.makeText(context, "Added real location as start point", Toast.LENGTH_SHORT).show()
                        } else {
                            singleTarget = latLng
                            waypoints.clear()
                            waypoints.add(Waypoint(location = latLng))
                            centerMapOn(latLng, null)
                            Toast.makeText(context, "Set single target to real location", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Unable to get current location", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: SecurityException) {}
        } else {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val handlePointSelect: (LatLng) -> Unit = { latLng ->
        when (selectedMode) {
            SpooferMode.SINGLE -> {
                singleTarget = latLng
                waypoints.clear()
                waypoints.add(Waypoint(location = latLng))
                centerMapOn(latLng, null)
            }
            SpooferMode.ROUTE -> {
                waypoints.add(Waypoint(location = latLng))
                centerMapOn(latLng, null)
                updateRouteAsync()
            }
        }
    }

    val handlePointSelectRef = rememberUpdatedState(handlePointSelect)

    val handleClearRoute: () -> Unit = {
        waypoints.clear()
        singleTarget = null
        calculatedRoute = emptyList()
    }

    val handleMyLocationClick: () -> Unit = {
        isTrackingModeEnabled = true
        if (spooferState.isRunning && spooferState.currentLocation != null) {
            centerMapOn(spooferState.currentLocation!!, 15.0)
        } else if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        centerMapOn(LatLng(loc.latitude, loc.longitude), 15.0)
                    } else {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { curr ->
                                if (curr != null) {
                                    centerMapOn(LatLng(curr.latitude, curr.longitude), 15.0)
                                }
                            }
                    }
                }
            } catch (_: SecurityException) {}
        }
    }

    val onStartSingleSpoofing: (LatLng) -> Unit = { target ->
        isTrackingModeEnabled = false
        val intent = Intent(context, LocationSpooferService::class.java).apply {
            action = LocationSpooferService.ACTION_START_SINGLE
            putExtra(LocationSpooferService.EXTRA_LATITUDE, target.latitude)
            putExtra(LocationSpooferService.EXTRA_LONGITUDE, target.longitude)
        }
        startServiceSafe(context, intent)
    }

    val onStartRouteSpoofing: (List<LatLng>, Float) -> Unit = { route, speed ->
        isTrackingModeEnabled = false
        val lats = route.map { it.latitude }.toDoubleArray()
        val lngs = route.map { it.longitude }.toDoubleArray()
        val isSmart = isGlobalSmartDrive || waypoints.any { it.isSmartDrive }
        val intent = Intent(context, LocationSpooferService::class.java).apply {
            action = LocationSpooferService.ACTION_START_ROUTE
            putExtra(LocationSpooferService.EXTRA_ROUTE_LATS, lats)
            putExtra(LocationSpooferService.EXTRA_ROUTE_LNGS, lngs)
            putExtra(LocationSpooferService.EXTRA_SPEED_KMH, speed.toDouble())
            putExtra(LocationSpooferService.EXTRA_IS_SMART_DRIVE, isSmart)
        }
        startServiceSafe(context, intent)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // OpenStreetMap (OsmDroid) View - Maintained Overlays (No overlay clear/recreate for 60fps performance)
            
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    configureOsmDroid(ctx)
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        setDestroyMode(false)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        isHorizontalMapRepetitionEnabled = true
                        isVerticalMapRepetitionEnabled = false
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        controller.setZoom(14.0)
                        controller.setCenter(LatLng(37.7749, -122.4194).toGeoPoint())
                        mapViewRef.value = this

                        val mapEventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                handlePointSelectRef.value(p.toLatLng())
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                handlePointSelectRef.value(p.toLatLng())
                                return true
                            }
                        }
                        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)

                        val polylineOverlay = Polyline(this).apply {
                            outlinePaint.color = android.graphics.Color.parseColor("#1976D2")
                            outlinePaint.strokeWidth = 12f
                            isEnabled = false
                        }

                        val singleTargetMarker = Marker(this).apply {
                            title = context.getString(R.string.teleport)
                            icon = singleTargetIcon
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isEnabled = false
                        }

                        val routeStartMarker = Marker(this).apply {
                            title = context.getString(R.string.start_route)
                            icon = routeStartIcon
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isEnabled = false
                        }

                        val routeEndMarker = Marker(this).apply {
                            title = context.getString(R.string.route)
                            icon = routeDestIcon
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isEnabled = false
                        }

                        val activeMockMarker = Marker(this).apply {
                            title = "Active Mock Location"
                            icon = activeMockIcon
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isEnabled = false
                        }

                        val realLocationMarker = Marker(this).apply {
                            title = "Real Device Location"
                            icon = realLocationIcon
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isEnabled = false
                        }

                        overlays.add(mapEventsOverlay)
                        overlays.add(polylineOverlay)
                        overlays.add(routeStartMarker)
                        overlays.add(routeEndMarker)
                        overlays.add(singleTargetMarker)
                        overlays.add(activeMockMarker)
                        overlays.add(realLocationMarker)

                        overlaysHolder.value = MapOverlaysHolder(
                            polyline = polylineOverlay,
                            singleTarget = singleTargetMarker,
                            routeStart = routeStartMarker,
                            routeEnd = routeEndMarker,
                            activeMock = activeMockMarker,
                            realLocation = realLocationMarker
                        )
                    }
                },
                update = { mapView ->
                    val holder = overlaysHolder.value ?: return@AndroidView

                    // Update Polyline for Route Simulation
                    if (selectedMode == SpooferMode.ROUTE && calculatedRoute.isNotEmpty()) {
                        holder.polyline.setPoints(calculatedRoute.map { it.toGeoPoint() })
                        holder.polyline.isEnabled = true
                    } else {
                        holder.polyline.isEnabled = false
                    }

                    // Update Single Target Marker
                    if (selectedMode == SpooferMode.SINGLE && singleTarget != null) {
                        val target = singleTarget!!
                        holder.singleTarget.position = target.toGeoPoint()
                        holder.singleTarget.snippet = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", target.latitude, target.longitude)
                        holder.singleTarget.icon = singleTargetIcon
                        holder.singleTarget.isEnabled = true
                    } else {
                        holder.singleTarget.isEnabled = false
                    }

                    // Update Real Device Location Marker
                    if (realDeviceLocation != null) {
                        val realLoc = realDeviceLocation!!
                        holder.realLocation.position = realLoc.toGeoPoint()
                        holder.realLocation.snippet = String.format(Locale.US, "Real Device Location\nLat: %.5f, Lng: %.5f", realLoc.latitude, realLoc.longitude)
                        holder.realLocation.icon = realLocationIcon
                        holder.realLocation.isEnabled = true
                    } else {
                        holder.realLocation.isEnabled = false
                    }

                    // Update Route Markers
                    holder.waypointsMarkers.forEach { mapView.overlays.remove(it) }
                    holder.waypointsMarkers.clear()
                    holder.routeStart.isEnabled = false
                    holder.routeEnd.isEnabled = false

                    if (selectedMode == SpooferMode.ROUTE) {
                        val intIcon = createIntermediateRouteDrawable(context)
                        waypoints.forEachIndexed { index, wp ->
                            val marker = Marker(mapView).apply {
                                position = wp.location.toGeoPoint()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = when {
                                    index == 0 -> routeStartIcon
                                    index == waypoints.size - 1 -> routeDestIcon
                                    else -> intIcon
                                }
                                title = if (index == 0) "Start" else if (index == waypoints.size - 1) "Destination" else "Stop ${index}"
                                isEnabled = true
                            }
                            mapView.overlays.add(marker)
                            holder.waypointsMarkers.add(marker)
                        }
                    }

                    // Update Active Mock Location Marker
                    if (spooferState.isRunning && spooferState.currentLocation != null) {
                        val activeLoc = spooferState.currentLocation!!
                        holder.activeMock.position = activeLoc.toGeoPoint()
                        holder.activeMock.snippet = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", activeLoc.latitude, activeLoc.longitude)
                        holder.activeMock.icon = activeMockIcon
                        holder.activeMock.isEnabled = true
                    } else {
                        holder.activeMock.isEnabled = false
                    }

                    mapView.invalidate()
                }
            )

            // Top Floating Search Bar
            Card(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                if (query.trim().length >= 2) {
                                    isSearching = true
                                    showSearchDropdown = true
                                } else {
                                    searchResults = emptyList()
                                    showSearchDropdown = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_place), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        showSearchDropdown = false
                                    }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        // Developer Options Quick Access Button
                        IconButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.DeveloperMode,
                                contentDescription = "Developer Options",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Search Results Suggestion Dropdown
                    if (showSearchDropdown && searchResults.isNotEmpty()) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            searchResults.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val target = result.latLng
                                            handlePointSelect(target)
                                            searchQuery = result.primaryText
                                            showSearchDropdown = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.primaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = result.fullAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else if (showSearchDropdown && isSearching && searchResults.isEmpty()) {
                        HorizontalDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            // Right-Side Floating Action Buttons (Positioned at bottom right above bottom control sheet)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 120.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // "My Location / Track Location" FAB Button
                SmallFloatingActionButton(
                    onClick = handleMyLocationClick,
                    containerColor = if (isTrackingModeEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (isTrackingModeEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isTrackingModeEnabled) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                        contentDescription = "My Location / Track Location"
                    )
                }
            }

            // Collapsible Bottom Control Sheet
            ControlCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                spooferState = spooferState,
                isSheetExpanded = isSheetExpanded,
                onToggleExpand = { isSheetExpanded = !isSheetExpanded },
                selectedMode = selectedMode,
                singleTarget = singleTarget,
                waypoints = waypoints,
                calculatedRoute = calculatedRoute,
                isCalculatingRoute = isCalculatingRoute,
                isMockAppSet = isMockAppSet,
                customLocations = customLocations,
                isGlobalSmartDrive = isGlobalSmartDrive,
                onGlobalSmartDriveToggle = { nextState ->
                    isGlobalSmartDrive = nextState
                    waypoints.indices.forEach { i ->
                        waypoints[i] = waypoints[i].copy(isSmartDrive = nextState)
                    }
                    updateRouteAsync()
                },
                onModeSelected = { mode -> selectedMode = mode },
                onSelectLocation = { latLng -> handlePointSelect(latLng) },
                onUseMyLocationAsStart = handleUseMyLocationAsStart,
                onWaypointUpdated = { updateRouteAsync() },
                onClearRoute = handleClearRoute,
                onOpenSaveLocationDialog = { showSaveLocationDialog = true },
                onDeleteCustomLocation = { id ->
                    CustomLocationRepository.deleteCustomLocation(context, id)
                    customLocations = CustomLocationRepository.getCustomLocations(context)
                    Toast.makeText(context, "Location removed", Toast.LENGTH_SHORT).show()
                },
                onStartSingleSpoofing = onStartSingleSpoofing,
                onStartRouteSpoofing = onStartRouteSpoofing,
                onStopSpoofing = {
                    val intent = Intent(context, LocationSpooferService::class.java).apply {
                        action = LocationSpooferService.ACTION_STOP
                    }
                    context.startService(intent)
                },
                onOpenDeveloperSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    context.startActivity(intent)
                }
            )

            // Save Custom Location Dialog
            if (showSaveLocationDialog) {
                val currentTarget = singleTarget ?: waypoints.firstOrNull()?.location

                SaveLocationDialog(
                    initialLat = currentTarget?.latitude ?: 37.7749,
                    initialLng = currentTarget?.longitude ?: -122.4194,
                    onDismiss = { showSaveLocationDialog = false },
                    onSave = { name, emoji, lat, lng ->
                        val newLoc = CustomLocation(name = name, emoji = emoji, lat = lat, lng = lng)
                        CustomLocationRepository.saveCustomLocation(context, newLoc)
                        customLocations = CustomLocationRepository.getCustomLocations(context)
                        showSaveLocationDialog = false
                        Toast.makeText(context, "Saved location $emoji $name!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun ControlCard(
    modifier: Modifier = Modifier,
    spooferState: SpooferState,
    isSheetExpanded: Boolean,
    onToggleExpand: () -> Unit,
    selectedMode: SpooferMode,
    singleTarget: LatLng?,
    waypoints: SnapshotStateList<Waypoint>,
    calculatedRoute: List<LatLng>,
    isCalculatingRoute: Boolean,
    isMockAppSet: Boolean,
    customLocations: List<CustomLocation>,
    isGlobalSmartDrive: Boolean = false,
    onGlobalSmartDriveToggle: (Boolean) -> Unit = {},
    onModeSelected: (SpooferMode) -> Unit,
    onSelectLocation: (LatLng) -> Unit,
    onUseMyLocationAsStart: () -> Unit,
    onWaypointUpdated: () -> Unit,
    onClearRoute: () -> Unit,
    onOpenSaveLocationDialog: () -> Unit,
    onDeleteCustomLocation: (String) -> Unit,
    onStartSingleSpoofing: (LatLng) -> Unit,
    onStartRouteSpoofing: (List<LatLng>, Float) -> Unit,
    onStopSpoofing: () -> Unit,
    onOpenDeveloperSettings: () -> Unit
) {
    var customLatInput by remember { mutableStateOf("") }
    var customLngInput by remember { mutableStateOf("") }
    var isManualCoordsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            // Drag Handle Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            // Peek Bar Header (Visible in both collapsed & expanded states)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (spooferState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Mock Location & Route Spoofer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (spooferState.isRunning && spooferState.currentLocation != null) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.4f, %.4f",
                                    spooferState.currentLocation.latitude,
                                    spooferState.currentLocation.longitude
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = if (selectedMode == SpooferMode.SINGLE) "Single Teleport" else "Route Simulation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Action Button in Peek mode
                    if (!isSheetExpanded) {
                        if (spooferState.isRunning) {
                            Button(
                                onClick = onStopSpoofing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop Spoofer", fontSize = 12.sp)
                            }
                        } else if (waypoints.size == 1 || (waypoints.isEmpty() && singleTarget != null)) {
                            val target = waypoints.firstOrNull()?.location ?: singleTarget!!
                            Button(
                                onClick = { onStartSingleSpoofing(target) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Teleport Here", fontSize = 12.sp)
                            }
                        } else if (waypoints.size > 1) {
                            Button(
                                onClick = {
                                    val routePoints = if (calculatedRoute.isNotEmpty()) calculatedRoute else waypoints.map { it.location }
                                    val initialSpeed = waypoints.firstOrNull()?.speedKmHToNext?.toFloat() ?: 50f
                                    onStartRouteSpoofing(routePoints, initialSpeed)
                                },
                                enabled = !isCalculatingRoute,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Route", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {},
                                enabled = false,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Point", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isSheetExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = if (isSheetExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expanded Bottom Control Sheet Contents
            AnimatedVisibility(visible = isSheetExpanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Developer Options Warning Notice
                    if (spooferState.errorMessage != null || !isMockAppSet) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = spooferState.errorMessage
                                            ?: "Please set Mock Location App in Developer Options.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenDeveloperSettings,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Open Settings", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Mode Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == SpooferMode.SINGLE,
                            onClick = { onModeSelected(SpooferMode.SINGLE) },
                            label = { Text(stringResource(R.string.single), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        FilterChip(
                            selected = selectedMode == SpooferMode.ROUTE,
                            onClick = { onModeSelected(SpooferMode.ROUTE) },
                            label = { Text(stringResource(R.string.route), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Custom Locations Manager Section
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Locations:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = onOpenSaveLocationDialog,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(stringResource(R.string.save_location), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (customLocations.isEmpty()) {
                            Text(
                                text = "No custom locations saved yet. Tap '+ Save Location' to add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                customLocations.forEach { loc ->
                                    AssistChip(
                                        onClick = {
                                            onSelectLocation(loc.latLng)
                                            customLatInput = loc.lat.toString()
                                            customLngInput = loc.lng.toString()
                                        },
                                        label = { Text("${loc.emoji} ${loc.name}", fontSize = 12.sp) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { onDeleteCustomLocation(loc.id) },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Delete Location",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Toggleable Manual Lat/Lng Section (Accordion)
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isManualCoordsExpanded = !isManualCoordsExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manual Coordinates " + (if (isManualCoordsExpanded) "▴" else "▾"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = isManualCoordsExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customLatInput,
                                    onValueChange = { customLatInput = it },
                                    label = { Text(stringResource(R.string.latitude), fontSize = 12.sp) },
                                    placeholder = { Text("37.7749", fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = customLngInput,
                                    onValueChange = { customLngInput = it },
                                    label = { Text(stringResource(R.string.longitude), fontSize = 12.sp) },
                                    placeholder = { Text("-122.4194", fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val lat = customLatInput.toDoubleOrNull()
                                        val lng = customLngInput.toDoubleOrNull()
                                        if (lat != null && lng != null) {
                                            onSelectLocation(LatLng(lat, lng))
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.set_location), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Mode Specific Controls
                    when (selectedMode) {
                        SpooferMode.SINGLE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (singleTarget != null) {
                                            String.format(
                                                Locale.US,
                                                "Target: %.5f, %.5f",
                                                singleTarget.latitude,
                                                singleTarget.longitude
                                            )
                                        } else {
                                            "💡 Search location or tap on map"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )

                                    AssistChip(
                                        onClick = onUseMyLocationAsStart,
                                        label = { Text("Use My Location", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.MyLocation,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }

                                Button(
                                    onClick = {
                                        singleTarget?.let { onStartSingleSpoofing(it) }
                                    },
                                    enabled = singleTarget != null && !spooferState.isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.teleport))
                                }
                            }
                        }

                        SpooferMode.ROUTE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Global Speed & Smart Drive Control
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val firstWp = waypoints.firstOrNull()
                                        val firstWpSpeed = firstWp?.speedKmHToNext?.toFloat() ?: 50f
                                        val firstWpMode = firstWp?.modeToNext ?: RouteMode.CAR_RIDE
                                        val isSmartDriveActive = isGlobalSmartDrive || waypoints.any { it.isSmartDrive }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Route Speed Mode:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (isSmartDriveActive) {
                                                    getSmartDescriptionForMode(firstWpMode)
                                                } else {
                                                    "${firstWpSpeed.toInt()} km/h"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSmartDriveActive) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Slider(
                                                value = firstWpSpeed.coerceIn(10f, 120f),
                                                onValueChange = { newSpeed ->
                                                    waypoints.indices.forEach { i ->
                                                        waypoints[i] = waypoints[i].copy(speedKmHToNext = newSpeed.toDouble())
                                                    }
                                                    onWaypointUpdated()
                                                },
                                                valueRange = 10f..120f,
                                                enabled = !isSmartDriveActive,
                                                modifier = Modifier.weight(1f)
                                            )

                                            FilterChip(
                                                selected = isSmartDriveActive,
                                                onClick = {
                                                    onGlobalSmartDriveToggle(!isSmartDriveActive)
                                                },
                                                label = { Text(if (isSmartDriveActive) getSmartTitleForMode(firstWpMode) else "Auto Speed", fontSize = 11.sp) },
                                                leadingIcon = { Text(getSmartIconForMode(firstWpMode), fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Waypoints (${waypoints.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(
                                            onClick = onUseMyLocationAsStart,
                                            label = { Text("Use My Location", fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.MyLocation,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                        if (waypoints.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            TextButton(
                                                onClick = onClearRoute,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                if (waypoints.isEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "💡 Search or tap map to add waypoints",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        waypoints.forEachIndexed { index, wp ->
                                            StopCard(
                                                index = index,
                                                totalWaypoints = waypoints.size,
                                                waypoint = wp,
                                                onWaitTimeChange = { newWait ->
                                                    waypoints[index] = wp.copy(waitTimeSeconds = newWait)
                                                    onWaypointUpdated()
                                                },
                                                onMoveUp = {
                                                    if (index > 0) {
                                                        val item = waypoints.removeAt(index)
                                                        waypoints.add(index - 1, item)
                                                        onWaypointUpdated()
                                                    }
                                                },
                                                onMoveDown = {
                                                    if (index < waypoints.size - 1) {
                                                        val item = waypoints.removeAt(index)
                                                        waypoints.add(index + 1, item)
                                                        onWaypointUpdated()
                                                    }
                                                },
                                                onRemove = {
                                                    waypoints.removeAt(index)
                                                    onWaypointUpdated()
                                                }
                                            )

                                            if (index < waypoints.size - 1) {
                                                val nextWp = waypoints[index + 1]
                                                ConnectingLegCard(
                                                    legIndex = index,
                                                    startWaypoint = wp,
                                                    endWaypoint = nextWp,
                                                    totalWaypoints = waypoints.size,
                                                    onModeChange = { newMode ->
                                                        val coercedSpeed = wp.speedKmHToNext.coerceIn(
                                                            newMode.minSpeedKmH.toDouble(),
                                                            newMode.maxSpeedKmH.toDouble()
                                                        )
                                                        waypoints[index] = wp.copy(modeToNext = newMode, speedKmHToNext = coercedSpeed)
                                                        onWaypointUpdated()
                                                    },
                                                    onSpeedChange = { newSpeed ->
                                                        waypoints[index] = wp.copy(speedKmHToNext = newSpeed)
                                                        onWaypointUpdated()
                                                    },
                                                    onSmartDriveChange = { isSmart ->
                                                        waypoints[index] = wp.copy(isSmartDrive = isSmart)
                                                        onWaypointUpdated()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = isCalculatingRoute) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Calculating route...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                if (calculatedRoute.isNotEmpty()) {
                                    Text(
                                        text = "Route Ready: ${calculatedRoute.size} step points",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onClearRoute,
                                        enabled = waypoints.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.Clear, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.clear_route))
                                    }

                                    Button(
                                        onClick = {
                                            if (waypoints.size == 1) {
                                                onStartSingleSpoofing(waypoints[0].location)
                                            } else if (calculatedRoute.isNotEmpty()) {
                                                val initialSpeed = waypoints.firstOrNull()?.speedKmHToNext?.toFloat() ?: 50f
                                                onStartRouteSpoofing(calculatedRoute, initialSpeed)
                                            }
                                        },
                                        enabled = (waypoints.size == 1 || calculatedRoute.isNotEmpty()) && !spooferState.isRunning && !isCalculatingRoute,
                                        modifier = Modifier.weight(2f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (waypoints.size == 1) "Teleport" else "Start Route")
                                    }
                                }
                            }
                        }
                    }

                    // Stop Spoofer Button (visible when running)
                    if (spooferState.isRunning) {
                        Button(
                            onClick = onStopSpoofing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.stop))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StopCard(
    index: Int,
    totalWaypoints: Int,
    waypoint: Waypoint,
    onWaitTimeChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWaitTimeDialog by remember { mutableStateOf(false) }

    val stopTitle = when {
        index == 0 -> "Start"
        index == totalWaypoints - 1 -> "Destination"
        else -> "Stop ${index + 1}"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Stop Name, Coordinates, Reorder (▲/▼) & Delete (X)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        index == 0 -> MaterialTheme.colorScheme.primary
                                        index == totalWaypoints - 1 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stopTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.4f, %.4f",
                            waypoint.location.latitude,
                            waypoint.location.longitude
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalWaypoints - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < totalWaypoints - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove Stop",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // Wait Time at this Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Wait Time at Stop:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = { showWaitTimeDialog = true },
                    label = {
                        Text(
                            text = if (waypoint.waitTimeSeconds > 0) "Wait ${formatWaitTime(waypoint.waitTimeSeconds)}" else "No Wait (0s)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (waypoint.waitTimeSeconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Wait Time",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }

    if (showWaitTimeDialog) {
        WaitTimeDialog(
            initialWaitTimeSeconds = waypoint.waitTimeSeconds,
            onDismiss = { showWaitTimeDialog = false },
            onConfirmWaitTime = { seconds ->
                onWaitTimeChange(seconds)
                showWaitTimeDialog = false
            }
        )
    }
}

@Composable
fun ConnectingLegCard(
    legIndex: Int,
    startWaypoint: Waypoint,
    endWaypoint: Waypoint,
    totalWaypoints: Int,
    onModeChange: (RouteMode) -> Unit,
    onSpeedChange: (Double) -> Unit,
    onSmartDriveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val startLabel = if (legIndex == 0) "Start" else "Stop ${legIndex + 1}"
    val endLabel = if (legIndex + 1 == totalWaypoints - 1) "Destination" else "Stop ${legIndex + 2}"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Leg Title "Leg 1: Stop 1 ➔ Stop 2"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.AltRoute,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Leg ${legIndex + 1}: $startLabel ➔ $endLabel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Travel Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Travel Mode:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RouteMode.entries.forEach { mode ->
                        val isSelected = startWaypoint.modeToNext == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onModeChange(mode) },
                            label = {
                                val modeLabel = when (mode) {
                                    RouteMode.CAR_RIDE -> "Car"
                                    RouteMode.WALKING -> "Walk"
                                    RouteMode.SIMPLE_LINE -> "Line"
                                }
                                Text(modeLabel, fontSize = 11.sp)
                            },
                            leadingIcon = {
                                val icon = when (mode) {
                                    RouteMode.CAR_RIDE -> Icons.Rounded.DirectionsCar
                                    RouteMode.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
                                    RouteMode.SIMPLE_LINE -> Icons.Rounded.LinearScale
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.displayName,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Speed Slider & Smart Drive / Smart Walk Toggle
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val minSpeed = startWaypoint.modeToNext.minSpeedKmH
                val maxSpeed = startWaypoint.modeToNext.maxSpeedKmH
                val currentSpeed = startWaypoint.speedKmHToNext.toFloat().coerceIn(minSpeed, maxSpeed)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Leg Speed:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (startWaypoint.isSmartDrive) getSmartDescriptionForMode(startWaypoint.modeToNext) else "${currentSpeed.toInt()} km/h",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (startWaypoint.isSmartDrive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Slider(
                        value = currentSpeed,
                        onValueChange = { newSpeed -> onSpeedChange(newSpeed.toDouble()) },
                        valueRange = minSpeed..maxSpeed,
                        enabled = !startWaypoint.isSmartDrive,
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = startWaypoint.isSmartDrive,
                        onClick = { onSmartDriveChange(!startWaypoint.isSmartDrive) },
                        label = { Text(if (startWaypoint.isSmartDrive) getSmartTitleForMode(startWaypoint.modeToNext) else "Auto Speed", fontSize = 10.sp) },
                        leadingIcon = { Text(getSmartIconForMode(startWaypoint.modeToNext), fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
        }
    }
}

fun formatWaitTime(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0s"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> {
            if (minutes == 0 && seconds == 0) {
                "${hours}h 00m"
            } else {
                String.format(Locale.US, "%dh %02dm", hours, minutes)
            }
        }
        minutes > 0 -> {
            if (seconds == 0) {
                "${minutes}m"
            } else {
                "${minutes}m ${seconds}s"
            }
        }
        else -> "${seconds}s"
    }
}

@Composable
fun WaitTimeDialog(
    initialWaitTimeSeconds: Int,
    onDismiss: () -> Unit,
    onConfirmWaitTime: (Int) -> Unit
) {
    var hoursText by remember {
        mutableStateOf((initialWaitTimeSeconds / 3600).let { if (it > 0) it.toString() else "" })
    }
    var minutesText by remember {
        mutableStateOf(((initialWaitTimeSeconds % 3600) / 60).let { if (it > 0) it.toString() else "" })
    }

    val presetValues = listOf(
        "0s" to 0,
        "1m" to 60,
        "5m" to 300,
        "15m" to 900,
        "30m" to 1800,
        "1h" to 3600,
        "1h 30m" to 5400,
        "3h" to 10800
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Stop Wait Time")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter stationary wait duration at this stop:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetValues.forEach { (label, secs) ->
                        AssistChip(
                            onClick = {
                                val hrs = secs / 3600
                                val mins = (secs % 3600) / 60
                                hoursText = if (hrs > 0) hrs.toString() else ""
                                minutesText = if (mins > 0) mins.toString() else ""
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hrs = hoursText.toIntOrNull() ?: 0
                    val mins = minutesText.toIntOrNull() ?: 0
                    val total = hrs * 3600 + mins * 60
                    onConfirmWaitTime(total.coerceAtLeast(0))
                }
            ) {
                Text("Set Wait Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SpeedInputDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onConfirmSpeed: (Float) -> Unit
) {
    var speedText by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentSpeed)) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Custom Speed")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter exact simulation speed in km/h:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = speedText,
                    onValueChange = {
                        speedText = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.speed)) },
                    placeholder = { Text("e.g. 65.5 or 80") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please enter a valid speed number greater than 0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = speedText.toFloatOrNull()
                    if (parsed != null && parsed > 0f) {
                        onConfirmSpeed(parsed)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Set Speed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SaveLocationDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏠") }
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.5f", initialLat)) }
    var lngText by remember { mutableStateOf(String.format(Locale.US, "%.5f", initialLng)) }

    val quickEmojis = listOf("🏠", "💼", "☕", "📍", "🏖️", "🚗", "🍔", "🎓", "🌲", "🏬")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Custom Location")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location Name") },
                    placeholder = { Text("e.g. Home, Work, Cafe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Choose Emoji:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickEmojis.forEach { em ->
                        FilterChip(
                            selected = emoji == em,
                            onClick = { emoji = em },
                            label = { Text(em, fontSize = 16.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("Emoji / Symbol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.latitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text(stringResource(R.string.longitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: initialLat
                    val lng = lngText.toDoubleOrNull() ?: initialLng
                    if (name.isNotBlank()) {
                        onSave(name.trim(), emoji.trim().ifEmpty { "📍" }, lat, lng)
                    }
                },
                enabled = name.isNotBlank() && latText.toDoubleOrNull() != null && lngText.toDoubleOrNull() != null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Custom Marker Drawables using Canvas & Paint

fun createRouteStartDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Emerald Green glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3310B981")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    // Emerald Green main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, mainPaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    // Crisp white core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    return BitmapDrawable(context.resources, bitmap)
}

fun createRouteDestinationDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Ruby Red glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#33EF4444")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    // Ruby Red main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#EF4444")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, mainPaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    // Crisp white core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    return BitmapDrawable(context.resources, bitmap)
}

fun createSingleTargetDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#30000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Indigo/Violet ring
    val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8B5CF6")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(cx, cy, 24f, outerRingPaint)

    // Fill Circle
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#7C3AED")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 16f, fillPaint)

    // Crisp White Core Dot
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 6f, corePaint)

    return BitmapDrawable(context.resources, bitmap)
}

fun createActiveMockLocationDrawable(context: Context): Drawable {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Semi-transparent outer halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#330091EA")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 34f, haloPaint)

    // Middle pulse ring
    val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#660091EA")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 22f, pulsePaint)

    // Electric Blue main core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0091EA")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 14f, corePaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(cx, cy, 14f, borderPaint)

    // White center pulse point
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 5f, centerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun getSmartTitleForMode(mode: RouteMode): String = when (mode) {
    RouteMode.WALKING -> "Smart Walk"
    RouteMode.CAR_RIDE -> "Smart Drive"
    RouteMode.SIMPLE_LINE -> "Smart Pace"
}

private fun getSmartIconForMode(mode: RouteMode): String = when (mode) {
    RouteMode.WALKING -> "🚶‍♂️"
    RouteMode.CAR_RIDE -> "🤖"
    RouteMode.SIMPLE_LINE -> "⚡"
}

private fun getSmartDescriptionForMode(mode: RouteMode): String = when (mode) {
    RouteMode.WALKING -> "Auto (Crosswalks, Phone Pauses & Natural Gait)"
    RouteMode.CAR_RIDE -> "Auto (Traffic Lights, Highway & City Speeds)"
    RouteMode.SIMPLE_LINE -> "Auto (Adaptive Pace)"
}

fun startServiceSafe(context: Context, intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun isMockLocationEnabled(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return try {
        @Suppress("DEPRECATION")
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}
