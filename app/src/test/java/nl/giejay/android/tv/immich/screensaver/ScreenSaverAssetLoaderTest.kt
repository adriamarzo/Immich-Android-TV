package nl.giejay.android.tv.immich.screensaver

import android.content.Context
import android.content.SharedPreferences
import arrow.core.Either
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.MAX_SEARCH_PAGE_SIZE
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.api.model.Tag
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import java.util.Date
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.Pref
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.transformations.GlideTransformations
import nl.giejay.mediaslider.plugin.MediaRemoteControlsKeyEventPlugin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenSaverAssetLoaderTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val preferenceValues = mutableMapOf<String, Any?>()

    private lateinit var appContext: Context
    private lateinit var sharedPreferences: SharedPreferences

    private fun apiClientWithExcludedAlbum(): ApiClient {
        val client = mock<ApiClient>()
        runBlocking {
            whenever(client.listAlbums(any())).thenReturn(
                Either.Right(listOf(Album("excluded", "", "excluded-album", null, Date(0), null, 1)))
            )
        }
        return client
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        appContext = mock()
        whenever(appContext.getString(any())).thenAnswer { "res-${it.getArgument<Int>(0)}" }
        ImmichApplication.appContext = appContext

        sharedPreferences = mock()
        whenever(sharedPreferences.getString(any(), anyOrNull())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? String ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getBoolean(any(), any())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? Boolean ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getInt(any(), any())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? Int ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getStringSet(any(), anyOrNull())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (preferenceValues[it.getArgument<String>(0)] as? Set<String> ?: it.getArgument(1)) as Set<String>?
        }
        whenever(sharedPreferences.contains(any())).thenAnswer {
            preferenceValues.containsKey(it.getArgument<String>(0))
        }
        whenever(sharedPreferences.all).thenAnswer {
            preferenceValues.filterValues { it != null }
        }

        val sharedPreferenceField = PreferenceManager.javaClass.getDeclaredField("sharedPreference")
        sharedPreferenceField.isAccessible = true
        sharedPreferenceField.set(PreferenceManager, sharedPreferences)

        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext.clear()
        preferenceValues.clear()
    }

    @After
    fun tearDown() {
        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext.clear()
        preferenceValues.clear()
        ImmichApplication.appContext = null
        Dispatchers.resetMain()
    }

    @Test
    fun `start falls back to full album fetch when random album search returns no assets`() = runLoaderTest(
        type = ScreenSaverType.ALBUMS,
        albums = setOf("shared-album"),
        setupMocks = { apiClient ->
            whenever(
                apiClient.listAssets(
                    page = any(),
                    pageCount = any(),
                    random = eq(true),
                    order = any(),
                    personIds = any(),
                    fromDate = anyOrNull(),
                    endDate = anyOrNull(),
                    contentType = eq(ContentType.IMAGE),
                    albumIds = eq(listOf("shared-album"))
                )
            ).thenReturn(Either.Right(AssetResponse(emptyList(), true)))

            whenever(
                apiClient.listAssetsFromAlbum(
                    albumIds = eq(listOf("shared-album")),
                    contentType = eq(ContentType.IMAGE),
                    pageCount = eq(1000)
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("shared-asset")), false)))
        }
    ) { actual, apiClient ->
        verify(apiClient, times(1)).listAssets(
            eq(1),
            eq(50),
            eq(true),
            any(),
            any(),
            isNull(),
            isNull(),
            eq(ContentType.IMAGE),
            eq(listOf("shared-album"))
        )
        verify(apiClient, times(1)).listAssetsFromAlbum(
            eq(listOf("shared-album")),
            eq(ContentType.IMAGE),
            eq(1000)
        )
        assertThat(actual.items.single().ids()).containsExactly("shared-asset")
    }

    @Test
    fun `loads random assets when type is RANDOM`() = runLoaderTest(
        type = ScreenSaverType.RANDOM,
        setupMocks = { apiClient ->
            whenever(
                apiClient.listAssets(
                    page = any(),
                    pageCount = any(),
                    random = any(),
                    order = any(),
                    personIds = any(),
                    fromDate = anyOrNull(),
                    endDate = anyOrNull(),
                    contentType = any(),
                    albumIds = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("random-asset")), true)))
        }
    ) { actual, _ ->
        assertThat(actual.items.single().ids()).containsExactly("random-asset")
    }

    @Test
    fun `loads recent assets when type is RECENT`() = runLoaderTest(
        type = ScreenSaverType.RECENT,
        setupMocks = { apiClient ->
            whenever(
                apiClient.recentAssets(
                    page = any(),
                    pageCount = any(),
                    contentType = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("recent-asset")), true)))
        }
    ) { actual, _ ->
        assertThat(actual.items.single().ids()).containsExactly("recent-asset")
    }

    @Test
    fun `loads similar assets when type is SIMILAR_TIME_PERIOD`() = runLoaderTest(
        type = ScreenSaverType.SIMILAR_TIME_PERIOD,
        setupMocks = { apiClient ->
            whenever(
                apiClient.similarAssets(
                    page = any(),
                    pageCount = any(),
                    contentType = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("similar-asset")), true)))
        }
    ) { actual, _ ->
        assertThat(actual.items.single().ids()).containsExactly("similar-asset")
    }

    @Test
    fun `filters out assets from excluded albums`() = runLoaderTest(
        type = ScreenSaverType.RANDOM,
        excludedAlbums = setOf("excluded-album"),
        setupMocks = { apiClient ->
            whenever(
                apiClient.listAssetsFromAlbum(
                    albumIds = eq(listOf("excluded-album")),
                    contentType = any(),
                    pageCount = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("excluded-asset")), false)))

            whenever(
                apiClient.listAssets(
                    page = any(),
                    pageCount = any(),
                    random = any(),
                    order = any(),
                    personIds = any(),
                    fromDate = anyOrNull(),
                    endDate = anyOrNull(),
                    contentType = any(),
                    albumIds = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("random-asset"), asset("excluded-asset")), true)))
        }
    ) { actual, apiClient ->
        // Immich rejects search page sizes above 1000; a larger value silently disabled album exclusion
        val pageCountCaptor = argumentCaptor<Int>()
        verify(apiClient).listAssetsFromAlbum(eq(listOf("excluded-album")), any(), pageCountCaptor.capture())
        assertThat(pageCountCaptor.firstValue).isAtMost(MAX_SEARCH_PAGE_SIZE)
        assertThat(actual.items.single().ids()).containsExactly("random-asset")
    }

    @Test
    fun `shows only landscape photos, filling the screen`() = runLoaderTest(
        type = ScreenSaverType.RANDOM,
        setupMocks = { apiClient ->
            whenever(
                apiClient.listAssets(
                    page = any(),
                    pageCount = any(),
                    random = any(),
                    order = any(),
                    personIds = any(),
                    fromDate = anyOrNull(),
                    endDate = anyOrNull(),
                    contentType = any(),
                    albumIds = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("landscape"), asset("portrait", 3000, 4000)), true)))
        }
    ) { actual, _ ->
        assertThat(actual.items.single().ids()).containsExactly("landscape")
        assertThat(actual.glideTransformation).isEqualTo(GlideTransformations.CENTER_CROP)
    }

    @Test
    fun `landscapes type plays only searched photos known to have no people`() = runLoaderTest(
        type = ScreenSaverType.LANDSCAPES,
        setupMocks = { apiClient ->
            whenever(apiClient.smartSearch(any(), any(), any()))
                .thenReturn(Either.Right(AssetResponse(listOf(asset("empty"), asset("unknown")), false)))
            whenever(apiClient.getAsset("empty")).thenReturn(Either.Right(asset("empty").copy(people = emptyList())))
            whenever(apiClient.getAsset("unknown")).thenReturn(Either.Left("failed"))
        }
    ) { actual, _ ->
        assertThat(actual.items.flatMap { it.ids() }).containsExactly("empty")
    }

    @Test
    fun `filters out assets with exclude tag`() = runLoaderTest(
        type = ScreenSaverType.RANDOM,
        setupMocks = { apiClient ->
            val taggedAsset = asset("tagged-asset").copy(tags = listOf(Tag(null, java.util.Date(), "exclude_immich_tv", "")))
            whenever(
                apiClient.listAssets(
                    page = any(),
                    pageCount = any(),
                    random = any(),
                    order = any(),
                    personIds = any(),
                    fromDate = anyOrNull(),
                    endDate = anyOrNull(),
                    contentType = any(),
                    albumIds = any()
                )
            ).thenReturn(Either.Right(AssetResponse(listOf(asset("normal-asset"), taggedAsset), true)))
        }
    ) { actual, _ ->
        assertThat(actual.items.single().ids()).containsExactly("normal-asset")
    }

    private suspend fun stubAnyListAssets(apiClient: ApiClient, vararg assets: Asset) {
        whenever(
            apiClient.listAssets(
                page = any(),
                pageCount = any(),
                random = any(),
                order = any(),
                personIds = any(),
                fromDate = anyOrNull(),
                endDate = anyOrNull(),
                contentType = any(),
                albumIds = any()
            )
        ).thenReturn(Either.Right(AssetResponse(assets.toList(), true)))
    }

    private fun failingExclusionPrefs() {
        setPreference(HOST_NAME, "https://example.com")
        setPreference(API_KEY, "api-key")
        setPreference(SCREENSAVER_TYPE, ScreenSaverType.RANDOM)
        setPreference(SCREENSAVER_ALBUMS, emptySet())
        setPreference(EXCLUDE_ASSETS_IN_ALBUM, setOf("excluded-album"))
    }

    @Test
    fun `preview leaves without playing when exclusions fail`() = runTest {
        failingExclusionPrefs()
        val apiClient = apiClientWithExcludedAlbum()
        whenever(apiClient.listAssetsFromAlbum(any(), any(), any())).thenReturn(Either.Left("Invalid status (500)"))
        stubAnyListAssets(apiClient, asset("private-asset"))
        val host = mock<ScreenSaverAssetLoader.Host>()
        whenever(host.exitWhenNothingToShow).thenReturn(true)

        ScreenSaverAssetLoader(this, apiClient, host).start().join()

        verify(host, never()).onScreenSaverConfigurationReady(any())
        verify(host).exitScreenSaver()
    }

    @Test
    fun `dream stays without assets while exclusions fail, until cancelled, without crashing`() = runTest {
        failingExclusionPrefs()
        val apiClient = apiClientWithExcludedAlbum()
        whenever(apiClient.listAssetsFromAlbum(any(), any(), any())).thenReturn(Either.Left("Invalid status (500)"))
        stubAnyListAssets(apiClient, asset("private-asset"))
        val host = mock<ScreenSaverAssetLoader.Host>()

        val job = ScreenSaverAssetLoader(this, apiClient, host, exclusionRetryMillis = 1).start()
        kotlinx.coroutines.withContext(Dispatchers.IO) { Thread.sleep(100) }
        assertThat(job.isActive).isTrue()
        job.cancel()
        job.join()

        verify(host, never()).onScreenSaverConfigurationReady(any())
        verify(host, never()).exitScreenSaver()
        verify(apiClient, never()).listAssets(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `dream retries exclusions and then plays a filtered set`() = runTest {
        failingExclusionPrefs()
        val apiClient = apiClientWithExcludedAlbum()
        whenever(apiClient.listAssetsFromAlbum(any(), any(), any()))
            .thenReturn(Either.Left("Invalid status (500)"))
            .thenReturn(Either.Right(AssetResponse(listOf(asset("excluded-asset")), false)))
        stubAnyListAssets(apiClient, asset("random-asset"), asset("excluded-asset"))
        val host = mock<ScreenSaverAssetLoader.Host>()

        mockConstruction(MediaRemoteControlsKeyEventPlugin::class.java).use {
            ScreenSaverAssetLoader(this, apiClient, host, exclusionRetryMillis = 1).start().join()
            val captor = argumentCaptor<MediaSliderConfiguration>()
            verify(host).onScreenSaverConfigurationReady(captor.capture())
            assertThat(captor.firstValue.items.single().ids()).containsExactly("random-asset")
        }
    }

    private fun runLoaderTest(
        type: ScreenSaverType = ScreenSaverType.RANDOM,
        albums: Set<String> = emptySet(),
        excludedAlbums: Set<String> = emptySet(),
        setupMocks: suspend (ApiClient) -> Unit,
        verifyResult: suspend (MediaSliderConfiguration, ApiClient) -> Unit
    ) = runTest {
        setPreference(HOST_NAME, "https://example.com")
        setPreference(API_KEY, "api-key")
        setPreference(SCREENSAVER_TYPE, type)
        setPreference(SCREENSAVER_ALBUMS, albums)
        setPreference(EXCLUDE_ASSETS_IN_ALBUM, excludedAlbums)

        val apiClient = apiClientWithExcludedAlbum()
        setupMocks(apiClient)

        val host = mock<ScreenSaverAssetLoader.Host>()
        val loader = ScreenSaverAssetLoader(
            scope = this,
            apiClient = apiClient,
            host = host
        )

        mockConstruction(MediaRemoteControlsKeyEventPlugin::class.java).use {
            loader.start().join()

            val configurationCaptor = argumentCaptor<MediaSliderConfiguration>()
            verify(host).onScreenSaverConfigurationReady(configurationCaptor.capture())
            verify(host, never()).showScreenSaverMessage(any(), any())
            verify(host, never()).exitScreenSaver()

            verifyResult(configurationCaptor.firstValue, apiClient)
        }
    }

    private fun <T> setPreference(pref: Pref<T, *, *>, value: T) {
        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext[pref.key()] = value
        preferenceValues[pref.key()] = value
    }

    private fun exif(width: Int, height: Int) =
        AssetExifInfo(null, 1, width, height, null, null, null, null, null)

    private fun asset(id: String, width: Int = 4000, height: Int = 3000) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = exif(width, height),
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = null,
        originalPath = null,
        originalFileName = null
    )
}
