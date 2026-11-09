# Quick Links research — per-app deep links + "info with permission" for Box

Date: 2026-11-09. Research only — no code changed.
Anthony's idea: Box should offer per-app **quick links** that jump straight to a
screen inside an app (e.g. a food app's menu) instead of just launching it —
for **any** app, not just food — plus find something useful Box can do with
food apps, and "get info with permission".

## 1. What Box has today (grounded in the code)

- **Catalog** (`data/AppCatalog.kt`): 115 apps, `AppEntry(name, packageName?,
  tier, category)`. Food apps (16, all `Tier.INBOX`): 7-Eleven, Arby's,
  Burger King, Chick-fil-A, Crumbl, Dairy Queen, Dunkin, Honey Dew, KELLY'S,
  KFC, McDonald's, Shake Shack, Starbucks, Taco Bell, Wendy's, Speedway.
  **DoorDash, Uber Eats and Grubhub are NOT in the catalog** — check whether
  Anthony has them installed; if yes, add them (they are natural quick-link
  targets). Big apps present: Spotify, YT Music, YouTube, Reddit, Discord,
  Gmail, Messages, Telegram, WhatsApp, Steam, Maps, etc.
- **Launching** (`data/AppLauncher.kt`): `openApp()` = `getLaunchIntentForPackage`
  (home screen only). `openUrl()` = `ACTION_VIEW` on any URL — Android
  resolves it to the app when the app claims the link, else the browser.
  `openInYtMusic()` already uses this: `https://music.youtube.com/watch?v=<id>`.
- **Hub routing**: tap on Home opens a Box Hub instead of the app for GitHub,
  Reddit, Messages, Gmail, Spotify, Discord, Steam (`hubForPackage` /
  `hubForEntry`). Everything else launches externally.
- **Home grid** (`ui/HomeScreen.kt`): cells use plain `Modifier.clickable` —
  **no long-press exists yet**. Tap = `launch(item)` (hub or openApp).
- **Permissions already granted**: Notification Listener
  (`service/BoxNotificationListener.kt`, inbox + Deals feed), READ_SMS,
  READ_CONTACTS, Health Connect steps. Manifest uses a `<queries>` intent for
  launcher apps, deliberately **no `QUERY_ALL_PACKAGES`**.
- No quick-link / deep-link / widget / accessibility / usage-stats code exists
  yet (`grep QuickLink|deepLink` = nothing).

## 2. Platform findings — what is actually possible

### 2.1 Other apps' published app shortcuts — BLOCKED ❌

`LauncherApps.getShortcuts()` is the API for reading another app's shortcuts
(the long-press menu items on a launcher icon). Per the Android docs for
`LauncherApps.hasShortcutHostPermission()`:

> "Access is currently available to: the current launcher (or default
> launcher if there is no set current launcher); the currently active voice
> interaction service."

Box is **not** the default launcher, so `hasShortcutHostPermission()` is
`false` and `getShortcuts()` will not return other apps' shortcuts. There is
no permission Box can request to get this. **Do not build on this.**
(Source: developer.android.com `LauncherApps` API reference.)

Note: `ShortcutManager` only exposes an app's **own** shortcuts — useless
here. And `requestPinShortcut()` pins a shortcut to the *home screen
launcher*, not inside Box.

### 2.2 Deep links / App Links — WORKS ✅ (this is the quick win)

Firing `Intent(ACTION_VIEW, uri)` from Box needs **no special permission**.
Two flavors:

- **Custom schemes** (`spotify:…`, `tg://…`, `steam://…`, `vnd.youtube://…`):
  the target app registers the scheme; the intent goes straight to it. If the
  app is missing, the intent fails — always wrap in try/catch
  (`ActivityNotFoundException`) and fall back to the web URL.
- **Android App Links** (`https://…` URLs the app has verified): same
  `ACTION_VIEW` intent; Android opens the app when it claims the domain,
  else the browser. Works even when the user never set a default, but only
  if the user hasn't disabled "Open supported links" for that app.

Caveats:
- Schemes are **undocumented for most apps and can change silently** with any
  app update. Every link needs an on-device test and a web fallback.
