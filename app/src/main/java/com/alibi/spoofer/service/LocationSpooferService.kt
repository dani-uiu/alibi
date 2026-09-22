package com.alibi.spoofer.service

import android.R
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
import com.alibi.spoofer.util.RouteCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.alibi.spoofer.util.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpooferState(
    val isRunning: Boolean = false,
    val currentLocation: LatLng? = null,
    val routePoints: List<LatLng> = emptyList(),
    val currentIndex: Int = 0,
    val speedKmH: Double = 50.0,
    val isRouteMode: Boolean = false,
    val isSmartDrive: Boolean = false,
    val errorMessage: String? = null
)

class LocationSpooferService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var tickerJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "App:LocationSpooferWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L) // 10 hours max timeout
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_SINGLE = "com.alibi.spoofer.START_SINGLE"
        const val ACTION_START_ROUTE = "com.alibi.spoofer.START_ROUTE"
        const val ACTION_STOP = "com.alibi.spoofer.STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_ROUTE_LATS = "extra_route_lats"
        const val EXTRA_ROUTE_LNGS = "extra_route_lngs"
        const val EXTRA_IS_SMART_DRIVE = "extra_is_smart_drive"

        const val NOTIFICATION_CHANNEL_ID = "location_spoofer_channel"
        const val NOTIFICATION_ID = 1001

        private val _spooferState = MutableStateFlow(SpooferState())
        val spooferState: StateFlow<SpooferState> = _spooferState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_SINGLE -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val target = LatLng(lat, lng)
                startSingleSpoofing(target)
            }
            ACTION_START_ROUTE -> {
                val lats = intent.getDoubleArrayExtra(EXTRA_ROUTE_LATS) ?: doubleArrayOf()
                val lngs = intent.getDoubleArrayExtra(EXTRA_ROUTE_LNGS) ?: doubleArrayOf()
                val speed = intent.getDoubleExtra(EXTRA_SPEED_KMH, 50.0)
                val isSmartDrive = intent.getBooleanExtra(EXTRA_IS_SMART_DRIVE, false)
                if (lats.isNotEmpty() && lats.size == lngs.size) {
                    val points = lats.indices.map { LatLng(lats[it], lngs[it]) }
                    startRouteSpoofing(points, speed, isSmartDrive)
                }
            }
            ACTION_STOP -> {
                stopSpoofing()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }

        return START_STICKY
    }

    private fun startSingleSpoofing(target: LatLng) {
        acquireWakeLock()
        val text = "Spoofing single location..."
        val notification = createNotification(text)
        startForegroundServiceWithNotification(notification)

        if (!setupMockProvider()) return

        tickerJob?.cancel()
        _spooferState.value = SpooferState(
            isRunning = true,
            currentLocation = target,
            routePoints = emptyList(),
            currentIndex = 0,
            isRouteMode = false,
            errorMessage = null
        )

        tickerJob = serviceScope.launch {
            while (true) {
                pushMockLocation(target, 0f, 0f)
                delay(1000)
            }
        }
    }

    private fun startRouteSpoofing(points: List<LatLng>, speedKmH: Double, isSmartDrive: Boolean = false) {
        if (points.isEmpty()) return
        acquireWakeLock()
        val labelText = "Route navigation active..."
        val notification = createNotification(labelText)
        startForegroundServiceWithNotification(notification)

        if (!setupMockProvider()) return

        tickerJob?.cancel()
        _spooferState.value = SpooferState(
            isRunning = true,
            currentLocation = points.first(),
            routePoints = points,
            currentIndex = 0,
            speedKmH = speedKmH,
            isRouteMode = true,
            isSmartDrive = isSmartDrive,
            errorMessage = null
        )

        val speedMps = (speedKmH * 1000.0 / 3600.0).toFloat()

        tickerJob = serviceScope.launch {
            var index = 0
            while (true) {
                val current = points[index]
                val next = points.getOrNull(index + 1) ?: points.first()
                val bearing = RouteCalculator.calculateBearing(current, next)
                val stepDistMeters = RouteCalculator.computeDistanceMeters(current, next)
                val calculatedSpeedMps = stepDistMeters.toFloat()
                val currentSpeedMps = if (isSmartDrive) calculatedSpeedMps else speedMps

                pushMockLocation(current, currentSpeedMps, bearing)

                _spooferState.value = _spooferState.value.copy(
                    currentLocation = current,
                    currentIndex = index
                )

                updateNotification("Route navigation active...")

                index = (index + 1) % points.size
                delay(1000)
            }
        }
    }

    @SuppressLint("MissingPermission", "WrongConstant")
    private fun setupMockProvider(): Boolean {
        return try {
            // Setup LocationManager GPS test provider
            try {
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,
                    true,
                    false,
                    false,
                    true,
                    true,
                    true,
                    1,
                    1
                )
            } catch (_: IllegalArgumentException) {
                // Test provider already exists, ignore
            }
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

            // Setup FusedLocationProviderClient mock mode
            fusedLocationClient.setMockMode(true)
            true
        } catch (e: SecurityException) {
            _spooferState.value = _spooferState.value.copy(
                isRunning = false,
                errorMessage = "Mock location permission not allowed. Please set Mock Location App in Developer Options."
            )
            stopSelf()
            false
        } catch (e: Exception) {
            _spooferState.value = _spooferState.value.copy(
                isRunning = false,
                errorMessage = e.localizedMessage ?: "Failed to set mock provider"
            )
            stopSelf()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun pushMockLocation(latLng: LatLng, speedMps: Float, bearing: Float) {
        // Micro GPS jitter / noise calculation
        val jitterLat = (Math.random() - 0.5) * 0.000012 // ~0.5 to 1.2 meters
        val jitterLng = (Math.random() - 0.5) * 0.000012
        val jitteredLat = latLng.latitude + jitterLat
        val jitteredLng = latLng.longitude + jitterLng

        val isStationary = speedMps <= 0.01f
        val actualSpeed = if (isStationary) {
            (Math.random() * 0.08).toFloat() // 0.0f..0.08f m/s
        } else {
            (speedMps + (Math.random() - 0.5) * 0.1).toFloat().coerceAtLeast(0.0f)
        }

        val actualAccuracy = (1.0 + Math.random() * 1.5).toFloat() // 1.0f..2.5f

        val actualBearing = if (isStationary) {
            (Math.random() * 360.0).toFloat() // 0.0f..360.0f
        } else {
            bearing
        }

        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = jitteredLat
            longitude = jitteredLng
            altitude = 10.0 + (Math.random() - 0.5) * 0.5
            accuracy = actualAccuracy
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
        } catch (_: Exception) {
            // Ignore individual push errors
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopSpoofing() {
        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()

        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            // Ignore removal errors if not registered
        }

        try {
            fusedLocationClient.setMockMode(false)
        } catch (_: Exception) {
            // Ignore
        }

        _spooferState.value = SpooferState(isRunning = false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundServiceWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mock Location Spoofer Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows mock location spoofer status"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationSpooferService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mock Location Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .addAction(R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        stopSpoofing()
        super.onDestroy()
    }
}
