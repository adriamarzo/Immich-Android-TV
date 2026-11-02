package nl.giejay.mediaslider.uhd

import kotlin.math.max
import kotlin.math.min

enum class UhdFit { FIT, FILL, SAFE_FILL }

data class UhdRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Some Android TV devices draw their interface at 1080p on a 4K screen. A surface of its own keeps
 * a photo at the screen's real resolution. It needs the original, so it is off when only thumbnails
 * are wanted.
 */
fun usesUhdSurface(
    physicalWidth: Int,
    physicalHeight: Int,
    uiWidth: Int,
    uiHeight: Int,
    onlyUseThumbnails: Boolean
): Boolean = !onlyUseThumbnails && physicalHeight >= 2160 && (physicalWidth > uiWidth || physicalHeight > uiHeight)

/** Videos, merged portraits and the zoom effects stay on the normal views. */
fun isUhdEligible(isImage: Boolean, hasSecondary: Boolean, usesZoomEffects: Boolean): Boolean =
    isImage && !hasSecondary && !usesZoomEffects

/** Where to draw the photo on the surface, centred. It can go past the edges when it is cropped. */
fun uhdDestination(
    imageWidth: Int,
    imageHeight: Int,
    surfaceWidth: Int,
    surfaceHeight: Int,
    fit: UhdFit,
    maxCutOffWidth: Int = 0,
    maxCutOffHeight: Int = 0
): UhdRect {
    if (imageWidth <= 0 || imageHeight <= 0) {
        return UhdRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat())
    }
    val scaleX = surfaceWidth.toFloat() / imageWidth
    val scaleY = surfaceHeight.toFloat() / imageHeight
    val fillScale = max(scaleX, scaleY)
    val scale = when (fit) {
        UhdFit.FIT -> min(scaleX, scaleY)
        UhdFit.FILL -> fillScale
        UhdFit.SAFE_FILL -> {
            val lostWidth = 100f * (1f - surfaceWidth / (imageWidth * fillScale))
            val lostHeight = 100f * (1f - surfaceHeight / (imageHeight * fillScale))
            if (lostWidth <= maxCutOffWidth && lostHeight <= maxCutOffHeight) fillScale else min(scaleX, scaleY)
        }
    }
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (surfaceWidth - width) / 2f
    val top = (surfaceHeight - height) / 2f
    return UhdRect(left, top, left + width, top + height)
}

/** Only the next photo is kept ready, so at most one waits in memory. */
fun uhdPreloadIndex(current: Int, count: Int, isEligible: (Int) -> Boolean): Int? {
    val next = current + 1
    return if (next < count && isEligible(next)) next else null
}
