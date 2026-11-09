package com.ppp65isking.box.data

import com.ppp65isking.box.model.MsDriveItem
import com.ppp65isking.box.model.MsEvent
import com.ppp65isking.box.model.MsMailFull
import com.ppp65isking.box.model.MsMailMessage
import com.ppp65isking.box.model.MsMailPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Microsoft Graph behind one OAuth sign-in (Authorization Code +
 * PKCE, public client — no secret anywhere).
 *
 * Outlook mail (list + search + read + reply + compose), calendar
 * (next 7 days, read-only) and OneDrive (browse + search + folders,
 * read-only). Every call takes a fresh token from
 * [ensureAccessToken]; a 401 means the sign-in went stale and the
 * user has to connect again. Callers must run these on
 * Dispatchers.IO.
 *
 * Graph honesty note: reply uses the /reply action (Graph threads it
 * server-side), compose uses /me/sendMail. Mail search uses $search,
 * which only works on the user's own mailbox — that is all Box ever
 * touches.
 */
class MicrosoftGraphClient(private val okHttp: OkHttpClient) {

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

    private fun <T> runCall(
        url: String,
        token: String,
        parse: (kotlinx.serialization.json.JsonObject) -> T,
    ): Result<T> {
        return runCatching {
            get(url, token).use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (!response.isSuccessful) {
                    return Result.Error("Microsoft answered ${response.code}")
                }
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                Result.Ok(parse(root))
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
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
                        "Microsoft refused — tap Connect again to update " +
                            "the permissions.",
                    )
                }
                if (!response.isSuccessful) {
                    return Result.Error("Microsoft answered ${response.code}")
                }
                Result.Ok(Unit)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    /** The signed-in account's display name (fall back to mail). */
    fun me(token: String): Result<String?> =
        runCall(
            "https://graph.microsoft.com/v1.0/me" +
                "?\$select=displayName,mail,userPrincipalName",
            token,
        ) { root ->
            root["displayName"]?.jsonPrimitive?.contentOrNull
                ?: root["mail"]?.jsonPrimitive?.contentOrNull
                ?: root["userPrincipalName"]?.jsonPrimitive?.contentOrNull
        }

