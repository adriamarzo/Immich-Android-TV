package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.interceptor.ResponseLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

object ApiClientFactory {

    /**
     * @param serverUrl read on every request, so a client outliving a server change never sends
     * the key to the previous server.
     */
    fun getClient(apiKey: String, debugMode: Boolean, serverUrl: () -> String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .followSslRedirects(false)
            // A network interceptor runs for every redirect hop. An application interceptor would
            // run once, and OkHttp would copy x-api-key onto a redirect to another host.
            .addNetworkInterceptor(apiKeyInterceptor(apiKey, serverUrl))
        return if (debugMode) {
            builder.addInterceptor(ResponseLoggingInterceptor()).build()
        } else {
            builder.build()
        }
    }

    internal fun apiKeyInterceptor(apiKey: String, serverUrl: () -> String): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val server = serverUrl().toHttpUrlOrNull()
        val sameOrigin = server != null &&
            request.url.scheme == server.scheme &&
            request.url.host == server.host &&
            request.url.port == server.port
        if (!sameOrigin) {
            return@Interceptor chain.proceed(request)
        }
        val authenticated = try {
            request.newBuilder().addHeader("x-api-key", apiKey.trim()).build()
        } catch (_: IllegalArgumentException) {
            // The exception text can quote the invalid header value, so it is not logged.
            Timber.e("Could not add API key header, invalid format")
            request
        }
        chain.proceed(authenticated)
    }
}
