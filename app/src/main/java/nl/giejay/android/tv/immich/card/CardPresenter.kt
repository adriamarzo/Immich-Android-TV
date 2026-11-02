package nl.giejay.android.tv.immich.card

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.presenter.AbstractPresenter
import timber.log.Timber

private const val CARD_CORNER_RADIUS_DP = 7f
private const val VIDEO_BADGE_SIZE_DP = 24
private const val OPAQUE = 255
private const val TRANSPARENT = 0
private const val VIDEO_BADGE_MARGIN_DP = 8

open class CardPresenter(context: Context, style: Int = R.style.DefaultCardTheme) :
    AbstractPresenter<ImageCardView, ICard>(ContextThemeWrapper(context, style)) {

    override fun onBindViewHolder(card: ICard, cardView: ImageCardView) {
        loadImage(card, cardView)

        cardView.tag = card
        cardView.cardType = if ((card as? Card)?.showsInfo() == false) BaseCardView.CARD_TYPE_MAIN_ONLY else BaseCardView.CARD_TYPE_INFO_UNDER
        cardView.setInfoAreaBackgroundColor(Color.TRANSPARENT)
        cardView.titleText = card.title
        if (card.description != "") {
            cardView.contentText = card.description
        }
        setSelected(cardView, card.selected)
        cardView.mainImageView!!.foreground = imageForeground((card as? Card)?.isVideo == true, cardView.hasFocus())
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        super.onUnbindViewHolder(viewHolder)
        if(context is Activity && context.isFinishing){
            return
        }
        try {
            Glide.with(context).clear((viewHolder.view as ImageCardView).mainImageView!!)
        } catch (e: IllegalArgumentException){
            Timber.e(e)
        }
    }

    open fun loadImage(card: ICard, cardView: ImageCardView) {
        card.thumbnailUrl?.let {
            if(it.startsWith("http")){
                Glide.with(context)
                    .asBitmap()
                    .centerCrop()
                    .load(it)
                    .into(cardView.mainImageView!!)
            } else {
                cardView.mainImageView!!.scaleType = ImageView.ScaleType.CENTER_INSIDE
                val resourceId = context.resources.getIdentifier(it, "drawable",
                    context.packageName);
                cardView.mainImageView!!.setImageResource(resourceId)
            }
//                .addListener(object : RequestListener<Bitmap> {
//                    override fun onLoadFailed(
//                        e: GlideException?,
//                        model: Any?,
//                        target: Target<Bitmap>?,
//                        isFirstResource: Boolean
//                    ): Boolean {
//                        return false
//                    }
//
//                    override fun onResourceReady(
//                        resource: Bitmap?,
//                        model: Any?,
//                        target: Target<Bitmap>?,
//                        dataSource: DataSource?,
//                        isFirstResource: Boolean
//                    ): Boolean {
//                        Timber.i("Loaded card image for: ${card.id}")
//                        return false
//                    }
//
//                })
        }
    }

    /** The focus ring, hidden until focused, and for a video a mark: a card without a footer has no badge. */
    private fun imageForeground(isVideo: Boolean, focused: Boolean): Drawable {
        val ring = context.getDrawable(R.drawable.card_focus_ring)!!.mutate()
        ring.alpha = if (focused) OPAQUE else TRANSPARENT
        if (!isVideo) return LayerDrawable(arrayOf(ring)).apply { setId(0, R.id.card_focus_ring_layer) }
        val density = context.resources.displayMetrics.density
        val margin = (VIDEO_BADGE_MARGIN_DP * density).toInt()
        val size = (VIDEO_BADGE_SIZE_DP * density).toInt()
        val mark = context.getDrawable(R.drawable.ic_card_video_mark)!!
        return LayerDrawable(arrayOf(ring, mark)).apply {
            setId(0, R.id.card_focus_ring_layer)
            setLayerGravity(1, Gravity.BOTTOM or Gravity.START)
            setLayerSize(1, size, size)
            setLayerInset(1, margin, margin, margin, margin)
        }
    }

    override fun onCreateView(): ImageCardView {
        return ImageCardView(context).apply {
            mainImageView!!.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.resources.displayMetrics.density * CARD_CORNER_RADIUS_DP)
                }
            }
            mainImageView!!.clipToOutline = true
            // Not a state selector: BaseCardView removes the focused state, and grids activate every card.
            setOnFocusChangeListener { _, hasFocus ->
                (mainImageView!!.foreground as? LayerDrawable)
                    ?.findDrawableByLayerId(R.id.card_focus_ring_layer)
                    ?.alpha = if (hasFocus) OPAQUE else TRANSPARENT
            }
        }
    }

    private fun setSelected(imageCardView: ImageCardView, selected: Boolean) {
        if(selected){
            imageCardView.mainImageView!!.background = context.getDrawable(R.drawable.border)
        } else {
            imageCardView.mainImageView!!.background = null
        }
    }
}