    private fun parseMailMessage(obj: kotlinx.serialization.json.JsonObject): MsMailMessage {
        val from = obj["from"]?.jsonObject
            ?.get("emailAddress")?.jsonObject
        return MsMailMessage(
            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            subject = obj["subject"]?.jsonPrimitive?.contentOrNull
                ?: "(no subject)",
            from = from?.get("name")?.jsonPrimitive?.contentOrNull
                ?: from?.get("address")?.jsonPrimitive?.contentOrNull
                ?: "Unknown sender",
            receivedDateTime = obj["receivedDateTime"]?.jsonPrimitive?.contentOrNull,
            bodyPreview = obj["bodyPreview"]?.jsonPrimitive?.contentOrNull,
            isRead = obj["isRead"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: true,
            conversationId = obj["conversationId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Newest mail first, one page at a time. [query] searches the
     *  mailbox (Graph $search); [nextLink] is the previous page's
     *  @odata.nextLink, null on the last page. 25 per page. */
    fun messages(
        token: String,
        query: String?,
        nextLink: String? = null,
    ): Result<MsMailPage> {
        val url = nextLink ?: buildString {
            append("https://graph.microsoft.com/v1.0/me/messages")
            append("?\$top=25&\$orderby=receivedDateTime desc")
            append(
                "&\$select=id,subject,from,receivedDateTime," +
                    "bodyPreview,isRead,conversationId",
            )
            if (!query.isNullOrBlank()) {
                append("&\$search=${encode("\"$query\"")}")
            }
        }
        return runCall(url, token) { root ->
            MsMailPage(
                messages = root["value"]?.jsonArray.orEmpty().mapNotNull {
                    runCatching { parseMailMessage(it.jsonObject) }.getOrNull()
                },
                nextLink = root["@odata.nextLink"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    /** One message, opened to read. HTML bodies are stripped to text
     *  for display. */
    fun message(token: String, id: String): Result<MsMailFull> =
        runCall(
            "https://graph.microsoft.com/v1.0/me/messages/$id" +
                "?\$select=id,subject,from,toRecipients,receivedDateTime," +
                "body,conversationId",
            token,
        ) { root ->
            val from = root["from"]?.jsonObject
                ?.get("emailAddress")?.jsonObject
            val to = root["toRecipients"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("emailAddress")?.jsonObject
                ?.get("address")?.jsonPrimitive?.contentOrNull
            val bodyObj = root["body"]?.jsonObject
            val rawBody = bodyObj?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            val contentType = bodyObj?.get("contentType")?.jsonPrimitive?.contentOrNull
            MsMailFull(
                id = id,
                subject = root["subject"]?.jsonPrimitive?.contentOrNull
                    ?: "(no subject)",
                from = from?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: from?.get("address")?.jsonPrimitive?.contentOrNull
                    ?: "Unknown sender",
                to = to,
                receivedDateTime = root["receivedDateTime"]?.jsonPrimitive?.contentOrNull,
                body = if (contentType == "html") stripHtml(rawBody) else rawBody,
                conversationId = root["conversationId"]?.jsonPrimitive?.contentOrNull,
            )
        }

    /** Reply in-thread: Graph's /reply action sends the comment as
     *  the reply to the original sender, threaded server-side. */
    fun reply(token: String, messageId: String, comment: String): Result<Unit> {
        val body = buildJsonObject { put("comment", comment) }.toString()
        return postJson(
            "https://graph.microsoft.com/v1.0/me/messages/$messageId/reply",
            token,
            body,
        )
    }

    /** New mail, sent from the signed-in account. */
    fun sendMail(token: String, to: String, subject: String, body: String): Result<Unit> {
        val payload = buildJsonObject {
            putJsonObject("message") {
                put("subject", subject)
                putJsonObject("body") {
                    put("contentType", "Text")
                    put("content", body)
                }
                putJsonArray("toRecipients") {
                    addJsonObject {
                        putJsonObject("emailAddress") {
                            put("address", to)
                        }
                    }
                }
            }
            put("saveToSentItems", true)
        }.toString()
        return postJson(
            "https://graph.microsoft.com/v1.0/me/sendMail",
            token,
            payload,
        )
    }

    /** Mark a message read (Graph-side, so Outlook agrees). */
    fun markRead(token: String, messageId: String): Result<Unit> {
        val body = buildJsonObject { put("isRead", true) }.toString()
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/messages/$messageId")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .patch(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (!response.isSuccessful) {
                    return Result.Error("Microsoft answered ${response.code}")
                }
                Result.Ok(Unit)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    /** Events on the primary calendar, now through 7 days out. */
    fun events(token: String): Result<List<MsEvent>> {
        val now = Instant.now()
        val url = "https://graph.microsoft.com/v1.0/me/calendarview" +
            "?startDateTime=${now}&endDateTime=${now.plus(7, ChronoUnit.DAYS)}" +
            "&\$orderby=start/dateTime&\$top=10" +
            "&\$select=subject,start,end,location"
        return runCall(url, token) { root ->
            root["value"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    MsEvent(
                        subject = obj["subject"]?.jsonPrimitive?.contentOrNull
                            ?: "(no title)",
                        start = obj["start"]?.jsonObject
                            ?.get("dateTime")?.jsonPrimitive?.contentOrNull,
                        location = obj["location"]?.jsonObject
                            ?.get("displayName")?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }
    }

    private fun parseDriveItem(obj: kotlinx.serialization.json.JsonObject): MsDriveItem =
        MsDriveItem(
            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isFolder = obj["folder"] != null,
            webUrl = obj["webUrl"]?.jsonPrimitive?.contentOrNull,
            lastModified = obj["lastModifiedDateTime"]?.jsonPrimitive?.contentOrNull,
        )

    /** OneDrive items. [parentId] lists inside a folder (null = the
     *  drive root). [query] searches the whole drive by name. */
    fun driveItems(
        token: String,
        parentId: String? = null,
        query: String? = null,
    ): Result<List<MsDriveItem>> {
        val url = when {
            !query.isNullOrBlank() ->
                "https://graph.microsoft.com/v1.0/me/drive/root/search" +
                    "(q='${query.replace("'", "''")}')?\$top=25" +
                    "&\$select=id,name,folder,webUrl,lastModifiedDateTime"
            parentId != null ->
                "https://graph.microsoft.com/v1.0/me/drive/items/$parentId/children" +
                    "?\$top=25&\$orderby=name" +
                    "&\$select=id,name,folder,webUrl,lastModifiedDateTime"
            else ->
                "https://graph.microsoft.com/v1.0/me/drive/root/children" +
                    "?\$top=25&\$orderby=name" +
                    "&\$select=id,name,folder,webUrl,lastModifiedDateTime"
        }
        return runCall(url, token) { root ->
            root["value"]?.jsonArray.orEmpty().mapNotNull {
                runCatching { parseDriveItem(it.jsonObject) }.getOrNull()
            }
        }
    }

    /** Strip HTML tags and unescape the common entities, for display. */
    private fun stripHtml(html: String): String {
        var text = html.replace(Regex("<(br|p|div|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<[^>]+>"), "")
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
