package nl.giejay.android.tv.immich.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `blank queries are rejected and others trimmed`() {
        assertNull(normalizeQuery(null))
        assertNull(normalizeQuery(""))
        assertNull(normalizeQuery("   \n"))
        assertEquals("coche rojo", normalizeQuery("  coche rojo "))
    }
}
