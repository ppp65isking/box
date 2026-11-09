package com.ppp65isking.box.data

import com.ppp65isking.box.model.CalendarEvent
import com.ppp65isking.box.model.DriveFile
import com.ppp65isking.box.model.GmailFullMessage
import com.ppp65isking.box.model.GmailMessage
import com.ppp65isking.box.model.GmailPage
import com.ppp65isking.box.model.GmailThreadMessage
import com.ppp65isking.box.model.GoogleProfile
import com.ppp65isking.box.model.PhotoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Google APIs behind one OAuth sign-in.
 *
 * Gmail (read + search + send for replies and new mail), Calendar
 * (next 7 days, read-only), Drive (recent files + search + folders,
 * read-only), and Photos (Library API list). Every call takes a fresh
 * token from [ensureAccessToken]; a 401 means the sign-in went stale
 * and the user has to connect again. Callers must run these on
 * Dispatchers.IO.
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

    /** Newest mail first, one page at a time. [query] is a Gmail
     *  search string, e.g. "from:amazon". Pass the previous page's
     *  [GmailPage.nextPageToken] to load more; it is null on the
     *  last page. 25 per page — the list grows with "Load more"
     *  instead of a short fixed list. Each message also carries its
     *  labelIds, so the list can render Gmail-style (bold unread,
     *  star, category chips). */
    fun messages(
        token: String,
        query: String?,
        maxResults: Int = 25,
        pageToken: String? = null,
    ): Result<GmailPage> {
        val listUrl = buildString {
            append("https://gmail.googleapis.com/gmail/v1/users/me/messages")
            append("?maxResults=$maxResults")
            if (!query.isNullOrBlank()) append("&q=${encode(query)}")
            if (pageToken != null) append("&pageToken=${encode(pageToken)}")
        }
        val page = when (val listed = runCall(listUrl, token) { root ->
            val ids = root["messages"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
            val next = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
            ids to next
        }) {
            is Result.Ok -> listed.value
            is Result.Unauthorized -> return Result.Unauthorized
            is Result.Error -> return listed
        }
        val (ids, nextToken) = page
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
                val labelIds = root["labelIds"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonPrimitive.contentOrNull }
                GmailMessage(
                    id = id,
                    subject = headers["Subject"] ?: "(no subject)",
                    from = headers["From"] ?: "Unknown sender",
                    date = headers["Date"],
                    snippet = root["snippet"]?.jsonPrimitive?.contentOrNull,
                    unread = "UNREAD" in labelIds,
                    starred = "STARRED" in labelIds,
                    labels = labelIds.mapNotNull { CATEGORY_LABELS[it] },
                )
            }) {
                is Result.Ok -> full.value
                else -> null
            }
        }
        return Result.Ok(GmailPage(messages, nextToken))
    }

    /** One message, opened to read — full body plus the threading
     *  headers a reply needs. Prefers the text/plain part; falls
     *  back to the snippet when there is no readable body. */
    fun message(token: String, id: String): Result<GmailFullMessage> =
        runCall(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id" +
                "?format=full",
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
            val payload = root["payload"]?.jsonObject
            val body = payload?.let { extractTextBody(it) }.orEmpty()
                .ifBlank { root["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            GmailFullMessage(
                id = id,
                threadId = root["threadId"]?.jsonPrimitive?.contentOrNull,
                subject = headers["Subject"] ?: "(no subject)",
                from = headers["From"] ?: "Unknown sender",
                to = headers["To"],
                date = headers["Date"],
                messageId = headers["Message-ID"],
                references = headers["References"],
                body = body,
            )
        }

    /** The whole conversation a message belongs to, oldest first —
     *  the reader shows the thread stacked, Gmail-style. A thread
     *  with one message reads exactly like the old single-message
     *  reader. Returns null (not an error) when the thread can't be
     *  loaded, so the caller falls back to the single message. */
    fun thread(token: String, threadId: String): List<GmailThreadMessage>? {
        val result = runCall(
            "https://gmail.googleapis.com/gmail/v1/users/me/threads/$threadId" +
                "?format=full",
            token,
        ) { root ->
            root["messages"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    val headers = obj["payload"]?.jsonObject
                        ?.get("headers")?.jsonArray.orEmpty()
                        .mapNotNull {
                            val header = it.jsonObject
                            val name = header["name"]?.jsonPrimitive?.contentOrNull
                            val value = header["value"]?.jsonPrimitive?.contentOrNull
                            if (name != null && value != null) name to value else null
                        }.toMap()
                    val payload = obj["payload"]?.jsonObject
                    GmailThreadMessage(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        from = headers["From"] ?: "Unknown sender",
                        date = headers["Date"],
                        body = payload?.let { extractTextBody(it) }.orEmpty()
                            .ifBlank {
                                obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            },
                    )
                }.getOrNull()
            }
        }
        return when (result) {
            is Result.Ok -> result.value.ifEmpty { null }
            else -> null
        }
    }

    /** Add / remove Gmail labels on a message — STARRED for the star
     *  toggle, UNREAD removal to mark a message read when opened.
     *  Silent best-effort: a failure just leaves the list as it was. */
    fun modifyLabels(
        token: String,
        messageId: String,
        add: List<String> = emptyList(),
        remove: List<String> = emptyList(),
    ): Result<Unit> {
        val body = buildJsonObject {
            if (add.isNotEmpty()) {
                put(
                    "addLabelIds",
                    kotlinx.serialization.json.JsonArray(
                        add.map { kotlinx.serialization.json.JsonPrimitive(it) },
                    ),
                )
            }
            if (remove.isNotEmpty()) {
                put(
                    "removeLabelIds",
                    kotlinx.serialization.json.JsonArray(
                        remove.map { kotlinx.serialization.json.JsonPrimitive(it) },
                    ),
                )
            }
        }.toString()
        return postJson(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId/modify",
            token,
            body,
        )
    }

    /** Send a message built by [buildRawMessage]. [threadId] threads
     *  a reply into the original thread; null sends a new message.
     *  A 403 means the grant predates the gmail.send scope — the user
     *  has to tap Connect again. */
    fun sendMessage(token: String, raw: String, threadId: String?): Result<Unit> {
        val body = buildJsonObject {
            put("raw", raw)
            if (threadId != null) put("threadId", threadId)
        }.toString()
        return postJson(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
            token,
            body,
        )
    }

    private fun postJson(url: String, token: String, body: String): Result<Unit> {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (response.code == 403) {
                    return Result.Error(
                        "Google refused — tap Connect again to update " +
                            "the permissions.",
                    )
                }
                if (!response.isSuccessful) {
                    return Result.Error("Google answered ${response.code}")
                }
                Result.Ok(Unit)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    /** Walk a Gmail payload for the first text/plain part, base64url
     *  decoded. Returns "" when there is no readable text part. */
    private fun extractTextBody(payload: kotlinx.serialization.json.JsonObject): String {
        fun walk(obj: kotlinx.serialization.json.JsonObject): String? {
            if (obj["mimeType"]?.jsonPrimitive?.contentOrNull == "text/plain") {
                val data = obj["body"]?.jsonObject
                    ?.get("data")?.jsonPrimitive?.contentOrNull
                if (!data.isNullOrBlank()) return decodeBase64Url(data)
            }
            obj["parts"]?.jsonArray?.forEach { part ->
                val found = walk(part.jsonObject)
                if (!found.isNullOrBlank()) return found
            }
            return null
        }
        return walk(payload).orEmpty()
    }

    private fun decodeBase64Url(data: String): String = runCatching {
        val padded = data + "=".repeat((4 - data.length % 4) % 4)
        String(
            java.util.Base64.getUrlDecoder().decode(padded),
            Charsets.UTF_8,
        )
    }.getOrElse { "" }

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
     * Drive files. [mimeFilter] narrows the list, e.g.
     * "application/vnd.google-apps.document" for Docs or
     * "application/vnd.google-apps.presentation" for Slides; null lists
     * everything, newest first. [query] searches by name, [parentId]
     * lists inside a folder (null = recent files across Drive).
     */
    fun driveFiles(
        token: String,
        mimeFilter: String? = null,
        query: String? = null,
        parentId: String? = null,
    ): Result<List<DriveFile>> {
        val conditions = mutableListOf("trashed = false")
        if (mimeFilter != null) conditions += "mimeType = '$mimeFilter'"
        if (parentId != null) conditions += "'$parentId' in parents"
        if (!query.isNullOrBlank()) {
            conditions += "name contains '${query.replace("'", "\\'")}'"
        }
        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files")
            append("?pageSize=25&orderBy=modifiedTime desc")
            append("&fields=files(id,name,mimeType,modifiedTime,webViewLink)")
            append("&q=${encode(conditions.joinToString(" and "))}")
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

    companion object {
        /** Gmail category labelIds → the chip text the list shows. */
        val CATEGORY_LABELS = mapOf(
            "CATEGORY_PERSONAL" to "Primary",
            "CATEGORY_SOCIAL" to "Social",
            "CATEGORY_PROMOTIONS" to "Promotions",
            "CATEGORY_UPDATES" to "Updates",
            "CATEGORY_FORUMS" to "Forums",
        )
    }
}

/** Build an RFC 822 message and base64url-encode it for the Gmail
 *  send endpoint. [inReplyTo] / [references] thread a reply into the
 *  original thread — pass both null for a new message. The "Re:"
 *  subject is the caller's job. */
fun buildRawMessage(
    to: String,
    subject: String,
    body: String,
    inReplyTo: String?,
    references: String?,
): String {
    val text = buildString {
        append("To: ").append(to).append("\r\n")
        append("Subject: ").append(subject).append("\r\n")
        if (!inReplyTo.isNullOrBlank()) {
            append("In-Reply-To: ").append(inReplyTo).append("\r\n")
        }
        val refs = listOfNotNull(
            references?.takeIf { it.isNotBlank() },
            inReplyTo?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        if (refs.isNotBlank()) append("References: ").append(refs).append("\r\n")
        append("MIME-Version: 1.0\r\n")
        append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
        append("Content-Transfer-Encoding: 8bit\r\n")
        append("\r\n")
        append(body)
    }
    return java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(text.toByteArray(Charsets.UTF_8))
}
