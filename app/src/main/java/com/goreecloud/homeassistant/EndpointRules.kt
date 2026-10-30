package com.goreecloud.homeassistant

import java.net.URI

/** Secure endpoint requirements shared by setup and tests. */
object EndpointRules {
    fun normalize(input: String): String {
        val source = input.trim()
        require(source.isNotEmpty()) { "Enter a Home Assistant HTTPS address." }
        val uri = try {
            URI(source)
        } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid HTTPS address.")
        }
        require(uri.scheme?.lowercase() == "https" && uri.host != null) {
            "Home Assistant requires a trusted HTTPS address."
        }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Do not include credentials, query parameters or fragments in the address."
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Enter the Home Assistant server root address."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "Invalid HTTPS port." }
        return URI("https", null, uri.host, uri.port, null, null, null).toASCIIString()
    }

    fun actionFor(domain: String, state: String): String? = when (domain) {
        "light", "switch", "input_boolean" -> when (state) {
            "on" -> "turn_off"
            "off" -> "turn_on"
            else -> null
        }
        "scene" -> "turn_on"
        else -> null
    }

    fun safeId(id: String, domain: String): Boolean =
        domain in setOf("light", "switch", "input_boolean", "scene") &&
            id.startsWith(domain + ".") &&
            Regex("^[a-z0-9_]+[.][a-z0-9_]+$").matches(id)
}
