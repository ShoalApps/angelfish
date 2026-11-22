package io.github.shoalapps.swordfish.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleTranslatorTest {
    @Test fun joinsPiecesAndReadsSource() {
        val r = TitleTranslator.parse("""[[["Bonjour. ","Hello. ",null,null,10],["Au revoir","Goodbye",null,null,10]],null,"en",null]""", "Hello. Goodbye", "fr")
        assertEquals(TitleTranslator.Result("Bonjour. Au revoir", "en"), r)
    }

    @Test fun keepsOriginalWhenAlreadyInMyLanguage() {
        val r = TitleTranslator.parse("""[[["Salut !","Salut !",null,null,10]],null,"fr"]""", "Salut !", "fr-FR")
        assertEquals("Salut !", r?.text)
    }

    @Test fun rubbishIsNull() {
        assertNull(TitleTranslator.parse("<html>", "x", "fr"))
        assertNull(TitleTranslator.parse("[[],null,\"en\"]", "x", "fr"))
    }
}
