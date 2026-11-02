package nl.giejay.android.tv.immich.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.giejay.android.tv.immich.api.model.SmartSearchRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSearchTest {
    @Test
    fun `request sends only the fields the v3 endpoint accepts`() {
        val json = Gson().toJson(SmartSearchRequest("coche rojo", 2, 50))
        assertEquals(
            setOf("query", "page", "size", "withExif", "visibility"),
            Gson().fromJson(json, JsonObject::class.java).keySet())
    }

    @Test
    fun `failure kinds`() {
        assertEquals(SearchFailure.NOT_ENABLED,
            searchFailure("Invalid status (400) returned by Immich Server: Smart search is not enabled"))
        assertEquals(SearchFailure.NOT_ENABLED,
            searchFailure("Invalid status (400) returned by Immich Server: SMART SEARCH IS NOT ENABLED"))
        assertEquals(SearchFailure.OFFLINE, searchFailure("Could not fetch data from API, response: x"))
        assertEquals(SearchFailure.OFFLINE, searchFailure("Could not fetch data from API, status: 502"))
        assertEquals(SearchFailure.OTHER, searchFailure("Invalid status (500) returned by Immich Server: x"))
    }
}
