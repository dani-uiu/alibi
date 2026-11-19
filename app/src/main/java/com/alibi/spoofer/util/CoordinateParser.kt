package com.alibi.spoofer.util

import java.net.URLDecoder

/**
 * Turns free text into a coordinate. Accepts:
 *  - "19.4326, -99.1332" / "19.4326 -99.1332" / "19.4326;-99.1332"
 *  - comma decimals: "19,4326; -99,1332" or "19,4326 -99,1332"
 *  - map links: ".../@19.4326,-99.1332,15z", "...?q=19.4326,-99.1332", "geo:19.4326,-99.1332"
 * Returns null when the text isn't a coordinate or is out of range.
 */
object CoordinateParser {

    private const val NUM = """[-+]?\d{1,3}(?:\.\d+)?"""
    private const val NUM_COMMA = """[-+]?\d{1,3}(?:,\d+)?"""

    private val linkPattern =
        Regex("""(?:@|[?&](?:q|ll|query|destination|center)=|geo:)\s*($NUM)\s*,\s*($NUM)""")
    private val dotPattern = Regex("""^($NUM)\s*(?:[,;]\s*|\s+)($NUM)$""")
    private val commaPattern = Regex("""^($NUM_COMMA)\s*(?:;\s*|\s+)($NUM_COMMA)$""")

    fun parse(input: String): LatLng? {
        val text = decode(input.trim())
        if (text.isEmpty()) return null

        linkPattern.find(text)?.let { return build(it.groupValues[1], it.groupValues[2]) }
        dotPattern.matchEntire(text)?.let { return build(it.groupValues[1], it.groupValues[2]) }
        commaPattern.matchEntire(text)?.let {
            return build(it.groupValues[1].replace(',', '.'), it.groupValues[2].replace(',', '.'))
        }
        return null
    }

    /** Parses a single number typed by a person, accepting "," or "." as decimal mark. */
    fun parseNumber(input: String): Double? =
        input.trim().replace(',', '.').toDoubleOrNull()

    fun isValid(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

    private fun build(latText: String, lngText: String): LatLng? {
        val lat = latText.toDoubleOrNull() ?: return null
        val lng = lngText.toDoubleOrNull() ?: return null
        return if (isValid(lat, lng)) LatLng(lat, lng) else null
    }

    private fun decode(text: String): String =
        if ('%' in text) runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text) else text
}
