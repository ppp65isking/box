# Box — Developer Setup (Google, Spotify, Discord)

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
not per-user. No client secrets are involved anywhere — all three
services use Authorization Code + PKCE, so the client ID alone is the
credential.

**Why embedding the IDs is safe:** an OAuth client ID is a public
identifier, not a secret — it shows up in the sign-in URL of every app
that uses one. The secret part of OAuth (the client secret) doesn't
exist here at all, because PKCE replaces it. Nothing sensitive ships
in the app; the IDs just tell Google / Spotify / Discord / Reddit
which Box app is asking.

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
   OAuth client ID → application type **Desktop app**). Desktop apps
   need no client secret with PKCE. The create form asks for no
   redirect — that is normal; it goes on the client's edit page next.
5. Click into the client you just made and add the authorized
   redirect URI **exactly**:
   `com.googleusercontent.apps.217889708383:/oauth2redirect`
   (Google refuses `box://…` here — custom schemes need a period in
   them — so Box uses this reverse-DNS form built from the client
   ID's number instead. If the client ID ever changes, this URI and
   the matching intent-filter in `AndroidManifest.xml` change with it.)
6. Copy the **Client ID** (the long `….apps.googleusercontent.com`
   string — not the secret) and **give it to Cosmo once**. It goes in
   `OAuthConfig.kt` (`GOOGLE_CLIENT_ID`) and ships with the app.
7. In Box, open **More → Google → Connect Google** and sign in.

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
  `SpotifyClient.kt`, `DiscordClient.kt` — the API calls (read-only,
  except Spotify play/pause).
- `app/src/main/java/com/ppp65isking/box/data/RedditClient.kt` —
  Reddit browsing + app-only OAuth search.
- `app/src/main/java/com/ppp65isking/box/ui/GoogleScreen.kt`,
  `SpotifyScreen.kt`, `DiscordScreen.kt`, `RedditScreen.kt` — the
  screens. Each one owns its own Connect card at the top; nothing
  about accounts lives in Settings.
- `app/src/main/java/com/ppp65isking/box/ui/SetupScreen.kt` —
  Settings (floating bottom bar toggle only).
- `BoxApp.handleOAuthRedirect()` — the `box://oauth/<service>` return
  path (intent-filter in `AndroidManifest.xml`).
- `app/src/main/java/com/ppp65isking/box/data/UiPrefs.kt` — UI
  preferences, including the floating bottom bar toggle (default on).
- `app/src/main/java/com/ppp65isking/box/MainActivity.kt` — the
  floating icon-only bottom bar (Haze blur over scrolling content).
