package nl.giejay.android.tv.immich.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.FolderFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import nl.giejay.android.tv.immich.timeline.TimelineFragment

const val SECTION_TIMELINE = "timeline"
const val SECTION_ALBUMS = "albums"
const val SECTION_FOLDERS = "folders"
const val SECTION_SETTINGS = "settings"

/**
 * Hosts one section opened from the "More" row.
 *
 * This is a plain Fragment and not a BrowseSupportFragment with a PageRow: Browse only resolves a
 * PageRow while it can show headers, so HEADERS_DISABLED would ignore the row, and HEADERS_HIDDEN
 * would bring the side menu back on D-pad left.
 */
class HomeFragment : Fragment(R.layout.fragment_home_section) {
    private val args: HomeFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Coming back from a detail screen, the section is still there with its position.
        if (childFragmentManager.findFragmentById(R.id.home_section_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.home_section_container, createSection(args.section))
                .commit()
        }
    }

    private fun createSection(section: String): Fragment = when (section) {
        SECTION_ALBUMS -> AlbumFragment().apply {
            arguments = bundleOf("selectionMode" to false)
            title = this@HomeFragment.getString(R.string.albums)
        }
        SECTION_FOLDERS -> FolderFragment().apply {
            title = this@HomeFragment.getString(R.string.folders)
        }
        SECTION_SETTINGS -> SettingsFragment()
        else -> TimelineFragment()
    }
}
