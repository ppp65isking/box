package com.ppp65isking.box.data

import com.ppp65isking.box.model.CalendarEvent
import com.ppp65isking.box.model.DriveFile
import com.ppp65isking.box.model.GmailMessage
import com.ppp65isking.box.model.GoogleProfile
import com.ppp65isking.box.model.PhotoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Google APIs behind one OAuth sign-in — read-only.
 *
 * Gmail (read + search), Calendar (next 7 days), Drive (recent files,
 * plus Docs/Slides filtered through the same Drive query), and Photos
 * (Library API list). Every call takes a fresh token from
 * [ensureAccessToken]; a 401 means the sign-in went stale and the user
 * has to connect again. Callers must run these on Dispatchers.IO.
 *
 * Photos honesty note: Google restricted the Photos Library API in 2025,
 * so the list may only show photos the user picked for Box rather than
 * the whole library. That is a Google-side limit, not a Box bug — the
 * screen says so when the list comes back empty.
 */
class GoogleClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The token is dead (revoked/expired beyond refresh) — sign in again. */
        data object Unauthorized : Result<Nothing>
        data class Error(val message: String) : Result<Nothing>
    }

    private fun get(url: String, token: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return okHttp.newCall(request).execute()
    }

    /** Returns null on transport failure; callers map Result from the code. */
    private fun <T> runCall(
        url: String,
        token: String,
        parse: (kotlinx.serialization.json.JsonObject) -> T,
    ): Result<T> {
        return runCatching {
            get(url, token).use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (!response.isSuccessful) {
                    return Result.Error("Google answered ${response.code}")
                }
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                Result.Ok(parse(root))
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    fun profile(token: String): Result<GoogleProfile> =
        runCall("https://www.googleapis.com/oauth2/v3/userinfo", token) { root ->
            GoogleProfile(
                name = root["name"]?.jsonPrimitive?.contentOrNull,
                email = root["email"]?.jsonPrimitive?.contentOrNull,
                pictureUrl = root["picture"]?.jsonPrimitive?.contentOrNull,
            )
        }

    /** Newest mail first. [query] is a Gmail search string, e.g. "from:amazon". */
    fun messages(token: String, query: String?, maxResults: Int = 10): Result<List<GmailMessage>> {
        val listUrl = buildString {
            append("https://gmail.googleapis.com/gmail/v1/users/me/messages")
            append("?maxResults=$maxResults")
            if (!query.isNullOrBlank()) append("&q=${encode(query)}")
        }
        val ids = when (val listed = runCall(listUrl, token) { root ->
            root["messages"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
        }) {
            is Result.Ok -> listed.value
            is Result.Unauthorized -> return Result.Unauthorized
            is Result.Error -> return listed
        }
        val messages = ids.mapNotNull { id ->
            when (val full = runCall(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id" +
                    "?format=metadata&metadataHeaders=Subject" +
                    "&metadataHeaders=From&metadataHeaders=Date",
                token,
            ) { root ->
                val headers = root["payload"]?.jsonObject
                    ?.get("headers")?.jsonArray.orEmpty()
                    .mapNotNull {
                        val obj = it.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull
                        val value = obj["value"]?.jsonPrimitive?.contentOrNull
                        if (name != null && value != null) name to value else null
                    }.toMap()
                GmailMessage(
                    id = id,
                    subject = headers["Subject"] ?: "(no subject)",
                    from = headers["From"] ?: "Unknown sender",
                    date = headers["Date"],
                    snippet = root["snippet"]?.jsonPrimitive?.contentOrNull,
                )
            }) {
                is Result.Ok -> full.value
                else -> null
            }
        }
        return Result.Ok(messages)
    }

    /** Events on the primary calendar, now through 7 days out. */
    fun events(token: String): Result<List<CalendarEvent>> {
        val now = Instant.now()
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
            "?timeMin=${now}&timeMax=${now.plus(7, ChronoUnit.DAYS)}" +
            "&singleEvents=true&orderBy=startTime&maxResults=15"
        return runCall(url, token) { root ->
            root["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    val start = obj["start"]?.jsonObject
                    CalendarEvent(
                        summary = obj["summary"]?.jsonPrimitive?.contentOrNull
                            ?: "(no title)",
                        start = start?.get("dateTime")?.jsonPrimitive?.contentOrNull
                            ?: start?.get("date")?.jsonPrimitive?.contentOrNull,
                        location = obj["location"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }
    }

    /**
     * Recent Drive files. [mimeFilter] narrows the list, e.g.
     * "application/vnd.google-apps.document" for Docs or
     * "application/vnd.google-apps.presentation" for Slides; null lists
     * everything, newest first.
     */
    fun driveFiles(token: String, mimeFilter: String?): Result<List<DriveFile>> {
        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files")
            append("?pageSize=20&orderBy=modifiedTime desc")
            append("&fields=files(id,name,mimeType,modifiedTime,webViewLink)")
            if (mimeFilter != null) {
                append("&q=${encode("mimeType = '$mimeFilter'")}")
            }
        }
        return runCall(url, token) { root ->
            root["files"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    DriveFile(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull,
                        modifiedTime = obj["modifiedTime"]?.jsonPrimitive?.contentOrNull,
                        webViewLink = obj["webViewLink"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }
    }

    /** Newest photos from the Library API (see the class doc — Google may
     *  limit this to user-picked photos). */
    fun photos(token: String): Result<List<PhotoItem>> =
        runCall(
            "https://photoslibrary.googleapis.com/v1/mediaItems" +
                "?pageSize=25&fields=mediaItems(id,filename,mimeType,baseUrl,mediaMetadata)",
            token,
        ) { root ->
            root["mediaItems"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    PhotoItem(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        filename = obj["filename"]?.jsonPrimitive?.contentOrNull,
                        mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull,
                        baseUrl = obj["baseUrl"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
