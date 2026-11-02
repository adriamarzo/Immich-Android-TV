package nl.giejay.android.tv.immich.shared.util

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExcludedAssetIdsTest {
    private val client = mock<ApiClient>()

    private fun asset(id: String) = Asset(
        id = id, type = "IMAGE", deviceAssetId = null, exifInfo = null, fileModifiedAt = null,
        albumName = null, people = null, tags = null, originalPath = null, originalFileName = null
    )

    private fun albumNamed(id: String) = Album("name", "", id, null, Date(0), null, 1)

    private suspend fun existingAlbums(vararg ids: String) {
        whenever(client.listAlbums(any())).thenReturn(Either.Right(ids.map { albumNamed(it) }))
    }

    private suspend fun album(id: String, result: Either<String, AssetResponse>) {
        whenever(client.listAssetsFromAlbum(eq(listOf(id)), any(), any())).thenReturn(result)
    }

    @Test
    fun `no excluded albums is an empty set and no request`(): Unit = runBlocking {
        assertEquals(Either.Right(emptySet<String>()), loadExcludedAssetIds(client, emptySet()))
        verify(client, never()).listAssetsFromAlbum(any(), any(), any())
    }

    @Test
    fun `ids of all excluded albums are joined`(): Unit = runBlocking {
        existingAlbums("a", "b")
        album("a", Either.Right(AssetResponse(listOf(asset("1"), asset("2")), false)))
        album("b", Either.Right(AssetResponse(listOf(asset("2"), asset("3")), false)))
        assertEquals(Either.Right(setOf("1", "2", "3")), loadExcludedAssetIds(client, setOf("a", "b")))
    }

    @Test
    fun `one failing album fails the whole set rather than giving a partial one`(): Unit = runBlocking {
        existingAlbums("a", "b")
        album("a", Either.Right(AssetResponse(listOf(asset("1")), false)))
        album("b", Either.Left("Invalid status (500)"))
        assertEquals(Either.Left("Invalid status (500)"), loadExcludedAssetIds(client, setOf("a", "b")))
    }

    @Test
    fun `an excluded album deleted from the server is ignored, so it cannot lock the app out`(): Unit = runBlocking {
        existingAlbums("a")
        album("a", Either.Right(AssetResponse(listOf(asset("1")), false)))
        assertEquals(Either.Right(setOf("1")), loadExcludedAssetIds(client, setOf("a", "deleted")))
        verify(client, never()).listAssetsFromAlbum(eq(listOf("deleted")), any(), any())
    }

    @Test
    fun `failing to list albums fails the set, because deleted cannot be told from unreachable`(): Unit = runBlocking {
        whenever(client.listAlbums(any())).thenReturn(Either.Left("Invalid status (500)"))
        assertEquals(Either.Left("Invalid status (500)"), loadExcludedAssetIds(client, setOf("a")))
    }

    @Test
    fun `an unexpected exception is a failure, not a crash`(): Unit = runBlocking {
        existingAlbums("a")
        whenever(client.listAssetsFromAlbum(any(), any(), any())).thenThrow(IllegalStateException("boom"))
        assertTrue(loadExcludedAssetIds(client, setOf("a")).isLeft())
    }
}
