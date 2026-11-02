package nl.giejay.android.tv.immich.home

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import androidx.leanback.widget.TitleViewAdapter
import nl.giejay.android.tv.immich.R

/** The home screen's menu, in the place Browse keeps its title: Up from the first row reaches it. */
class HomeNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), TitleViewAdapter.Provider {

    private val titleViewAdapter = object : TitleViewAdapter() {
        override fun getSearchAffordanceView(): View = findViewById(R.id.nav_search)
    }

    override fun getTitleViewAdapter(): TitleViewAdapter = titleViewAdapter

    // Browse sends Right to the rows wherever focus is, so the menu moves along itself.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            FocusFinder.getInstance().findNextFocus(this, findFocus(), direction)?.requestFocus()
        }
        return true
    }

    // Browse answers Up with this view; a button that already has focus keeps it.
    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean =
        hasFocus() || super.requestFocus(direction, previouslyFocusedRect)
}
