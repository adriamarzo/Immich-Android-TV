package nl.giejay.android.tv.immich.screensaver

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSaverLandscapeTest {
    private fun asset(width: Int?, height: Int?, orientation: Int? = 1, type: String = "IMAGE") = Asset(
        id = "a", type = type, deviceAssetId = null,
        exifInfo = AssetExifInfo(null, orientation, width, height, null, null, null, null, null),
        fileModifiedAt = null, albumName = null, people = null, tags = null,
        originalPath = null, originalFileName = null
    )

    @Test
    fun `wider than tall is landscape`() = assertTrue(asset(4000, 3000).isLandscapeForScreenSaver())

    @Test
    fun `taller than wide is not`() = assertFalse(asset(3000, 4000).isLandscapeForScreenSaver())

    @Test
    fun `square is not`() = assertFalse(asset(3000, 3000).isLandscapeForScreenSaver())

    @Test
    fun `orientations 5 to 8 rotate the photo by 90 degrees`() {
        for (orientation in 5..8) {
            assertFalse(asset(4000, 3000, orientation).isLandscapeForScreenSaver())
            assertTrue(asset(3000, 4000, orientation).isLandscapeForScreenSaver())
        }
    }

    @Test
    fun `orientations 1 to 4 and unknown do not rotate it`() {
        for (orientation in listOf(1, 2, 3, 4, null)) {
            assertTrue(asset(4000, 3000, orientation).isLandscapeForScreenSaver())
        }
    }

    @Test
    fun `a photo of unknown size is not shown`() {
        assertFalse(asset(null, 3000).isLandscapeForScreenSaver())
        assertFalse(asset(4000, null).isLandscapeForScreenSaver())
        assertFalse(asset(null, null).copy(exifInfo = null).isLandscapeForScreenSaver())
    }

    @Test
    fun `videos are not filtered by this check`() =
        assertTrue(asset(1080, 1920, type = "VIDEO").isLandscapeForScreenSaver())
}
