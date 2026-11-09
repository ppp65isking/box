package com.ppp65isking.box.innertube

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A resolved, playable audio stream. URLs expire in ~6h — resolve fresh on
 *  every play, never cache them. */
data class StreamResult(
    /** HLS master manifest — hand to ExoPlayer (preferred). */
    val hlsUrl: String?,
    /** Best direct audio URL (itag 251 Opus, else 140 AAC, else best bitrate). */
    val directUrl: String?,
    val itag: Int?,
    val expiresInSeconds: Long?,
)

/**
 * Player endpoint via the VISIONOS client (the only client returning plain
 * URLs as of Nov 2026 — see Clients.kt).
 *
 * First-run trap: a player call sent without visitorData answers
 * LOGIN_REQUIRED. So resolve() primes visitorData first and retries once
 * on LOGIN_REQUIRED — the failed call harvests it.
 */
class StreamResolver(private val client: InnerTubeClient) {

    fun resolve(videoId: String): StreamResult {
        client.ensureVisitorData()
        var player = playerCall(videoId)
        var status = playabilityStatus(player)
        if (status == "LOGIN_REQUIRED") {
            player = playerCall(videoId)
            status = playabilityStatus(player)
        }
        require(status == "OK") { "player playabilityStatus=$status for $videoId" }
        val streamingData = player["streamingData"]?.jsonObject
            ?: error("player response has no streamingData for $videoId")
        val hls = streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull
        val audio = streamingData["adaptiveFormats"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .filter { format ->
                (format["mimeType"]?.jsonPrimitive?.contentOrNull ?: "")
                    .startsWith("audio/")
            }
            .filter { it.containsKey("url") }
        val best = audio.firstOrNull { it["itag"]?.jsonPrimitive?.intOrNull == 251 }
            ?: audio.firstOrNull { it["itag"]?.jsonPrimitive?.intOrNull == 140 }
            ?: audio.maxByOrNull { it["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L }
        return StreamResult(
            hlsUrl = hls,
            directUrl = best?.get("url")?.jsonPrimitive?.contentOrNull,
            itag = best?.get("itag")?.jsonPrimitive?.intOrNull,
            expiresInSeconds = streamingData["expiresInSeconds"]
                ?.jsonPrimitive?.longOrNull,
        )
    }

    private fun playerCall(videoId: String): JsonObject =
        client.post("player", Clients.VISIONOS) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

    private fun playabilityStatus(player: JsonObject): String? =
        player["playabilityStatus"]
            ?.jsonObject?.get("status")
            ?.jsonPrimitive?.contentOrNull
}
