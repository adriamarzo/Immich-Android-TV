package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.ImmichApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultPreferencesTest {
    @Before
    fun setUp() {
        val context = mock<Context>()
        whenever(context.getString(any())).thenReturn("text")
        ImmichApplication.appContext = context
    }

    @After
    fun tearDown() {
        ImmichApplication.appContext = null
    }

    @Test
    fun `portrait photos are shown one at a time by default`() {
        assertThat(SLIDER_MERGE_PORTRAIT_PHOTOS.defaultValue).isFalse()
    }
}
