package com.kutir.drtube.data.api

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Query

interface YoutubeApiService {

    @GET("youtube/v3/videos")
    suspend fun getMostPopular(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("chart") chart: String = "mostPopular",
        @Query("regionCode") regionCode: String = "ID",
        @Query("videoCategoryId") categoryId: String? = null,
        @Query("maxResults") maxResults: Int = 25,
        @Query("key") key: String
    ): YoutubeVideoListResponse

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet,statistics",
        @Query("type") type: String = "video",
        @Query("q") query: String? = null,
        @Query("channelId") channelId: String? = null,
        @Query("order") order: String? = null,
        @Query("eventType") eventType: String? = null,
        @Query("videoDuration") videoDuration: String? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") key: String
    ): YoutubeSearchResponse

    @GET("youtube/v3/videos")
    suspend fun getVideosByIds(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") ids: String,
        @Query("key") key: String
    ): YoutubeVideoListResponse

    @GET("youtube/v3/channels")
    suspend fun getChannels(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") ids: String,
        @Query("key") key: String
    ): YoutubeChannelListResponse

    @GET("youtube/v3/commentThreads")
    suspend fun getCommentThreads(
        @Query("part") part: String = "snippet,replies",
        @Query("videoId") videoId: String,
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") key: String
    ): YoutubeCommentResponse

    @GET("youtube/v3/search")
    suspend fun getRelatedVideos(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 15,
        @Query("key") key: String
    ): YoutubeSearchResponse

    @POST("youtube/v3/comments?part=snippet")
    suspend fun postComment(
        @Header("Authorization") token: String,
        @Body request: YoutubeCommentPostRequest,
        @Query("key") key: String
    ): YoutubeCommentItem

    @GET("youtube/v3/subscriptions")
    suspend fun listSubscriptions(
        @Header("Authorization") token: String,
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("forChannelId") forChannelId: String? = null,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") key: String
    ): YoutubeSubscriptionListResponse

    @POST("youtube/v3/subscriptions?part=snippet")
    suspend fun insertSubscription(
        @Header("Authorization") token: String,
        @Body request: YoutubeSubscriptionInsertRequest,
        @Query("key") key: String
    ): YoutubeSubscriptionItem

    @DELETE("youtube/v3/subscriptions")
    suspend fun deleteSubscription(
        @Header("Authorization") token: String,
        @Query("id") subscriptionId: String,
        @Query("key") key: String
    ): retrofit2.Response<Unit>

    @POST("youtube/v3/videos/rate")
    suspend fun rateVideo(
        @Header("Authorization") token: String,
        @Query("id") videoId: String,
        @Query("rating") rating: String,
        @Query("key") key: String
    ): retrofit2.Response<Unit>

    @GET("youtube/v3/videos")
    suspend fun getVideoRating(
        @Header("Authorization") token: String,
        @Query("part") part: String = "status",
        @Query("id") ids: String,
        @Query("key") key: String
    ): YoutubeVideoRatingResponse
}

data class YoutubeSubscriptionListResponse(
    val items: List<YoutubeSubscriptionItem> = emptyList()
)

data class YoutubeSubscriptionItem(
    val id: String? = null,
    val snippet: YoutubeSubscriptionSnippet? = null
)

data class YoutubeSubscriptionSnippet(
    val resourceId: YoutubeSubscriptionResourceId? = null,
    val title: String? = null,
    val channelId: String? = null
)

data class YoutubeSubscriptionResourceId(
    val channelId: String? = null
)

data class YoutubeSubscriptionInsertRequest(
    val snippet: YoutubeSubscriptionInsertSnippet
)

data class YoutubeSubscriptionInsertSnippet(
    val resourceId: YoutubeSubscriptionResourceId
)

data class YoutubeVideoRatingResponse(
    val items: List<YoutubeVideoRatingItem> = emptyList()
)

data class YoutubeVideoRatingItem(
    val id: String? = null,
    val status: YoutubeVideoStatus? = null
)

data class YoutubeVideoStatus(
    val privacyStatus: String? = null,
    val license: String? = null,
    val publicStatsViewable: Boolean? = null,
    val embeddable: Boolean? = null,
    val madeForKids: Boolean? = null,
    val selfDeclaredMadeForKids: Boolean? = null,
    val uploadStatus: String? = null
)


data class YoutubeChannelListResponse(
    val items: List<YoutubeChannelItem> = emptyList()
)

data class YoutubeChannelItem(
    val id: String? = null,
    val snippet: YoutubeChannelSnippet? = null,
    val statistics: YoutubeChannelStatistics? = null
)

data class YoutubeChannelStatistics(
    val subscriberCount: String? = null
)

data class YoutubeChannelSnippet(
    val title: String? = null,
    val thumbnails: YoutubeThumbnails? = null
)

data class YoutubeVideoListResponse(
    val items: List<YoutubeVideoItem> = emptyList()
)

data class YoutubeVideoItem(
    val id: String? = null,
    val snippet: YoutubeSnippet? = null,
    val contentDetails: YoutubeContentDetails? = null,
    val statistics: YoutubeStatistics? = null
)

data class YoutubeSearchResponse(
    val items: List<YoutubeSearchItem> = emptyList(),
    val nextPageToken: String? = null
)

data class YoutubeSearchItem(
    val id: YoutubeSearchId? = null,
    val snippet: YoutubeSnippet? = null
)

data class YoutubeSearchId(
    val videoId: String? = null
)

data class YoutubeSnippet(
    val title: String? = null,
    val description: String? = null,
    val channelTitle: String? = null,
    val channelId: String? = null,
    val publishedAt: String? = null,
    val thumbnails: YoutubeThumbnails? = null
)

data class YoutubeThumbnails(
    val default: YoutubeThumbnail? = null,
    val medium: YoutubeThumbnail? = null,
    val high: YoutubeThumbnail? = null
)

data class YoutubeThumbnail(
    val url: String? = null
)

data class YoutubeContentDetails(
    val duration: String? = null
)

data class YoutubeStatistics(
    val viewCount: String? = null
)



data class YoutubeCommentResponse(
    val items: List<YoutubeCommentItem> = emptyList()
)

data class YoutubeCommentItem(
    val id: String? = null,
    val snippet: YoutubeCommentSnippet? = null
)

data class YoutubeCommentSnippet(
    val topLevelComment: YoutubeTopLevelComment? = null,
    val totalReplyCount: Int? = null,
    val replies: YoutubeCommentReplies? = null
)

data class YoutubeCommentReplies(
    val comments: List<YoutubeCommentItem> = emptyList()
)

data class YoutubeTopLevelComment(
    val id: String? = null,
    val snippet: YoutubeCommentDetails? = null
)

data class YoutubeCommentDetails(
    val authorDisplayName: String? = null,
    val authorProfileImageUrl: String? = null,
    val authorChannelId: YoutubeAuthorChannelId? = null,
    val textDisplay: String? = null,
    val publishedAt: String? = null,
    val likeCount: Long? = null
)

data class YoutubeAuthorChannelId(
    val value: String? = null
)


data class YoutubeCommentPostRequest(
    val snippet: YoutubeCommentPostSnippet
)

data class YoutubeCommentPostSnippet(
    val videoId: String? = null,
    val parentId: String? = null,
    val textOriginal: String
)
