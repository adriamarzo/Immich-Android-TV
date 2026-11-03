package nl.giejay.mediaslider.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class VideoInfoRulesTest {
    private val spanish = Locale("es", "ES")

    private fun track(
        width: Int = 3840,
        height: Int = 2160,
        rotation: Int = 0,
        frameRate: Float = 30f,
        mimeType: String? = "video/hevc",
        codecs: String? = null,
        colorTransfer: Int? = null
    ) = VideoTrack(width, height, rotation, frameRate, mimeType, codecs, colorTransfer)

    @Test
    fun `shows the resolution`() {
        assertThat(track().resolution()).isEqualTo("3840 × 2160")
    }

    // A phone video is stored sideways, with a rotation to apply.
    @Test
    fun `swaps width and height for a rotated video`() {
        assertThat(track(width = 1920, height = 1080, rotation = 90).resolution()).isEqualTo("1080 × 1920")
    }

    @Test
    fun `has no resolution when the file does not say`() {
        assertThat(track(width = -1, height = -1).resolution()).isNull()
    }

    @Test
    fun `shows a whole frame rate without decimals, even if it is slightly off`() {
        assertThat(track(frameRate = 30.000002f).frameRateText(spanish)).isEqualTo("30 fps")
    }

    @Test
    fun `shows a fractional frame rate with two decimals in the local format`() {
        assertThat(track(frameRate = 29.97003f).frameRateText(spanish)).isEqualTo("29,97 fps")
        assertThat(track(frameRate = 29.97003f).frameRateText(Locale.US)).isEqualTo("29.97 fps")
    }

    @Test
    fun `has no frame rate when the file does not say`() {
        assertThat(track(frameRate = -1f).frameRateText(spanish)).isNull()
    }

    @Test
    fun `names the common codecs`() {
        assertThat(track(mimeType = "video/hevc").codecName()).isEqualTo("H.265 (HEVC)")
        assertThat(track(mimeType = "video/avc").codecName()).isEqualTo("H.264 (AVC)")
        assertThat(track(mimeType = "video/av01").codecName()).isEqualTo("AV1")
        assertThat(track(mimeType = "video/x-vnd.on2.vp9").codecName()).isEqualTo("VP9")
        assertThat(track(mimeType = "video/dolby-vision").codecName()).isEqualTo("Dolby Vision")
    }

    @Test
    fun `adds the profile when the file gives it`() {
        assertThat(track(mimeType = "video/hevc", codecs = "hvc1.2.4.L153.B0").codecName())
            .isEqualTo("H.265 (HEVC) · hvc1.2.4.L153.B0")
    }

    @Test
    fun `shows an unknown codec by its own name`() {
        assertThat(track(mimeType = "video/mp2t").codecName()).isEqualTo("mp2t")
    }

    @Test
    fun `has no codec when the file does not say`() {
        assertThat(track(mimeType = null).codecName()).isNull()
    }

    @Test
    fun `recognises HDR10 and HLG from the colour transfer`() {
        assertThat(track(colorTransfer = COLOR_TRANSFER_PQ).dynamicRange()).isEqualTo(VideoDynamicRange.HDR10)
        assertThat(track(colorTransfer = COLOR_TRANSFER_HLG).dynamicRange()).isEqualTo(VideoDynamicRange.HLG)
    }

    @Test
    fun `recognises Dolby Vision from the type or the codec`() {
        assertThat(track(mimeType = "video/dolby-vision").dynamicRange()).isEqualTo(VideoDynamicRange.DOLBY_VISION)
        assertThat(track(codecs = "dvh1.05.06").dynamicRange()).isEqualTo(VideoDynamicRange.DOLBY_VISION)
    }

    @Test
    fun `Dolby Vision wins over the colour transfer`() {
        assertThat(track(codecs = "dvhe.08.06", colorTransfer = COLOR_TRANSFER_HLG).dynamicRange())
            .isEqualTo(VideoDynamicRange.DOLBY_VISION)
    }

    @Test
    fun `a stated SDR transfer is SDR`() {
        assertThat(track(colorTransfer = COLOR_TRANSFER_SDR_VIDEO).dynamicRange()).isEqualTo(VideoDynamicRange.SDR)
    }

    // Many SDR files say nothing, but so might an HDR one: do not claim either.
    @Test
    fun `is unknown when the file gives no colour data`() {
        assertThat(track(colorTransfer = null).dynamicRange()).isEqualTo(VideoDynamicRange.UNKNOWN)
    }
}
