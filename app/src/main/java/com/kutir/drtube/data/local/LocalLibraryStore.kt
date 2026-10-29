package com.kutir.drtube.data.local

import android.content.Context
import android.content.SharedPreferences
import com.kutir.drtube.data.model.Channel
import com.kutir.drtube.data.model.Video
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LocalLibraryStore(context: Context) {

    private companion object {
        const val PREFS_NAME = "drtube_library"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_PROGRESS = "progress"
        const val MAX_HISTORY = 100
        val VIDEO_LIST_TYPE = object : TypeToken<List<Video>>() {}.type
        val CHANNEL_LIST_TYPE = object : TypeToken<List<Channel>>() {}.type
        val PROGRESS_MAP_TYPE = object : TypeToken<Map<String, Long>>() {}.type
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    fun getFavorites(): List<Video> = synchronized(lock) { readVideos(KEY_FAVORITES) }

    fun isFavorite(videoId: String): Boolean = getFavorites().any { it.id == videoId }

    fun toggleFavorite(video: Video): Boolean = synchronized(lock) {
        val current = readVideos(KEY_FAVORITES)
        val exists = current.any { it.id == video.id }
        val updated = if (exists) {
            current.filterNot { it.id == video.id }
        } else {
            listOf(video) + current.filterNot { it.id == video.id }
        }
        write(KEY_FAVORITES, updated)
        !exists
    }

    fun getHistory(): List<Video> = synchronized(lock) { readVideos(KEY_HISTORY) }

    fun recordHistory(video: Video) = synchronized(lock) {
        val updated = (listOf(video) + readVideos(KEY_HISTORY).filterNot { it.id == video.id })
            .take(MAX_HISTORY)
        write(KEY_HISTORY, updated)
    }

    fun getProgress(videoId: String): Long = synchronized(lock) {
        readProgress()[videoId] ?: 0L
    }

    fun saveProgress(videoId: String, positionMs: Long) = synchronized(lock) {
        if (videoId.isBlank() || positionMs <= 0) return@synchronized
        val updated = readProgress().toMutableMap()
        updated[videoId] = positionMs
        write(KEY_PROGRESS, updated)
    }

    fun getSubscriptions(): List<Channel> = synchronized(lock) {
        readChannels(KEY_SUBSCRIPTIONS)
    }

    fun isSubscribed(channelId: String): Boolean =
        getSubscriptions().any { it.id == channelId }

    fun toggleSubscription(channel: Channel): Boolean = synchronized(lock) {
        val current = readChannels(KEY_SUBSCRIPTIONS)
        val exists = current.any { it.id == channel.id }
        val updated = if (exists) {
            current.filterNot { it.id == channel.id }
        } else {
            listOf(channel) + current.filterNot { it.id == channel.id }
        }
        write(KEY_SUBSCRIPTIONS, updated)
        !exists
    }

    private fun readVideos(key: String): List<Video> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Video>>(raw, VIDEO_LIST_TYPE) }
            .getOrDefault(emptyList())
    }

    private fun readChannels(key: String): List<Channel> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Channel>>(raw, CHANNEL_LIST_TYPE) }
            .getOrDefault(emptyList())
    }

    private fun readProgress(): Map<String, Long> {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, Long>>(raw, PROGRESS_MAP_TYPE) }
            .getOrDefault(emptyMap())
    }

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }
}
