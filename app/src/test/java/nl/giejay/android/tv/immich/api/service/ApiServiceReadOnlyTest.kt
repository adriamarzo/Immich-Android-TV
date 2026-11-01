package nl.giejay.android.tv.immich.api.service

import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

class ApiServiceReadOnlyTest {
    @Test
    fun `service only reads`() {
        val http = ApiService::class.java.methods.flatMap { it.annotations.toList() }
        assertTrue(http.none { it is PUT || it is DELETE || it is PATCH })
        val posts = http.filterIsInstance<POST>().map { it.value }
        assertTrue(posts.toString(), posts.all { it.startsWith("search/") })
    }
}
