# Box — Developer Setup (Google, Spotify, Discord, Microsoft)

This page is for **Anthony-as-developer**, not for people using Box.

## The one idea to hold onto

Each service needs **one OAuth client for the whole app**, registered once
by the developer. The client IDs ship **in the app**, in
`app/src/main/java/com/ppp65isking/box/data/OAuthConfig.kt` — Anthony
creates the dev app once, gives the client ID to Cosmo once, and it
lands there. An end user never creates a dev account, never sees a
client ID, and never pastes anything: they tap **Connect** on the
service's screen and sign in with **their own** Google / Spotify /
Discord account. Box then stores *that user's* token on their own phone.

Same principle as the Reddit integration: its client ID is Box's app ID,
not per-user. Spotify and Discord need no client secret — they use
Authorization Code + PKCE, so the client ID alone is the credential.
Google is the one exception (see §1, step 4): its Desktop OAuth clients
demand the client_secret on the token exchange and refresh, alongside
PKCE. That secret lives only as a GitHub repo secret
(`GOOGLE_CLIENT_SECRET`), injected by CI into the build — it never
appears in this repo or in the app's source.

**Why embedding the IDs is safe:** an OAuth client ID is a public
identifier, not a secret — it shows up in the sign-in URL of every app
that uses one. For Spotify/Discord/Reddit there is no client secret at
all, because PKCE replaces it. Google's Desktop client does have a
secret, but Google's own installed-app guidance treats it as
non-confidential (PKCE is the real protection) — and Box still never
puts it in the repo: it arrives only through the `GOOGLE_CLIENT_SECRET`
repo secret at build time.

---

## 1. Google (Gmail + Calendar + Drive + Docs/Slides + Photos)

What it does: one sign-in, read-only. Gmail read + search, Calendar
events for the next 7 days, Drive files (recent, plus Docs/Slides
filters), Photos list.

Scopes Box asks for (all read-only):
`openid email profile`, `gmail.readonly`, `calendar.readonly`,
`drive.readonly`, `documents.readonly`, `presentations.readonly`,
`photoslibrary.readonly`.

Steps:

1. Go to [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials).
   Create a project if there isn't one (name it Box).
2. **Enable the APIs** (APIs & Services → Library): Gmail API, Google
   Calendar API, Google Drive API, Google Docs API, Google Slides API,
   Google Photos Library API.
3. **OAuth consent screen** (APIs & Services → OAuth consent screen):
   - User type **External**.
   - App name `Box`. No logo needed.
   - Under **Test users**, add the Google account(s) that will sign in.
     **This matters:** Gmail/Calendar/Drive scopes are "sensitive", so
     until Google verifies the app it only works for test users, and
     sign-in shows an "unverified app" warning screen. That is normal
     for a personal app — tap Advanced → Go to Box to continue. Full
     verification is only needed if Box goes public.
4. **Create the OAuth client** (Credentials → Create Credentials →
   OAuth client ID → application type **Desktop app**). The create form
   asks for no redirect, and the client's edit page has no redirect
   field either — that is normal: Desktop clients cannot register a
   redirect URI at all. Box instead uses a loopback redirect
   (`http://127.0.0.1:<port>/`, a fresh port per sign-in), which
   Google accepts for Desktop clients with no registration. There is
   nothing to paste into the console for the redirect.
   **The Desktop client_secret IS required anyway:** Google's token
   endpoint rejects the code exchange (and the refresh) with
   `invalid_request: client_secret is missing` unless the secret is
   sent alongside the PKCE verifier — even though Google's docs label
   it "optional". So after creating the client, add its secret as a
   GitHub repo secret: box → Settings → Secrets and variables →
   Actions → New repository secret, name `GOOGLE_CLIENT_SECRET`,
   value the secret from the console. CI injects it into the build
   (`BuildConfig.GOOGLE_CLIENT_SECRET`); it must never be pasted
   into chat or committed to the repo. Without it, Google sign-in
   fails at the exchange step.
