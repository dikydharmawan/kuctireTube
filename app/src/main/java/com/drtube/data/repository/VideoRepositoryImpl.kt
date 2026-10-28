package com.drtube.data.repository

import com.drtube.data.api.PipedApiService
import com.drtube.data.api.PipedStreamResponse
import com.drtube.data.api.PipedVideoResponse
import com.drtube.data.api.VideoStream
import com.drtube.data.api.YoutubeApiService
import com.drtube.data.local.LocalLibraryStore
import com.drtube.data.mapper.toVideo
import com.drtube.data.model.Channel
import com.drtube.data.model.CommentModel
import com.drtube.data.model.Video
import com.drtube.data.model.VideoPage
import com.drtube.data.api.YoutubeCommentPostRequest
import com.drtube.data.api.YoutubeCommentPostSnippet
import com.drtube.data.model.VideoStreamInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val pipedApi: PipedApiService,
    private val youtubeApi: YoutubeApiService,
    private val youtubeApiKey: String,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext context: Context
) : VideoRepository {

    private data class CacheEntry<T>(val timestamp: Long, val value: T)

    private companion object {
        const val REGION = "ID"
        const val FEED_CACHE_TTL_MS = 10 * 60 * 1000L
        const val SEARCH_CACHE_TTL_MS = 10 * 60 * 1000L
        const val MAX_SEARCH_CACHE_ENTRIES = 30
        const val MAX_PROGRESSIVE_HEIGHT = 720
        const val MAX_CHANNELS_PER_REQUEST = 50
        const val MAX_SUBSCRIPTION_CHANNELS = 20
        const val SUBSCRIPTION_VIDEOS_PER_CHANNEL = 15

        val PIPED_INSTANCES = listOf(
            "https://pipedapi.ducks.party",
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space"
        )

        val YOUTUBE_CATEGORIES = listOf(
            "Trending" to null,
            "Musik" to "10",
            "Gaming" to "20",
            "Berita" to "25"
        )
    }

    private val localStore = LocalLibraryStore(context)

    private val sampleStreamUrl = "https://media.w3.org/2010/05/bunny/trailer.mp4"

    @Volatile
    private var feedCache: CacheEntry<Map<String, List<Video>>>? = null

    private val searchCache = ConcurrentHashMap<String, CacheEntry<VideoPage>>()

    @Volatile
    private var shortsCache: CacheEntry<List<Video>>? = null

    @Volatile
    private var subscriptionFeedCache: CacheEntry<List<Video>>? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val fallbackCategories: Map<String, List<Video>> = mapOf(
        "Rekomendasi" to listOf(
            Video(
                id = "dQw4w9WgXcQ",
                title = "Big Buck Bunny Animation (4K)",
                description = "A large and lovable rabbit deals with bullying forest creatures in this classic open-source animation.",
                thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800",
                streamUrl = sampleStreamUrl,
                channelTitle = "Blender Foundation",
                viewsCount = "1,2 jt tayangan",
                publishedAt = "2 tahun lalu"
            ),
            Video(
                id = "sample_2",
                title = "Elephant Dream - Open Movie Project",
                description = "The first open source movie produced by Blender Foundation.",
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800",
                streamUrl = sampleStreamUrl,
                channelTitle = "Blender Cloud",
                viewsCount = "850 rb tayangan",
                publishedAt = "3 tahun lalu"
            )
        ),
        "Trending" to listOf(
            Video(
                id = "sample_3",
                title = "Sintel Open Source Fantasy Movie",
                description = "Sintel is an independently produced short animated film, initiated by the Blender Foundation.",
                thumbnailUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800",
                streamUrl = sampleStreamUrl,
                channelTitle = "Blender Animation",
                viewsCount = "3,4 jt tayangan",
                publishedAt = "1 tahun lalu"
            ),
            Video(
                id = "sample_4",
                title = "Tears of Steel Sci-Fi Short",
                description = "Tears of Steel is a 2012 science fiction short film realized with open source software.",
                thumbnailUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800",
                streamUrl = sampleStreamUrl,
                channelTitle = "Studio Blender",
                viewsCount = "2,1 jt tayangan",
                publishedAt = "4 tahun lalu"
            )
        ),
        "Musik & Hiburan" to listOf(
            Video(
                id = "sample_5",
                title = "Lofi Hip Hop Radio - Beats to Relax/Study to",
                description = "Relaxing lofi beats 24/7 for your study, work, and relaxation sessions.",
                thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800",
                streamUrl = sampleStreamUrl,
                channelTitle = "Lofi Girl",
                viewsCount = "15 jt tayangan",
                publishedAt = "Streaming langsung"
            )
        )
    )

    override suspend fun getFeedCategories(): Result<Map<String, List<Video>>> = withContext(Dispatchers.IO) {
        runCatching {
            feedCache
                ?.takeIf { isFresh(it.timestamp, FEED_CACHE_TTL_MS) }
                ?.let { return@runCatching it.value }

            val youtubeFeed = fetchYoutubeFeed()
            if (youtubeFeed.isNotEmpty()) {
                feedCache = CacheEntry(System.currentTimeMillis(), youtubeFeed)
                return@runCatching youtubeFeed
            }

            val pipedTrending = fetchPipedTrending()
            val result = if (pipedTrending.isNotEmpty()) {
                mapOf("Trending" to pipedTrending)
            } else {
                fallbackCategories
            }
            feedCache = CacheEntry(System.currentTimeMillis(), result)
            result
        }
    }

    override suspend fun searchVideos(
        query: String,
        pageToken: String?
    ): Result<VideoPage> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheKey = query.trim().lowercase(Locale.US)
            if (pageToken == null) {
                searchCache[cacheKey]
                    ?.takeIf { isFresh(it.timestamp, SEARCH_CACHE_TTL_MS) }
                    ?.let { return@runCatching it.value }
            }

            val youtubeResults = searchYoutubePage(query, pageToken)
            val page = if (youtubeResults.videos.isNotEmpty()) {
                youtubeResults
            } else if (pageToken == null) {
                VideoPage(searchPiped(query), null)
            } else {
                VideoPage(emptyList(), null)
            }

            if (pageToken == null && page.videos.isNotEmpty()) {
                putSearchCache(cacheKey, page)
            }
            page
        }
    }

    override suspend fun getChannelVideos(
        channelId: String,
        pageToken: String?
    ): Result<VideoPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (youtubeApiKey.isBlank() || channelId.isBlank()) {
                    return@runCatching VideoPage(emptyList(), null)
                }

                val searchResponse = youtubeApi.searchVideos(
                    query = null,
                    channelId = channelId,
                    order = "date",
                    pageToken = pageToken,
                    key = youtubeApiKey
                )
                val videoIds = searchResponse.items
                    .mapNotNull { it.id?.videoId }
                    .distinct()
                    .take(25)
                if (videoIds.isEmpty()) {
                    return@runCatching VideoPage(emptyList(), searchResponse.nextPageToken)
                }

                val details = youtubeApi.getVideosByIds(
                    ids = videoIds.joinToString(","),
                    key = youtubeApiKey
                )
                val byId = details.items.associateBy { it.id }
                val videos = videoIds.mapNotNull { id -> byId[id]?.toVideo() }
                val avatars = fetchChannelAvatars(videos.map { it.channelId })
                VideoPage(
                    videos = videos.map { video ->
                        video.copy(
                            channelAvatarUrl = avatars[video.channelId] ?: video.channelAvatarUrl
                        )
                    },
                    nextPageToken = searchResponse.nextPageToken
                )
            }
        }

    override suspend fun getSubscriptionFeed(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching {
            subscriptionFeedCache
                ?.takeIf { isFresh(it.timestamp, SEARCH_CACHE_TTL_MS) }
                ?.let { return@runCatching it.value }

            val subscriptions = localStore.getSubscriptions()
            if (subscriptions.isEmpty()) return@runCatching emptyList()

            val channels = subscriptions.take(MAX_SUBSCRIPTION_CHANNELS)
            val pipedResults = coroutineScope {
                channels.map { channel ->
                    async { channel to fetchPipedChannelVideos(channel.id) }
                }.awaitAll()
            }
            val merged = pipedResults
                .flatMap { (channel, items) ->
                    items.take(SUBSCRIPTION_VIDEOS_PER_CHANNEL)
                        .map { Triple(it, it.uploaded ?: 0L, channel) }
                }
                .sortedByDescending { it.second }
                .map { (item, _, channel) ->
                    item.toVideo().copy(
                        channelAvatarUrl = channel.avatarUrl.ifBlank {
                            item.uploaderAvatar.orEmpty()
                        }
                    )
                }
                .distinctBy { it.id }

            if (merged.isNotEmpty() || youtubeApiKey.isBlank()) {
                if (merged.isNotEmpty()) {
                    subscriptionFeedCache = CacheEntry(System.currentTimeMillis(), merged)
                }
                return@runCatching merged
            }

            val fallbackLists = coroutineScope {
                channels.take(3).map { channel ->
                    async {
                        getChannelVideos(channel.id)
                            .getOrDefault(VideoPage(emptyList(), null))
                            .videos
                    }
                }.awaitAll()
            }
            val fallback = (0 until 10)
                .flatMap { index -> fallbackLists.mapNotNull { it.getOrNull(index) } }
                .distinctBy { it.id }
                .take(30)
            if (fallback.isNotEmpty()) {
                subscriptionFeedCache = CacheEntry(System.currentTimeMillis(), fallback)
            }
            fallback
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || videoId.isBlank()) {
                return@runCatching Video(videoId, "", "", "", "", "", "", "")
            }
            val response = youtubeApi.getVideosByIds(ids = videoId, key = youtubeApiKey)
            val item = response.items.firstOrNull() ?: return@runCatching Video(videoId, "", "", "", "", "", "", "")
            val video = item.toVideo() ?: Video(videoId, "", "", "", "", "", "", "")
            val avatars = fetchChannelAvatars(listOf(video.channelId))
            video.copy(channelAvatarUrl = avatars[video.channelId] ?: video.channelAvatarUrl)
        }
    }

    
    override suspend fun getChannelSubscriberCount(channelId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || channelId.isBlank()) return@runCatching "1jt+ subscriber"
            val response = youtubeApi.getChannels(ids = channelId, key = youtubeApiKey)
            val item = response.items.firstOrNull() ?: return@runCatching "1jt+ subscriber"
            val count = item.statistics?.subscriberCount?.toLongOrNull() ?: return@runCatching "1jt+ subscriber"
            formatSubscriberCount(count)
        }
    }

    private fun formatSubscriberCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.2f jt subscriber", count / 1_000_000.0).replace(".00", "").replace(',', '.')
            count >= 1_000 -> String.format(Locale.US, "%.1f rb subscriber", count / 1_000.0).replace(".0", "")
            else -> "$count subscriber"
        }
    }

    override suspend fun getComments(videoId: String): Result<List<CommentModel>> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || videoId.isBlank()) return@runCatching emptyList()
            val response = youtubeApi.getCommentThreads(videoId = videoId, key = youtubeApiKey)
            android.util.Log.d("DRTube", "Comments response items: ${response.items.size}")
            val result = mutableListOf<CommentModel>()
            response.items.forEach { item ->
                val topItem = item.snippet?.topLevelComment
                val details = topItem?.snippet ?: return@forEach
                val topId = topItem.id ?: item.id.orEmpty()
                result += CommentModel(
                    id = topId,
                    authorName = details.authorDisplayName.orEmpty(),
                    authorAvatarUrl = details.authorProfileImageUrl.orEmpty(),
                    commentText = details.textDisplay.orEmpty(),
                    publishedAt = com.drtube.data.mapper.formatRelativeInstant(details.publishedAt),
                    likeCount = details.likeCount ?: 0L,
                    isReply = false,
                    parentId = ""
                )
                item.snippet.replies?.comments?.forEach { reply ->
                    val replyTop = reply.snippet?.topLevelComment
                    val replyInfo = replyTop?.snippet
                    if (replyInfo != null) {
                        result += CommentModel(
                            id = replyTop.id ?: reply.id.orEmpty(),
                            authorName = replyInfo.authorDisplayName.orEmpty(),
                            authorAvatarUrl = replyInfo.authorProfileImageUrl.orEmpty(),
                            commentText = replyInfo.textDisplay.orEmpty(),
                            publishedAt = com.drtube.data.mapper.formatRelativeInstant(replyInfo.publishedAt),
                            likeCount = replyInfo.likeCount ?: 0L,
                            isReply = true,
                            parentId = topId
                        )
                    }
                }
            }
            result
        }
    }

    override suspend fun postComment(videoId: String, text: String, accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || videoId.isBlank()) return@runCatching Unit
            val request = YoutubeCommentPostRequest(
                snippet = YoutubeCommentPostSnippet(videoId = videoId, textOriginal = text)
            )
            youtubeApi.postComment(token = "Bearer $accessToken", request = request, key = youtubeApiKey)
            Unit
        }
    }

    override suspend fun postReply(parentId: String, text: String, accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || parentId.isBlank()) return@runCatching Unit
            val request = YoutubeCommentPostRequest(
                snippet = YoutubeCommentPostSnippet(parentId = parentId, textOriginal = text)
            )
            youtubeApi.postComment(token = "Bearer $accessToken", request = request, key = youtubeApiKey)
            Unit
        }
    }

    override suspend fun getRelatedVideos(videoId: String): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching {
            if (videoId.isBlank()) return@runCatching emptyList()

            val seed = getVideoDetails(videoId).getOrNull()
            val query = seed?.title?.takeIf { it.isNotBlank() }
                ?: seed?.channelTitle?.takeIf { it.isNotBlank() }
                ?: ""

            // 1. Try YouTube API search
            if (youtubeApiKey.isNotBlank() && query.isNotBlank()) {
                val search = runCatching {
                    youtubeApi.getRelatedVideos(query = query, key = youtubeApiKey)
                }.getOrNull()
                val ids = search?.items?.mapNotNull { it.id?.videoId }
                    ?.filter { it != videoId }
                    ?.distinct()?.take(15).orEmpty()
                if (ids.isNotEmpty()) {
                    val details = youtubeApi.getVideosByIds(ids = ids.joinToString(","), key = youtubeApiKey)
                    val byId = details.items.associateBy { it.id }
                    val videos = ids.mapNotNull { id -> byId[id]?.toVideo() }
                    if (videos.isNotEmpty()) {
                        val avatars = fetchChannelAvatars(videos.map { video -> video.channelId })
                        return@runCatching videos.map { video ->
                            video.copy(channelAvatarUrl = avatars[video.channelId] ?: video.channelAvatarUrl)
                        }
                    }
                }
            }

            // 2. Fallback to Piped search using query or title
            if (query.isNotBlank()) {
                val pipedResults = searchPiped(query).filter { it.id != videoId }
                if (pipedResults.isNotEmpty()) {
                    return@runCatching pipedResults.take(15)
                }
            }

            // 3. Ultimate fallback: fetch trending/popular videos
            val trending = fetchPipedTrending().filter { it.id != videoId }
            if (trending.isNotEmpty()) {
                return@runCatching trending.take(15)
            }

            emptyList()
        }
    }

    override suspend fun getShorts(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching {
            shortsCache
                ?.takeIf { isFresh(it.timestamp, SEARCH_CACHE_TTL_MS) }
                ?.let { return@runCatching it.value }

            val shorts = fetchYoutubeShorts().ifEmpty { searchPipedShorts() }
            if (shorts.isNotEmpty()) {
                shortsCache = CacheEntry(System.currentTimeMillis(), shorts)
            }
            shorts
        }
    }

    override suspend fun getVideoStream(videoId: String): Result<VideoStreamInfo> = withContext(Dispatchers.IO) {
        runCatching {
            resolveWithNewPipe(videoId)?.let { return@runCatching it }

            for (baseUrl in PIPED_INSTANCES) {
                try {
                    val response = pipedApi.getVideoStream("$baseUrl/streams/$videoId")
                    val candidate = selectStream(response) ?: continue
                    val videoPlayable = isPlayable(candidate.videoUrl)
                    val audioPlayable = candidate.audioUrl == null || isPlayable(candidate.audioUrl)
                    if (videoPlayable && audioPlayable) {
                        return@runCatching candidate
                    }
                } catch (e: Exception) {
                    // Try the next Piped instance
                }
            }

            throw IllegalStateException("Stream untuk video ini tidak tersedia")
        }
    }

    override suspend fun saveWatchProgress(videoId: String, positionMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        localStore.saveProgress(videoId, positionMs)
        runCatching {
            val uid = auth.currentUser?.uid ?: return@runCatching Unit
            firestore.collection("users").document(uid)
                .set(mapOf("watch_history.$videoId" to positionMs), SetOptions.merge()).await()
            Unit
        }
    }

    override suspend fun getWatchProgress(videoId: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val localProgress = localStore.getProgress(videoId)
            if (localProgress > 0) return@runCatching localProgress

            val uid = auth.currentUser?.uid ?: return@runCatching 0L
            val doc = firestore.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val history = doc.get("watch_history") as? Map<String, Long> ?: return@runCatching 0L
            history[videoId] ?: 0L
        }
    }

    override suspend fun getFavorites(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching { localStore.getFavorites() }
    }

    override suspend fun isFavorite(videoId: String): Boolean = withContext(Dispatchers.IO) {
        localStore.isFavorite(videoId)
    }

    override suspend fun toggleFavorite(video: Video): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching { localStore.toggleFavorite(video) }
    }

    override suspend fun getWatchHistory(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching { localStore.getHistory() }
    }

    override suspend fun recordWatchHistory(video: Video) = withContext(Dispatchers.IO) {
        localStore.recordHistory(video)
    }

    override suspend fun getSubscriptions(): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching { localStore.getSubscriptions() }
    }

    override suspend fun isSubscribed(channelId: String): Boolean = withContext(Dispatchers.IO) {
        localStore.isSubscribed(channelId)
    }

    override suspend fun toggleSubscription(channel: Channel): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching { localStore.toggleSubscription(channel) }
            .also { subscriptionFeedCache = null }
    }

    override suspend fun checkYoutubeSubscription(channelId: String, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || channelId.isBlank() || accessToken.isBlank()) return@runCatching localStore.isSubscribed(channelId)
            val response = youtubeApi.listSubscriptions(
                token = "Bearer $accessToken",
                forChannelId = channelId,
                maxResults = 1,
                key = youtubeApiKey
            )
            response.items.isNotEmpty()
        }
    }

    override suspend fun subscribeYoutubeChannel(channelId: String, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || channelId.isBlank() || accessToken.isBlank()) {
                val channel = Channel(id = channelId, name = "", avatarUrl = "")
                localStore.toggleSubscription(channel)
                subscriptionFeedCache = null
                return@runCatching true
            }
            val request = com.drtube.data.api.YoutubeSubscriptionInsertRequest(
                snippet = com.drtube.data.api.YoutubeSubscriptionInsertSnippet(
                    resourceId = com.drtube.data.api.YoutubeSubscriptionResourceId(channelId = channelId)
                )
            )
            val result = youtubeApi.insertSubscription(token = "Bearer $accessToken", request = request, key = youtubeApiKey)
            localStore.toggleSubscription(Channel(id = channelId, name = result.snippet?.title.orEmpty(), avatarUrl = ""))
            subscriptionFeedCache = null
            true
        }
    }

    override suspend fun unsubscribeYoutubeChannel(channelId: String, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || channelId.isBlank() || accessToken.isBlank()) {
                val channel = Channel(id = channelId, name = "", avatarUrl = "")
                if (localStore.isSubscribed(channelId)) {
                    localStore.toggleSubscription(channel)
                }
                subscriptionFeedCache = null
                return@runCatching true
            }
            val response = youtubeApi.listSubscriptions(
                token = "Bearer $accessToken",
                forChannelId = channelId,
                maxResults = 1,
                key = youtubeApiKey
            )
            val subId = response.items.firstOrNull()?.id
            if (subId.isNullOrBlank()) {
                if (localStore.isSubscribed(channelId)) {
                    localStore.toggleSubscription(Channel(id = channelId, name = "", avatarUrl = ""))
                }
                subscriptionFeedCache = null
                return@runCatching true
            }
            youtubeApi.deleteSubscription(token = "Bearer $accessToken", subscriptionId = subId, key = youtubeApiKey)
            if (localStore.isSubscribed(channelId)) {
                localStore.toggleSubscription(Channel(id = channelId, name = "", avatarUrl = ""))
            }
            subscriptionFeedCache = null
            true
        }
    }

    override suspend fun rateYoutubeVideo(videoId: String, rating: String, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || videoId.isBlank() || accessToken.isBlank()) return@runCatching false
            val res = youtubeApi.rateVideo(token = "Bearer $accessToken", videoId = videoId, rating = rating, key = youtubeApiKey)
            res.isSuccessful
        }
    }

    override suspend fun getYoutubeRating(videoId: String, accessToken: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            if (youtubeApiKey.isBlank() || videoId.isBlank() || accessToken.isBlank()) return@runCatching null
            val res = youtubeApi.getVideoRating(token = "Bearer $accessToken", ids = videoId, key = youtubeApiKey)
            res.items.firstOrNull()?.status?.privacyStatus
        }
    }

    private suspend fun fetchYoutubeFeed(): Map<String, List<Video>> {
        if (youtubeApiKey.isBlank()) return emptyMap()

        return coroutineScope {
            val results = YOUTUBE_CATEGORIES.map { (_, categoryId) ->
                async {
                    runCatching {
                        youtubeApi.getMostPopular(
                            regionCode = REGION,
                            categoryId = categoryId,
                            key = youtubeApiKey
                        ).items.mapNotNull { it.toVideo() }
                    }.getOrDefault(emptyList())
                }
            }
            val liveDeferred = async {
                runCatching {
                    val search = youtubeApi.searchVideos(
                        query = "live",
                        eventType = "live",
                        maxResults = 10,
                        key = youtubeApiKey
                    )
                    val videoIds = search.items
                        .mapNotNull { it.id?.videoId }
                        .distinct()
                    if (videoIds.isEmpty()) return@runCatching emptyList<Video>()

                    val details = youtubeApi.getVideosByIds(
                        ids = videoIds.joinToString(","),
                        key = youtubeApiKey
                    )
                    val byId = details.items.associateBy { it.id }
                    videoIds.mapNotNull { id -> byId[id]?.toVideo() }
                }.getOrDefault(emptyList())
            }
            val categoryResults = results.awaitAll()
            val liveVideos = liveDeferred.await()

            val feed = linkedMapOf<String, List<Video>>()
            YOUTUBE_CATEGORIES.forEachIndexed { index, (name, _) ->
                val videos = categoryResults[index]
                if (videos.isNotEmpty()) feed[name] = videos
            }
            if (liveVideos.isNotEmpty()) feed["Live"] = liveVideos

            val channelAvatars = fetchChannelAvatars(feed.values.flatten().map { it.channelId })
            if (channelAvatars.isEmpty()) {
                feed
            } else {
                feed.mapValues { (_, videos) ->
                    videos.map { video ->
                        video.copy(
                            channelAvatarUrl = channelAvatars[video.channelId]
                                ?: video.channelAvatarUrl
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchYoutubeShorts(): List<Video> {
        if (youtubeApiKey.isBlank()) return emptyList()

        return runCatching {
            val searchResponse = youtubeApi.searchVideos(
                query = "#shorts",
                videoDuration = "short",
                order = "viewCount",
                maxResults = 25,
                key = youtubeApiKey
            )
            val videoIds = searchResponse.items
                .mapNotNull { it.id?.videoId }
                .distinct()
                .take(20)
            if (videoIds.isEmpty()) return@runCatching emptyList<Video>()

            val details = youtubeApi.getVideosByIds(
                ids = videoIds.joinToString(","),
                key = youtubeApiKey
            )
            val byId = details.items.associateBy { it.id }
            val videos = videoIds.mapNotNull { id -> byId[id]?.toVideo() }
            val avatars = fetchChannelAvatars(videos.map { it.channelId })
            videos.map { video ->
                video.copy(
                    channelAvatarUrl = avatars[video.channelId] ?: video.channelAvatarUrl
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun searchPipedShorts(): List<Video> {
        val encodedQuery = URLEncoder.encode("#shorts", "UTF-8").replace("+", "%20")
        for (baseUrl in PIPED_INSTANCES) {
            try {
                val response = pipedApi.search("$baseUrl/search?q=$encodedQuery&filter=videos")
                val shorts = response.items
                    .filter { it.isShort || isShortDuration(it.duration) }
                    .map { it.toVideo() }
                    .filter { it.title.isNotBlank() }
                if (shorts.isNotEmpty()) return shorts
            } catch (e: Exception) {
                // Try the next Piped instance
            }
        }
        return emptyList()
    }

    private fun isShortDuration(durationSeconds: Long?): Boolean {
        return durationSeconds != null && durationSeconds in 1..180
    }

    private suspend fun searchYoutubePage(query: String, pageToken: String?): VideoPage {
        if (youtubeApiKey.isBlank()) return VideoPage(emptyList(), null)

        return runCatching {
            val searchResponse = youtubeApi.searchVideos(
                query = query,
                pageToken = pageToken,
                key = youtubeApiKey
            )
            val videoIds = searchResponse.items
                .mapNotNull { it.id?.videoId }
                .distinct()
                .take(20)

            if (videoIds.isEmpty()) {
                return@runCatching VideoPage(emptyList(), searchResponse.nextPageToken)
            }

            val details = youtubeApi.getVideosByIds(
                ids = videoIds.joinToString(","),
                key = youtubeApiKey
            )
            val byId = details.items.associateBy { it.id }
            val videos = videoIds.mapNotNull { id -> byId[id]?.toVideo() }

            val channelAvatars = fetchChannelAvatars(videos.map { it.channelId })
            val enriched = videos.map { video ->
                video.copy(
                    channelAvatarUrl = channelAvatars[video.channelId]
                        ?: video.channelAvatarUrl
                )
            }
            VideoPage(enriched, searchResponse.nextPageToken)
        }.getOrDefault(VideoPage(emptyList(), null))
    }

    private suspend fun fetchChannelAvatars(channelIds: List<String>): Map<String, String> {
        if (youtubeApiKey.isBlank()) return emptyMap()

        return runCatching {
            val avatarMap = mutableMapOf<String, String>()
            channelIds
                .filter { it.isNotBlank() }
                .distinct()
                .chunked(MAX_CHANNELS_PER_REQUEST)
                .forEach chunkLoop@ { idChunk ->
                    val response = youtubeApi.getChannels(
                        ids = idChunk.joinToString(","),
                        key = youtubeApiKey
                    )
                    response.items.forEach itemLoop@ { channel ->
                        val channelId = channel.id ?: return@itemLoop
                        val avatarUrl = channel.snippet?.thumbnails?.high?.url
                            ?: channel.snippet?.thumbnails?.medium?.url
                            ?: channel.snippet?.thumbnails?.default?.url
                            ?: return@itemLoop
                        avatarMap[channelId] = avatarUrl
                    }
                }
            avatarMap
        }.getOrDefault(emptyMap())
    }

    private fun resolveWithNewPipe(videoId: String): VideoStreamInfo? {
        return runCatching {
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )

            val muxedStreams = info.videoStreams
                .filter { it.isUrl && it.format == MediaFormat.MPEG_4 }
                .sortedByDescending { it.height }
            val muxed = muxedStreams.firstOrNull { it.height in 1..MAX_PROGRESSIVE_HEIGHT }
                ?: muxedStreams.firstOrNull()
            if (muxed != null) {
                return@runCatching VideoStreamInfo(muxed.content)
            }

            val videoOnlyStreams = info.videoOnlyStreams
                .filter { it.isUrl && it.format == MediaFormat.MPEG_4 }
                .sortedByDescending { it.height }
            val videoStream = videoOnlyStreams.firstOrNull {
                it.height in 1..MAX_PROGRESSIVE_HEIGHT
            } ?: videoOnlyStreams.firstOrNull() ?: return@runCatching null

            val audioStream = info.audioStreams
                .filter { it.isUrl && it.format == MediaFormat.M4A }
                .maxByOrNull { it.averageBitrate }

            VideoStreamInfo(videoStream.content, audioStream?.content)
        }.getOrNull()
    }

    private suspend fun fetchPipedChannelVideos(channelId: String): List<PipedVideoResponse> {
        if (channelId.isBlank()) return emptyList()
        for (baseUrl in PIPED_INSTANCES) {
            val items = runCatching {
                pipedApi.getChannel("$baseUrl/channel/$channelId").relatedStreams
            }.getOrDefault(emptyList())
            val playable = items.filter { it.title.isNotBlank() }
            if (playable.isNotEmpty()) return playable
        }
        return emptyList()
    }

    private suspend fun fetchPipedTrending(): List<Video> {
        for (baseUrl in PIPED_INSTANCES) {
            try {
                val response = pipedApi.getTrending("$baseUrl/trending?region=$REGION")
                val videos = response.map { it.toVideo() }.filter { it.title.isNotBlank() }
                if (videos.isNotEmpty()) return videos
            } catch (e: Exception) {
                // Try the next Piped instance
            }
        }
        return emptyList()
    }

    private suspend fun searchPiped(query: String): List<Video> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        for (baseUrl in PIPED_INSTANCES) {
            try {
                val response = pipedApi.search("$baseUrl/search?q=$encodedQuery&filter=videos")
                val videos = response.items.map { it.toVideo() }.filter { it.title.isNotBlank() }
                if (videos.isNotEmpty()) return videos
            } catch (e: Exception) {
                // Try the next Piped instance
            }
        }
        return emptyList()
    }

    private fun isPlayable(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1023")
                .get()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun selectStream(response: PipedStreamResponse): VideoStreamInfo? {
        val progressive = response.videoStreams
            .filter { it.isMp4() && it.videoOnly != true }
            .sortedByDescending { it.height() }

        progressive.firstOrNull { it.height() in 1..MAX_PROGRESSIVE_HEIGHT }
            ?.let { return VideoStreamInfo(it.url) }
        progressive.firstOrNull()?.let { return VideoStreamInfo(it.url) }

        val videoOnly = response.videoStreams
            .filter { it.isMp4() }
            .sortedByDescending { it.height() }
            .firstOrNull() ?: return null

        val audio = response.audioStreams
            ?.firstOrNull {
                it.mimeType?.contains("audio/mp4", ignoreCase = true) == true ||
                    it.format?.equals("M4A", ignoreCase = true) == true
            }
            ?: response.audioStreams?.firstOrNull()

        return VideoStreamInfo(videoOnly.url, audio?.url)
    }

    private fun VideoStream.isMp4(): Boolean {
        val mime = mimeType.orEmpty().lowercase(Locale.US)
        val formatName = format.orEmpty().lowercase(Locale.US)
        return mime.contains("video/mp4") ||
            formatName.contains("mp4") ||
            formatName.contains("mpeg_4")
    }

    private fun VideoStream.height(): Int =
        Regex("(\\d{3,4})p").find(quality.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun putSearchCache(key: String, page: VideoPage) {
        searchCache[key] = CacheEntry(System.currentTimeMillis(), page)
        if (searchCache.size > MAX_SEARCH_CACHE_ENTRIES) {
            searchCache.entries.minByOrNull { it.value.timestamp }?.let { searchCache.remove(it.key) }
        }
    }

    private fun isFresh(timestamp: Long, ttlMs: Long): Boolean =
        System.currentTimeMillis() - timestamp in 0L until ttlMs
}
