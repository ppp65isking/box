package com.ppp65isking.box.data

/**
 * Box's own OAuth client IDs — one per service, shipped in the app.
 *
 * Anthony registers each dev app once (the steps are in SETUP.md at the
 * repo root) and hands the client ID to Cosmo once; it lands here and
 * ships with the app. End users never see a client ID, never paste
 * anything, and never need a dev account: they just tap Connect on the
 * service's screen and sign in with their own account.
 *
 * These IDs are PUBLIC identifiers, not secrets — every service here
 * uses Authorization Code + PKCE, so there is no client secret
 * anywhere. Embedding them is exactly how a public app is meant to
 * work. (The same goes for the Reddit "installed app" ID.)
 *
 * Blank for now: no dev accounts exist yet. While an ID here is blank,
 * that service's screen says plainly that sign-in isn't available yet
 * and hides its Connect button.
 */
object OAuthConfig {
    /** Google Cloud OAuth client ID (`….apps.googleusercontent.com`). */
    const val GOOGLE_CLIENT_ID: String = ""

    /** Spotify app client ID from the developer dashboard. */
    const val SPOTIFY_CLIENT_ID: String = ""

    /** Discord application ID (the OAuth2 client ID). */
    const val DISCORD_CLIENT_ID: String = "1569326730638786754"

    /** Reddit "installed app" client ID, for app-only search OAuth. */
    const val REDDIT_CLIENT_ID: String = ""
}

/** The shipped client ID for an OAuth service, or null while blank. */
fun OAuthService.configuredClientId(): String? =
    when (this) {
        OAuthService.GOOGLE -> OAuthConfig.GOOGLE_CLIENT_ID
        OAuthService.SPOTIFY -> OAuthConfig.SPOTIFY_CLIENT_ID
        OAuthService.DISCORD -> OAuthConfig.DISCORD_CLIENT_ID
    }.takeIf { it.isNotBlank() }

/** The shipped Reddit client ID, or null while blank. */
fun redditConfiguredClientId(): String? =
    OAuthConfig.REDDIT_CLIENT_ID.takeIf { it.isNotBlank() }
