package com.alibi.spoofer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.alibi.spoofer.MainActivity
import com.alibi.spoofer.R
import com.alibi.spoofer.util.CoordinateParser
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.RouteCalculator
import com.alibi.spoofer.util.SimulatedRoute
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

enum class SpoofPhase { IDLE, PINNED, MOVING, WAITING, ARRIVED }

enum class SpoofError { MOCK_NOT_ALLOWED, PROVIDER_FAILED, NO_ROUTE }

data class SpooferState(
    val isRunning: Boolean = false,
    val phase: SpoofPhase = SpoofPhase.IDLE,
    val currentLocation: LatLng? = null,
    val isRouteMode: Boolean = false,
    val currentIndex: Int = 0,
    val totalPoints: Int = 0,
    /** Estimated seconds left in the route, including waits. */
    val secondsRemaining: Int = 0,
    /** Seconds left at the current stop while [phase] is WAITING. */
    val waitSecondsRemaining: Int = 0,
    val speedKmH: Double = 0.0,
    val error: SpoofError? = null
) {
    val progress: Float
        get() = if (totalPoints <= 1) 0f else (currentIndex.toFloat() / (totalPoints - 1)).coerceIn(0f, 1f)
}

/**
 * Passes a route from the UI to the service inside the same process.
 * Routes can be far bigger than the ~1 MB Binder limit, so they never go in an Intent.
 */
object RouteHandoff {
    private val pending = AtomicReference<SimulatedRoute?>(null)
    fun put(route: SimulatedRoute) = pending.set(route)
    fun take(): SimulatedRoute? = pending.getAndSet(null)
}

