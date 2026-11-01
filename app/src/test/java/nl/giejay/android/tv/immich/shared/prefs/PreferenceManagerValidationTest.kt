package nl.giejay.android.tv.immich.shared.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceManagerValidationTest {

    @Test
    fun `https server with a key is accepted`() {
        assertTrue(PreferenceManager.isValid("https://immich.example.com:2283", "key"))
    }

    @Test
    fun `http server is rejected`() {
        assertFalse(PreferenceManager.isValid("http://192.168.1.10:2283", "key"))
    }

    @Test
    fun `missing host or key is rejected`() {
        assertFalse(PreferenceManager.isValid("", "key"))
        assertFalse(PreferenceManager.isValid(null, "key"))
        assertFalse(PreferenceManager.isValid("not a url", "key"))
        assertFalse(PreferenceManager.isValid("https://immich.example.com", ""))
        assertFalse(PreferenceManager.isValid("https://immich.example.com", null))
    }
}
