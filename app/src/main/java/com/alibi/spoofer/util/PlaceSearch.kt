package com.alibi.spoofer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

enum class PlaceKind { COORDINATE, ADDRESS, STREET, CITY, REGION, POI }

data class PlaceResult(
    val primaryText: String,
    val secondaryText: String,
    val latLng: LatLng,
    val kind: PlaceKind = PlaceKind.POI
)

sealed interface SearchOutcome {
    data class Results(val places: List<PlaceResult>) : SearchOutcome
    data class Failed(val error: NetworkError) : SearchOutcome
}

/**
 * Place search built on two OpenStreetMap geocoders:
 *
 *  - **Photon** (photon.komoot.io) for search-as-you-type. It's designed for
 *    autocomplete, matches partial words ("eiffel tow"), and accepts a location
 *    bias so nearby places rank first.
 *  - **Nominatim** only when the user explicitly submits, as its usage policy
 *    forbids autocomplete. Used as a deeper second opinion and merged in.
 *
 * Typed coordinates and map links are recognised locally with no network call.
 */
object PlaceSearch {

    const val MIN_QUERY_LENGTH = 3

    /** Quick, as-you-type search. [near] biases results toward the map's current view. */
    suspend fun autocomplete(query: String, near: LatLng?): SearchOutcome =
        withContext(Dispatchers.IO) {
            coordinateResult(query)?.let { return@withContext SearchOutcome.Results(listOf(it)) }
            if (query.trim().length < MIN_QUERY_LENGTH) return@withContext SearchOutcome.Results(emptyList())
            photon(query, near)
        }

    /** Full search after the user presses "search": Photon + Nominatim, de-duplicated. */
    suspend fun search(query: String, near: LatLng?): SearchOutcome = withContext(Dispatchers.IO) {
        coordinateResult(query)?.let { return@withContext SearchOutcome.Results(listOf(it)) }
        if (query.isBlank()) return@withContext SearchOutcome.Results(emptyList())

        val photon = photon(query, near)
        val nominatim = nominatim(query, near)

        val merged = buildList {
            (photon as? SearchOutcome.Results)?.let { addAll(it.places) }
            (nominatim as? SearchOutcome.Results)?.let { addAll(it.places) }
        }.distinctBy { "${it.primaryText.lowercase()}_${(it.latLng.latitude * 1000).toInt()}_${(it.latLng.longitude * 1000).toInt()}" }

        when {
            merged.isNotEmpty() -> SearchOutcome.Results(rankByDistance(merged, near))
            photon is SearchOutcome.Failed && nominatim is SearchOutcome.Failed -> photon
            else -> SearchOutcome.Results(emptyList())
        }
    }

    private fun coordinateResult(query: String): PlaceResult? {
        val coords = CoordinateParser.parse(query) ?: return null
        return PlaceResult(
            primaryText = String.format(Locale.US, "%.6f, %.6f", coords.latitude, coords.longitude),
            secondaryText = "",
            latLng = coords,
            kind = PlaceKind.COORDINATE
        )
    }

    private fun photon(query: String, near: LatLng?): SearchOutcome {
        val url = buildString {
            append("https://photon.komoot.io/api/?limit=10&q=")
            append(URLEncoder.encode(query.trim(), "UTF-8"))
            photonLanguage()?.let { append("&lang=").append(it) }
            if (near != null) {
                append(String.format(Locale.US, "&lat=%.5f&lon=%.5f&location_bias_scale=0.3", near.latitude, near.longitude))
            }
        }
        return when (val res = Http.get(url)) {
            is HttpResult.Failed -> SearchOutcome.Failed(res.error)
            is HttpResult.Ok -> parsePhoton(res.body)?.let { SearchOutcome.Results(it) }
                ?: SearchOutcome.Failed(NetworkError.BAD_RESPONSE)
        }
    }

    private val nominatimLock = Any()
    private var lastNominatimAt = 0L

    /** Nominatim allows at most one request per second per application. */
    private fun waitForNominatimSlot() = synchronized(nominatimLock) {
        val wait = lastNominatimAt + 1100 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastNominatimAt = System.currentTimeMillis()
    }

