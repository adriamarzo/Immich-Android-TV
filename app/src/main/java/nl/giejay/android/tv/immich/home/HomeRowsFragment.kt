package nl.giejay.android.tv.immich.home

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
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
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.util.buildViewerConfiguration
import nl.giejay.android.tv.immich.shared.util.loadExcludedAssetIds
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel
import timber.log.Timber
import java.util.Locale
import java.util.TimeZone

private const val LATEST_PAGE_SIZE = 20
private const val HERO_DEBOUNCE_MILLIS = 300L
private const val HERO_FADE_MILLIS = 250
private val CONTENT_ROWS = listOf(ROW_LATEST, ROW_TRIPS, ROW_PEOPLE)

private val ROW_ITEM_DIFF = object : DiffCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any) = isSameRowItem(oldItem, newItem)
    override fun areContentsTheSame(oldItem: Any, newItem: Any) = oldItem == newItem
}

/** The home screen: a menu, a large photo of what is selected, and rows that load and fail independently. */
class HomeRowsFragment : BrowseSupportFragment() {
    private data class RowFocus(val rowKey: String, val itemIndex: Int)

    private val sliderViewModel: MediaSliderViewModel by activityViewModels()
    private val rowsAdapter = ArrayObjectAdapter(
        ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL).apply { shadowEnabled = false }
    )
    private val rows = mutableMapOf<String, ListRow>()
    private val rowJobs = mutableMapOf<String, Job>()
    private val rowLoadedAt = mutableMapOf<String, Long>()
    private lateinit var itemPresenters: PresenterSelector

    // The last good set, and the albums it was loaded for. Only replaced by a complete load.
    @Volatile
    private var excludedAssetIds: Set<String> = emptySet()
    private var excludedAssetIdsFor: Set<String>? = null
    private var focus = RowFocus(ROW_LATEST, 0)
    private var initialFocusPlaced = false
    private val heroHandler = Handler(Looper.getMainLooper())
    private var heroImage: ImageView? = null
    private var heroLineView: TextView? = null

    // A new view selects its first row before focus is restored; that must not overwrite [focus].
    private var restoreFocusPending = false
    private var openingAsset: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set before super.onCreate, which only tracks the headers back stack if they can show.
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        super.onCreate(savedInstanceState)
        brandColor = ContextCompat.getColor(requireContext(), R.color.black)
        itemPresenters = HomeItemPresenterSelector(requireContext())

        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ -> onItemClicked(item) }
        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, row ->
            val rowKey = rows.entries.firstOrNull { it.value === row }?.key
            if (!restoreFocusPending && item != null && rowKey != null) {
                focus = RowFocus(rowKey, (row as ListRow).items().indexOf(item).coerceAtLeast(0))
            }
            // Only over the first row: lower ones scroll to where the menu is.
            titleView?.visibility = if (row != null && rowsAdapter.indexOf(row) > 0) View.INVISIBLE else View.VISIBLE
            scheduleHero(item)
        })
    }

    // The root is opaque, so that a background left by a section's BackgroundManager does not show through.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_home_rows, container, false) as ViewGroup
        heroImage = root.findViewById(R.id.home_hero_image)
        heroLineView = root.findViewById(R.id.home_hero_line)
        root.addView(super.onCreateView(inflater, root, savedInstanceState))
        return root
    }

    override fun onInflateTitleView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.home_nav_bar, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.nav_home).isActivated = true
        view.findViewById<View>(R.id.nav_home).setOnClickListener { rowsSupportFragment?.verticalGridView?.requestFocus() }
        view.findViewById<View>(R.id.nav_timeline).setOnClickListener { openSection(SECTION_TIMELINE) }
        view.findViewById<View>(R.id.nav_albums).setOnClickListener { openSection(SECTION_ALBUMS) }
        view.findViewById<View>(R.id.nav_settings).setOnClickListener { openSection(SECTION_SETTINGS) }
        view.findViewById<View>(R.id.nav_search).setOnClickListener {
            navigate(HomeRowsFragmentDirections.actionGlobalSearch())
        }
    }

    private fun openSection(key: String) = navigate(HomeRowsFragmentDirections.actionGlobalHomeSection(key))

    /** Debounced: moving along a row should not load a large photo for every card it passes. */
    private fun scheduleHero(item: Any?) {
        heroHandler.removeCallbacksAndMessages(null)
        heroHandler.postDelayed({ showHero(item) }, HERO_DEBOUNCE_MILLIS)
    }

    private fun showHero(item: Any?) {
        val image = heroImage ?: return
        val photo = when (item) {
            is HomeCardItem.AssetItem -> {
                val exif = item.asset.exifInfo
                heroLineView?.text = heroLine(
                    exif?.city,
                    exif?.dateTimeOriginal ?: item.asset.fileCreatedAt,
                    Locale.getDefault(),
                    TimeZone.getDefault()
                )
                ApiUtil.getThumbnailUrl(item.asset.id, "preview")
            }
            is HomeCardItem.TripItem -> {
                heroLineView?.text = item.trip.title
                ApiUtil.getThumbnailUrl(item.trip.thumbnailAssetId, "preview")
            }
            else -> {
                // A portrait is too small to fill this space: the last photo stays.
                heroLineView?.text = ""
                return
            }
        }
        Glide.with(this).load(photo).centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(HERO_FADE_MILLIS))
            .into(image)
    }

    override fun onStart() {
        super.onStart()
        val now = System.currentTimeMillis()
        val exclusionsChanged = excludedAssetIdsFor.let { it != null && it != currentExcludedAlbums() }
        CONTENT_ROWS.filter { shouldRefresh(now, rowLoadedAt[it]) || (exclusionsChanged && it != ROW_PEOPLE) }
            .forEach { loadRow(it) }
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
        heroHandler.removeCallbacksAndMessages(null)
        heroImage = null
        heroLineView = null
        super.onDestroyView()
    }

    private fun loadRow(key: String) {
        if (rowJobs[key]?.isActive == true) return
        rowJobs[key] = viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { fetchRow(key) }
            // The first row goes in first, so that the initial focus lands on it and not on a faster one.
            if (key != ROW_LATEST) rowJobs[ROW_LATEST]?.join()
            // Not stamped on failure, so the next start tries again.
            if (content !is RowContent.Failed) rowLoadedAt[key] = System.currentTimeMillis()
            showRow(key, if (key == ROW_LATEST) keepLoadedItemsOnFailure(loadedLatestItems(), content) else content)
        }
    }

    private suspend fun fetchRow(key: String): RowContent<HomeCardItem> = try {
        val client = ApiClient.getClient(ApiClientConfig.fromPrefs())
        when (key) {
            ROW_LATEST -> fetchLatest(client)
            ROW_TRIPS -> client.listAlbums().map { albums -> albums.toTrips(currentExcludedAlbums()).map { it.toItem() } }
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
        val excludedAlbums = currentExcludedAlbums()
        return loadVisibleLatest(
            exclusions = { loadExcludedAssetIds(client, excludedAlbums) },
            assets = {
                client.listAssets(page = 1, pageCount = LATEST_PAGE_SIZE, contentType = ContentType.ALL).map { it.assets }
            }
        ).map { latest ->
            excludedAssetIds = latest.excludedAssetIds
            excludedAssetIdsFor = excludedAlbums
            latest.assets.map { HomeCardItem.AssetItem(it, it.toCard()) }
        }
    }

    private fun currentExcludedAlbums(): Set<String> = PreferenceManager.get(EXCLUDE_ASSETS_IN_ALBUM)

    /** What a failed refresh may keep: nothing, if the items were filtered for other excluded albums. */
    private fun loadedLatestItems(): List<HomeCardItem> =
        if (excludedAssetIdsFor == currentExcludedAlbums()) {
            rows[ROW_LATEST]?.items().orEmpty().filterIsInstance<HomeCardItem>()
        } else {
            emptyList()
        }

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
        if (!initialFocusPlaced) {
            initialFocusPlaced = true
            view?.post { rowsSupportFragment?.verticalGridView?.requestFocus() }
        }
    }

    private fun removeRow(key: String) {
        val row = rows.remove(key) ?: return
        val position = rowsAdapter.indexOf(row)
        rowsAdapter.remove(row)
        if (focus.rowKey == key && rows.isEmpty()) {
            titleView?.requestFocus()
        } else if (focus.rowKey == key) {
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
                { LoadMoreResult(it.assets.withoutExcluded(excludedAssetIds).toSliderItems(true, mergePortrait), it.canLoadMore) }
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
        private val personPresenter = PersonCardPresenter()

        override fun getPresenter(item: Any?): Presenter = when (item) {
            is HomeAction -> actionPresenter
            is HomeCardItem.PersonItem -> personPresenter
            else -> cardPresenter
        }

        override fun getPresenters(): Array<Presenter> = arrayOf(cardPresenter, actionPresenter, personPresenter)
    }

    private class HomeCardPresenter(context: Context) : CardPresenter(context) {
        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            super.onBindViewHolder(viewHolder, (item as HomeCardItem).card)
        }
    }
}