5. Copy the **Client ID** (the long `….apps.googleusercontent.com`
   string — not the secret) and **give it to Cosmo once**. It goes in
   `OAuthConfig.kt` (`GOOGLE_CLIENT_ID`) and ships with the app.
6. In Box, tap **Gmail** on Home to open the Google hub, then
   **Connect Google** and sign in.

Photos note: Google restricted the Photos Library API in 2025, so the
list may only show photos the user picked for Box rather than the whole
library. Box says so on the Photos tab when the list comes back empty —
that is a Google-side limit, not a Box bug.

## 2. Spotify (library, playlists, currently playing, play/pause)

What it does: profile, your playlists, liked songs, currently playing,
and play/pause on your own active player.

Scopes Box asks for: `user-read-private user-read-email
playlist-read-private playlist-read-collaborative user-library-read
user-read-currently-playing user-read-playback-state
user-modify-playback-state`.

Steps:

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   and **Create app**. Name it Box, description anything.
2. In the app settings, add the redirect URI **exactly**:
   `box://oauth/spotify`
   (Spotify matches it character-for-character, including case.)
3. Copy the **Client ID** from the dashboard and **give it to Cosmo
   once**. It goes in `OAuthConfig.kt` (`SPOTIFY_CLIENT_ID`) and ships
   with the app. No secret is needed — Box uses Authorization Code +
   PKCE, which is the flow Spotify requires for mobile apps.
4. In Box, open **More → Spotify → Connect Spotify** and sign in.

Two honest limits:

- **Playback control needs Premium.** Play/pause through the Web API
  only works on Premium accounts, and only while a Spotify device is
  already open/playing. A free account still gets the library,
  playlists, and currently playing. When control is refused, Box says
  which of the two is missing instead of showing a generic error.
- **Development mode:** a new Spotify app starts in development mode.
  If sign-in complains the account isn't allowed, add the account under
  the app's **Users and Access** in the dashboard.

## 3. Discord (servers, channels, messages — with one real limit)

What it does: profile, server list, text-channel lists. Read-only —
Box never sends messages and never uses a bot token.

Scopes Box asks for: `identify email guilds`.

Steps:

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
   → **New Application**. Name it Box.
2. **OAuth2 → Redirects**: add **exactly** `box://oauth/discord`,
   then Save Changes.
3. **General Information**: copy the **Application ID** — that is the
   client ID. **Give it to Cosmo once.** It goes in `OAuthConfig.kt`
   (`DISCORD_CLIENT_ID`) and ships with the app. (The Client Secret on
   that page is never needed: Box uses PKCE.)
4. In Box, open **More → Discord → Connect Discord** and sign in.

**The real limit — read this before expecting messages to load:**
Discord's `messages.read` scope is for local RPC server access only,
per Discord's own docs. A user OAuth2 token **cannot read channel
messages through Discord's HTTP API**. Box requests only the scopes
that actually work for a user token, and it still attempts the
messages endpoint — but when Discord refuses (401/403), the screen
says plainly that Discord doesn't allow it, instead of showing an
empty list and pretending there are no messages. Servers and channels
work fine. Sending messages is impossible with a user token, so Box
doesn't try.

## 4. Reddit search (app-only OAuth, no user sign-in)

Reddit blocks anonymous search, so search goes through an "installed
app" client ID — free, no user login, no secret.

1. Go to [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps)
   → **create another app…** → type **installed app**, name it Box.
   No redirect URI needed.
2. Copy the **client ID** (the short string under the app name) and
   **give it to Cosmo once**. It goes in `OAuthConfig.kt`
   (`REDDIT_CLIENT_ID`) and ships with the app.
3. Reddit search starts working; browsing r/popular and subreddits
   never needed the ID.

## 5. Microsoft (Outlook mail + calendar, OneDrive)

What it does: one sign-in, Outlook mail (read, search, reply,
compose), calendar events for the next 7 days (bundled into the
Email tab, Outlook-style), OneDrive files (browse, search, folders,
read-only).

