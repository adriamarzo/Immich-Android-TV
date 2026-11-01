package nl.giejay.android.tv.immich.api

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ApiClientFactoryTest {

    private val server = "https://immich.example.com:2283"

    /** Runs the interceptor for [url] and returns the x-api-key the outgoing request carried. */
    private fun sentKey(url: String, configured: String = server, apiKey: String = "secret"): String? {
        val chain = mock(Interceptor.Chain::class.java)
        val request = Request.Builder().url(url).build()
        var proceeded: Request? = null
        `when`(chain.request()).thenReturn(request)
        `when`(chain.proceed(org.mockito.kotlin.any())).thenAnswer { call ->
            proceeded = call.getArgument(0)
            Response.Builder()
                .request(proceeded!!)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        ApiClientFactory.apiKeyInterceptor(apiKey) { configured }.intercept(chain)
        return proceeded!!.header("x-api-key")
    }

    @Test
    fun `key is sent to the configured server`() {
        assertEquals("secret", sentKey("$server/api/albums"))
    }

    @Test
    fun `key is not sent to another host`() {
        assertNull(sentKey("https://attacker.example.net:2283/api/albums"))
    }

    @Test
    fun `key is not sent to another port on the same host`() {
        assertNull(sentKey("https://immich.example.com:8443/api/albums"))
    }

    @Test
    fun `key is not sent over cleartext to the same host`() {
        assertNull(sentKey("http://immich.example.com:2283/api/albums"))
    }

    @Test
    fun `key is not sent when the configured server is not a valid url`() {
        assertNull(sentKey("$server/api/albums", configured = ""))
    }

    @Test
    fun `an invalid key does not abort the request`() {
        assertNull(sentKey("$server/api/albums", apiKey = "bad\nkey"))
    }

    @Test
    fun `client does not follow redirects between https and http`() {
        assertFalse(ApiClientFactory.getClient("secret", false) { server }.followSslRedirects)
    }

    @Test
    fun `key interceptor runs on every redirect hop`() {
        val client = ApiClientFactory.getClient("secret", false) { server }
        assertEquals(1, client.networkInterceptors.size)
        assertEquals(0, client.interceptors.size)
    }
}