- Package-visibility (Android 11+): `resolveActivity()` returns null for
  intents not covered by a `<queries>` entry. Box already avoids this by
  using try/catch around `startActivity` (see `openUrl`/`openPlayStore`) —
  keep that pattern for quick links; do **not** add a `<queries>` entry per
  scheme.
- Some apps' https links only open in-app after the user has opened them in
  the app once, or require login (Reddit r/popular 403'd on cloud devices
  before; on-device it works).

### 2.3 Hosting other apps' widgets inside Box — POSSIBLE ✅ (with user consent)

Android docs ("Build a widget host", developer.android.com): **any** app can
implement `AppWidgetHost`. Declare `BIND_APPWIDGET` in the manifest; at
runtime, if `bindAppWidgetIdIfAllowed()` returns false, fire
`Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)` and the system shows the
user a dialog: "allow" for this widget or "always allow" for all future
ones. This is **not** signature-only — the user grant is the mechanism.
So Box *can* embed e.g. a Spotify / weather / calendar widget on Home.
Costs: host lifecycle (`startListening`/`stopListening`), widget sizing is
fiddly in a non-launcher layout, and each widget needs its own consent tap.
**Phase 3, not Phase 1.**

### 2.4 "Get info with permission" — what each permission actually buys

| Permission | What Box can read | Cost / friction | Verdict |
|---|---|---|---|
| **Notification Listener** (already have) | Every notification's title/text + its **tap intent** (`contentIntent`) and action buttons. Tapping a deal notification's intent opens the app's **exact screen** (e.g. DoorDash order tracker), not just the home screen. | Already granted. | ✅ Use now — cheapest "info with permission" win (Phase 2). |
| **MediaSession** (via the listener grant) | Other apps' now-playing info: track title/artist, play state, position — `MediaSessionManager` active sessions are visible to an enabled notification listener. | No new permission; small new component. | ✅ Phase 2 — live "now playing" from Spotify/YT Music in the Music tab. |
| **UsageStats** (`PACKAGE_USAGE_STATS`) | Per-app usage time + last-used time, foreground-app detection. Powers "most used" ordering and a smarter "Jump back in". **No screen content, no messages.** | Special permission: user must enable in Settings → Usage access (`ACTION_USAGE_ACCESS_SETTINGS`); check via `AppOpsManager`. Verified in Android docs. | ✅ Phase 2 — easy, honest value. (Whether sideloaded Box also needs the "Allow restricted settings" step for this one is unconfirmed — verify on device.) |
| **Accessibility Service** | Full screen content (text, buttons) of any app + can tap/scroll for the user. The only way to read e.g. a food app's order-status or rewards-points screen with no API. | Heavy: user enables in Settings → Accessibility **plus** Android 13+ "Allow restricted settings" extra step for sideloaded apps (toggle is greyed out until App info → ⋮ → Allow restricted settings). Battery cost, big privacy surface, and screen layouts change constantly so every read is fragile. Play policy is NOT the blocker (Box is sideloaded), but the friction is real. | ⚠️ Phase 4 at most, and only for one named use case. Recommend **against** building it speculatively. |

Restricted-settings sources: Android 13 blocks sideloaded apps from enabling
Accessibility / Notification Listener until the user taps "Allow restricted
settings" (App info → ⋮ menu). Box's notification listener is already
working, so Anthony has presumably already done this step once — a second
service (Accessibility) would need the same step again.

### 2.5 Food apps without official APIs — what Box can do

Confirmed: **no public consumer ordering API exists** for DoorDash, Uber
Eats, or Grubhub (their APIs are partner/fulfilment only — DoorDash Drive,
Uber Direct — not for individual users). Same for the chain apps. So:

- ✅ **Quick links to menu / deals / rewards / order / find-a-store** via
  https App Links (see table). This is the whole food win — one tap to the
  menu instead of app → navigate → menu.
- ✅ **Deals feed already exists** (notification-based). Upgrade it with
  notification tap-intent passthrough (2.4) so a deal notification opens
  the deal screen in the app.
- ✅ **Order tracking via notifications**: delivery apps post rich
  notifications ("arriving in 10 min"); Box can surface the live status
  text and deep-link to the tracker.
