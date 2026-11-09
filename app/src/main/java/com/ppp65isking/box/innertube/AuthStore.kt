package com.ppp65isking.box.innertube

import android.content.Context
import android.content.SharedPreferences

/**
 * Small persistent store for the InnerTube session.
 *
 * Holds visitorData (issued by the first response, required on every later
 * call or the player answers LOGIN_REQUIRED). No account data lives here
 * in v1 — Box plays YouTube Music anonymously, exactly like the YTM TV app.
 */
class AuthStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_ytm_auth", Context.MODE_PRIVATE)

    var visitorData: String?
        get() = prefs.getString(KEY_VISITOR_DATA, null)
        set(value) {
            prefs.edit().putString(KEY_VISITOR_DATA, value).apply()
        }

    companion object {
        private const val KEY_VISITOR_DATA = "visitor_data"
    }
}
