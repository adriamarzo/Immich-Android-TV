package nl.giejay.android.tv.immich.shared.guidedstep

import android.text.InputType
import androidx.leanback.widget.GuidedAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedStepUtilTest {
    private val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    private fun editable(inputType: Int): GuidedAction {
        val actions = mutableListOf<GuidedAction>()
        GuidedStepUtil.addEditableAction(actions, 1L, "title", "", inputType)
        return actions.single()
    }

    // Leanback switches a description to its edit input type while it is being edited.
    @Test
    fun `a password field stays a password field while being edited`() {
        val action = editable(password)
        assertEquals(password, action.descriptionInputType)
        assertEquals(password, action.descriptionEditInputType)
    }

    @Test
    fun `a plain field stays visible while being edited`() {
        val action = editable(InputType.TYPE_CLASS_TEXT)
        assertEquals(InputType.TYPE_CLASS_TEXT, action.descriptionInputType)
        assertEquals(InputType.TYPE_CLASS_TEXT, action.descriptionEditInputType)
    }
}
