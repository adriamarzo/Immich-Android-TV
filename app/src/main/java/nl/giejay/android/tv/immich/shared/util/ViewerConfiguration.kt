package nl.giejay.android.tv.immich.shared.util

import nl.giejay.android.tv.immich.home.startIndex
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE_TOP_LEFT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.LoadMore

fun buildViewerConfiguration(
    items: List<SliderItemViewHolder>,
    startId: String,
    loadMore: LoadMore?,
    onPositionChanged: (SliderItemViewHolder) -> Unit
): MediaSliderConfiguration = MediaSliderConfiguration(
    startIndex(items.indexOfFirst { it.ids().contains(startId) }),
    PreferenceManager.get(SLIDER_INTERVAL),
    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
    isVideoSoundEnable = true,
    items,
    loadMore,
    onPositionChanged,
    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
    glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
    gradiantOverlay = false,
    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER),
    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
    dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
    showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT)
)
