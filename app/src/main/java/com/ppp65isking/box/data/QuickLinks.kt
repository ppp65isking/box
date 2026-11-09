package com.ppp65isking.box.data

import com.ppp65isking.box.model.AppEntry

/** How a quick link reaches its screen. */
enum class LinkKind {
    /** A custom scheme the target app registers (spotify:, steam:, geo:).
     *  Fired with the app's package pinned so no other app can claim it. */
    CUSTOM_SCHEME,
    /** An https URL the target app has verified as an App Link — Android
     *  opens the app when it claims the domain, else the browser. */
    APP_LINK,
}

/** One jump-straight-there link for an app: a food app's menu, a
 *  service's search page, a store's library. All static in code for v1
 *  (same pattern as AppCatalog) — no server, no per-user editing. */
data class QuickLink(
    val id: String,
    val label: String,
    /** The deep link to fire first. */
    val uri: String,
    val kind: LinkKind,
    /** Web URL to open when the deep link fails (usually the same https
     *  page for App Links). */
    val fallbackUrl: String? = null,
)

/**
 * Per-app quick links, keyed by catalog name. Links that need a runtime
 * value (a Telegram username, a Discord guild/channel id, a WhatsApp
 * number) are deliberately left out until the matching Hub can fill
 * them in — Phase 1b. DoorDash / Uber Eats / Grubhub are not here:
 * Anthony doesn't have them installed.
 *
 * Every URL below comes from the verified table in
 * quick-links-research.md (§3). Statuses there: V = official docs,
 * R = widely reported, T = plausible App Link — ALL of them still need
 * the on-device tap test before they count as "working".
 */