    private fun nominatim(query: String, near: LatLng?): SearchOutcome {
        waitForNominatimSlot()
        val url = buildString {
            append("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=10&q=")
            append(URLEncoder.encode(query.trim(), "UTF-8"))
            if (near != null) {
                // Prefer (but don't restrict to) a ~1° box around the map view.
                append(
                    String.format(
                        Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=0",
                        near.longitude - 1, near.latitude + 1, near.longitude + 1, near.latitude - 1
                    )
                )
            }
        }
        return when (val res = Http.get(url, acceptLanguage = Locale.getDefault().toLanguageTag())) {
            is HttpResult.Failed -> SearchOutcome.Failed(res.error)
            is HttpResult.Ok -> parseNominatim(res.body)?.let { SearchOutcome.Results(it) }
                ?: SearchOutcome.Failed(NetworkError.BAD_RESPONSE)
        }
    }

    /** Photon only supports a few result languages; otherwise let it return local names. */
    private fun photonLanguage(): String? =
        Locale.getDefault().language.takeIf { it in setOf("en", "de", "fr") }

    private fun rankByDistance(places: List<PlaceResult>, near: LatLng?): List<PlaceResult> {
        if (near == null) return places
        // Keep the geocoders' relevance order, but pull clearly-local results up a little.
        return places.withIndex().sortedBy { (i, p) ->
            val km = RouteCalculator.computeDistanceMeters(near, p.latLng) / 1000.0
            i + when {
                km < 25 -> -3.0
                km < 200 -> -1.0
                else -> 0.0
            }
        }.map { it.value }
    }

    // --- Parsing (internal for tests) ---

    internal fun parsePhoton(json: String): List<PlaceResult>? = try {
        val features = JSONObject(json).optJSONArray("features") ?: JSONArray()
        (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val lng = coords.optDouble(0)
            val lat = coords.optDouble(1)
            if (!CoordinateParser.isValid(lat, lng)) return@mapNotNull null
            val p = f.optJSONObject("properties") ?: JSONObject()

            val street = listOfNotNull(p.str("street"), p.str("housenumber")).joinToString(" ").ifBlank { null }
            val city = p.str("city") ?: p.str("town") ?: p.str("village") ?: p.str("locality") ?: p.str("district")
            val name = p.str("name") ?: street ?: city ?: return@mapNotNull null

            val secondary = listOfNotNull(
                street.takeIf { it != name },
                city.takeIf { it != name },
                p.str("state").takeIf { it != name },
                p.str("country").takeIf { it != name }
            ).distinct().joinToString(", ")

            PlaceResult(name, secondary, LatLng(lat, lng), photonKind(p))
        }
    } catch (_: Exception) {
        null
    }

    internal fun parseNominatim(json: String): List<PlaceResult>? = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            if (!CoordinateParser.isValid(lat, lng)) return@mapNotNull null
            val a = o.optJSONObject("address") ?: JSONObject()

            val street = listOfNotNull(
                a.str("road") ?: a.str("pedestrian") ?: a.str("footway"),
                a.str("house_number")
            ).joinToString(" ").ifBlank { null }
            val city = a.str("city") ?: a.str("town") ?: a.str("village") ?: a.str("suburb") ?: a.str("municipality")
            val displayFirst = o.optString("display_name").substringBefore(',').trim().ifBlank { null }
            val name = o.str("name") ?: street ?: displayFirst ?: return@mapNotNull null

            val secondary = listOfNotNull(
                street.takeIf { it != name },
                city.takeIf { it != name },
                (a.str("state") ?: a.str("county")).takeIf { it != name },
                a.str("country").takeIf { it != name }
            ).distinct().joinToString(", ")

            val kind = when (o.optString("addresstype")) {
                "house", "building" -> PlaceKind.ADDRESS
                "road" -> PlaceKind.STREET
                "city", "town", "village", "suburb", "neighbourhood", "municipality" -> PlaceKind.CITY
                "state", "country", "county", "region" -> PlaceKind.REGION
                else -> PlaceKind.POI
            }
            PlaceResult(name, secondary, LatLng(lat, lng), kind)
        }
    } catch (_: Exception) {
        null
    }

    private fun photonKind(p: JSONObject): PlaceKind = when (p.optString("type")) {
        "house" -> if (p.has("housenumber") && !p.has("name")) PlaceKind.ADDRESS else PlaceKind.POI
        "street" -> PlaceKind.STREET
        "city", "district", "locality" -> PlaceKind.CITY
        "county", "state", "country" -> PlaceKind.REGION
        else -> PlaceKind.POI
    }

    private fun JSONObject.str(key: String): String? =
        optString(key, "").trim().ifEmpty { null }
}
