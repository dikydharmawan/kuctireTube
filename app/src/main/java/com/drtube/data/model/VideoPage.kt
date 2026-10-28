package com.drtube.data.model

data class VideoPage(
    val videos: List<Video>,
    val nextPageToken: String? = null
)
