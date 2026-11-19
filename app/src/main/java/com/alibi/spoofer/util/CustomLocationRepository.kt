package com.alibi.spoofer.util

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val lat: Double,
    val lng: Double,
    val isDefault: Boolean = false
) {
    val latLng: LatLng get() = LatLng(lat, lng)
}

object CustomLocationRepository {

    private const val PREFS_NAME = "custom_locations_prefs"
    private const val KEY_CUSTOM_LOCATIONS = "saved_locations_json"

    val DEFAULT_LOCATIONS: List<CustomLocation> = emptyList()

    fun getCustomLocations(context: Context): List<CustomLocation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_LOCATIONS, null) ?: return emptyList()
        val jsonArray = try {
            JSONArray(jsonStr)
        } catch (_: Exception) {
            return emptyList()
        }
        val list = mutableListOf<CustomLocation>()
        // Parse entries one by one so a single bad entry doesn't drop the rest.
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val lat = obj.getDouble("lat")
                val lng = obj.getDouble("lng")
                if (!CoordinateParser.isValid(lat, lng)) continue
                list.add(
                    CustomLocation(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        emoji = obj.optString("emoji", "📍"),
                        lat = lat,
                        lng = lng,
                        isDefault = false
                    )
                )
            } catch (_: Exception) {
                // skip malformed entry
            }
        }
        return list
    }

    fun saveCustomLocation(context: Context, location: CustomLocation) {
        val current = getCustomLocations(context).toMutableList()
        current.add(location)
        saveList(context, current)
    }

    /** Re-inserts a previously deleted location at its old position (for "Undo"). */
    fun restoreCustomLocation(context: Context, location: CustomLocation, position: Int) {
        val current = getCustomLocations(context).toMutableList()
        if (current.any { it.id == location.id }) return
        current.add(position.coerceIn(0, current.size), location)
        saveList(context, current)
    }

    fun deleteCustomLocation(context: Context, id: String) {
        val current = getCustomLocations(context).filterNot { it.id == id }
        saveList(context, current)
    }

    private fun saveList(context: Context, locations: List<CustomLocation>) {
        val jsonArray = JSONArray()
        locations.forEach { loc ->
            val obj = JSONObject().apply {
                put("id", loc.id)
                put("name", loc.name)
                put("emoji", loc.emoji)
                put("lat", loc.lat)
                put("lng", loc.lng)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_CUSTOM_LOCATIONS, jsonArray.toString())
        }
    }
}
