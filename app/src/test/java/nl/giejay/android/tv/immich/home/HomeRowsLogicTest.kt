package nl.giejay.android.tv.immich.home

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Tag
import nl.giejay.android.tv.immich.card.Card
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
        assertEquals(listOf("Lisboa", "Roma", "Oslo"), albums.toTrips(emptySet()).map { it.title })
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

    @Test
    fun `a row is inserted by the fixed order whatever loaded first`() {
        assertEquals(0, rowInsertIndex(HOME_ROW_ORDER, emptySet(), ROW_PEOPLE))
        assertEquals(0, rowInsertIndex(HOME_ROW_ORDER, setOf(ROW_PEOPLE), ROW_LATEST))
        assertEquals(1, rowInsertIndex(HOME_ROW_ORDER, setOf(ROW_LATEST, ROW_PEOPLE), ROW_TRIPS))
    }

    private val october = java.util.Date(1_791_000_000_000L) // 3 Oct 2026, 03:20 UTC
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    private val spanish = java.util.Locale("es")

    @Test
    fun `the hero line has the place and the date`() {
        assertEquals("Menorca · 3 oct 2026", heroLine("Menorca", october, spanish, utc))
    }

    @Test
    fun `the hero line has only the date without a place`() {
        assertEquals("3 oct 2026", heroLine(" ", october, spanish, utc))
        assertEquals("3 oct 2026", heroLine(null, october, spanish, utc))
    }

    @Test
    fun `the hero line has only the place without a date`() {
        assertEquals("Menorca", heroLine("Menorca", null, spanish, utc))
    }

    @Test
    fun `the hero line is empty with neither`() {
        assertEquals("", heroLine(null, null, spanish, utc))
    }

    @Test
    fun `row items are matched by kind and id`() {
        val card = Card("a", "", "1", null, null)
        assertTrue(isSameRowItem(HomeCardItem.PersonItem(card), HomeCardItem.PersonItem(card.copy(title = "b"))))
        assertFalse(isSameRowItem(HomeCardItem.PersonItem(card), HomeCardItem.PersonItem(card.copy(id = "2"))))
        assertFalse(
            isSameRowItem(HomeCardItem.PersonItem(card), HomeCardItem.TripItem(Trip("1", "a", null), card))
        )
        assertTrue(isSameRowItem(HomeAction.Retry(ROW_TRIPS, "a"), HomeAction.Retry(ROW_TRIPS, "b")))
        assertFalse(isSameRowItem(HomeAction.Retry(ROW_TRIPS, "a"), HomeCardItem.PersonItem(card)))
    }

    private fun asset(id: String, tags: List<Tag>? = null) = Asset(
        id = id, type = "IMAGE", deviceAssetId = null, exifInfo = null, fileModifiedAt = null,
        albumName = null, people = null, tags = tags, originalPath = null, originalFileName = null
    )

    @Test
    fun `an excluded album is not a trip`() {
        val albums = listOf(album("Viatge Roma"), album("Viatge Lisboa"))
        assertEquals(listOf("Lisboa"), albums.toTrips(setOf("id-Viatge Roma")).map { it.title })
    }

    @Test
    fun `a failed exclusion load yields no list and asks for no assets`() = runBlocking {
        var assetsAsked = false
        val result = loadVisibleLatest(
            exclusions = { Either.Left("Invalid status (500)") },
            assets = { assetsAsked = true; Either.Right(listOf(asset("private"))) }
        )
        assertEquals(Either.Left("Invalid status (500)"), result)
        assertFalse(assetsAsked)
    }

    @Test
    fun `the latest list leaves out excluded and tagged assets, and carries the set it used`() = runBlocking {
        val tagged = asset("tagged", listOf(Tag(null, Date(), "exclude_immich_tv", "")))
        val result = loadVisibleLatest(
            exclusions = { Either.Right(setOf("excluded")) },
            assets = { Either.Right(listOf(asset("ok"), asset("excluded"), tagged)) }
        )
        assertEquals(Either.Right(VisibleLatest(setOf("excluded"), listOf(asset("ok")))), result)
    }

    @Test
    fun `a failed asset load is a failure even when exclusions loaded`() = runBlocking {
        val result = loadVisibleLatest(
            exclusions = { Either.Right(emptySet()) },
            assets = { Either.Left("Could not fetch data from API, response: timeout") }
        )
        assertTrue(result.isLeft())
    }

    @Test
    fun `a failed refresh keeps the items already loaded`() {
        val content = keepLoadedItemsOnFailure(listOf("a", "b"), RowContent.Failed(offline = true))
        assertEquals(RowContent.Items(listOf("a", "b")), content)
    }

    @Test
    fun `a failure with nothing loaded stays a failure`() {
        val failed = RowContent.Failed(offline = false)
        assertEquals(failed, keepLoadedItemsOnFailure(emptyList<String>(), failed))
    }

    @Test
    fun `a successful refresh replaces the items`() {
        val fresh = RowContent.Items(listOf("c"))
        assertEquals(fresh, keepLoadedItemsOnFailure(listOf("a"), fresh))
        assertEquals(RowContent.Hidden, keepLoadedItemsOnFailure(listOf("a"), RowContent.Hidden))
    }
}
