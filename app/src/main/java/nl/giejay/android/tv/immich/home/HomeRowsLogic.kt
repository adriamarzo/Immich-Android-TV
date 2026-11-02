package nl.giejay.android.tv.immich.home

import arrow.core.Either
import arrow.core.flatMap
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.util.isHiddenByTag

const val REFRESH_AFTER_MILLIS = 5 * 60 * 1000L

private const val TRIP_PREFIX = "Viatge "
private const val OFFLINE_PREFIX = "Could not fetch data from API"

data class Trip(val albumId: String, val title: String, val thumbnailAssetId: String?)

fun List<Album>.toTrips(excludedAlbumIds: Set<String>): List<Trip> =
    filter { it.assetCount > 0 && it.albumName.startsWith(TRIP_PREFIX) && it.id !in excludedAlbumIds }
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

/** A page of the Latest row and the exclusion set it was filtered with. */
data class VisibleLatest(val excludedAssetIds: Set<String>, val assets: List<Asset>)

fun List<Asset>.withoutExcluded(excludedAssetIds: Set<String>): List<Asset> =
    filterNot { it.isHiddenByTag() || it.id in excludedAssetIds }

/** Exclusions come first: when they cannot be loaded there is no list, never an unfiltered one. */
suspend fun loadVisibleLatest(
    exclusions: suspend () -> Either<String, Set<String>>,
    assets: suspend () -> Either<String, List<Asset>>
): Either<String, VisibleLatest> =
    exclusions().flatMap { excluded ->
        assets().map { VisibleLatest(excluded, it.withoutExcluded(excluded)) }
    }

/** A failed refresh leaves a row that has already loaded as it is. */
fun <T> keepLoadedItemsOnFailure(loaded: List<T>, content: RowContent<T>): RowContent<T> =
    if (content is RowContent.Failed && loaded.isNotEmpty()) RowContent.Items(loaded) else content

fun shouldRefresh(nowMillis: Long, lastLoadMillis: Long?): Boolean =
    lastLoadMillis == null || nowMillis - lastLoadMillis > REFRESH_AFTER_MILLIS

fun startIndex(foundIndex: Int): Int = if (foundIndex < 0) 0 else foundIndex

const val ROW_SEARCH = "search"
const val ROW_LATEST = "latest"
const val ROW_TRIPS = "trips"
const val ROW_PEOPLE = "people"
const val ROW_MORE = "more"
val HOME_ROW_ORDER = listOf(ROW_SEARCH, ROW_LATEST, ROW_TRIPS, ROW_PEOPLE, ROW_MORE)

sealed interface HomeAction {
    data object Search : HomeAction
    data class Section(val key: String, val title: String) : HomeAction
    data class Retry(val rowKey: String, val message: String) : HomeAction
}

sealed interface HomeCardItem {
    val card: Card

    data class AssetItem(val asset: Asset, override val card: Card) : HomeCardItem
    data class TripItem(val trip: Trip, override val card: Card) : HomeCardItem
    data class PersonItem(override val card: Card) : HomeCardItem
}

/** Where a row goes when it appears, given that rows load out of order and some may be hidden. */
fun rowInsertIndex(order: List<String>, present: Set<String>, key: String): Int =
    order.takeWhile { it != key }.count { it in present }

/** Whether two values are the same element of a row, so that a refresh keeps its focus. */
fun isSameRowItem(old: Any, new: Any): Boolean = when {
    old is HomeCardItem && new is HomeCardItem -> old::class == new::class && old.card.id == new.card.id
    old is HomeAction.Retry && new is HomeAction.Retry -> old.rowKey == new.rowKey
    else -> old == new
}
