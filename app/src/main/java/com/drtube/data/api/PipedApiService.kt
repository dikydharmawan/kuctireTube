package com.drtube.data.api

import retrofit2.http.GET
import retrofit2.http.Url

interface PipedApiService {
    @GET
    suspend fun getTrending(@Url url: String): List<PipedVideoResponse>

    @GET
    suspend fun getVideoStream(@Url url: String): PipedStreamResponse

    @GET
    suspend fun search(@Url url: String): PipedSearchResponse

    @GET
    suspend fun getChannel(@Url url: String): PipedChannelResponse
}

data class PipedVideoResponse(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val duration: Long? = null,
    val views: Long? = null,
    val uploaded: Long? = null,
    val uploadedDate: String? = null,
    val isShort: Boolean = false
) {
    val videoId: String
        get() = if (url.contains("?v=")) {
            url.substringAfter("?v=").substringBefore("&")
        } else {
            url.substringAfterLast("/")
        }
}

data class PipedSearchResponse(
    val items: List<PipedSearchItem> = emptyList()
)

data class PipedChannelResponse(
    val relatedStreams: List<PipedVideoResponse> = emptyList()
)

data class PipedSearchItem(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val duration: Long? = null,
    val views: Long? = null,
    val uploaded: Long? = null,
    val uploadedDate: String? = null,
    val isShort: Boolean = false
) {
    val videoId: String
        get() = if (url.contains("?v=")) {
            url.substringAfter("?v=").substringBefore("&")
        } else {
            url.substringAfterLast("/")
        }
}

data class PipedStreamResponse(
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream>? = null
)

data class VideoStream(
    val url: String,
    val format: String? = null,
    val quality: String? = null,
    val mimeType: String? = null,
    val videoOnly: Boolean? = null
)

data class AudioStream(
    val url: String,
    val format: String? = null,
    val quality: String? = null,
    val mimeType: String? = null
)
