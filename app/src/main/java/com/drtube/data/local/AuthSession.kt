package com.drtube.data.local

import android.content.Context

object AuthSession {

    private const val PREFS = "kuctire_auth_session"
    private const val KEY_YOUTUBE_TOKEN = "youtube_access_token"

    private var cachedToken: String? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    var youtubeAccessToken: String?
        get() {
            if (cachedToken == null) {
                cachedToken = appContext
                    ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    ?.getString(KEY_YOUTUBE_TOKEN, null)
            }
            return cachedToken
        }
        set(value) {
            cachedToken = value
            appContext
                ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_YOUTUBE_TOKEN, value)
                ?.apply()
        }

    fun clear() {
        cachedToken = null
        appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_YOUTUBE_TOKEN)
            ?.apply()
    }
}
