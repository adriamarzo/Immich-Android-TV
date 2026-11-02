package nl.giejay.android.tv.immich.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R

/**
 * Search input: a text field and a microphone button. Dictation only starts when the button is
 * pressed. The query is passed to the results screen as a navigation argument and is never
 * logged or stored.
 */
class SearchFragment : Fragment() {
    private var input: EditText? = null
    private var mic: View? = null

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // A cancelled recogniser, or a result that arrives after the view is gone, does nothing.
        if (view == null || result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val query = normalizeQuery(spoken) ?: return@registerForActivityResult
        input?.setText(query)
        input?.setSelection(query.length)
        openResults(query)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val field = view.findViewById<EditText>(R.id.search_input)
        val button = view.findViewById<View>(R.id.search_voice)
        input = field
        mic = button

        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // An empty query does nothing and focus stays on the field.
                normalizeQuery(field.text?.toString())?.let { openResults(it) }
                true
            } else {
                false
            }
        }
        button.setOnClickListener { startDictation() }
        field.requestFocus()
    }

    override fun onDestroyView() {
        input = null
        mic = null
        super.onDestroyView()
    }

    private fun startDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.search_voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openResults(query: String) {
        if (view == null || !isAdded) return
        val nav = findNavController()
        // Guards against the IME action and D-pad centre both firing.
        if (nav.currentDestination?.id != R.id.searchFragment) return
        nav.navigate(SearchFragmentDirections.actionSearchToResults(query))
    }
}
