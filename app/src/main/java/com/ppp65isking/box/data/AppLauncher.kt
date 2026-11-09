package com.ppp65isking.box.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import com.ppp65isking.box.model.AppEntry

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

enum class AppStatus { INSTALLED, NOT_INSTALLED }

data class ResolvedApp(
    val entry: AppEntry,
    val status: AppStatus,
    /** Real package to launch, or null when not installed. */
    val packageName: String?,
    val icon: Drawable?,
)

/** All launchable apps on the device, from PackageManager. */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos: List<ResolveInfo> = runCatching {
        context.packageManager.queryIntentActivities(intent, 0)
    }.getOrElse { emptyList() }
    return infos.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        InstalledApp(
            packageName = activityInfo.packageName,
            label = info.loadLabel(context.packageManager).toString(),
            icon = runCatching { info.loadIcon(context.packageManager) }.getOrNull(),
        )
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

/** Match a catalog entry to what's actually installed. Package first,
 *  then label — so a wrong or missing package in the catalog still
 *  launches the app when its label matches. */
fun resolveEntry(
    entry: AppEntry,
    byPackage: Map<String, InstalledApp>,
    byLabel: Map<String, InstalledApp>,
): ResolvedApp {
    entry.packageName?.let { pkg ->
        byPackage[pkg]?.let { return ResolvedApp(entry, AppStatus.INSTALLED, pkg, it.icon) }
    }
    byLabel[entry.name.lowercase()]?.let {
        return ResolvedApp(entry, AppStatus.INSTALLED, it.packageName, it.icon)
    }
    return ResolvedApp(entry, AppStatus.NOT_INSTALLED, null, null)
}

/** Box has a built-in Hub for a few apps — tap one on Home and the Hub
 *  opens instead of launching the outside app. Keyed by package, with a
 *  name fallback for catalog entries whose package is unknown. */
private val HUB_BY_PACKAGE = mapOf(
    "com.github.android" to "github",
    "com.reddit.frontpage" to "reddit",
    "com.google.android.apps.messaging" to "sms",
    "com.google.android.gm" to "google",
    "com.spotify.music" to "spotify",
    "com.discord" to "discord",
    "com.valvesoftware.android.steam.community" to "steam",
)

fun hubForPackage(packageName: String): String? = HUB_BY_PACKAGE[packageName]

fun hubForEntry(entry: AppEntry): String? =
    entry.packageName?.let { HUB_BY_PACKAGE[it] }
        ?: when (entry.name.lowercase()) {
            "github" -> "github"
            "reddit" -> "reddit"
            "messages" -> "sms"
            "gmail" -> "google"
            "spotify" -> "spotify"
            "discord" -> "discord"
            "steam" -> "steam"
            else -> null
        }

/** Launch an installed app. Returns false when there is nothing to launch. */
fun openApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

/** Play Store page for the app, or a Play Store search when the package
 *  is unknown. */
fun openPlayStore(context: Context, entry: AppEntry) {
    val storeUri = if (entry.packageName != null) {
        Uri.parse("market://details?id=${entry.packageName}")
    } else {
        Uri.parse("https://play.google.com/store/search?q=${Uri.encode(entry.name)}&c=apps")
    }
    val intent = Intent(Intent.ACTION_VIEW, storeUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val webUri = if (entry.packageName != null) {
            Uri.parse("https://play.google.com/store/apps/details?id=${entry.packageName}")
        } else {
            storeUri
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Open any URL (repo pages, Reddit posts, Play Store web links). */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Fire a quick link: try the deep link first, then the web fallback.
 *  Returns false only when nothing could open, so the UI can say the
 *  link isn't supported. Never uses resolveActivity() — package
 *  visibility (Android 11+) makes it return null for intents Box
 *  doesn't declare in <queries>; try/catch is the reliable pattern. */
fun openQuickLink(context: Context, link: QuickLink, entry: AppEntry): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (link.kind == LinkKind.CUSTOM_SCHEME) {
        // Pin a custom scheme to the app's package so no other app can
        // claim it — only when the package is known.
        entry.packageName?.let { intent.setPackage(it) }
    }
    val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (opened) return true
    val fallback = link.fallbackUrl ?: return false
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallback))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(fallbackIntent); true }.getOrDefault(false)
}

/** Hand a song to the real YouTube Music app. Android resolves the
 *  music.youtube.com link to the YT Music app when it is installed, and
 *  to the browser when it is not — no special-casing needed. */
fun openInYtMusic(context: Context, videoId: String) {
    openUrl(context, "https://music.youtube.com/watch?v=$videoId")
}
