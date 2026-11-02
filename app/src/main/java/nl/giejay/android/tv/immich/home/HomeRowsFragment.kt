package nl.giejay.android.tv.immich.home

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenter
import nl.giejay.android.tv.immich.people.toCard
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.util.buildViewerConfiguration
import nl.giejay.android.tv.immich.shared.util.isHiddenByTag
import nl.giejay.android.tv.immich.shared.util.loadExcludedAssetIds
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel
import timber.log.Timber

private const val LATEST_PAGE_SIZE = 20
private val CONTENT_ROWS = listOf(ROW_LATEST, ROW_TRIPS, ROW_PEOPLE)

private val ROW_ITEM_DIFF = object : DiffCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any) = isSameRowItem(oldItem, newItem)
    override fun areContentsTheSame(oldItem: Any, newItem: Any) = oldItem == newItem
}

/** The home screen: a search card, then rows of content that load and fail independently. */
class HomeRowsFragment : BrowseSupportFragment() {
    private data class RowFocus(val rowKey: String, val itemIndex: Int)

    private val sliderViewModel: MediaSliderViewModel by activityViewModels()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL))
    private val rows = mutableMapOf<String, ListRow>()
    private val rowJobs = mutableMapOf<String, Job>()
    private val rowLoadedAt = mutableMapOf<String, Long>()
    private lateinit var itemPresenters: PresenterSelector

    @Volatile
    private var excludedAssetIds: Set<String> = emptySet()
    private var focus = RowFocus(ROW_SEARCH, 0)

    // A new view selects its first row before focus is restored; that must not overwrite [focus].
    private var restoreFocusPending = false
    private var openingAsset: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set before super.onCreate, which only tracks the headers back stack if they can show.
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        super.onCreate(savedInstanceState)
        title = ""
        showTitle(false)
        brandColor = ContextCompat.getColor(requireContext(), R.color.black)
        itemPresenters = HomeItemPresenterSelector(requireContext())

        addRow(ROW_SEARCH, null, listOf(HomeAction.Search))
        addRow(
            ROW_MORE, getString(R.string.home_more), listOf(
                HomeAction.Section(SECTION_TIMELINE, getString(R.string.timeline)),
                HomeAction.Section(SECTION_ALBUMS, getString(R.string.albums)),
                HomeAction.Section(SECTION_FOLDERS, getString(R.string.folders)),
                HomeAction.Section(SECTION_SETTINGS, getString(R.string.settings))
            )
        )
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ -> onItemClicked(item) }
        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, row ->
            val rowKey = rows.entries.firstOrNull { it.value === row }?.key
            if (!restoreFocusPending && item != null && rowKey != null) {
                focus = RowFocus(rowKey, (row as ListRow).items().indexOf(item).coerceAtLeast(0))
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Opaque, so that a background left by a section's BackgroundManager does not show through.
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.black))
    }

    override fun onStart() {
        super.onStart()
        val now = System.currentTimeMillis()
        CONTENT_ROWS.filter { shouldRefresh(now, rowLoadedAt[it]) }.forEach { loadRow(it) }
    }

    override fun onResume() {
        super.onResume()
        if (restoreFocusPending) {
            view?.post {
                restoreFocusPending = false
                moveFocusTo(focus.rowKey, focus.itemIndex)
            }
        }
    }

    override fun onDestroyView() {
        restoreFocusPending = true
        super.onDestroyView()
    }

    private fun loadRow(key: String) {
        if (rowJobs[key]?.isActive == true) return
        rowJobs[key] = viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { fetchRow(key) }
            rowLoadedAt[key] = System.currentTimeMillis()
            showRow(key, content)
        }
    }

    private suspend fun fetchRow(key: String): RowContent<HomeCardItem> = try {
        val client = ApiClient.getClient(ApiClientConfig.fromPrefs())
        when (key) {
            ROW_LATEST -> fetchLatest(client)
            ROW_TRIPS -> client.listAlbums().map { albums -> albums.toTrips().map { it.toItem() } }
            else -> client.listPeople().map { people -> people.map { HomeCardItem.PersonItem(it.toCard()) } }
        }.toRowContent()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A malformed response in one row must not take down the others. Its message could hold
        // names or URLs, so only the type is logged.
        Timber.w("Could not load home row %s: %s", key, e.javaClass.simpleName)
        RowContent.Failed(offline = false)
    }

    private suspend fun fetchLatest(client: ApiClient): Either<String, List<HomeCardItem>> {
        excludedAssetIds = loadExcludedAssetIds(client)
        return client.listAssets(page = 1, pageCount = LATEST_PAGE_SIZE, contentType = ContentType.ALL)
            .map { response -> response.assets.visible().map { HomeCardItem.AssetItem(it, it.toCard()) } }
    }

    private fun List<Asset>.visible(): List<Asset> =
        filterNot { it.isHiddenByTag() || excludedAssetIds.contains(it.id) }

    private fun Trip.toItem(): HomeCardItem {
        val thumbnail = ApiUtil.getThumbnailUrl(thumbnailAssetId, "thumbnail")
        return HomeCardItem.TripItem(this, Card(title, "", albumId, thumbnail, thumbnail))
    }

    private fun showRow(key: String, content: RowContent<HomeCardItem>) {
        val items: List<Any> = when (content) {
            RowContent.Hidden -> return removeRow(key)
            is RowContent.Items -> content.items
            is RowContent.Failed -> listOf(
                HomeAction.Retry(
                    key,
                    getString(if (content.offline) R.string.home_server_unreachable else R.string.home_row_failed)
                )
            )
        }
        val existing = rows[key]
        if (existing == null) {
            addRow(key, rowTitle(key), items)
            return
        }
        val rowItems = existing.items()
        val kindChanged = rowItems.firstOrNull() is HomeAction != items.first() is HomeAction
        (existing.adapter as ArrayObjectAdapter).setItems(items, ROW_ITEM_DIFF)
        // The focused card has just been replaced by one of a different kind.
        if (kindChanged && focus.rowKey == key) moveFocusTo(key, 0)
    }

    private fun addRow(key: String, title: String?, items: List<Any>) {
        val itemsAdapter = ArrayObjectAdapter(itemPresenters).apply { addAll(0, items) }
        val row = if (title == null) ListRow(itemsAdapter)
        else ListRow(HeaderItem(HOME_ROW_ORDER.indexOf(key).toLong(), title), itemsAdapter)
        rowsAdapter.add(rowInsertIndex(HOME_ROW_ORDER, rows.keys, key), row)
        rows[key] = row
    }

    private fun removeRow(key: String) {
        val row = rows.remove(key) ?: return
        val position = rowsAdapter.indexOf(row)
        rowsAdapter.remove(row)
        if (focus.rowKey == key) {
            val next = HOME_ROW_ORDER.filter { it in rows.keys }[position.coerceAtMost(rows.size - 1)]
            moveFocusTo(next, 0)
        }
    }

    private fun moveFocusTo(rowKey: String, itemIndex: Int) {
        val row = rows[rowKey]
        val position = row?.let { rowsAdapter.indexOf(it) }?.coerceAtLeast(0) ?: 0
        val index = itemIndex.coerceAtMost((row?.adapter?.size() ?: 1) - 1).coerceAtLeast(0)
        // Not Browse's setSelectedPosition: with a task it starts a headers transition, which
        // throws when headers are disabled.
        rowsSupportFragment?.setSelectedPosition(
            position,
            false,
            ListRowPresenter.SelectItemViewHolderTask(index).apply { isSmoothScroll = false }
        )
    }

    private fun rowTitle(key: String): String = getString(
        when (key) {
            ROW_LATEST -> R.string.home_latest
            ROW_TRIPS -> R.string.home_trips
            else -> R.string.home_people
        }
    )

    private fun onItemClicked(item: Any?) {
        when (item) {
            HomeAction.Search -> navigate(HomeRowsFragmentDirections.actionGlobalSearch())
            is HomeAction.Section -> navigate(HomeRowsFragmentDirections.actionGlobalHomeSection(item.key))
            is HomeAction.Retry -> loadRow(item.rowKey)
            is HomeCardItem.AssetItem -> openAsset(item)
            is HomeCardItem.TripItem -> navigate(
                HomeRowsFragmentDirections.actionHomeFragmentToAlbumDetailsFragment(item.trip.albumId, item.trip.title)
            )
            is HomeCardItem.PersonItem -> navigate(
                HomeRowsFragmentDirections.actionHomeFragmentToPersonAssetsFragment(item.card.id, item.card.title)
            )
        }
    }

    /** The current destination check drops a second press that arrives before the screen changes. */
    private fun navigate(directions: NavDirections) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.homeRowsFragment) {
            navController.navigate(directions)
        }
    }

    private fun openAsset(item: HomeCardItem.AssetItem) {
        if (openingAsset?.isActive == true) return
        val assets = latestAssets()
        openingAsset = viewLifecycleOwner.lifecycleScope.launch {
            val mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)
            val sliderItems = withContext(Dispatchers.Default) { assets.toSliderItems(true, mergePortrait) }
            sliderViewModel.configuration = buildViewerConfiguration(
                sliderItems,
                item.card.id,
                loadMoreLatest(mergePortrait),
                ::onViewerPositionChanged
            )
            navigate(HomeRowsFragmentDirections.actionHomeFragmentToPhotoSlider())
        }
    }

    private fun loadMoreLatest(mergePortrait: Boolean): LoadMore {
        val client = ApiClient.getClient(ApiClientConfig.fromPrefs())
        var page = 1
        return suspend {
            page += 1
            client.listAssets(page = page, pageCount = LATEST_PAGE_SIZE, contentType = ContentType.ALL).fold(
                { LoadMoreResult(emptyList(), false) },
                { LoadMoreResult(it.assets.visible().toSliderItems(true, mergePortrait), it.canLoadMore) }
            )
        }
    }

    /** Going back from the viewer lands on the asset last seen, if it is one of this row's. */
    private fun onViewerPositionChanged(holder: SliderItemViewHolder) {
        val index = latestAssets().indexOfFirst { holder.ids().contains(it.id) }
        if (index >= 0) focus = RowFocus(ROW_LATEST, index)
    }

    private fun latestAssets(): List<Asset> =
        rows[ROW_LATEST]?.items().orEmpty().filterIsInstance<HomeCardItem.AssetItem>().map { it.asset }

    private fun ListRow.items(): List<Any> = (adapter as ArrayObjectAdapter).unmodifiableList()

    private class HomeItemPresenterSelector(context: Context) : PresenterSelector() {
        private val cardPresenter = HomeCardPresenter(context)
        private val actionPresenter = ActionCardPresenter()

        override fun getPresenter(item: Any?): Presenter =
            if (item is HomeAction) actionPresenter else cardPresenter

        override fun getPresenters(): Array<Presenter> = arrayOf(cardPresenter, actionPresenter)
    }

    private class HomeCardPresenter(context: Context) : CardPresenter(context) {
        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            super.onBindViewHolder(viewHolder, (item as HomeCardItem).card)
        }
    }
}
