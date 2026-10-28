package com.drtube.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Video(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val channelTitle: String,
    val viewsCount: String,
    val publishedAt: String,
    val duration: String = "",
    val channelId: String = "",
    val channelAvatarUrl: String = ""
) : Parcelable
