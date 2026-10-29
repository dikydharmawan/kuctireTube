package com.kutir.drtube.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommentModel(
    val id: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val commentText: String = "",
    val publishedAt: String = "",
    val likeCount: Long = 0L,
    val isReply: Boolean = false,
    val parentId: String = ""
) : Parcelable
