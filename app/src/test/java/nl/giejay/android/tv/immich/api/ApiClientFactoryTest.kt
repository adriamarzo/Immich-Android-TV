package nl.giejay.android.tv.immich.api

import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `client does not follow redirects`() {
        assertFalse(ApiClientFactory.getClient("secret", false) { server }.followRedirects)
        assertFalse(ApiClientFactory.getClient("secret", true) { server }.followRedirects)
    }

    @Test
    fun `player client does not log or buffer bodies and does not follow redirects`() {
        val client = ApiClientFactory.getPlayerClient("secret") { server }
        assertEquals(0, client.interceptors.size)
        assertEquals(1, client.networkInterceptors.size)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun `a redirect to another host is an error and never reaches that host`() {
        val otherHostHits = AtomicInteger()
        val other = localServer { otherHostHits.incrementAndGet(); 200 to emptyMap() }
        var keySeen: String? = null
        val configured = localServer { exchange ->
            keySeen = exchange.requestHeaders.getFirst("x-api-key")
            302 to mapOf("Location" to "http://127.0.0.1:${other.address.port}/stolen")
        }
        try {
            val url = "http://127.0.0.1:${configured.address.port}"
            val client = ApiClientFactory.getClient("secret", false) { url }
            client.newCall(Request.Builder().url("$url/api/albums").build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals("secret", keySeen)
            assertEquals(0, otherHostHits.get())
        } finally {
            configured.stop(0)
            other.stop(0)
        }
    }

    private fun localServer(handler: (com.sun.net.httpserver.HttpExchange) -> Pair<Int, Map<String, String>>): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val (code, headers) = handler(exchange)
                headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
                exchange.sendResponseHeaders(code, -1)
                exchange.close()
            }
            start()
        }
}
