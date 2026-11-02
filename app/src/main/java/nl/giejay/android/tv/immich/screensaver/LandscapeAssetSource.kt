package nl.giejay.android.tv.immich.screensaver

import arrow.core.Either
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse

/**
 * What the Landscapes screensaver asks the server's smart search for. Beaches and pools are left out
 * on purpose: the point of this mode is that photos of people in swimwear do not come up.
 */
internal val LANDSCAPE_QUERIES = listOf(
    "mountain landscape",
    "forest landscape",
    "lake in nature",
    "sunset over a landscape",
    "city skyline",
    "city street architecture"
)

private const val LANDSCAPE_PAGE_SIZE = 25

// Smart search ranks the whole library and has no score to cut at, so deep pages stop being landscapes.
private const val LANDSCAPE_MAX_PAGES = 4
private const val DETAIL_REQUESTS_AT_ONCE = 4

/** Immich detected no face. A server that does not say counts as a photo that might have one. */
internal fun Asset.isKnownToHaveNoPeople(): Boolean =
    people?.isEmpty() == true && unassignedFaces.isNullOrEmpty()

/**
 * Photos that match [queries] and show nobody. It only ever narrows down: a term that fails or a photo
 * whose details fail is left out, and if every term fails there is an error and not other photos.
 */
internal class LandscapeAssetSource(
    private val search: suspend (query: String, page: Int, size: Int) -> Either<String, AssetResponse>,
    private val details: suspend (id: String) -> Either<String, Asset>,
    private val prefilter: (List<Asset>) -> List<Asset>,
    private val queries: List<String> = LANDSCAPE_QUERIES,
    private val pageSize: Int = LANDSCAPE_PAGE_SIZE,
    private val maxPages: Int = LANDSCAPE_MAX_PAGES
) {
    private var page = 0

    suspend fun next(): Either<String, AssetResponse> = coroutineScope {
        page += 1
        val currentPage = page
        val responses = queries.map { query -> async { search(query, currentPage, pageSize) } }.awaitAll()
        val found = responses.mapNotNull { it.getOrNull() }
        if (found.isEmpty()) {
            return@coroutineScope responses.first()
        }
        val candidates = prefilter(found.flatMap { it.assets }.distinctBy { it.id }.filter { it.type == "IMAGE" })
        val permits = Semaphore(DETAIL_REQUESTS_AT_ONCE)
        val withoutPeople = candidates.map { candidate ->
            async {
                val shown = permits.withPermit { details(candidate.id) }.getOrNull()?.isKnownToHaveNoPeople() == true
                candidate.takeIf { shown }
            }
        }.awaitAll().filterNotNull()
        Either.Right(
            AssetResponse(withoutPeople.shuffled(), currentPage < maxPages && found.any { it.canLoadMore })
        )
    }
}
