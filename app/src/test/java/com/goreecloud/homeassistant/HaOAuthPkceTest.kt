package com.goreecloud.homeassistant

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class HaOAuthPkceTest {
    private val client = "https://goreecloud.example/oauth/metadata.json"
    private val redirect = "goreecloud-homeassistant://auth/callback"

    @Test fun createsRandomStateAndPkceChallenge() {
        val first = HaOAuthPkce.begin("https://home.example", client, redirect)
        val second = HaOAuthPkce.begin("https://home.example", client, redirect)
        assertNotEquals(first.state, second.state)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
        assertTrue(first.codeVerifier.length in 43..128)
        val url = URI(first.authorizationUrl)
        assertEquals("/auth/authorize", url.path)
        val query = url.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("code", query["response_type"])
        assertEquals(first.state, query["state"])
        assertEquals(client, query["client_id"])
        assertEquals(redirect, query["redirect_uri"])
        assertEquals(43, query.getValue("code_challenge").length)
    }

    @Test fun acceptsMatchingCallbackOnly() {
        val request = HaOAuthPkce.begin("https://home.example", client, redirect)
        val callback = redirect + "?state=" + request.state + "&code=one-time-code"
        assertEquals("one-time-code", HaOAuthPkce.verifiedCode(request, callback))
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.verifiedCode(request, redirect + "?state=forged&code=stolen")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.verifiedCode(request,
                "goreecloud-homeassistant://other/callback?state=" + request.state + "&code=stolen")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.verifiedCode(request,
                redirect + "?state=" + request.state + "&code=a&code=b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.verifiedCode(request,
                redirect + "?state=" + request.state + "&code=x&error=access_denied")
        }
    }

    @Test fun rejectsUnsupportedRedirectSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.begin("https://home.example", client, "javascript:callback")
        }
    }

    @Test fun rejectsUntrustedClientId() {
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.begin("https://home.example", "http://untrusted.example", redirect)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HaOAuthPkce.begin("http://home.example", client, redirect)
        }
    }
}
