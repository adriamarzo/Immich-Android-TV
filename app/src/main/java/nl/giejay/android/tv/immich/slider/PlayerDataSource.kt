package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import nl.giejay.android.tv.immich.api.ApiClientFactory
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

/**
 * Video data source for every player in the app. The key is added per connection to the configured
 * server only. HttpURLConnection, under DefaultHttpDataSource, would forward an x-api-key request
 * header across a redirect to another host.
 */
@SuppressLint("UnsafeOptInUsageError")
fun playerDataSourceFactory(): DataSource.Factory =
    OkHttpDataSource.Factory(
        ApiClientFactory.getPlayerClient(PreferenceManager.get(API_KEY)) { PreferenceManager.hostName }
    )
