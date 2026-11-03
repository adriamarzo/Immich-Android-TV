package nl.giejay.mediaslider.plugin

import androidx.media3.common.C
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val COLOR_TRANSFER_SDR_VIDEO = C.COLOR_TRANSFER_SDR

const val COLOR_TRANSFER_PQ = C.COLOR_TRANSFER_ST2084

const val COLOR_TRANSFER_HLG = C.COLOR_TRANSFER_HLG

enum class VideoDynamicRange(val label: String?) {
    SDR("SDR"),
    HDR10("HDR10"),
    HLG("HLG"),
    DOLBY_VISION("Dolby Vision"),
    UNKNOWN(null)
}

/** The track being played. A value of -1, or null, means the file does not say. */
data class VideoTrack(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float,
    val mimeType: String?,
    val codecs: String?,
    val colorTransfer: Int?
)

fun VideoTrack.resolution(): String? {
    if (width <= 0 || height <= 0) return null
    val sideways = rotationDegrees == 90 || rotationDegrees == 270
    return if (sideways) "$height × $width" else "$width × $height"
}

fun VideoTrack.frameRateText(locale: Locale): String? {
    if (frameRate <= 0f) return null
    val whole = frameRate.roundToInt()
    return if (abs(frameRate - whole) < 0.01f) "$whole fps" else String.format(locale, "%.2f fps", frameRate)
}

fun VideoTrack.codecName(): String? {
    val name = when (val type = mimeType) {
        null -> return null
        "video/hevc" -> "H.265 (HEVC)"
        "video/avc" -> "H.264 (AVC)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/mp4v-es" -> "MPEG-4"
        "video/dolby-vision" -> "Dolby Vision"
        else -> type.substringAfter('/')
    }
    return if (codecs.isNullOrBlank()) name else "$name · $codecs"
}

fun VideoTrack.dynamicRange(): VideoDynamicRange {
    val codec = codecs.orEmpty()
    return when {
        mimeType == "video/dolby-vision" || codec.startsWith("dvh") || codec.startsWith("dav") -> VideoDynamicRange.DOLBY_VISION
        colorTransfer == COLOR_TRANSFER_PQ -> VideoDynamicRange.HDR10
        colorTransfer == COLOR_TRANSFER_HLG -> VideoDynamicRange.HLG
        colorTransfer == COLOR_TRANSFER_SDR_VIDEO -> VideoDynamicRange.SDR
        else -> VideoDynamicRange.UNKNOWN
    }
}
