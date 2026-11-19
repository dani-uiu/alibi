package com.alibi.spoofer.ui

import androidx.annotation.StringRes
import com.alibi.spoofer.R
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.NetworkError
import java.util.Locale

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> String.format(Locale.getDefault(), "%d m", meters.toInt())
    meters < 100_000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000)
    else -> String.format(Locale.getDefault(), "%,d km", (meters / 1000).toInt())
}

/** "45 s", "12 min", "1 h 05 min". */
fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> String.format(Locale.getDefault(), "%d h %02d min", h, m)
        m > 0 -> String.format(Locale.getDefault(), "%d min", if (s >= 30) m + 1 else m)
        else -> String.format(Locale.getDefault(), "%d s", s)
    }
}

/** "1:05" or "1:02:05" countdown style. */
fun formatClock(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

/** Short wait label for chips: "5 min", "1 h 30 min", "45 s". */
fun formatWait(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 && m > 0 -> String.format(Locale.getDefault(), "%d h %d min", h, m)
        h > 0 -> String.format(Locale.getDefault(), "%d h", h)
        m > 0 -> String.format(Locale.getDefault(), "%d min", m)
        else -> String.format(Locale.getDefault(), "%d s", s)
    }
}

/** Coordinates are always shown with a dot decimal so they can be copied anywhere. */
fun formatCoords(latLng: LatLng, digits: Int = 5): String =
    String.format(Locale.US, "%.${digits}f, %.${digits}f", latLng.latitude, latLng.longitude)

@StringRes
fun NetworkError.message(): Int = when (this) {
    NetworkError.OFFLINE -> R.string.error_offline
    NetworkError.TIMEOUT -> R.string.error_timeout
    NetworkError.RATE_LIMITED -> R.string.error_rate_limited
    NetworkError.SERVER, NetworkError.BAD_RESPONSE -> R.string.error_server
}
