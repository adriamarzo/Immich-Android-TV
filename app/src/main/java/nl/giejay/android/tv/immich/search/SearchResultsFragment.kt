package nl.giejay.android.tv.immich.search

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.searchFailure
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.shared.util.isHiddenByTag

/**
 * Smart-search results, most similar first. The query is only held in memory: it is never logged,
 * shown, or written to preferences. Error text from the API is classified, never displayed.
 */
class SearchResultsFragment : GenericAssetFragment() {
    private lateinit var query: String
    private var messageView: TextView? = null
    private var pendingMessage: Int? = null
    private var emptyShown = false
    private var lastLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        query = SearchResultsFragmentArgs.fromBundle(requireArguments()).query
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dock = view.findViewById<ViewGroup>(R.id.browse_grid_dock)
        messageView = TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 22f
            val pad = (48 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            visibility = View.GONE
        }
        dock.addView(messageView)
        pendingMessage?.let { showMessage(it) }
    }

    override fun onDestroyView() {
        messageView = null
        super.onDestroyView()
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        apiClient.smartSearch(query, page, pageCount).map { it.assets }

    // Keep the server's similarity order.
    override fun sortItems(items: List<Asset>): List<Asset> = items

    // Only the hidden-tag and excluded-album filters apply; the saved content-type filter must not hide results.
    override fun filterItems(items: List<Asset>): List<Asset> =
        items.filterNot { it.isHiddenByTag() || excludedAssetIds.contains(it.id) }

    override fun openPopUpMenu() {
        // No sort/filter menu on this screen.
    }

    override fun setData(assets: List<Asset>) {
        super.setData(assets)
        if (allPagesLoaded && this.assets.isEmpty() && adapter.size() == 0 && !emptyShown) {
            emptyShown = true
            showMessage(R.string.search_no_results)
        }
    }

    override suspend fun loadMoreAssets(): List<Asset> {
        lastLoadFailed = false
        val result = super.loadMoreAssets()
        if (lastLoadFailed) {
            // Do not skip the failed page: the next scroll retries it. Reset so that the
            // enclosing recursive call does not step back a second time.
            lastLoadFailed = false
            currentPage -= 1
        }
        return result
    }

    override suspend fun showErrorMessage(message: String) {
        lastLoadFailed = true
        val res = searchFailure(message).messageRes()
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            if (this@SearchResultsFragment.assets.isEmpty() && adapter.size() == 0) {
                showMessage(res)
            } else {
                // Keep the results already on screen.
                Toast.makeText(requireContext(), res, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMessage(@StringRes res: Int) {
        pendingMessage = res
        progressBar?.visibility = View.GONE
        messageView?.apply {
            setText(res)
            visibility = View.VISIBLE
        }
    }
}
