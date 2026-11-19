package com.alibi.spoofer

import com.alibi.spoofer.util.PlaceKind
import com.alibi.spoofer.util.PlaceSearch
import com.alibi.spoofer.util.SearchOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {

    @Test
    fun parsesPhotonFeatures() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"osm_key":"man_made","type":"house","housenumber":"5","name":"Tour Eiffel",
                "street":"Avenue Anatole France","city":"Paris","state":"Île-de-France","country":"France"},
               "geometry":{"type":"Point","coordinates":[2.2945006,48.8582599]}},
              {"type":"Feature","properties":{"type":"city","name":"Paris","state":"Île-de-France","country":"France"},
               "geometry":{"type":"Point","coordinates":[2.3483915,48.8534951]}},
              {"type":"Feature","properties":{"name":"Broken"},"geometry":{"type":"Point","coordinates":[500,500]}}
            ]}
        """.trimIndent()
        val places = PlaceSearch.parsePhoton(json)!!
        assertEquals(2, places.size) // out-of-range point dropped
        assertEquals("Tour Eiffel", places[0].primaryText)
        assertEquals("Avenue Anatole France 5, Paris, Île-de-France, France", places[0].secondaryText)
        assertEquals(48.8582599, places[0].latLng.latitude, 1e-9)
        assertEquals(2.2945006, places[0].latLng.longitude, 1e-9)
        assertEquals(PlaceKind.CITY, places[1].kind)
        assertEquals("Île-de-France, France", places[1].secondaryText)
    }

    @Test
    fun parsesNominatimResults() {
        val json = """
            [{"lat":"19.4326","lon":"-99.1332","name":"Zócalo","addresstype":"square",
              "display_name":"Zócalo, Centro, Ciudad de México, México",
              "address":{"road":"Plaza de la Constitución","city":"Ciudad de México","country":"México"}},
             {"lat":"19.43","lon":"-99.13","name":"","addresstype":"road","display_name":"Calle 5 de Mayo, Centro",
              "address":{"road":"Calle 5 de Mayo","city":"Ciudad de México","country":"México"}}]
        """.trimIndent()
        val places = PlaceSearch.parseNominatim(json)!!
        assertEquals(2, places.size)
        assertEquals("Zócalo", places[0].primaryText)
        assertEquals("Plaza de la Constitución, Ciudad de México, México", places[0].secondaryText)
        assertEquals("Calle 5 de Mayo", places[1].primaryText)
        assertEquals(PlaceKind.STREET, places[1].kind)
    }

    @Test
    fun badJsonReturnsNull() {
        assertNull(PlaceSearch.parsePhoton("not json"))
        assertNull(PlaceSearch.parseNominatim("{}"))
    }

    @Test
    fun coordinatesResolveWithoutNetwork() = runBlocking {
        val outcome = PlaceSearch.autocomplete("19.4326, -99.1332", null)
        assertTrue(outcome is SearchOutcome.Results)
        val place = (outcome as SearchOutcome.Results).places.single()
        assertEquals(PlaceKind.COORDINATE, place.kind)
        assertEquals(19.4326, place.latLng.latitude, 1e-9)
    }

    @Test
    fun shortQueriesDontHitTheNetwork() = runBlocking {
        val outcome = PlaceSearch.autocomplete("ab", null)
        assertEquals(SearchOutcome.Results(emptyList()), outcome)
    }
}
