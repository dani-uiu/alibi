package com.alibi.spoofer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class NominatimResult(
    val primaryText: String,
    val secondaryText: String,
    val fullAddress: String,
    val lat: Double,
    val lon: Double
) {
    val latLng: LatLng get() = LatLng(lat, lon)
}

object NominatimSearch {

    suspend fun search(query: String): List<NominatimResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&extratags=1&namedetails=1&limit=10&accept-language=en"

        val results = mutableListOf<NominatimResult>()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Alibi/1.0 (Android; com.alibi.spoofer)")
            connection.setRequestProperty("Accept-Language", "en")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val inputStream = connection.inputStream
                val jsonText = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val lat = obj.optString("lat", "0.0").toDoubleOrNull() ?: 0.0
                    val lon = obj.optString("lon", "0.0").toDoubleOrNull() ?: 0.0

                    val addressObj = obj.optJSONObject("address")
                    val houseNumber = addressObj?.optString("house_number")?.ifBlank { null }
                        ?: addressObj?.optString("building")?.ifBlank { null }
                    val road = addressObj?.optString("road")?.ifBlank { null }
                        ?: addressObj?.optString("street")?.ifBlank { null }
                        ?: addressObj?.optString("pedestrian")?.ifBlank { null }
                        ?: addressObj?.optString("footway")?.ifBlank { null }

                    val city = addressObj?.optString("city")?.ifBlank { null }
                        ?: addressObj?.optString("town")?.ifBlank { null }
                        ?: addressObj?.optString("village")?.ifBlank { null }
                        ?: addressObj?.optString("suburb")?.ifBlank { null }
                        ?: addressObj?.optString("municipality")?.ifBlank { null }

                    val state = addressObj?.optString("state")?.ifBlank { null }
                        ?: addressObj?.optString("county")?.ifBlank { null }
                        ?: addressObj?.optString("region")?.ifBlank { null }

                    val country = addressObj?.optString("country")?.ifBlank { null }

                    val streetAddress = listOfNotNull(houseNumber, road).joinToString(" ").ifBlank { null }

                    val namedetails = obj.optJSONObject("namedetails")
                    val placeName = namedetails?.optString("name:en")?.ifBlank { null }
                        ?: namedetails?.optString("name")?.ifBlank { null }
                        ?: obj.optString("name").ifBlank { null }
                        ?: streetAddress
                        ?: obj.optString("display_name").split(",").firstOrNull()?.trim()
                        ?: "Unknown Location"

                    val primaryText = placeName

                    val addressComponents = listOfNotNull(
                        if (streetAddress != placeName) streetAddress else null,
                        city,
                        state,
                        country
                    ).distinct()

                    val secondaryText = if (addressComponents.isNotEmpty()) {
                        addressComponents.joinToString(", ")
                    } else {
                        obj.optString("display_name")
                    }

                    val fullAddress = listOfNotNull(
                        streetAddress,
                        city,
                        state,
                        country
                    ).distinct().joinToString(", ").ifBlank { obj.optString("display_name") }

                    results.add(NominatimResult(primaryText, secondaryText, fullAddress, lat, lon))
                }
            }
        } catch (_: Exception) {
            // Return empty list on network or parse error
        } finally {
            connection?.disconnect()
        }
        results
    }
}