- ❌ **Menu content inside Box** (browsing a menu without opening the app):
  only via web scraping — fragile, anti-bot blocked, ToS-grey. **Not
  recommended.** The honest version is "tap → menu opens in the app".
- ❌ **Rewards points / order history inside Box**: no API; only via
  Accessibility (fragile) or notification text. Don't promise it.

## 3. Verified deep-link table

Status key: **V** = verified from official docs / the app's own
documentation. **R** = widely reported by deep-link services, needs
on-device test. **T** = plausible https App Link, needs on-device test
(whether it lands in-app depends on the app's verified domains + the
user's "open supported links" setting).

| App | Quick link | Goes to | Status |
|---|---|---|---|
| Spotify | `spotify:track:<id>`, `spotify:playlist:<id>`, `spotify:album:<id>`, `spotify:artist:<id>` | That item in-app | **V** — Spotify for Developers, "Spotify URIs and IDs" |
| Spotify | `https://open.spotify.com/search/<query>` | Search results in-app | **T** (web URL is real; in-app routing to test) |
| Spotify | `https://open.spotify.com/track/<id>` | Track in-app | **V** — same Spotify docs page |
| YouTube | `vnd.youtube://watch?v=<id>` | Video in-app | **R** — the standard documented YouTube scheme; test |
| YouTube | `https://www.youtube.com/watch?v=<id>` | Video in-app | **V** — verified App Link domain |
| YT Music | `https://music.youtube.com/watch?v=<id>` | Song in-app | **V** — already used by Box (`openInYtMusic`), proven |
| Telegram | `tg://resolve?domain=<username>` | Chat/channel/bot in-app | **V** — corefork.telegram.org "Deep links" (official) |
| Telegram | `https://t.me/<username>` | Same, via https | **V** — same official docs |
| Steam | `steam://store`, `steam://open/games`, `steam://store/<appid>` | Store / Library / game page | **V** scheme, **T** on mobile — Valve's own wiki documents `steam://` (desktop client); whether the Android Steam app handles it needs an on-device test |
| Discord | `https://discord.com/channels/<guild>/<channel>` | That channel in-app | **V** — Discord claims discord.com as App Link; copy-link format from Discord itself |
| Discord | `discord://channels/<guild>/<channel>`, `discord://discord.com/invite/<code>` | Same, via scheme | **R** — reported by multiple deep-link services; test |
| WhatsApp | `https://wa.me/<number>?text=<msg>` | Chat with prefilled text | **V** — Meta/WhatsApp documented |
| Google Maps | `geo:0,0?q=<query>` | Search in Maps | **V** — Android "common intents" documented |
| Gmail | `https://mail.google.com/mail/u/0/#inbox` | Inbox in-app | **T** |
| Reddit | `https://www.reddit.com/r/<sub>` | Subreddit in-app | **T** (works on his phone; 403'd on cloud devices before) |
| X | `https://x.com/<username>` | Profile in-app | **T**; `twitter://` scheme = **R** |
| DoorDash | `https://www.doordash.com/store/<slug>` | Restaurant page in-app | **R** — App Link pattern used by delivery deep-link tooling; test |
| DoorDash | `doordash://` | Opens app | **R** — listed in iOS scheme lists; Android behavior to test |
| Uber Eats | `https://www.ubereats.com/store/<slug>/<uuid>` | Restaurant page in-app | **R** — same pattern; test. `ubereats://` unverified — do not ship |
| McDonald's | `https://www.mcdonalds.com/us/en-us/full-menu.html` | Menu | **T** — real page; in-app routing to test |
| McDonald's | `https://www.mcdonalds.com/us/en-us/deals.html`, `…/mobile-order-and-pay.html` | Deals / Order | **T** — real pages; in-app routing to test |
| Chick-fil-A | `https://www.chick-fil-a.com/menu`, `…/rewards` | Menu / Rewards | **T** — real pages; in-app routing to test |
| Wendy's | `https://www.wendys.com/menu`, `https://www.wendys.com/offers` | Menu / Offers | **T** — real pages; in-app routing to test |
| Dunkin' | `https://www.dunkindonuts.com/en/menu`, `…/dd-perks` | Menu / Rewards | **T** — real pages; in-app routing to test |
| Taco Bell / KFC / Starbucks / Shake Shack | `https://www.tacobell.com/food`, `https://www.kfc.com/menu`, `https://www.starbucks.com/menu`, `https://www.shakeshack.com/food-and-drink` | Menu | **T** — real pages; in-app routing to test |

No documented custom scheme was found for McDonald's, Chick-fil-A,
Wendy's, Dunkin', Taco Bell, KFC, Starbucks, or Shake Shack — https App
Links are the only route, and each needs Anthony to tap it once and report
whether it landed in-app, in-browser, or nowhere.

## 4. Implementation plan

### Data model — new file `data/QuickLinks.kt`

```kotlin
enum class LinkKind { CUSTOM_SCHEME, APP_LINK, WEB }

data class QuickLink(
    val id: String,        // e.g. "mcdonalds-menu"
    val label: String,     // "Menu", "Deals", "Rewards", "Order"
    val uri: String,       // the deep link
    val kind: LinkKind,
    /** Web URL to open when the app link fails (usually the same https page). */
    val fallbackUrl: String? = null,
)

/** Per-app quick links, keyed by catalog name (stable) with a package
 *  fallback. Static map in code for v1 — no server, no per-user editing. */
val QUICK_LINKS: Map<String, List<QuickLink>>
```

Keep links **static in code** for v1 (same pattern as `AppCatalog`): no
backend, no user-editable links yet. A few links need a runtime value
(Spotify playlist id, Discord guild/channel id, Telegram username) — model
those as `template: String` with `{placeholder}` filled from the matching
Hub's stored data (e.g. Discord guilds already loaded in `DiscordScreen`)
or left out until the Hub has the id.

### Resolver — extend `data/AppLauncher.kt`

```kotlin
/** Fire a quick link. Tries the deep link first, then the web fallback,
 *  then the Play Store page. Returns false only if nothing could open. */
fun openQuickLink(context: Context, link: QuickLink, entry: AppEntry): Boolean
```

Rules: try `ACTION_VIEW` on `uri` in try/catch; on
`ActivityNotFoundException` try `fallbackUrl`; on failure return false so
the UI can show "couldn't open that — the app may not support this link".
Do **not** use `resolveActivity()` (package-visibility — see 2.2).
Optional hardening: `intent.setPackage(entry.packageName)` for custom
schemes so a random browser can't claim them — but only when the package
is known AND installed; otherwise leave it unset so App Links still work.

### UI — where quick links surface

- **Long-press on a Home grid cell** → bottom sheet with the app's quick
  links + "Open app" + "App info" (Play Store). This is the cheapest
  surface: `HomeScreen.AppGridCell` switches `Modifier.clickable` to
  `combinedClickable(onClick = launch, onLongClick = showLinks)`. Tap
  behavior is unchanged (Hub routing still wins on tap), so nothing
  existing breaks.
- **"Jump back in" + search results**: same bottom sheet on long-press
  (reuse the sheet, don't build a second UI).
- **Hub screens**: where a Hub already knows an id, add one inline link
  row (e.g. Spotify hub → "Open playlist in Spotify"; Discord hub →
  per-server "Open in Discord app"). Defer to Phase 1b — the bottom sheet
  covers the general case first.
- Do **not** add a separate "Quick links" tab or a per-app detail screen
  in v1 — the sheet is the whole UI.

### Permission flows (only for later phases)

- Phase 2 (UsageStats): manifest `PACKAGE_USAGE_STATS` +
  `tools:ignore="ProtectedPermissions"`; Settings row "Show most-used
  first" that deep-links to `Settings.ACTION_USAGE_ACCESS_SETTINGS`;
  re-check with `AppOpsManager` on resume (same pattern as Health
  Connect). No runtime permission dialog exists for this — it is a
  Settings toggle.
- Phase 2 (notification tap-through / MediaSession): no new permission —
  reuse the existing `BoxNotificationListener` grant. Store the
  notification's `contentIntent` (a `PendingIntent`) with the captured
  notification and fire it via `send()` when the user taps the
  notification row. Note: pending intents can expire; catch and fall back
  to `openApp()`.
- Phase 3 (widgets): manifest `BIND_APPWIDGET`; per-widget
  `ACTION_APPWIDGET_BIND` consent dialog; a "Widgets" section on Home
  with an "Add widget" picker (`AppWidgetManager.getInstalledProviders`).
- Phase 4 (Accessibility): only if a specific use case demands it. Needs
  the service declaration + "Allow restricted settings" walkthrough UI
  (App info → ⋮ → Allow restricted settings) + the Accessibility
  settings toggle. Flag the battery/privacy cost in the UI copy.

### Phased order

**Phase 1 — Quick links (no new permissions). The quick win.**
1. `QuickLinks.kt` + `openQuickLink()` + bottom sheet on long-press.
2. Seed links for ~15 apps: Spotify (Liked Songs via
   `spotify:collection:tracks` — **R**, test; plus Search), YouTube
   (Subscriptions `https://www.youtube.com/feed/subscriptions` — **T**),
   YT Music (Library `https://music.youtube.com/library` — **T**), Telegram
   (resolve), Steam (Store/Library), Discord (channel via https),
   WhatsApp (wa.me), Maps (geo search), Gmail (inbox), Reddit (r/popular),
   X (home), McDonald's (Menu/Deals/Order), DoorDash (store search
   `https://www.doordash.com/store` — **T**), Chick-fil-A (Menu/Rewards),
   Wendy's (Menu/Offers).
3. Anthony's on-device verification pass (see §5) before calling any link
   "done".

**Phase 2 — Info with permission, using grants Box already has.**
1. Notification tap-through in Inbox/Deals (open the exact screen).
2. MediaSession now-playing from other apps in the Music tab.
3. UsageStats "most used" ordering for the Home grid + smarter
   "Jump back in".

**Phase 3 — Widgets in Box.**
`AppWidgetHost` + "Add widget" on Home. Start with one widget type
(e.g. Spotify or weather) to prove the host works.

**Phase 4 — Accessibility (only on a named use case).**
Scoped service that reads one screen type (e.g. food order status).
Recommend against unless Anthony names the exact screen — fragile and
high-friction.

### Food-app mini-plan (inside Phase 1 + 2)

- Phase 1: per food app, 3–4 links — Menu, Deals/Rewards, Order,
  Find a store (locator page). McDonald's / Chick-fil-A / Wendy's /
  Dunkin' / Taco Bell / KFC / Starbucks / Shake Shack from the table.
- Phase 2: Deals feed rows become tappable to the deal screen
  (notification intent passthrough); add a "Track order" row when a
  delivery notification is live.
- Ask Anthony: does he have DoorDash / Uber Eats / Grubhub installed?
  They aren't in the catalog — add them if yes, skip if no.

## 5. What Anthony must test on his phone (Pixel 9 Pro)

For each seeded link, tap it once and report: **in-app / browser /
nothing**. Priority checks:
1. McDonald's Menu / Deals / Order https links — in-app or browser?
2. Chick-fil-A, Wendy's, Dunkin' menu https links — in-app or browser?
3. `doordash://` — opens DoorDash? (only if installed)
4. `discord://channels/<guild>/<channel>` vs the https form — which
   lands in-app?
5. `steam://store` / `steam://open/games` — does the Android Steam app
   handle them?
6. `spotify:search:<query>` — opens Spotify search?
7. YouTube Subscriptions / YT Music Library https links — in-app?
8. (Phase 2) Usage access: does Box appear under Settings → Usage
   access, and is "Allow restricted settings" also needed for it?

## 6. Risks / honest limits

- Custom schemes change without notice; every link needs a web fallback
  and a graceful "couldn't open" state.
- App Links only open in-app if the app verified the domain **and** the
  user hasn't disabled "Open supported links" for that app.
- Long-press on launcher shortcuts is **not available** to Box (2.1) —
  quick links are Box's own curated list, not the app's own shortcuts.
  Some apps' real shortcuts (e.g. "New chat" in WhatsApp) can't be
  reproduced — only what a URL can express.
- Accessibility-based reading is fragile by design; don't build the food
  plan on it.
- Nothing here needs a new OAuth client, API key, or server — the whole
  Phase 1 is client-side intents.
