package nl.giejay.android.tv.immich.search

import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.SearchFailure
import nl.giejay.android.tv.immich.api.searchFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultMessagesTest {
    @Test
    fun `each failure maps to its message`() {
        assertEquals(R.string.search_not_enabled, SearchFailure.NOT_ENABLED.messageRes())
        assertEquals(R.string.home_server_unreachable, SearchFailure.OFFLINE.messageRes())
        assertEquals(R.string.search_failed, SearchFailure.OTHER.messageRes())
    }

    @Test
    fun `api error text ends up on a message`() {
        assertEquals(R.string.home_server_unreachable,
            searchFailure("Could not fetch data from API, response: timeout").messageRes())
        assertEquals(R.string.search_failed, searchFailure("Invalid status (500) returned by Immich Server: x").messageRes())
    }
}
