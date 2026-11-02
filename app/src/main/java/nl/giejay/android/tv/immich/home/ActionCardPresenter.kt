package nl.giejay.android.tv.immich.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import nl.giejay.android.tv.immich.R

private const val CARD_HEIGHT_DP = 88
private const val WIDE_CARD_WIDTH_DP = 640
private const val SECTION_CARD_WIDTH_DP = 220
private const val CORNER_RADIUS_DP = 8
private const val PADDING_DP = 20
private const val TEXT_SIZE_SP = 18f

/** A text card for a [HomeAction]. Its focus is shown by inverting the colours. */
class ActionCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val view = TextView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            setPadding(context.dp(PADDING_DP), 0, context.dp(PADDING_DP), 0)
            setTextColor(textColors(context))
            compoundDrawablePadding = context.dp(PADDING_DP)
            background = background(context)
            layoutParams = ViewGroup.LayoutParams(context.dp(SECTION_CARD_WIDTH_DP), context.dp(CARD_HEIGHT_DP))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view as TextView
        val context = view.context
        val action = item as HomeAction
        view.text = when (action) {
            is HomeAction.Retry -> action.message
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as TextView).text = null
    }

    private fun textColors(context: Context) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(
            ContextCompat.getColor(context, R.color.action_button_text_focused_color),
            ContextCompat.getColor(context, R.color.action_button_text_unfocused_color)
        )
    )

    private fun background(context: Context) = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_focused),
            rounded(context, R.color.action_button_background_focused_color)
        )
        addState(intArrayOf(), rounded(context, R.color.action_button_background_unfocused_color))
    }

    private fun rounded(context: Context, color: Int) = GradientDrawable().apply {
        setColor(ContextCompat.getColor(context, color))
        cornerRadius = context.dp(CORNER_RADIUS_DP).toFloat()
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
