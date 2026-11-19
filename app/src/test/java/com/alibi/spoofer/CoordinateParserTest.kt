package com.alibi.spoofer

import com.alibi.spoofer.util.CoordinateParser
import com.alibi.spoofer.util.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    private fun assertParses(input: String, lat: Double, lng: Double) {
        val parsed = CoordinateParser.parse(input)
        assertEquals("parsing '$input'", LatLng(lat, lng), parsed)
    }

    @Test
    fun dotDecimals() {
        assertParses("19.4326, -99.1332", 19.4326, -99.1332)
        assertParses("19.4326,-99.1332", 19.4326, -99.1332)
        assertParses("19.4326 -99.1332", 19.4326, -99.1332)
        assertParses("  -33.8688 ; 151.2093 ", -33.8688, 151.2093)
        assertParses("+40.4168, -3.7038", 40.4168, -3.7038)
    }

    @Test
    fun commaDecimals() {
        assertParses("19,4326; -99,1332", 19.4326, -99.1332)
        assertParses("19,4326 -99,1332", 19.4326, -99.1332)
    }

    @Test
    fun mapLinks() {
        assertParses("https://www.google.com/maps/@19.4326,-99.1332,15z", 19.4326, -99.1332)
        assertParses("https://maps.google.com/?q=19.4326,-99.1332", 19.4326, -99.1332)
        assertParses("https://maps.google.com/?q=19.4326%2C-99.1332", 19.4326, -99.1332)
        assertParses("geo:19.4326,-99.1332?z=12", 19.4326, -99.1332)
    }

    @Test
    fun rejectsNonCoordinates() {
        assertNull(CoordinateParser.parse("Starbucks Reforma"))
        assertNull(CoordinateParser.parse("123 Main Street"))
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("19.4326"))
    }

    @Test
    fun rejectsOutOfRange() {
        assertNull(CoordinateParser.parse("91.0, 10.0"))
        assertNull(CoordinateParser.parse("10.0, 181.0"))
        assertNull(CoordinateParser.parse("999, 999"))
    }

    @Test
    fun parsesSingleNumbersWithEitherDecimalMark() {
        assertEquals(19.43, CoordinateParser.parseNumber("19,43")!!, 1e-9)
        assertEquals(-99.13, CoordinateParser.parseNumber(" -99.13 ")!!, 1e-9)
        assertNull(CoordinateParser.parseNumber("abc"))
    }
}
