package nl.giejay.android.tv.immich.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardInfoTest {
    private val card = Card("Lisboa", "132", "id", null, null)

    @Test
    fun `a card shows its title and description by default`() = assertTrue(card.showsInfo())

    @Test
    fun `a photo card shows only the image`() = assertFalse(card.copy(imageOnly = true).showsInfo())
}
