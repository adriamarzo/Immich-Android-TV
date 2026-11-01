package nl.giejay.android.tv.immich.home

import arrow.core.Either
import nl.giejay.android.tv.immich.api.model.Album

const val REFRESH_AFTER_MILLIS = 5 * 60 * 1000L

private const val TRIP_PREFIX = "Viatge "
private const val OFFLINE_PREFIX = "Could not fetch data from API"

data class Trip(val albumId: String, val title: String, val thumbnailAssetId: String?)

fun List<Album>.toTrips(): List<Trip> =
    filter { it.assetCount > 0 && it.albumName.startsWith(TRIP_PREFIX) }
        .mapNotNull { album ->
            val title = album.albumName.removePrefix(TRIP_PREFIX).trim()
            if (title.isEmpty()) null else album to title
        }
        .sortedWith(compareByDescending<Pair<Album, String>, java.util.Date?>(nullsFirst()) { it.first.endDate })
        .map { (album, title) -> Trip(album.id, title, album.albumThumbnailAssetId) }

sealed interface RowContent<out T> {
    data class Items<T>(val items: List<T>) : RowContent<T>
    data object Hidden : RowContent<Nothing>
    data class Failed(val offline: Boolean) : RowContent<Nothing>
}

fun <T> Either<String, List<T>>.toRowContent(): RowContent<T> = fold(
    { RowContent.Failed(offline = it.startsWith(OFFLINE_PREFIX)) },
    { if (it.isEmpty()) RowContent.Hidden else RowContent.Items(it) }
)

fun shouldRefresh(nowMillis: Long, lastLoadMillis: Long?): Boolean =
    lastLoadMillis == null || nowMillis - lastLoadMillis > REFRESH_AFTER_MILLIS

fun startIndex(foundIndex: Int): Int = if (foundIndex < 0) 0 else foundIndex
