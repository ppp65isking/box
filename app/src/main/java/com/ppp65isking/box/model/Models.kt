package com.ppp65isking.box.model

/** How deeply Box can reach into an app (see box-plan.md). */
enum class Tier { DEEP, INBOX, LAUNCHER }

enum class Category {
    FOOD, CHAT, MUSIC, VIDEO, SOCIAL, EMAIL, FILES, SCHOOL,
    MONEY, GAMES, AI, HEALTH, SYSTEM, OTHER,
}

/** One of Anthony's 115 apps, from the app-drawer screenshots (2026-11-08). */
data class AppEntry(
    val name: String,
    /** Play package name when known; null = match by label at runtime. */
    val packageName: String?,
    val tier: Tier,
    val category: Category,
)

/** A notification captured by Box's NotificationListenerService. */
data class BoxNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    /** The notification's own tap intent, when it has one. Tapping
     *  the row fires this (see openNotificationTap) so it opens the
     *  exact screen in the source app; it can expire, so callers
     *  always fall back to launching the app. In-memory only. */
    val contentIntent: android.app.PendingIntent? = null,
)

/** A public GitHub repo (unauthenticated search API). No owner photos —
 *  search results show the repo, not the person behind it. */
data class GitHubRepo(
    val fullName: String,
    val description: String?,
    val stars: Int,
    val language: String?,
    val htmlUrl: String,
)

/** A public Reddit post (unauthenticated .json endpoints). */
data class RedditPost(
    val title: String,
    val subreddit: String,
    val author: String,
    val score: Int,
    val comments: Int,
    val url: String,
    val thumbnail: String?,
    val permalink: String,
)

/** One SMS conversation (thread). */
data class SmsConversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
)

/** One SMS message inside a conversation. Read-only in v1. */
data class SmsMessage(
    val id: Long,
    val body: String,
    val date: Long,
    val isOutgoing: Boolean,
)

/** --- Google (one OAuth sign-in, read-only) --- */

/** The signed-in Google account (userinfo endpoint). */
data class GoogleProfile(
    val name: String?,
    val email: String?,
    val pictureUrl: String?,
)

/** One Gmail message (subject/from/date + snippet). */
data class GmailMessage(
    val id: String,
    val subject: String,
    val from: String,
    val date: String?,
    val snippet: String?,
)

/** One page of Gmail results — [nextPageToken] is null on the last
 *  page. The list grows with "Load more" instead of a short fixed list. */
data class GmailPage(
    val messages: List<GmailMessage>,
    val nextPageToken: String?,
)

/** One Gmail message, opened to read — full body plus the threading
 *  headers a reply needs (threadId, Message-ID, References). */
data class GmailFullMessage(
    val id: String,
    val threadId: String?,
    val subject: String,
    val from: String,
    val to: String?,
    val date: String?,
    val messageId: String?,
    val references: String?,
    val body: String,
)

/** One Calendar event in the next 7 days. `start` is an RFC 3339
 *  timestamp or a plain date for all-day events. */
data class CalendarEvent(
    val summary: String,
    val start: String?,
    val location: String?,
)

/** One Drive file (or a Doc/Slide, filtered by MIME type). */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String?,
    val modifiedTime: String?,
    val webViewLink: String?,
) {
    /** Google Drive folders are a MIME type, not a flag. */
    val isFolder: Boolean
        get() = mimeType == "application/vnd.google-apps.folder"
}

/** One photo from the Google Photos Library API. `baseUrl` needs a size
 *  suffix (e.g. "=w400") before it can be loaded as an image. */
data class PhotoItem(
    val id: String,
    val filename: String?,
    val mimeType: String?,
    val baseUrl: String?,
)

/** --- Spotify (user OAuth sign-in) --- */

data class SpotifyProfile(
    val displayName: String?,
    val email: String?,
    /** "premium" or "free" — decides whether playback control works. */
    val product: String?,
    val imageUrl: String?,
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?,
    val owner: String?,
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<String>,
    val albumName: String?,
    val imageUrl: String?,
    val durationMs: Long,
)

data class SpotifyNowPlaying(
    val track: SpotifyTrack?,
    val isPlaying: Boolean,
    val progressMs: Long,
)

/** --- Discord (user OAuth2 sign-in, read-only) --- */

data class DiscordUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
)

data class DiscordGuild(
    val id: String,
    val name: String,
    val iconUrl: String?,
)

/** A text (type 0) or announcement (type 5) channel. */
data class DiscordChannel(
    val id: String,
    val name: String,
    val type: Int,
    val position: Int,
)

data class DiscordMessage(
    val id: String,
    val content: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val timestamp: String?,
)

/** Current weather in Woburn, MA (Open-Meteo, no key). */
data class WeatherNow(
    val tempC: Double,
    val humidity: Int,
    val windKph: Double,
    val code: Int,
    val isDay: Boolean,
) {
    val tempF: Int get() = (tempC * 9.0 / 5.0 + 32).toInt()
    val label: String
        get() = when (code) {
            0 -> "Clear sky"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61 -> "Light rain"
            63 -> "Rain"
            65 -> "Heavy rain"
            66, 67 -> "Freezing rain"
            71 -> "Light snow"
            73 -> "Snow"
            75 -> "Heavy snow"
            77 -> "Snow grains"
            80 -> "Light showers"
            81 -> "Showers"
            82 -> "Heavy showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Storm with hail"
            else -> "Cloudy"
        }
}
