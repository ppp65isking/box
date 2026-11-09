package com.ppp65isking.box.data

import com.ppp65isking.box.model.AppEntry
import com.ppp65isking.box.model.Category
import com.ppp65isking.box.model.Tier

/**
 * All 115 of Anthony's apps, counted from his app-drawer screenshots
 * (2026-11-08). Package names are filled in where known with confidence;
 * null means "match by label at runtime, Play Store search as fallback".
 * A wrong package is worse than a missing one — a missing package still
 * launches via the label match, a wrong one shows "Get it" for an app
 * that is already installed.
 */
object AppCatalog {

    val apps: List<AppEntry> = listOf(
        // Food & drink — deal alerts in the Deals feed.
        AppEntry("7-Eleven", null, Tier.INBOX, Category.FOOD),
        AppEntry("Arby's", null, Tier.INBOX, Category.FOOD),
        AppEntry("Burger King", null, Tier.INBOX, Category.FOOD),
        AppEntry("Chick-fil-A", "com.chickfila.mobile", Tier.INBOX, Category.FOOD),
        AppEntry("Crumbl", null, Tier.INBOX, Category.FOOD),
        AppEntry("Dairy Queen", null, Tier.INBOX, Category.FOOD),
        AppEntry("Dunkin", "com.dunkinbrands.otgo", Tier.INBOX, Category.FOOD),
        AppEntry("Honey Dew", null, Tier.INBOX, Category.FOOD),
        AppEntry("KELLY'S", null, Tier.INBOX, Category.FOOD),
        AppEntry("KFC", "com.kfc.us", Tier.INBOX, Category.FOOD),
        AppEntry("McDonald's", "com.mcdonalds.app", Tier.INBOX, Category.FOOD),
        AppEntry("Shake Shack", "com.shakeshack.app", Tier.INBOX, Category.FOOD),
        AppEntry("Starbucks", "com.starbucks.mobilecard", Tier.INBOX, Category.FOOD),
        AppEntry("Taco Bell", "com.tacobell.olo", Tier.INBOX, Category.FOOD),
        AppEntry("Wendy's", "com.wendysapp", Tier.INBOX, Category.FOOD),
        AppEntry("Speedway", null, Tier.INBOX, Category.FOOD),

        // Chat & messaging.
        AppEntry("WhatsApp", "com.whatsapp", Tier.INBOX, Category.CHAT),
        AppEntry("Telegram", "org.telegram.messenger", Tier.DEEP, Category.CHAT),
        AppEntry("Signal", "org.thoughtcrime.securesms", Tier.INBOX, Category.CHAT),
        AppEntry("TextNow", "com.enflick.android.TextNow", Tier.INBOX, Category.CHAT),
        AppEntry("Messages", "com.google.android.apps.messaging", Tier.DEEP, Category.CHAT),
        AppEntry("Beeper", "com.beeper.android", Tier.INBOX, Category.CHAT),
        AppEntry("Discord", "com.discord", Tier.DEEP, Category.CHAT),
        AppEntry("Snapchat", "com.snapchat.android", Tier.INBOX, Category.CHAT),
        AppEntry("Meet", "com.google.android.apps.meetings", Tier.INBOX, Category.CHAT),

        // Music & audio.
        AppEntry("Spotify", "com.spotify.music", Tier.DEEP, Category.MUSIC),
        AppEntry("YT Music", "com.google.android.apps.youtube.music", Tier.DEEP, Category.MUSIC),
        AppEntry("Amazon Music", "com.amazon.mp3", Tier.INBOX, Category.MUSIC),
        AppEntry("SiriusXM", "com.siriusxm", Tier.INBOX, Category.MUSIC),

        // Video.
        AppEntry("YouTube", "com.google.android.youtube", Tier.DEEP, Category.VIDEO),
        AppEntry("Prime Video", "com.amazon.avod.thirdpartyclient", Tier.INBOX, Category.VIDEO),
        AppEntry("Hulu", "com.hulu.plus", Tier.INBOX, Category.VIDEO),
        AppEntry("Sora", "com.openai.sora", Tier.INBOX, Category.VIDEO),
        AppEntry("Google TV", "com.google.android.videos", Tier.INBOX, Category.VIDEO),

        // Social.
        AppEntry("TikTok", "com.zhiliaoapp.musically", Tier.INBOX, Category.SOCIAL),
        AppEntry("X", "com.twitter.android", Tier.DEEP, Category.SOCIAL),
        AppEntry("Threads", "com.instagram.barcelona", Tier.DEEP, Category.SOCIAL),
        AppEntry("Reddit", "com.reddit.frontpage", Tier.DEEP, Category.SOCIAL),

        // Email.
        AppEntry("Gmail", "com.google.android.gm", Tier.DEEP, Category.EMAIL),
        AppEntry("Outlook", "com.microsoft.office.outlook", Tier.DEEP, Category.EMAIL),
        AppEntry("Proton Mail", "ch.protonmail.android", Tier.DEEP, Category.EMAIL),

        // Files & docs.
        AppEntry("Drive", "com.google.android.apps.docs", Tier.DEEP, Category.FILES),
        AppEntry("Docs", "com.google.android.apps.docs.editors.docs", Tier.DEEP, Category.FILES),
        AppEntry("Slides", "com.google.android.apps.docs.editors.slides", Tier.DEEP, Category.FILES),
        AppEntry("Photos", "com.google.android.apps.photos", Tier.DEEP, Category.FILES),
        AppEntry("OneDrive", "com.microsoft.skydrive", Tier.DEEP, Category.FILES),
        AppEntry("Files", "com.google.android.apps.nbu.files", Tier.LAUNCHER, Category.FILES),
        AppEntry("Keep Notes", "com.google.android.keep", Tier.LAUNCHER, Category.FILES),

        // School.
        AppEntry("Classroom", "com.google.android.apps.classroom", Tier.INBOX, Category.SCHOOL),
        AppEntry("PowerSchool", "com.powerschool.portal", Tier.INBOX, Category.SCHOOL),
        AppEntry("BusRight", null, Tier.INBOX, Category.SCHOOL),
        AppEntry("Saturn", null, Tier.INBOX, Category.SCHOOL),

        // Money & deals.
        AppEntry("Fetch", "com.fetchrewards.fetch", Tier.INBOX, Category.MONEY),
        AppEntry("Upside", null, Tier.INBOX, Category.MONEY),
        AppEntry("UNiDAYS", "com.unidays.app", Tier.INBOX, Category.MONEY),
        AppEntry("Gold Deals +", null, Tier.INBOX, Category.MONEY),
        AppEntry("Evidation", null, Tier.INBOX, Category.MONEY),
        AppEntry("Rewards", null, Tier.INBOX, Category.MONEY),
        AppEntry("Wallet", "com.google.android.apps.walletnfcrel", Tier.DEEP, Category.MONEY),
        AppEntry("Amazon Shopping", "com.amazon.mShop.android.shopping", Tier.DEEP, Category.MONEY),

        // Games.
        AppEntry("Brawl Stars", "com.supercell.brawlstars", Tier.INBOX, Category.GAMES),
        AppEntry("Clash Royale", "com.supercell.clashroyale", Tier.INBOX, Category.GAMES),
        AppEntry("Roblox", "com.roblox.client", Tier.INBOX, Category.GAMES),
        AppEntry("Royal Match", "com.dreamgames.royalmatch", Tier.INBOX, Category.GAMES),
        AppEntry("Geometry Dash", "com.robtopx.geometryjump", Tier.INBOX, Category.GAMES),
        AppEntry("Play Games", "com.google.android.play.games", Tier.LAUNCHER, Category.GAMES),
        AppEntry("XBOX", "com.microsoft.xboxone.smartglass", Tier.INBOX, Category.GAMES),
        AppEntry("Steam", "com.valvesoftware.android.steam.community", Tier.DEEP, Category.GAMES),

        // AI.
        AppEntry("ChatGPT", "com.openai.chatgpt", Tier.DEEP, Category.AI),
        AppEntry("Gemini", "com.google.android.apps.bard", Tier.DEEP, Category.AI),
        AppEntry("Grok", "ai.x.grok", Tier.DEEP, Category.AI),
        AppEntry("Copilot", "com.microsoft.copilot", Tier.INBOX, Category.AI),
        AppEntry("Perplexity", "ai.perplexity.app.android", Tier.DEEP, Category.AI),
        AppEntry("Muse", "com.anthropic.claude", Tier.INBOX, Category.AI),

        // Health & location.
        AppEntry("Samsung Health", "com.sec.android.app.shealth", Tier.DEEP, Category.HEALTH),
        AppEntry("Galaxy Wearable", "com.samsung.android.app.watchmanager", Tier.LAUNCHER, Category.HEALTH),
        AppEntry("Life360", "com.life360.android.safetymapd", Tier.INBOX, Category.HEALTH),

        // Learning & books.
        AppEntry("Duolingo", "com.duolingo", Tier.INBOX, Category.OTHER),
        AppEntry("Libby", "com.overdrive.mobile.android.libby", Tier.DEEP, Category.OTHER),
        AppEntry("Kindle", "com.amazon.kindle", Tier.LAUNCHER, Category.OTHER),

        // System & utilities.
        AppEntry("Calculator", "com.google.android.calculator", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Camera", "com.google.android.GoogleCamera", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Clock", "com.google.android.deskclock", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Contacts", "com.google.android.contacts", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Phone", "com.google.android.dialer", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Recorder", "com.google.android.apps.recorder", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Settings", "com.android.settings", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Safety", "com.google.android.apps.safetyhub", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Thermometer", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Authenticator", "com.azure.authenticator", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("MicroG RE", "org.microg.gms", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("My Pixel", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Now Playing", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Find Hub", "com.google.android.apps.adm", Tier.INBOX, Category.SYSTEM),
        AppEntry("Google One", "com.google.android.apps.subscriptions.red", Tier.INBOX, Category.SYSTEM),
        AppEntry("Google Fi", "com.google.android.apps.tycho", Tier.INBOX, Category.SYSTEM),
        AppEntry("Home", "com.google.android.apps.chromecast.app", Tier.DEEP, Category.SYSTEM),
        AppEntry("Maps", "com.google.android.apps.maps", Tier.DEEP, Category.SYSTEM),
        AppEntry("Google", "com.google.android.googlequicksearchbox", Tier.DEEP, Category.SYSTEM),
        AppEntry("Translate", "com.google.android.apps.translate", Tier.DEEP, Category.SYSTEM),
        AppEntry("GitHub", "com.github.android", Tier.DEEP, Category.SYSTEM),
        AppEntry("Chrome Beta", "com.chrome.beta", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Play Store", "com.android.vending", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Send Files to TV", "com.yablio.sendfilestotv", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Screenshots", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Morphe", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("RentAHuman", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Antigravity", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Brawl Network", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Feedback", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Weather", "com.google.android.apps.weather", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Verse Garden", "com.versegarden.app", Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("Winchester", null, Tier.LAUNCHER, Category.SYSTEM),
        AppEntry("myQ", null, Tier.INBOX, Category.SYSTEM),
        AppEntry("Calendar", "com.google.android.calendar", Tier.DEEP, Category.SYSTEM),
    )

    /** Apps whose notifications are deals/discounts — the Deals feed. */
    val dealAppNames: Set<String> = setOf(
        "7-Eleven", "Arby's", "Burger King", "Chick-fil-A", "Crumbl",
        "Dairy Queen", "Dunkin", "Honey Dew", "KELLY'S", "KFC", "McDonald's",
        "Shake Shack", "Starbucks", "Taco Bell", "Wendy's", "Speedway",
        "Fetch", "Upside", "UNiDAYS", "Gold Deals +",
    )

    /** Best-known package names for deal apps (label match is the fallback). */
    val dealPackages: Set<String> = setOf(
        "com.mcdonalds.app", "com.wendysapp", "com.chickfila.mobile",
        "com.tacobell.olo", "com.kfc.us", "com.shakeshack.app",
        "com.starbucks.mobilecard", "com.dunkinbrands.otgo",
        "com.fetchrewards.fetch", "com.unidays.app",
    )

    fun isDealApp(packageName: String, appName: String): Boolean =
        packageName in dealPackages || appName in dealAppNames
}
