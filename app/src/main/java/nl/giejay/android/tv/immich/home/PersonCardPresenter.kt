package nl.giejay.android.tv.immich.home

import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R

private const val CARD_WIDTH_DP = 90
private const val SPACING_DP = 6

/** A person as a round portrait with the name below. */
class PersonCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val avatarSize = context.resources.getDimensionPixelSize(R.dimen.person_avatar_size)
        val avatar = ImageView(context).apply {
            id = R.id.person_avatar
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.default_card_background_color))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
            foreground = context.getDrawable(R.drawable.avatar_focus_ring)
            isDuplicateParentStateEnabled = true
        }
        val name = TextView(context).apply {
            id = R.id.person_name
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 13f
            setTextColor(context.getColor(R.color.screen_white))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = ViewGroup.LayoutParams((CARD_WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(avatar, LinearLayout.LayoutParams(avatarSize, avatarSize))
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (SPACING_DP * density).toInt()
            })
        }
        return ViewHolder(root)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = (item as HomeCardItem.PersonItem).card
        viewHolder.view.findViewById<TextView>(R.id.person_name).text = card.title
        val avatar = viewHolder.view.findViewById<ImageView>(R.id.person_avatar)
        Glide.with(avatar).load(card.thumbnailUrl).centerCrop().into(avatar)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Glide.with(viewHolder.view).clear(viewHolder.view.findViewById<ImageView>(R.id.person_avatar))
    }
}
