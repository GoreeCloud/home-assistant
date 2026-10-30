package com.goreecloud.homeassistant

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.StringReader

class BoundedTextTest {
    @Test fun readsWithinLimit() {
        assertEquals("sample", BoundedText.read(StringReader("sample"), 6))
    }

    @Test fun rejectsOneCharacterOverLimit() {
        assertThrows(IOException::class.java) {
            BoundedText.read(StringReader("abcdefg"), 6)
        }
    }

    @Test fun rejectsInvalidSize() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedText.read(StringReader("abc"), 0)
        }
    }

    @Test fun handlesEmptyInput() {
        assertEquals("", BoundedText.read(StringReader(""), 2))
    }
}
