# Box

One hub for your apps. Box puts the apps on your phone into a single launcher
with one notification inbox, a deals feed, public GitHub + Reddit browsing,
YouTube Music search and playback, and your SMS — no account needed for any
of it in v1.

## What v1 does

| Tab | What it does |
| --- | --- |
| **Home** | Weather for Woburn, MA (Open-Meteo, no key), today's steps from Health Connect, a "Jump back in" row of apps you launched from Box, and a searchable grid of all 115 apps from the plan. Installed apps launch; not-installed apps show a Get it button that opens the Play Store. |
| **Inbox** | Every notification from every app, newest first, grouped by app. Tap one to open the app it came from. Read-only. |
| **Deals** | The inbox filtered to the 15 food apps + Fetch, Upside, UNiDAYS, Gold Deals+ — deal alerts only. |
| **Music** | Search and play YouTube Music via the public InnerTube endpoints (same approach as the YTM TV app: VISIONOS client, visitorData, itag 251 Opus). Every song row and the now-playing bar have an **Open in YT Music** button that hands the song to the real YT Music app. |
| **More** | GitHub (public repo search + detail), Reddit (r/popular, search, any subreddit), Messages (SMS, read-only). Plus the honest "coming tomorrow" list. |

Everything works with **no sign-in and no dev account**. The tabs that need
an account (Gmail, Spotify, Discord, Telegram, Outlook, Threads, AI chats)
are deliberately **not** in v1 — they are listed under More → "Coming
tomorrow" instead of shipping as stubs.

## Permissions — what Anthony grants on first run

| Permission | Why | Where it is asked |
| --- | --- | --- |
| Notification Listener access | So Box can collect notifications for the Inbox and Deals tabs. This is **not** a runtime permission — Android shows a system Settings page. Box shows an in-app card with a button that opens that page. | Inbox / Deals (card) |
| SMS (READ_SMS) | So the Messages tab can list conversations and messages. Read-only in v1; Box cannot send. | Messages tab |
| Health Connect — Steps (read) | So the Home card can show today's steps. Read-only. If not granted, the card shows a setup button instead. | Home (steps card) |
| Notifications (POST_NOTIFICATIONS, Android 13+) | Only for Box's own foreground music-playback notification. | Asked once at startup. |

No other permissions. Box asks for nothing on launch except the music
notification permission; everything else is asked for in context, on the tab
that needs it.

## What's tomorrow (needs Anthony's dev accounts)

- **Gmail** — Google Cloud project + Gmail API OAuth
- **Spotify** — Spotify Developer app + OAuth
- **Discord / Telegram** — bot or OAuth app per service
- **Outlook** — Azure app registration
- **Threads** — Meta developer app
- **AI chats** — Gemini, ChatGPT, Grok, Copilot, Perplexity accounts

Each one needs one OAuth sign-in inside Box. Until then, those apps still
appear in the Home grid and their notifications still land in the Inbox.

## Building

Push to `main` and GitHub Actions builds a debug APK (artifact name
`box-apk`). No local Android SDK is needed for the build.

## Tech

- Native Kotlin, Jetpack Compose (BOM 2026.06.01), Material 3 Expressive
  (`material3 1.5.0-beta02`)
- minSdk 26, targetSdk 36, compileSdk 36
- OkHttp + coroutines for all network; Media3 ExoPlayer for music
- InnerTube music: VISIONOS player client, `visitorData` required, stream
  URLs expire in ~6h so every play resolves fresh
- Theme: orange primary, green secondary, blue tertiary — no purple

## Honest limits

- Box cannot log into WhatsApp, Snapchat, Signal, TikTok, X, or food apps —
  they have no public APIs. Their notifications are the integration.
- Food apps have no APIs at all; the Deals feed is built from their
  notifications, so a deal only appears if the app sends an alert.
- GitHub search is unauthenticated: 60 requests/hour per IP, then a
  friendly "try again later" message.
- Reddit is read-only and public; no voting, no posting, no account.
- Music uses the unofficial InnerTube endpoints — the same gray area as the
  YTM TV app. It can break if YouTube changes things.
