package com.kutir.drtube.data.model

data class VideoPage(
    val videos: List<Video>,
    val nextPageToken: String? = null
)