class LocationSpooferService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationText: String? = null

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_SINGLE = "com.alibi.spoofer.START_SINGLE"
        const val ACTION_START_ROUTE = "com.alibi.spoofer.START_ROUTE"
        const val ACTION_STOP = "com.alibi.spoofer.STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        const val NOTIFICATION_CHANNEL_ID = "location_spoofer_channel"
        const val NOTIFICATION_ID = 1001

        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 60 * 1000L

        private val _spooferState = MutableStateFlow(SpooferState())
        val spooferState: StateFlow<SpooferState> = _spooferState.asStateFlow()

        /** Lets the UI dismiss an error once it has been shown. */
        fun clearError() = _spooferState.update { it.copy(error = null) }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SINGLE -> {
                // Must call startForeground promptly after startForegroundService, even on bad input.
                goForeground(getString(R.string.notif_single))
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                if (CoordinateParser.isValid(lat, lng)) {
                    startSingleSpoofing(LatLng(lat, lng))
                } else {
                    shutDown(SpoofError.NO_ROUTE)
                }
            }
            ACTION_START_ROUTE -> {
                goForeground(getString(R.string.notif_route_starting))
                val route = RouteHandoff.take()
                if (route != null && !route.isEmpty) {
                    startRouteSpoofing(route)
                } else {
                    shutDown(SpoofError.NO_ROUTE)
                }
            }
            ACTION_STOP -> shutDown(null)
            else -> {
                // Restarted by the system without a command: nothing to resume.
                stopSelf()
            }
        }
        // Don't let the system restart us with a null intent; the UI starts us explicitly.
        return START_NOT_STICKY
    }

    private fun startSingleSpoofing(target: LatLng) {
        if (!setupMockProvider()) return
        acquireWakeLock()
        tickerJob?.cancel()
        _spooferState.value = SpooferState(
            isRunning = true,
            phase = SpoofPhase.PINNED,
            currentLocation = target
        )
        updateNotification(getString(R.string.notif_single))

        tickerJob = serviceScope.launch {
            while (isActive) {
                pushMockLocation(target, 0f, 0f)
                delay(1000)
            }
        }
    }

    private fun startRouteSpoofing(route: SimulatedRoute) {
        if (!setupMockProvider()) return
        acquireWakeLock()
        tickerJob?.cancel()

        val points = route.points
        _spooferState.value = SpooferState(
            isRunning = true,
            phase = SpoofPhase.MOVING,
            currentLocation = points.first(),
            isRouteMode = true,
            totalPoints = points.size,
            secondsRemaining = route.durationSeconds
        )

        tickerJob = serviceScope.launch {
            var remaining = route.durationSeconds
            var lastBearing = 0f

            for (index in points.indices) {
                val current = points[index]
                val next = points.getOrNull(index + 1)
                // Points are one second apart, so metres to the next point = m/s.
                val speedMps = next?.let { RouteCalculator.computeDistanceMeters(current, it) } ?: 0.0
                if (next != null && speedMps > 0.05) {
                    lastBearing = RouteCalculator.calculateBearing(current, next)
                }

                pushMockLocation(current, speedMps.toFloat(), lastBearing)
                remaining = (remaining - 1).coerceAtLeast(0)
                _spooferState.update {
                    it.copy(
                        phase = SpoofPhase.MOVING,
                        currentLocation = current,
                        currentIndex = index,
                        secondsRemaining = remaining,
                        waitSecondsRemaining = 0,
                        speedKmH = speedMps * 3.6
                    )
                }
                updateRouteNotification(_spooferState.value)
                delay(1000)

                val hold = route.holdsAtIndex[index] ?: 0
                for (left in hold downTo 1) {
                    pushMockLocation(current, 0f, lastBearing)
                    remaining = (remaining - 1).coerceAtLeast(0)
                    _spooferState.update {
                        it.copy(
                            phase = SpoofPhase.WAITING,
                            secondsRemaining = remaining,
                            waitSecondsRemaining = left,
                            speedKmH = 0.0
                        )
                    }
                    updateRouteNotification(_spooferState.value)
                    delay(1000)
                }
            }

            // Arrived: stay at the destination until the user stops.
            val destination = points.last()
            _spooferState.update {
                it.copy(
                    phase = SpoofPhase.ARRIVED,
                    currentLocation = destination,
                    currentIndex = points.lastIndex,
                    secondsRemaining = 0,
                    waitSecondsRemaining = 0,
                    speedKmH = 0.0
                )
            }
            updateNotification(getString(R.string.notif_arrived))
            while (isActive) {
                pushMockLocation(destination, 0f, lastBearing)
                delay(1000)
            }
        }
    }

    @SuppressLint("MissingPermission", "WrongConstant")
    private fun setupMockProvider(): Boolean {
        return try {
            try {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, true, false, false, true, true, true,
                    1, 1
                )
            } catch (_: IllegalArgumentException) {
                // Test provider already exists
            }
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            fusedLocationClient.setMockMode(true)
            true
        } catch (_: SecurityException) {
            shutDown(SpoofError.MOCK_NOT_ALLOWED)
            false
        } catch (_: Exception) {
            shutDown(SpoofError.PROVIDER_FAILED)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun pushMockLocation(latLng: LatLng, speedMps: Float, bearing: Float) {
        // Small GPS-like noise (~0.5–1.2 m)
        val jitterLat = (Math.random() - 0.5) * 0.000012
        val jitterLng = (Math.random() - 0.5) * 0.000012

        val isStationary = speedMps <= 0.01f
        val actualSpeed = if (isStationary) {
            (Math.random() * 0.08).toFloat()
        } else {
            (speedMps + (Math.random() - 0.5) * 0.1).toFloat().coerceAtLeast(0.0f)
        }
        val actualBearing = if (isStationary) (Math.random() * 360.0).toFloat() else bearing

        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = latLng.latitude + jitterLat
            longitude = latLng.longitude + jitterLng
            altitude = 10.0 + (Math.random() - 0.5) * 0.5
            accuracy = (1.0 + Math.random() * 1.5).toFloat()
            speed = actualSpeed
            this.bearing = actualBearing
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 1.0f
                speedAccuracyMetersPerSecond = 0.1f
                bearingAccuracyDegrees = 0.1f
            }
        }

        try {
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
            fusedLocationClient.setMockLocation(mockLocation)
        } catch (_: SecurityException) {
            // Mock-location permission was revoked while running.
            serviceScope.launch(Dispatchers.Main) { shutDown(SpoofError.MOCK_NOT_ALLOWED) }
        } catch (_: Exception) {
            // Ignore a single failed push
        }
    }

    /** Stops spoofing, removes the test provider and the notification, and ends the service. */
    @SuppressLint("MissingPermission")
    private fun shutDown(error: SpoofError?) {
        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()
        removeMockProvider()
        _spooferState.value = SpooferState(isRunning = false, error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun removeMockProvider() {
        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            // Not registered
        }
        try {
            fusedLocationClient.setMockMode(false)
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Alibi:LocationSpooferWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun goForeground(text: String) {
        val notification = buildNotification(text)
        lastNotificationText = text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateRouteNotification(state: SpooferState) {
        val minutesLeft = (state.secondsRemaining + 59) / 60
        val percent = (state.progress * 100).toInt()
        val text = if (state.phase == SpoofPhase.WAITING) {
            getString(R.string.notif_waiting, formatClock(state.waitSecondsRemaining))
        } else {
            getString(R.string.notif_route_progress, percent, minutesLeft)
        }
        updateNotification(text)
    }

    /** Only re-posts the notification when its text actually changes. */
    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatClock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LocationSpooferService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        removeMockProvider()
        if (_spooferState.value.isRunning) {
            _spooferState.value = SpooferState(isRunning = false)
        }
        super.onDestroy()
    }
}
