package com.goreecloud.homeassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HaConnection(val baseUrl: String, val token: String)

data class HaEntity(
    val id: String,
    val name: String,
    val domain: String,
    val state: String,
    val unit: String
)

/** No automatic redirects: bearer tokens must never be forwarded to a redirected host. */
class HaClient {
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun entities(connection: HaConnection): List<HaEntity> = withContext(Dispatchers.IO) {
        val address = EndpointRules.normalize(connection.baseUrl)
        require(connection.token.isNotBlank()) { "A Home Assistant token is required." }
        val request = Request.Builder()
            .url(address + "/api/states")
            .header("Authorization", "Bearer " + connection.token.trim())
            .header("Accept", "application/json")
            .get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("State read failed (HTTP " + response.code + ").")
            val body = response.body ?: throw IOException("Home Assistant returned no state data.")
            if (body.contentLength() > 4_000_000L) throw IOException("State response exceeded 4 MB.")
            val raw = body.charStream().use { it.readText() }
            if (raw.length > 4_000_000) throw IOException("State response exceeded 4 MB.")
            val json = JSONArray(raw)
            val result = ArrayList<HaEntity>()
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val id = item.optString("entity_id")
                val domain = id.substringBefore(".")
                if (domain !in DISPLAY_DOMAINS || id.length > 200) continue
                val attrs = item.optJSONObject("attributes") ?: JSONObject()
                val title = attrs.optString("friendly_name").takeIf { it.isNotBlank() } ?: id
                result.add(HaEntity(
                    id, title.take(90), domain,
                    item.optString("state", "unknown").take(80),
                    attrs.optString("unit_of_measurement", "").take(20)
                ))
                if (result.size >= 500) break
            }
            result.sortedWith(compareBy<HaEntity> { it.domain }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun activate(connection: HaConnection, entity: HaEntity) = withContext(Dispatchers.IO) {
        val action = EndpointRules.actionFor(entity.domain, entity.state)
            ?: throw IllegalArgumentException("This entity has no supported action.")
        require(EndpointRules.safeId(entity.id, entity.domain)) { "Invalid entity identifier." }
        val json = JSONObject().put("entity_id", entity.id).toString()
        val request = Request.Builder()
            .url(EndpointRules.normalize(connection.baseUrl) +
                "/api/services/" + entity.domain + "/" + action)
            .header("Authorization", "Bearer " + connection.token.trim())
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Control request failed (HTTP " + response.code + ").")
        }
    }

    companion object {
        private val DISPLAY_DOMAINS = setOf(
            "light", "switch", "input_boolean", "scene",
            "sensor", "binary_sensor", "climate"
        )
    }
}
