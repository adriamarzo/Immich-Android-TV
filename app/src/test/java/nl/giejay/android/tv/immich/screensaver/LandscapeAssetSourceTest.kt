package nl.giejay.android.tv.immich.screensaver

import arrow.core.Either
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetFace
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.api.model.Person
import org.junit.Test
import java.util.UUID

class LandscapeAssetSourceTest {
    private fun asset(
        id: String,
        type: String = "IMAGE",
        people: List<Person>? = emptyList(),
        unassignedFaces: List<AssetFace>? = emptyList()
    ) = Asset(
        id = id, type = type, deviceAssetId = null, exifInfo = null, fileModifiedAt = null, albumName = null,
        people = people, tags = null, originalPath = null, originalFileName = null,
        unassignedFaces = unassignedFaces
    )

    private val detailCalls = mutableListOf<String>()

    private fun source(
        results: Map<String, Either<String, AssetResponse>>,
        details: Map<String, Asset> = emptyMap(),
        prefilter: (List<Asset>) -> List<Asset> = { it },
        maxPages: Int = 2
    ) = LandscapeAssetSource(
        search = { query, _, _ -> results.getValue(query) },
        details = { id ->
            detailCalls += id
            details[id]?.let { Either.Right(it) } ?: Either.Left("failed")
        },
        prefilter = prefilter,
        queries = results.keys.toList(),
        maxPages = maxPages
    )

    private fun found(vararg ids: String, more: Boolean = false): Either<String, AssetResponse> =
        Either.Right(AssetResponse(ids.map { asset(it) }, more))

    private fun Either<String, AssetResponse>.ids() = getOrNull()!!.assets.map { it.id }

    @Test
    fun `merges the terms, without repeating a photo found by two of them`() = runTest {
        val source = source(
            mapOf("a" to found("1", "2"), "b" to found("2", "3")),
            mapOf("1" to asset("1"), "2" to asset("2"), "3" to asset("3"))
        )
        assertThat(source.next().ids()).containsExactly("1", "2", "3")
    }

    @Test
    fun `leaves out a photo with a named person`() = runTest {
        val person = Person("x", UUID.randomUUID(), "")
        val source = source(mapOf("a" to found("1")), mapOf("1" to asset("1", people = listOf(person))))
        assertThat(source.next().ids()).isEmpty()
    }

    @Test
    fun `leaves out a photo with a face nobody has named`() = runTest {
        val source = source(mapOf("a" to found("1")), mapOf("1" to asset("1", unassignedFaces = listOf(AssetFace("f")))))
        assertThat(source.next().ids()).isEmpty()
    }

    @Test
    fun `leaves out a photo when the server does not say who is in it`() = runTest {
        val source = source(mapOf("a" to found("1")), mapOf("1" to asset("1", people = null)))
        assertThat(source.next().ids()).isEmpty()
    }

    @Test
    fun `leaves out a photo whose details cannot be loaded`() = runTest {
        val source = source(mapOf("a" to found("1", "2")), mapOf("2" to asset("2")))
        assertThat(source.next().ids()).containsExactly("2")
    }

    @Test
    fun `leaves out videos without asking for their details`() = runTest {
        val video = asset("v", type = "VIDEO")
        val source = source(mapOf("a" to Either.Right(AssetResponse(listOf(video), false))))
        assertThat(source.next().ids()).isEmpty()
        assertThat(detailCalls).isEmpty()
    }

    @Test
    fun `does not ask for the details of a photo the screensaver filters discard`() = runTest {
        val source = source(
            mapOf("a" to found("1", "2")),
            mapOf("2" to asset("2")),
            prefilter = { assets -> assets.filter { it.id != "1" } }
        )
        assertThat(source.next().ids()).containsExactly("2")
        assertThat(detailCalls).containsExactly("2")
    }

    @Test
    fun `keeps the other terms when one fails`() = runTest {
        val source = source(mapOf("a" to Either.Left("failed"), "b" to found("2")), mapOf("2" to asset("2")))
        assertThat(source.next().ids()).containsExactly("2")
    }

    @Test
    fun `fails when every term fails, so nothing else plays in their place`() = runTest {
        val source = source(mapOf("a" to Either.Left("failed"), "b" to Either.Left("failed")))
        assertThat(source.next().isLeft()).isTrue()
    }

    @Test
    fun `stops when no term has more`() = runTest {
        val source = source(mapOf("a" to found(more = false)))
        assertThat(source.next().getOrNull()!!.canLoadMore).isFalse()
    }

    // Results are ranked with no score: deep pages stop being landscapes.
    @Test
    fun `stops at the page limit even if the server has more`() = runTest {
        val source = source(mapOf("a" to found(more = true)), maxPages = 2)
        assertThat(source.next().getOrNull()!!.canLoadMore).isTrue()
        assertThat(source.next().getOrNull()!!.canLoadMore).isFalse()
    }

    @Test
    fun `asks for consecutive pages starting at the first`() = runTest {
        val pages = mutableListOf<Int>()
        val source = LandscapeAssetSource(
            search = { _, page, _ ->
                pages += page
                Either.Right(AssetResponse(emptyList(), true))
            },
            details = { Either.Left("unused") },
            prefilter = { it },
            queries = listOf("a"),
            maxPages = 3
        )
        repeat(2) { source.next() }
        assertThat(pages).containsExactly(1, 2).inOrder()
    }
}
