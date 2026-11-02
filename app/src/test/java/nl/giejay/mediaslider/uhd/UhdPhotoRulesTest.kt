package nl.giejay.mediaslider.uhd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UhdPhotoRulesTest {
    @Test
    fun `uses the surface when a 4K screen runs its interface at 1080p`() {
        assertThat(usesUhdSurface(3840, 2160, 1920, 1080, onlyUseThumbnails = false)).isTrue()
    }

    @Test
    fun `does not use the surface when the system only offers 1080p`() {
        assertThat(usesUhdSurface(1920, 1080, 1920, 1080, onlyUseThumbnails = false)).isFalse()
    }

    @Test
    fun `does not use the surface when the interface already runs at 4K`() {
        assertThat(usesUhdSurface(3840, 2160, 3840, 2160, onlyUseThumbnails = false)).isFalse()
    }

    // With this setting on, the original must never be downloaded.
    @Test
    fun `does not use the surface when only thumbnails are wanted`() {
        assertThat(usesUhdSurface(3840, 2160, 1920, 1080, onlyUseThumbnails = true)).isFalse()
    }

    @Test
    fun `a single photo can be shown in 4K`() {
        assertThat(isUhdEligible(isImage = true, hasSecondary = false, usesZoomEffects = false)).isTrue()
    }

    @Test
    fun `a video is not shown on the surface`() {
        assertThat(isUhdEligible(isImage = false, hasSecondary = false, usesZoomEffects = false)).isFalse()
    }

    @Test
    fun `two merged portraits are not shown on the surface`() {
        assertThat(isUhdEligible(isImage = true, hasSecondary = true, usesZoomEffects = false)).isFalse()
    }

    // The zoom and pan effects animate the normal image view.
    @Test
    fun `a photo with zoom effects is not shown on the surface`() {
        assertThat(isUhdEligible(isImage = true, hasSecondary = false, usesZoomEffects = true)).isFalse()
    }

    @Test
    fun `fit keeps a 4 by 3 photo whole, centred with side bars`() {
        assertThat(uhdDestination(4000, 3000, 3840, 2160, UhdFit.FIT))
            .isEqualTo(UhdRect(480f, 0f, 3360f, 2160f))
    }

    @Test
    fun `fill covers the screen, cropping the 4 by 3 photo top and bottom`() {
        assertThat(uhdDestination(4000, 3000, 3840, 2160, UhdFit.FILL))
            .isEqualTo(UhdRect(0f, -360f, 3840f, 2520f))
    }

    @Test
    fun `a 16 by 9 photo covers the screen exactly in either mode`() {
        val whole = UhdRect(0f, 0f, 3840f, 2160f)
        assertThat(uhdDestination(1920, 1080, 3840, 2160, UhdFit.FIT)).isEqualTo(whole)
        assertThat(uhdDestination(1920, 1080, 3840, 2160, UhdFit.FILL)).isEqualTo(whole)
    }

    @Test
    fun `a tall photo fits with side bars`() {
        assertThat(uhdDestination(2160, 3840, 3840, 2160, UhdFit.FIT).left).isGreaterThan(0f)
    }

    // 4:3 on 16:9 loses 25% of the height.
    @Test
    fun `safe crop fills when the part lost is within the limit`() {
        assertThat(uhdDestination(4000, 3000, 3840, 2160, UhdFit.SAFE_FILL, maxCutOffWidth = 30, maxCutOffHeight = 30))
            .isEqualTo(uhdDestination(4000, 3000, 3840, 2160, UhdFit.FILL))
    }

    @Test
    fun `safe crop fits when too much would be lost`() {
        assertThat(uhdDestination(4000, 3000, 3840, 2160, UhdFit.SAFE_FILL, maxCutOffWidth = 20, maxCutOffHeight = 20))
            .isEqualTo(uhdDestination(4000, 3000, 3840, 2160, UhdFit.FIT))
    }

    @Test
    fun `a photo with no size does not crash`() {
        assertThat(uhdDestination(0, 0, 3840, 2160, UhdFit.FILL)).isEqualTo(UhdRect(0f, 0f, 3840f, 2160f))
    }

    @Test
    fun `preloads the next photo`() {
        assertThat(uhdPreloadIndex(2, 5) { true }).isEqualTo(3)
    }

    @Test
    fun `preloads nothing after the last photo`() {
        assertThat(uhdPreloadIndex(4, 5) { true }).isNull()
    }

    @Test
    fun `preloads nothing when the next item cannot be shown in 4K`() {
        assertThat(uhdPreloadIndex(2, 5) { it != 3 }).isNull()
    }
}
