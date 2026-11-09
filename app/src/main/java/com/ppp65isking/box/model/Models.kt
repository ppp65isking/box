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
)

/** A public GitHub repo (unauthenticated search API). */
data class GitHubRepo(
    val fullName: String,
    val description: String?,
    val stars: Int,
    val language: String?,
    val htmlUrl: String,
    val ownerAvatarUrl: String?,
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
