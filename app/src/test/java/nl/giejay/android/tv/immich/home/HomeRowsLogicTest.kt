package nl.giejay.android.tv.immich.home

import arrow.core.Either
import nl.giejay.android.tv.immich.api.model.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class HomeRowsLogicTest {
    private fun date(year: Int): Date =
        Calendar.getInstance().apply { clear(); set(year, Calendar.JUNE, 1) }.time

    private fun album(name: String, endDate: Date? = null, assetCount: Int = 5) =
        Album(name, "", "id-$name", "thumb-$name", Date(0), endDate, assetCount)

    @Test
    fun `only named non-empty trips, newest first, undated last`() {
        val albums = listOf(
            album("Viatge Roma", endDate = date(2023)), album("Viatge Lisboa", endDate = date(2025)),
            album("Viatge Oslo", endDate = null), album("Viatge"), album("Viatge  "),
            album("viatge Paris"), album("Viatges Bali"), album("Viatge Vacío", assetCount = 0))
        assertEquals(listOf("Lisboa", "Roma", "Oslo"), albums.toTrips().map { it.title })
    }

    @Test
    fun `connection failure is reported as offline`() {
        val e: Either<String, List<Int>> = Either.Left("Could not fetch data from API, response: timeout")
        assertEquals(RowContent.Failed(offline = true), e.toRowContent())
    }

    @Test
    fun `server failure is not offline`() {
        val e: Either<String, List<Int>> = Either.Left("Invalid status (500) returned by Immich Server: x")
        assertEquals(RowContent.Failed(offline = false), e.toRowContent())
    }

    @Test
    fun `empty result hides the row`() {
        val e: Either<String, List<Int>> = Either.Right(emptyList())
        assertEquals(RowContent.Hidden, e.toRowContent())
    }

    @Test
    fun `items are kept`() {
        val e: Either<String, List<Int>> = Either.Right(listOf(1))
        assertEquals(RowContent.Items(listOf(1)), e.toRowContent())
    }

    @Test
    fun `refresh when never loaded or stale`() {
        assertTrue(shouldRefresh(1000, null))
        assertFalse(shouldRefresh(REFRESH_AFTER_MILLIS, 1))
        assertTrue(shouldRefresh(REFRESH_AFTER_MILLIS + 2, 1))
    }

    @Test
    fun `missing id opens first item`() {
        assertEquals(0, startIndex(-1)); assertEquals(3, startIndex(3))
    }
}
