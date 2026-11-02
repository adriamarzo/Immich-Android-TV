package nl.giejay.android.tv.immich.search

import androidx.annotation.StringRes
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.SearchFailure

@StringRes
fun SearchFailure.messageRes(): Int = when (this) {
    SearchFailure.NOT_ENABLED -> R.string.search_not_enabled
    SearchFailure.OFFLINE -> R.string.home_server_unreachable
    SearchFailure.OTHER -> R.string.search_failed
}
