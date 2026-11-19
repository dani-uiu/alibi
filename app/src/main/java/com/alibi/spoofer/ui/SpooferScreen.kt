package com.alibi.spoofer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alibi.spoofer.R
import com.alibi.spoofer.util.LatLng
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpooferScreen(vm: SpooferViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val spoofer by vm.spooferState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var map by remember { mutableStateOf<MapController?>(null) }
    var mapCenter by remember { mutableStateOf<LatLng?>(null) }
    var waitDialogFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var askedPermissions by rememberSaveable { mutableStateOf(false) }

    // ---- permissions & system status
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshSystemStatus() }

    val requestPermissions = {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!askedPermissions && !ui.hasLocationPermission) {
            askedPermissions = true
            requestPermissions()
        }
    }

    // Returning from Developer options / Settings: re-check, so the setup card updates.
    LifecycleResumeEffect(Unit) {
        vm.refreshSystemStatus()
        onPauseOrDispose { }
    }

    RealLocationUpdates(enabled = ui.hasLocationPermission, onLocation = vm::onRealLocation)

    // ---- ViewModel → screen
    LaunchedEffect(map) {
        val controller = map ?: return@LaunchedEffect
        vm.commands.collect { cmd ->
            when (cmd) {
                is UiCommand.CenterOn -> controller.centerOn(cmd.latLng, cmd.zoom)
                is UiCommand.FitRoute -> controller.fit(cmd.points)
                UiCommand.RequestLocationPermission -> requestPermissions()
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val text = resources.getString(msg.text, *msg.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = msg.actionLabel?.let { resources.getString(it) },
                duration = if (msg.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = msg.action == null
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    // Follow mode: keep the camera on the live (or real) location.
    LaunchedEffect(ui.followLocation, spoofer.currentLocation, ui.realLocation, spoofer.isRunning) {
        if (!ui.followLocation) return@LaunchedEffect
        val target = if (spoofer.isRunning) spoofer.currentLocation else ui.realLocation
        target?.let { map?.centerOn(it) }
    }

    // ---- layout
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    BackHandler(ui.search.isOpen) { vm.closeSearch() }
    BackHandler(!ui.search.isOpen && sheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.partialExpand() }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = if (headerHeightPx == 0) 140.dp else with(density) { headerHeightPx.toDp() } + navBarBottom

    val actions = remember(vm) {
        SheetActions(
            onModeChange = vm::setMode,
            onStartSingle = vm::startSingle,
            onStartRoute = vm::startRoute,
            onStop = vm::stop,
            onUseMyLocation = vm::useMyLocation,
            onClearTarget = vm::clearTarget,
            onClearRoute = vm::clearRoute,
            onRetryRoute = vm::retryRoute,
            onSavePlace = { showSaveDialog = true },
            onPickPlace = { vm.selectPoint(it.latLng, "${it.emoji} ${it.name}") },
            onDeletePlace = vm::deletePlace,
            onGrantPermission = { requestPermissions() },
            onOpenDeveloperOptions = { openDeveloperOptions(context) },
            onDismissError = vm::dismissSpooferError,
            onWaitClick = { waitDialogFor = it },
            onMoveStop = vm::moveWaypoint,
            onRemoveStop = vm::removeWaypoint,
            onLegMode = vm::setLegMode,
            onLegSpeed = vm::setLegSpeed,
            onLegSmart = vm::setLegSmart,
            onAllSpeeds = vm::setAllSpeeds,
            onGlobalSmart = vm::setGlobalSmart
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.85f

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetDragHandle = null,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            sheetShadowElevation = 12.dp,
            snackbarHost = { SnackbarHost(snackbar) },
            sheetContent = {
                Column(
                    Modifier
                        .heightIn(max = maxSheetHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(Modifier.onSizeChanged { headerHeightPx = it.height }) {
                        SheetHeader(ui, spoofer, actions)
                    }
                    SheetBody(ui, spoofer, actions)
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    Spacer(Modifier.padding(bottom = 16.dp))
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                OsmMap(
                    modifier = Modifier.fillMaxSize(),
                    mode = ui.mode,
                    singleTarget = ui.singleTarget,
                    waypoints = ui.waypoints,
                    route = ui.route,
                    mockLocation = spoofer.currentLocation.takeIf { spoofer.isRunning },
                    realLocation = ui.realLocation,
                    onTap = {
                        focusManager.clearFocus()
                        vm.closeSearch()
                        vm.selectPoint(it, center = false)
                    },
                    onUserPan = { if (ui.followLocation) vm.setFollow(false) },
                    onCenterChanged = {
                        mapCenter = it
                        vm.onMapCenterChanged(it)
                    },
                    onReady = { controller ->
                        map = controller
                        mapCenter = controller.center
                    }
                )

                SearchPanel(
                    state = ui.search,
                    mapCenter = mapCenter,
                    onQueryChange = vm::onSearchQueryChange,
                    onSubmit = vm::onSearchSubmit,
                    onPick = vm::onSearchResultPicked,
                    onClose = vm::closeSearch,
                    onClear = vm::clearSearch,
                    onOpenDeveloperOptions = { openDeveloperOptions(context) },
                    onOpenAbout = { showAbout = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                )

                FloatingActionButton(
                    onClick = { vm.setFollow(!ui.followLocation) },
                    containerColor = if (ui.followLocation) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (ui.followLocation) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                ) {
                    Icon(
                        if (ui.followLocation) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                        contentDescription = stringResource(
                            if (ui.followLocation) R.string.cd_stop_following else R.string.cd_follow_location
                        )
                    )
                }

                // OpenStreetMap attribution (required by the tile usage policy).
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = padding.calculateBottomPadding() + 8.dp)
                ) {
                    Text(
                        stringResource(R.string.osm_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    // ---- dialogs
    val waitIndex = waitDialogFor
    val waitStop = waitIndex?.let { ui.waypoints.getOrNull(it) }
    if (waitIndex != null && waitStop != null) {
        WaitTimeDialog(
            initialSeconds = waitStop.waitTimeSeconds,
            onDismiss = { waitDialogFor = null },
            onConfirm = {
                vm.setWaitTime(waitIndex, it)
                waitDialogFor = null
            }
        )
    }

    if (showSaveDialog) {
        val target = when (ui.mode) {
            SpooferMode.SINGLE -> ui.singleTarget
            SpooferMode.ROUTE -> ui.waypoints.lastOrNull()?.location
        } ?: mapCenter
        SaveLocationDialog(
            initial = target,
            initialName = if (ui.mode == SpooferMode.SINGLE) ui.singleTargetLabel else ui.waypoints.lastOrNull()?.label,
            onDismiss = { showSaveDialog = false },
            onSave = { name, emoji, lat, lng ->
                vm.savePlace(name, emoji, lat, lng)
                showSaveDialog = false
            }
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

/**
 * Opens Developer options. Some devices don't expose that screen (or crash on it)
 * until it's enabled, so fall back to "About phone" where Build number lives.
 */
private fun openDeveloperOptions(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try the next one
        } catch (_: SecurityException) {
            // try the next one
        }
    }
}

/**
 * Tracks the device's real position while the app is visible (started), and ignores
 * mock fixes, so the "real location" marker never shows the fake one.
 */
@SuppressLint("MissingPermission")
@Composable
private fun RealLocationUpdates(enabled: Boolean, onLocation: (LatLng) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onLocation)

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose { }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (!LocationCompat.isMock(loc)) callback(LatLng(loc.latitude, loc.longitude))
            }
        }
        var active = false
        fun start() {
            if (active) return
            try {
                client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                active = true
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && !LocationCompat.isMock(loc)) callback(LatLng(loc.latitude, loc.longitude))
                }
            } catch (_: SecurityException) {
                // Permission revoked
            }
        }
        fun stop() {
            if (!active) return
            client.removeLocationUpdates(locationCallback)
            active = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}