Scopes Box asks for (all delegated Microsoft Graph, no admin
consent needed): `User.Read Mail.Read Mail.Send Calendars.Read
Files.Read offline_access` (`offline_access` is what gets a refresh
token, so Box stays signed in).

**The Azure offer in the GitHub Student Pack:** claim **Azure for
Students** from the pack (education.github.com/pack → Azure offer).
It is free, needs no credit card, and gives $100 of Azure credit —
but the app registration itself is free and does not use the credit.
Claiming it gives you an Azure account/tenant to register the app
under; if you already have a Microsoft account you can also register
straight from the Entra admin center without it.

Steps:

1. Go to the [Microsoft Entra admin center](https://entra.microsoft.com)
   → **App registrations** → **New registration**. Name it Box.
2. **Supported account types:** pick **Accounts in any organizational
   directory and personal Microsoft accounts** — that covers both a
   school/work account and a personal Outlook/Hotmail account signing
   in.
3. Leave **Redirect URI** empty on this form; it is set on the next
   screen. Click **Register**.
4. On the app's page, go to **Authentication** → **Add a platform**
   → **Mobile and desktop applications**. Under **Custom redirect
   URIs** add **exactly** `box://oauth/microsoft` and click
   **Configure**. (Entra accepts a custom `<scheme>://<path>` URI for
   native apps — this is the form Box uses, same pattern as
   `box://oauth/spotify` and `box://oauth/discord`. If the portal ever
   refuses it, the MSAL fallback is `msauth://com.ppp65isking.box/<signature-hash>`,
   using the app's signing-certificate SHA-1 — ask Cosmo, it needs the
   debug keystore hash.)
5. Still on **Authentication**, the app is a public client by
   default under this platform — no secret is created and none is
   needed: Box uses Authorization Code + PKCE, same as Spotify and
   Discord.
6. Go to **API permissions** → **Add a permission** → **Microsoft
   Graph** → **Delegated permissions**, and add: `User.Read`,
   `Mail.Read`, `Mail.Send`, `Calendars.Read`, `Files.Read`,
   `offline_access`. (These are all user-consentable — no admin
   consent is needed, and no "Grant admin consent" step is required
   for a personal account.)
7. Copy the **Application (client) ID** from the app's Overview page
   (the GUID — not a secret) and **give it to Cosmo once**. It goes in
   `OAuthConfig.kt` (`MICROSOFT_CLIENT_ID`) and ships with the app.
8. In Box, tap **Outlook** or **OneDrive** on Home to open the
   Microsoft hub, then **Connect Microsoft** and sign in with your
   own Microsoft account.

Until the client ID is in, the Microsoft hub says plainly that
sign-in isn't available yet — same as Reddit's paused state. The hub
is Email (Outlook mail + calendar bundled) and Files (OneDrive); a
third tab is still undecided.

**Xbox was checked and dropped (2026-11-09):** the Xbox Live auth
path (XSTS token flow) was verified live — `login.live.com`,
`xsts.auth.xboxlive.com`, `profile.xboxlive.com`,
`social.xboxlive.com`, `userpresence.xboxlive.com` and
`titlehub.xboxlive.com` all answer — but Anthony dropped the Xbox
tab before it was built. If he re-picks it, the tab needs its own
Xbox Live sign-in (separate from the Graph login), via the
community-documented XSTS flow against login.live.com.

---

## Testing it end to end

1. Check `OAuthConfig.kt` has the client IDs filled in — blank means
   that service's screen says "sign-in isn't available yet".
2. Open each service under More and tap Connect. You should land in a
   Custom Tab (browser sheet inside Box), sign in with your own
   account, and come straight back into Box signed in.
3. Sign out from the top of any service screen to test again.

## Where things live (for the next person touching this code)

- `app/src/main/java/com/ppp65isking/box/data/OAuthConfig.kt` — the
  app's client IDs, shipped in the app (blank until the dev apps
  exist).
