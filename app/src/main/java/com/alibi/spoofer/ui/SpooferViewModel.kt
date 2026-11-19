package com.alibi.spoofer.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibi.spoofer.R
import com.alibi.spoofer.service.LocationSpooferService
import com.alibi.spoofer.service.RouteHandoff
import com.alibi.spoofer.service.SpooferState
import com.alibi.spoofer.util.CustomLocation
import com.alibi.spoofer.util.CustomLocationRepository
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.NetworkError
import com.alibi.spoofer.util.PlaceResult
import com.alibi.spoofer.util.PlaceSearch
import com.alibi.spoofer.util.RouteCalculator
import com.alibi.spoofer.util.RouteMode
import com.alibi.spoofer.util.SearchOutcome
import com.alibi.spoofer.util.SimulatedRoute
import com.alibi.spoofer.util.Waypoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpooferMode { SINGLE, ROUTE }

data class SearchUiState(
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: NetworkError? = null,
    /** True after an explicit "search" press, so "no results" can be shown confidently. */
    val submitted: Boolean = false,
    val isOpen: Boolean = false
)

data class UiState(
    val mode: SpooferMode = SpooferMode.SINGLE,
    val singleTarget: LatLng? = null,
    val singleTargetLabel: String? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val route: SimulatedRoute = SimulatedRoute.EMPTY,
    val isCalculatingRoute: Boolean = false,
    val routeError: Boolean = false,
    val isGlobalSmart: Boolean = false,
    val search: SearchUiState = SearchUiState(),
    val savedPlaces: List<CustomLocation> = emptyList(),
    val isMockAppSet: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val realLocation: LatLng? = null,
    val followLocation: Boolean = false
) {
    val isSmartActive: Boolean get() = isGlobalSmart || waypoints.any { it.isSmartDrive }

    /** Route is calculated, up to date, and can be played. */
    val canStartRoute: Boolean
        get() = waypoints.size >= 2 && !isCalculatingRoute && !route.isEmpty
}

/** One-off messages for the snackbar. */
data class UiMessage(
    @param:StringRes val text: Int,
    val args: List<Any> = emptyList(),
    @param:StringRes val actionLabel: Int? = null,
    val action: (() -> Unit)? = null
)

/** Something the screen should do that needs an Activity (camera moves, permission prompts…). */
sealed interface UiCommand {
    data class CenterOn(val latLng: LatLng, val zoom: Double? = null) : UiCommand
    data class FitRoute(val points: List<LatLng>) : UiCommand
    data object RequestLocationPermission : UiCommand
}

class SpooferViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val spooferState: StateFlow<SpooferState> = LocationSpooferService.spooferState

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _commands = Channel<UiCommand>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    private var routeJob: Job? = null
    private var searchJob: Job? = null

    /** Last known map centre, used to bias search toward what the user is looking at. */
    private var mapCenter: LatLng? = null

    init {
        _state.update { it.copy(savedPlaces = CustomLocationRepository.getCustomLocations(context)) }
        refreshSystemStatus()
    }

    // ---------------------------------------------------------------- system status

    /** Re-check permissions and the mock-location app setting (call on every resume). */
    fun refreshSystemStatus() {
        _state.update {
            it.copy(
                hasLocationPermission = hasLocationPermission(),
                isMockAppSet = isMockLocationAppSet()
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isMockLocationAppSet(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- map & location

    fun onMapCenterChanged(center: LatLng) {
        mapCenter = center
    }

    fun onRealLocation(latLng: LatLng) {
        val first = _state.value.realLocation == null
        _state.update { it.copy(realLocation = latLng) }
        if (first) send(UiCommand.CenterOn(latLng, 15.0))
    }

    fun setFollow(enabled: Boolean) {
        _state.update { it.copy(followLocation = enabled) }
        if (enabled) {
            val target = spooferState.value.currentLocation?.takeIf { spooferState.value.isRunning }
                ?: _state.value.realLocation
            if (target != null) send(UiCommand.CenterOn(target, 16.0))
            else if (!_state.value.hasLocationPermission) send(UiCommand.RequestLocationPermission)
        }
    }

    // ---------------------------------------------------------------- mode & points

    fun setMode(mode: SpooferMode) {
        _state.update { it.copy(mode = mode) }
    }

    /** Tap on map, search result, saved place, or typed coordinates. */
    fun selectPoint(latLng: LatLng, label: String? = null, center: Boolean = true) {
        when (_state.value.mode) {
            SpooferMode.SINGLE -> _state.update { it.copy(singleTarget = latLng, singleTargetLabel = label) }
            SpooferMode.ROUTE -> {
                val previous = _state.value.waypoints.lastOrNull()
                val wp = Waypoint(
                    location = latLng,
                    label = label,
                    // New stops inherit how the previous leg was being travelled.
                    modeToNext = previous?.modeToNext ?: RouteMode.CAR_RIDE,
                    speedKmHToNext = previous?.speedKmHToNext ?: RouteMode.CAR_RIDE.defaultSpeedKmH.toDouble(),
                    isSmartDrive = previous?.isSmartDrive ?: _state.value.isGlobalSmart
                )
                setWaypoints(_state.value.waypoints + wp)
            }
        }
        if (center) send(UiCommand.CenterOn(latLng))
    }

    fun useMyLocation() {
        val loc = _state.value.realLocation
        when {
            loc != null -> {
                if (_state.value.mode == SpooferMode.ROUTE) {
                    val wp = Waypoint(location = loc, label = context.getString(R.string.my_location))
                    setWaypoints(listOf(wp) + _state.value.waypoints)
                    send(UiCommand.CenterOn(loc))
                } else {
                    selectPoint(loc, context.getString(R.string.my_location))
                }
            }
            !_state.value.hasLocationPermission -> send(UiCommand.RequestLocationPermission)
            else -> message(R.string.msg_location_unavailable)
        }
    }

    fun clearTarget() {
        _state.update { it.copy(singleTarget = null, singleTargetLabel = null) }
    }

    fun clearRoute() {
        routeJob?.cancel()
        _state.update {
            it.copy(waypoints = emptyList(), route = SimulatedRoute.EMPTY, isCalculatingRoute = false, routeError = false)
        }
    }

    fun moveWaypoint(from: Int, to: Int) {
        val list = _state.value.waypoints.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        setWaypoints(list)
    }

    fun removeWaypoint(index: Int) {
        val list = _state.value.waypoints.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        setWaypoints(list)
    }

    fun setWaitTime(index: Int, seconds: Int) =
        editWaypoint(index) { it.copy(waitTimeSeconds = seconds.coerceIn(0, Waypoint.MAX_WAIT_SECONDS)) }

    fun setLegMode(index: Int, mode: RouteMode) = editWaypoint(index) {
        it.copy(
            modeToNext = mode,
            speedKmHToNext = it.speedKmHToNext.coerceIn(mode.minSpeedKmH.toDouble(), mode.maxSpeedKmH.toDouble())
        )
    }

    /** Called when a speed slider is released, not on every drag tick. */
    fun setLegSpeed(index: Int, kmh: Double) = editWaypoint(index) { it.copy(speedKmHToNext = kmh) }

    fun setLegSmart(index: Int, smart: Boolean) = editWaypoint(index) { it.copy(isSmartDrive = smart) }

    fun setAllSpeeds(kmh: Double) {
        setWaypoints(_state.value.waypoints.map {
            it.copy(speedKmHToNext = kmh.coerceIn(it.modeToNext.minSpeedKmH.toDouble(), it.modeToNext.maxSpeedKmH.toDouble()))
        })
    }

    fun setGlobalSmart(enabled: Boolean) {
        _state.update { it.copy(isGlobalSmart = enabled) }
        setWaypoints(_state.value.waypoints.map { it.copy(isSmartDrive = enabled) })
    }

    private fun editWaypoint(index: Int, transform: (Waypoint) -> Waypoint) {
        val list = _state.value.waypoints
        if (index !in list.indices) return
        val updated = transform(list[index])
        if (updated == list[index]) return
        setWaypoints(list.toMutableList().also { it[index] = updated })
    }

    private fun setWaypoints(list: List<Waypoint>) {
        _state.update { it.copy(waypoints = list) }
        scheduleRouteCalculation()
    }

    /**
     * One calculation at a time: any edit cancels the in-flight one, so a slow,
     * stale result can never overwrite a newer route.
     */
    private fun scheduleRouteCalculation() {
        routeJob?.cancel()
        val waypoints = _state.value.waypoints
        if (waypoints.size < 2) {
            _state.update { it.copy(route = SimulatedRoute.EMPTY, isCalculatingRoute = false, routeError = false) }
            return
        }
        _state.update { it.copy(isCalculatingRoute = true, routeError = false) }
        routeJob = viewModelScope.launch {
            delay(250) // coalesce bursts of edits
            try {
                val route = RouteCalculator.calculateComplexRoute(waypoints, _state.value.isGlobalSmart)
                _state.update { it.copy(route = route, isCalculatingRoute = false, routeError = route.isEmpty) }
                if (route.points.size >= 2) send(UiCommand.FitRoute(route.points))
                if (route.fallbackLegs.isNotEmpty()) message(R.string.msg_route_fallback)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(route = SimulatedRoute.EMPTY, isCalculatingRoute = false, routeError = true) }
            }
        }
    }

    fun retryRoute() = scheduleRouteCalculation()

    // ---------------------------------------------------------------- search

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        _state.update {
            it.copy(search = it.search.copy(query = query, isOpen = query.isNotBlank(), submitted = false, error = null))
        }
        if (query.trim().length < PlaceSearch.MIN_QUERY_LENGTH && !looksLikeCoordinates(query)) {
            _state.update { it.copy(search = it.search.copy(results = emptyList(), isLoading = false)) }
            return
        }
        _state.update { it.copy(search = it.search.copy(isLoading = true)) }
        searchJob = viewModelScope.launch {
            delay(350)
            applySearch(PlaceSearch.autocomplete(query, mapCenter), submitted = false)
        }
    }

    /** Keyboard "search" pressed: run the deeper search (also queries Nominatim). */
    fun onSearchSubmit() {
        val query = _state.value.search.query
        if (query.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(search = it.search.copy(isLoading = true, isOpen = true, error = null)) }
        searchJob = viewModelScope.launch {
            applySearch(PlaceSearch.search(query, mapCenter), submitted = true)
        }
    }

    private fun applySearch(outcome: SearchOutcome, submitted: Boolean) {
        _state.update {
            when (outcome) {
                is SearchOutcome.Results -> it.copy(
                    search = it.search.copy(results = outcome.places, isLoading = false, error = null, submitted = submitted)
                )
                is SearchOutcome.Failed -> it.copy(
                    search = it.search.copy(isLoading = false, error = outcome.error, submitted = submitted)
                )
            }
        }
    }

    fun onSearchResultPicked(result: PlaceResult) {
        selectPoint(result.latLng, result.primaryText)
        send(UiCommand.CenterOn(result.latLng, 16.0))
        _state.update { it.copy(search = SearchUiState()) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = it.search.copy(isOpen = false, isLoading = false)) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = SearchUiState()) }
    }

    private fun looksLikeCoordinates(q: String) = q.count { it.isDigit() } >= 4

    // ---------------------------------------------------------------- saved places

    fun savePlace(name: String, emoji: String, lat: Double, lng: Double) {
        CustomLocationRepository.saveCustomLocation(context, CustomLocation(name = name, emoji = emoji, lat = lat, lng = lng))
        _state.update { it.copy(savedPlaces = CustomLocationRepository.getCustomLocations(context)) }
        message(R.string.msg_place_saved, name)
    }

    fun deletePlace(place: CustomLocation) {
        val position = _state.value.savedPlaces.indexOfFirst { it.id == place.id }
        CustomLocationRepository.deleteCustomLocation(context, place.id)
        _state.update { it.copy(savedPlaces = CustomLocationRepository.getCustomLocations(context)) }
        _messages.trySend(
            UiMessage(R.string.msg_place_deleted, listOf(place.name), R.string.undo) {
                CustomLocationRepository.restoreCustomLocation(context, place, position)
                _state.update { it.copy(savedPlaces = CustomLocationRepository.getCustomLocations(context)) }
            }
        )
    }

    // ---------------------------------------------------------------- spoofing

    fun startSingle() {
        val target = _state.value.singleTarget ?: return
        if (!checkCanSpoof()) return
        val intent = Intent(context, LocationSpooferService::class.java)
            .setAction(LocationSpooferService.ACTION_START_SINGLE)
            .putExtra(LocationSpooferService.EXTRA_LATITUDE, target.latitude)
            .putExtra(LocationSpooferService.EXTRA_LONGITUDE, target.longitude)
        startService(intent)
    }

    fun startRoute() {
        val s = _state.value
        if (!s.canStartRoute) return
        if (!checkCanSpoof()) return
        RouteHandoff.put(s.route)
        startService(Intent(context, LocationSpooferService::class.java).setAction(LocationSpooferService.ACTION_START_ROUTE))
        _state.update { it.copy(followLocation = true) }
    }

    fun stop() {
        context.startService(
            Intent(context, LocationSpooferService::class.java).setAction(LocationSpooferService.ACTION_STOP)
        )
    }

    fun dismissSpooferError() = LocationSpooferService.clearError()

    private fun checkCanSpoof(): Boolean {
        refreshSystemStatus()
        val s = _state.value
        return when {
            !s.hasLocationPermission -> {
                send(UiCommand.RequestLocationPermission)
                false
            }
            !s.isMockAppSet -> {
                message(R.string.msg_set_mock_app)
                false
            }
            else -> true
        }
    }

    private fun startService(intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException if somehow called from background
            message(R.string.msg_service_failed)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun message(@StringRes text: Int, vararg args: Any) {
        _messages.trySend(UiMessage(text, args.toList()))
    }

    private fun send(command: UiCommand) {
        _commands.trySend(command)
    }
}
