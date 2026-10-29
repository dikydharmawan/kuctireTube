package com.kutir.drtube.data.repository

import com.kutir.drtube.data.model.Channel
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.model.VideoPage
import com.kutir.drtube.data.model.VideoStreamInfo

interface VideoRepository {
    suspend fun getFeedCategories(): Result<Map<String, List<Video>>>
    suspend fun getVideoStream(videoId: String): Result<VideoStreamInfo>
    suspend fun saveWatchProgress(videoId: String, positionMs: Long): Result<Unit>
    suspend fun getWatchProgress(videoId: String): Result<Long>
    suspend fun getFavorites(): Result<List<Video>>
    suspend fun isFavorite(videoId: String): Boolean
    suspend fun toggleFavorite(video: Video): Result<Boolean>
    suspend fun getWatchHistory(): Result<List<Video>>
    suspend fun recordWatchHistory(video: Video)
    suspend fun getSubscriptions(): Result<List<Channel>>
    suspend fun isSubscribed(channelId: String): Boolean
    suspend fun toggleSubscription(channel: Channel): Result<Boolean>
    suspend fun checkYoutubeSubscription(channelId: String, accessToken: String): Result<Boolean>
    suspend fun subscribeYoutubeChannel(channelId: String, accessToken: String): Result<Boolean>
    suspend fun unsubscribeYoutubeChannel(channelId: String, accessToken: String): Result<Boolean>
    suspend fun rateYoutubeVideo(videoId: String, rating: String, accessToken: String): Result<Boolean>
    suspend fun getYoutubeRating(videoId: String, accessToken: String): Result<String?>
    suspend fun searchVideos(query: String, pageToken: String? = null): Result<VideoPage>
    suspend fun getChannelVideos(channelId: String, pageToken: String? = null): Result<VideoPage>
    suspend fun getVideoDetails(videoId: String): Result<Video>
    suspend fun getComments(videoId: String): Result<List<com.kutir.drtube.data.model.CommentModel>>
    suspend fun getRelatedVideos(videoId: String): Result<List<Video>>
    suspend fun postComment(videoId: String, text: String, accessToken: String): Result<Unit>
    suspend fun postReply(parentId: String, text: String, accessToken: String): Result<Unit>
    suspend fun getChannelSubscriberCount(channelId: String): Result<String>
    suspend fun getSubscriptionFeed(): Result<List<Video>>
    suspend fun getShorts(): Result<List<Video>>
}