private val QUICK_LINKS_BY_NAME: Map<String, List<QuickLink>> = mapOf(
    "Spotify" to listOf(
        QuickLink(
            id = "spotify-search",
            label = "Search",
            uri = "https://open.spotify.com/search",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://open.spotify.com/search",
        ),
        QuickLink(
            id = "spotify-liked",
            label = "Liked Songs",
            uri = "spotify:collection:tracks",
            kind = LinkKind.CUSTOM_SCHEME,
            fallbackUrl = "https://open.spotify.com/collection/tracks",
        ),
    ),
    "YouTube" to listOf(
        QuickLink(
            id = "youtube-subscriptions",
            label = "Subscriptions",
            uri = "https://www.youtube.com/feed/subscriptions",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.youtube.com/feed/subscriptions",
        ),
        QuickLink(
            id = "youtube-shorts",
            label = "Shorts",
            uri = "https://www.youtube.com/shorts",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.youtube.com/shorts",
        ),
    ),
    "YT Music" to listOf(
        QuickLink(
            id = "ytmusic-library",
            label = "Library",
            uri = "https://music.youtube.com/library",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://music.youtube.com/library",
        ),
        QuickLink(
            id = "ytmusic-search",
            label = "Search",
            uri = "https://music.youtube.com/search",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://music.youtube.com/search",
        ),
    ),
    "Steam" to listOf(
        QuickLink(
            id = "steam-store",
            label = "Store",
            uri = "steam://store",
            kind = LinkKind.CUSTOM_SCHEME,
            fallbackUrl = "https://store.steampowered.com",
        ),
        QuickLink(
            id = "steam-library",
            label = "Library",
            uri = "steam://open/games",
            kind = LinkKind.CUSTOM_SCHEME,
            fallbackUrl = "https://store.steampowered.com",
        ),
    ),
    "Maps" to listOf(
        QuickLink(
            id = "maps-nearby-food",
            label = "Nearby restaurants",
            uri = "geo:0,0?q=restaurants",
            kind = LinkKind.CUSTOM_SCHEME,
            fallbackUrl = "https://www.google.com/maps/search/restaurants",
        ),
    ),
    "Gmail" to listOf(
        QuickLink(
            id = "gmail-inbox",
            label = "Inbox",
            uri = "https://mail.google.com/mail/u/0/#inbox",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://mail.google.com/mail/u/0/#inbox",
        ),
    ),
    "Reddit" to listOf(
        QuickLink(
            id = "reddit-popular",
            label = "r/popular",
            uri = "https://www.reddit.com/r/popular",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.reddit.com/r/popular",
        ),
        QuickLink(
            id = "reddit-home",
            label = "Home",
            uri = "https://www.reddit.com/",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.reddit.com/",
        ),
    ),
    "X" to listOf(
        QuickLink(
            id = "x-home",
            label = "Home",
            uri = "https://x.com/home",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://x.com/home",
        ),
        QuickLink(
            id = "x-notifications",
            label = "Notifications",
            uri = "https://x.com/notifications",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://x.com/notifications",
        ),
    ),
    "McDonald's" to listOf(
        QuickLink(
            id = "mcdonalds-menu",
            label = "Menu",
            uri = "https://www.mcdonalds.com/us/en-us/full-menu.html",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.mcdonalds.com/us/en-us/full-menu.html",
        ),
        QuickLink(
            id = "mcdonalds-deals",
            label = "Deals",
            uri = "https://www.mcdonalds.com/us/en-us/deals.html",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.mcdonalds.com/us/en-us/deals.html",
        ),
        QuickLink(
            id = "mcdonalds-order",
            label = "Order",
            uri = "https://www.mcdonalds.com/us/en-us/mobile-order-and-pay.html",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.mcdonalds.com/us/en-us/mobile-order-and-pay.html",
        ),
    ),
    "Chick-fil-A" to listOf(
        QuickLink(
            id = "chickfila-menu",
            label = "Menu",
            uri = "https://www.chick-fil-a.com/menu",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.chick-fil-a.com/menu",
        ),
        QuickLink(
            id = "chickfila-rewards",
            label = "Rewards",
            uri = "https://www.chick-fil-a.com/rewards",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.chick-fil-a.com/rewards",
        ),
    ),
    "Wendy's" to listOf(
        QuickLink(
            id = "wendys-menu",
            label = "Menu",
            uri = "https://www.wendys.com/menu",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.wendys.com/menu",
        ),
        QuickLink(
            id = "wendys-offers",
            label = "Offers",
            uri = "https://www.wendys.com/offers",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.wendys.com/offers",
        ),
    ),
    "Dunkin" to listOf(
        QuickLink(
            id = "dunkin-menu",
            label = "Menu",
            uri = "https://www.dunkindonuts.com/en/menu",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.dunkindonuts.com/en/menu",
        ),
        QuickLink(
            id = "dunkin-rewards",
            label = "Rewards",
            uri = "https://www.dunkindonuts.com/en/dd-perks",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.dunkindonuts.com/en/dd-perks",
        ),
    ),
    "Taco Bell" to listOf(
        QuickLink(
            id = "tacobell-menu",
            label = "Menu",
            uri = "https://www.tacobell.com/food",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.tacobell.com/food",
        ),
    ),
    "KFC" to listOf(
        QuickLink(
            id = "kfc-menu",
            label = "Menu",
            uri = "https://www.kfc.com/menu",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.kfc.com/menu",
        ),
    ),
    "Starbucks" to listOf(
        QuickLink(
            id = "starbucks-menu",
            label = "Menu",
            uri = "https://www.starbucks.com/menu",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.starbucks.com/menu",
        ),
    ),
    "Shake Shack" to listOf(
        QuickLink(
            id = "shakeshack-menu",
            label = "Menu",
            uri = "https://www.shakeshack.com/food-and-drink",
            kind = LinkKind.APP_LINK,
            fallbackUrl = "https://www.shakeshack.com/food-and-drink",
        ),
    ),
)

/** Quick links for a catalog entry — matched by catalog name. The
 *  entry always keeps its catalog name even when it was matched to an
 *  installed app by label, so the name is the stable key. */
fun quickLinksFor(entry: AppEntry): List<QuickLink> =
    QUICK_LINKS_BY_NAME[entry.name] ?: emptyList()
