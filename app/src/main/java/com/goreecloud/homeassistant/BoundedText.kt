package com.goreecloud.homeassistant

import java.io.IOException
import java.io.Reader

/** Fail closed before an unexpectedly large or chunked API response enters memory. */
object BoundedText {
    fun read(reader: Reader, maxCharacters: Int): String {
        require(maxCharacters > 0)
        return reader.use {
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = it.read(buffer)
                if (count == -1) break
                if (count <= 0) continue
                if (result.length > maxCharacters - count) {
                    throw IOException("State response exceeded its safe size limit.")
                }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
    }
}
