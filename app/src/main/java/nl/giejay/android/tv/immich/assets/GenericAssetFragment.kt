package nl.giejay.android.tv.immich.assets

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.ALL_ASSETS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.util.buildViewerConfiguration
import nl.giejay.android.tv.immich.shared.util.isHiddenByTag
import nl.giejay.android.tv.immich.shared.util.loadExcludedAssetIds
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

abstract class GenericAssetFragment : VerticalCardGridFragment<Asset>() {
    protected lateinit var currentFilter: ContentType
    protected lateinit var currentSort: PhotosOrder
    private var excludedAssetsLoaded = false
    protected var excludedAssetIds: Set<String> = emptySet()

    private val sliderViewModel: MediaSliderViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val sortingKey = getSortingKey()
        val filterKey = getFilterKey()
        currentSort = PreferenceManager.get(sortingKey)
        currentFilter = PreferenceManager.get(filterKey)
        super.onCreate(savedInstanceState)
        PreferenceManager.subscribeMultiple(listOf(sortingKey, filterKey)) { state ->
            if(state[sortingKey.key()] != currentSort || state[filterKey.key()] != currentFilter){
                clearState()
                currentSort = state[sortingKey.key()] as PhotosOrder
                currentFilter = state[filterKey.key()] as ContentType
                fetchInitialItems()
            }
        }
    }

    override fun filterItems(items: List<Asset>): List<Asset> {
        return items.filter { currentFilter ==  ContentType.ALL || it.type.equals(currentFilter.toString(), ignoreCase = true) }
            .filter(excludeByTag())
            .filterNot { excludedAssetIds.contains(it.id) }
    }

    private fun excludeByTag() = { asset: Asset -> !asset.isHiddenByTag() }

    override suspend fun loadData(): Either<String, List<Asset>> {
        if (!excludedAssetsLoaded) {
            excludedAssetIds = loadExcludedAssetIds(apiClient)
            excludedAssetsLoaded = true
        }
        return super.loadData()
    }

    open fun getSortingKey(): EnumByTitlePref<PhotosOrder>{
        return ALL_ASSETS_SORTING
    }

    open fun getFilterKey(): EnumByTitlePref<ContentType>{
        return FILTER_CONTENT_TYPE
    }

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items.sortedWith(currentSort.sort)
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    open fun showMediaCount(): Boolean {
        return false
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }

    /**
     * Handles clicking an asset card to open the photo slider.
     *
     * Similar to the Timeline, preparing the full list of slider items (potentially
     * thousands) is done on a background thread to keep the UI responsive and
     * provide immediate visual feedback via a loading spinner.
     */
    override fun onItemClicked(card: Card) {
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val toSliderItems = assets.toSliderItems(
                keepOrder = true,
                mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)
            )
            val loadMore: LoadMore = suspend {
                val moreAssets = loadMoreAssets()
                // also load the data in the overview
                setDataOnMain(moreAssets)
                LoadMoreResult(
                    moreAssets.toSliderItems(true, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)),
                    moreAssets.isNotEmpty() && !allPagesLoaded
                )
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progressBar?.visibility = View.GONE
                val config = buildViewerConfiguration(
                    toSliderItems,
                    card.id,
                    loadMore
                ) { item -> manualUpdatePosition(this@GenericAssetFragment.assets.indexOfFirst { item.ids().contains(it.id) }) }
                sliderViewModel.configuration = config
                findNavController().navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider())
            }
        }
    }

    override fun getBackgroundPicture(it: Asset): String? {
        return ApiUtil.getFileUrl(it.id, "IMAGE")
    }

    override fun createCard(a: Asset): Card {
        return a.toCard()
    }
}
