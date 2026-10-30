package com.goreecloud.homeassistant

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A small, bounded subset of the officially documented Home Assistant WebSocket protocol. */
object HaSocketWire {
    const val subscriptionId = 1
    const val maxFrameCharacters = 512_000

    fun auth(token: String): String =
        JSONObject().put("type", "auth").put("access_token", token).toString()

    fun subscribe(): String =
        JSONObject().put("id", subscriptionId).put("type", "subscribe_entities").toString()

    fun parse(raw: String): JSONObject {
        require(raw.length <= maxFrameCharacters) { "WebSocket frame is too large." }
        return JSONObject(raw)
    }

    fun entityEvent(message: JSONObject): Boolean {
        if (message.optString("type") != "event" || message.optInt("id") != subscriptionId) {
            return false
        }
        val event = message.optJSONObject("event") ?: return false
        return event.has("a") || event.has("c") || event.has("r")
    }
}

private data class SessionEnd(val rejected: Boolean, val subscribed: Boolean)

/**
 * Foreground-only, private-network WSS notification channel.
 * REST remains authoritative for actual state and device command outcomes.
 * No token is logged, put in a URL or placed in a WebSocket HTTP header.
 */
class HaLiveFeed {
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun observe(
        connection: HaConnection,
        onSubscribed: () -> Unit,
        onEntityChange: () -> Unit,
        onDisconnected: () -> Unit,
        onAuthenticationRejected: () -> Unit
    ) {
        val https = EndpointRules.normalize(connection.baseUrl)
        require(connection.token.isNotBlank()) { "Home Assistant token missing." }
        val url = "wss://" + https.removePrefix("https://") + "/api/websocket"
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            val end = suspendCancellableCoroutine<SessionEnd> { continuation ->
                val completeOnce = AtomicBoolean(false)
                var phase = 0
                var rejected = false
                var subscribed = false
                var receivedInitial = false

                fun complete() {
                    if (completeOnce.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(SessionEnd(rejected, subscribed))
                    }
                }

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val message = HaSocketWire.parse(text)
                            when (message.optString("type")) {
                                "auth_required" -> if (phase == 0) {
                                    phase = 1
                                    webSocket.send(HaSocketWire.auth(connection.token.trim()))
                                }
                                "auth_ok" -> if (phase == 1) {
                                    phase = 2
                                    webSocket.send(HaSocketWire.subscribe())
                                }
                                "auth_invalid" -> {
                                    rejected = true
                                    webSocket.close(1008, "Authentication rejected")
                                    complete()
                                }
                                "result" -> if (phase == 2 &&
                                    message.optInt("id") == HaSocketWire.subscriptionId) {
                                    if (!message.optBoolean("success", false)) {
                                        webSocket.close(1008, "Subscription unavailable")
                                        complete()
                                    } else {
                                        phase = 3
                                        subscribed = true
                                        onSubscribed()
                                    }
                                }
                                "event" -> if (phase == 3 && HaSocketWire.entityEvent(message)) {
                                    // The first event is the initial snapshot; REST is the source of truth.
                                    if (!receivedInitial) receivedInitial = true
                                    else onEntityChange()
                                }
                            }
                        } catch (_: Exception) {
                            webSocket.close(1003, "Unsupported message")
                            complete()
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        complete()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        // Do not echo raw errors: network exceptions may include sensitive URLs.
                        complete()
                    }
                }

                val socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
                continuation.invokeOnCancellation {
                    completeOnce.set(true)
                    socket.cancel()
                }
            }
            onDisconnected()
            if (end.rejected) {
                onAuthenticationRejected()
                return
            }
            if (end.subscribed) backoff = 1_000L
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }
}
