package nl.giejay.mediaslider.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.Format
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.view.MediaSliderController
import java.util.Locale

/** A button on videos that shows the resolution, frame rate, HDR and codec of the track being played. */
class VideoInfoPlugin : SliderControllerPlugin {
    private var panel: TextView? = null

    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        hidePanel() // the page changed
        if (!context.isVideo) return null
        val button = createDefaultControllerButton(context, R.string.video_info, R.drawable.ic_video_info)
        button.setOnClickListener { togglePanel(context) }
        return ControllerButtonSpec(button, ControllerButtonPlacement.END)
    }

    override fun onControllerVisibilityChanged(
        isVisible: Boolean,
        rootView: View,
        controller: MediaSliderController,
        config: MediaSliderConfiguration
    ) {
        if (!isVisible) hidePanel()
    }

    override fun onDestroy(controller: MediaSliderController) {
        (panel?.parent as? ConstraintLayout)?.removeView(panel)
        panel = null
    }

    private fun togglePanel(context: ControllerPluginContext) {
        if (panel?.visibility == View.VISIBLE) {
            hidePanel()
            return
        }
        val view = panel ?: createPanel(context) ?: return
        view.text = panelText(context.context, context.controller)
        view.visibility = View.VISIBLE
    }

    private fun hidePanel() {
        panel?.visibility = View.GONE
    }

    private fun createPanel(context: ControllerPluginContext): TextView? {
        val root = context.rootView as? ConstraintLayout ?: return null
        val view = LayoutInflater.from(context.context).inflate(R.layout.video_info_panel, root, false) as TextView
        root.addView(view)
        panel = view
        return view
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun panelText(context: Context, controller: MediaSliderController): String {
        val format = controller.currentPlayer?.videoFormat ?: return context.getString(R.string.video_info_loading)
        val track = format.toVideoTrack()
        val unknown = context.getString(R.string.video_info_not_stated)
        return listOf(
            R.string.video_info_resolution to track.resolution(),
            R.string.video_info_frame_rate to track.frameRateText(Locale.getDefault()),
            R.string.video_info_hdr to track.dynamicRange().label,
            R.string.video_info_codec to track.codecName()
        ).joinToString("\n") { (label, value) -> "${context.getString(label)}: ${value ?: unknown}" }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun Format.toVideoTrack() = VideoTrack(
        width, height, rotationDegrees, frameRate, sampleMimeType, codecs, colorInfo?.colorTransfer
    )
}
