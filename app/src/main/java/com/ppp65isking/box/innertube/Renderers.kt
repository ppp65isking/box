package com.ppp65isking.box.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationText: String?,
    val thumbnailUrl: String?,
)

/**
 * Walks search/browse JSON the way ytmusicapi's parsers do, but with plain
 * JsonElement navigation and null-safety everywhere — YouTube's renderer
 * shapes change without notice, so a missing field must yield an empty
 * list, never a crash.
 */
object Renderers {

    fun parseSearchSongs(response: JsonObject): List<Song> {
        val tabs = response["contents"]
            ?.jsonObject?.get("tabbedSearchResultsRenderer")
            ?.jsonObject?.get("tabs")
            ?.jsonArray.orEmpty()
        val sectionContents = tabs.firstNotNullOfOrNull { tab ->
            tab.jsonObject["tabRenderer"]?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
        } ?: return emptyList()
        return sectionContents.flatMap { section ->
            section.jsonObject["musicShelfRenderer"]
                ?.jsonObject?.get("contents")
                ?.jsonArray.orEmpty()
                .mapNotNull { item -> parseSongItem(item.jsonObject) }
        }
    }

    private fun parseSongItem(item: JsonObject): Song? {
        val renderer = item["musicResponsiveListItemRenderer"]?.jsonObject ?: return null
        val videoId = renderer["navigationEndpoint"]?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null
        val flexColumns = renderer["flexColumns"]?.jsonArray.orEmpty()
        val title = flexColumns.getOrNull(0)
            ?.let { columnText(it.jsonObject) } ?: return null
        val artist = flexColumns.getOrNull(1)
            ?.let { columnText(it.jsonObject) } ?: ""
        val duration = renderer["lengthText"]?.jsonObject
            ?.get("simpleText")?.jsonPrimitive?.contentOrNull
        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray.orEmpty()
        val thumb = thumbnails.lastOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        return Song(videoId, title, artist, duration, thumb)
    }

    /** Concatenates the `runs[].text` of a flexColumn's text object. */
    private fun columnText(column: JsonObject): String? {
        val text = column["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
            ?.get("text")?.jsonObject ?: return null
        val runs: JsonArray? = text["runs"]?.jsonArray
        if (runs != null) {
            return runs.joinToString("") { run ->
                run.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }
        return text["simpleText"]?.jsonPrimitive?.contentOrNull
    }
}