- `app/src/main/java/com/ppp65isking/box/data/OAuth.kt` — service
  definitions, PKCE, auth-URL building, code exchange, token refresh.
- `app/src/main/java/com/ppp65isking/box/data/OAuthStore.kt` — user
  tokens in SharedPreferences.
- `app/src/main/java/com/ppp65isking/box/data/GoogleClient.kt`,
  `SpotifyClient.kt`, `DiscordClient.kt`, `MicrosoftGraphClient.kt`
  — the API calls (read-only, except Spotify play/pause, Gmail
  send/star, and Outlook reply/compose).
- `app/src/main/java/com/ppp65isking/box/data/RedditClient.kt` —
  Reddit browsing + app-only OAuth search.
- `app/src/main/java/com/ppp65isking/box/ui/GoogleScreen.kt`,
  `SpotifyScreen.kt`, `DiscordScreen.kt`, `RedditScreen.kt`,
  `MicrosoftScreen.kt` — the screens. Each one owns its own Connect
  card at the top; nothing about accounts lives in Settings.
- `app/src/main/java/com/ppp65isking/box/ui/SetupScreen.kt` —
  Settings (floating bottom bar toggle only).
- `BoxApp.handleOAuthRedirect()` — the `box://oauth/<service>` return
  path (intent-filter in `AndroidManifest.xml`).
- `app/src/main/java/com/ppp65isking/box/data/UiPrefs.kt` — UI
  preferences, including the floating bottom bar toggle (default on).
- `app/src/main/java/com/ppp65isking/box/MainActivity.kt` — the
  floating icon-only bottom bar (Haze blur over scrolling content).

## AI in Box (Phase 1)

Multiple free AI models with automatic fallback switching — one
`AiRouter.complete(task)` entry point, every feature calls it, never
a model directly. Full research: `ai-in-box-research.md` (§9).

- Chain (Auto): Gemini Nano → downloaded Qwen model → Groq cloud →
  Gemini cloud → OpenRouter free → ML Kit classic (Smart Reply) →
  plain list. A leg that fails or hits a rate limit is cooled down
  (15 min for rate limits, 5 min otherwise) instead of hammered.
- `app/src/main/java/com/ppp65isking/box/data/ai/` — `AiRouter.kt`
  (types + router), `NanoEngine.kt` (ML Kit GenAI Prompt API,
  `genai-prompt:1.0.0-beta4`), `LiteRtEngine.kt` (LiteRT-LM
  `litertlm-android:0.16.1` — pinned: 0.17+ needs Kotlin 2.3+, the
  build has Kotlin 2.2.10 — plus the model download with size +
  SHA-256 checks), `CloudEngines.kt` (Groq / Gemini / OpenRouter,
  per-user keys only), `ClassicEngine.kt` (Smart Reply),
  `PlainEngine.kt`, `EntityActions.kt`, `AiPrefs.kt`, `AiKeyStore.kt`.
- The downloaded model is Qwen 2.5 1.5B int8 `.litertlm` (~1.6 GB),
  NOT the 0.5B: the litert-community 0.5B repo has no `.litertlm`
  bundle (only MediaPipe `.task` files, which LiteRT-LM can't load).
  Wi-Fi-only download, verified by exact size + SHA-256.
- Gemini Nano is FOREGROUND-ONLY (BACKGROUND_USE_BLOCKED) — every
  Nano call in Box runs while Box is open, on tap. The Settings
  "Check Nano" button is the on-device test for sideloaded Box.
- No Muse leg: no free Muse API exists (pay-as-you-go, key required).
  No Together / Cerebras / HF free legs (terms changed Oct 2026).
  No keys are ever embedded in the app or repo — cloud keys are
  typed in by the user on the AI settings screen, same pattern as
  the Steam key.
- Features: "Catch me up" digest on Inbox, Smart Reply drafts +
  entity quick actions on Messages, Settings → "AI model" screen
  (Auto/force modes, model download, keys, Nano check).
