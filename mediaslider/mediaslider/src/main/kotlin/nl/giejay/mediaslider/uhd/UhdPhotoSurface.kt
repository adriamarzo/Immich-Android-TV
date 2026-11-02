package nl.giejay.mediaslider.uhd

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import timber.log.Timber

/**
 * Shows the current photo at the screen's real resolution on one surface under the pager, with the
 * overlays still on top. The page keeps its preview, used while sliding, and hides it once the
 * photo is on the surface.
 *
 * Memory: photos are decoded to the screen's size at most, never kept in Glide's caches or on disk,
 * and freed once drawn. Only the next photo waits in memory, and the surface is destroyed whenever
 * no photo is on it.
 */
class UhdPhotoSurface(
    parent: ConstraintLayout,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
    private val fit: UhdFit,
    private val maxCutOffWidth: Int,
    private val maxCutOffHeight: Int
) : SurfaceHolder.Callback, ComponentCallbacks2 {
    private val context: Context = parent.context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val surface = SurfaceView(parent.context)

    private var shownIndex: Int? = null
    private var hiddenPage: View? = null
    private var pendingPage: View? = null
    private var current: PhotoLoad? = null
    private var next: PhotoLoad? = null
    private var surfaceReady = false

    init {
        surface.holder.setFormat(PixelFormat.RGBX_8888)
        surface.holder.setFixedSize(surfaceWidth, surfaceHeight)
        surface.holder.addCallback(this)
        surface.visibility = View.GONE
        parent.addView(
            surface, 0,
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        )
        context.registerComponentCallbacks(this)
    }

    fun show(index: Int, url: String, page: View) {
        if (shownIndex == index) return
        leave()
        shownIndex = index
        pendingPage = page
        current = next?.takeIf { it.url == url }?.also { next = null } ?: load(url)
        next?.clear()
        next = null
        current?.onReady = ::photoReady
        if (current?.bitmap != null) photoReady()
    }

    fun preload(url: String) {
        if (next?.url == url || current?.url == url) return
        next?.clear()
        next = load(url)
    }

    /** Gives the screen back to the page, which still has its preview. */
    fun leave() {
        handler.removeCallbacksAndMessages(null)
        shownIndex = null
        pendingPage = null
        current?.clear()
        current = null
        hiddenPage?.visibility = View.VISIBLE
        hiddenPage = null
        surface.visibility = View.GONE
    }

    fun release() {
        leave()
        next?.clear()
        next = null
        context.unregisterComponentCallbacks(this)
    }

    private fun load(url: String): PhotoLoad {
        val load = PhotoLoad(url, surfaceWidth, surfaceHeight)
        Glide.with(context).asBitmap().load(url)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .downsample(DownsampleStrategy.CENTER_INSIDE)
            .into(load)
        return load
    }

    private fun photoReady() {
        surface.visibility = View.VISIBLE
        if (surfaceReady) draw()
    }

    private fun draw() {
        val photo = current?.bitmap ?: return
        val page = pendingPage ?: return
        try {
            val canvas = surface.holder.surface.lockHardwareCanvas()
            try {
                val d = uhdDestination(
                    photo.width, photo.height, surfaceWidth, surfaceHeight, fit, maxCutOffWidth, maxCutOffHeight
                )
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(photo, null, RectF(d.left, d.top, d.right, d.bottom), paint)
            } finally {
                surface.holder.surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: RuntimeException) {
            // The page keeps showing its preview.
            Timber.e(e, "Could not draw the photo on the surface")
            leave()
            return
        }
        current?.clear()
        current = null
        pendingPage = null
        handler.postDelayed({
            page.visibility = View.INVISIBLE
            hiddenPage = page
        }, FIRST_FRAME_DELAY_MILLIS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        draw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            next?.clear()
            next = null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    private inner class PhotoLoad(val url: String, width: Int, height: Int) : CustomTarget<Bitmap>(width, height) {
        var bitmap: Bitmap? = null
        var onReady: (() -> Unit)? = null

        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            bitmap = resource
            // Drawing frees the photo, and Glide does not allow that from inside this callback.
            handler.post { onReady?.invoke() }
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            bitmap = null
        }

        // A format the decoder cannot read, or a failed download: the page keeps its preview.
        override fun onLoadFailed(errorDrawable: Drawable?) {
            Timber.w("Could not load the photo for the surface")
        }

        fun clear() {
            onReady = null
            bitmap = null
            Glide.with(context).clear(this)
        }
    }

    private companion object {
        // Keeps the preview until the surface has had time to reach the screen.
        const val FIRST_FRAME_DELAY_MILLIS = 100L
    }
}
