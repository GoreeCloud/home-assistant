package com.goreecloud.homeassistant

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE S256 and callback verification for Home Assistant's authorization-code flow.
 * This deliberately does not launch OAuth: client ID metadata and deep-link registration
 * must first be hosted and verified, and refresh-token lifecycle must be implemented.
 */
data class HaPendingAuthorization(
    val serverUrl: String,
    val clientId: String,
    val redirectUri: String,
    val state: String,
    val codeVerifier: String,
    val authorizationUrl: String
)

object HaOAuthPkce {
    private val random = SecureRandom()

    fun begin(serverUrl: String, clientId: String, redirectUri: String): HaPendingAuthorization {
        val server = EndpointRules.normalize(serverUrl)
        val client = safeClientUri(clientId)
        val redirect = safeRedirectUri(redirectUri)
        val verifier = randomToken()
        val challenge = base64Url(MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val state = randomToken()
        val parameters = linkedMapOf(
            "client_id" to client.toASCIIString(),
            "redirect_uri" to redirect.toASCIIString(),
            "response_type" to "code",
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256"
        )
        val query = parameters.entries.joinToString("&") {
            percentEncode(it.key) + "=" + percentEncode(it.value)
        }
        return HaPendingAuthorization(
            server, client.toASCIIString(), redirect.toASCIIString(),
            state, verifier, server + "/auth/authorize?" + query
        )
    }

    fun verifiedCode(request: HaPendingAuthorization, callbackUrl: String): String {
        require(callbackUrl.length < 8192) { "Unexpected authorization response." }
        val expected = safeRedirectUri(request.redirectUri)
        val actual = try { URI(callbackUrl) }
            catch (_: Exception) { throw IllegalArgumentException("Invalid authorization callback.") }
        require(actual.scheme.equals(expected.scheme, ignoreCase = true) &&
            actual.host.equals(expected.host, ignoreCase = true) &&
            actual.port == expected.port &&
            actual.rawPath == expected.rawPath &&
            actual.rawFragment == null && actual.rawUserInfo == null
        ) { "Unrecognized authorization callback." }
        val query = actual.rawQuery ?: throw IllegalArgumentException("Authorization response is missing.")
        val params = query.split("&").map { part ->
            val pair = part.split("=", limit = 2)
            percentDecode(pair[0]) to percentDecode(pair.getOrElse(1) { "" })
        }.groupBy({ it.first }, { it.second })
        require(params["state"]?.size == 1 && params["code"]?.size == 1 &&
            params["error"] == null) { "Authorization response is invalid." }
        val givenState = params.getValue("state").single()
        require(MessageDigest.isEqual(
            request.state.toByteArray(StandardCharsets.US_ASCII),
            givenState.toByteArray(StandardCharsets.US_ASCII)
        )) { "Authorization state mismatch." }
        val code = params.getValue("code").single()
        require(code.isNotBlank() && code.length <= 4096) { "Authorization code missing or invalid." }
        return code
    }

    private fun safeClientUri(input: String): URI {
        require(input.length in 8..512)
        val uri = URI(input)
        require(uri.scheme.equals("https", ignoreCase = true) &&
            uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null
        ) { "Client ID must be a hosted HTTPS URL." }
        return uri
    }

    private fun safeRedirectUri(input: String): URI {
        require(input.length in 4..2048)
        val uri = URI(input)
        require(uri.isAbsolute && uri.rawFragment == null && uri.rawUserInfo == null) {
            "Invalid redirect URI."
        }
        if (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) {
            require(uri.host != null) { "Invalid web redirect URI." }
        }
        return uri
    }

    private fun randomToken(): String {
        val entropy = ByteArray(32)
        random.nextBytes(entropy)
        return base64Url(entropy)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun percentEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun percentDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
