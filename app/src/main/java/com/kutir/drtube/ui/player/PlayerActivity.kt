package com.kutir.drtube.ui.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kutir.drtube.R
import com.kutir.drtube.data.local.AuthSession
import com.kutir.drtube.data.model.Channel
import com.kutir.drtube.data.model.CommentModel
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.model.VideoStreamInfo
import com.kutir.drtube.data.repository.VideoRepository
import com.kutir.drtube.ui.adapter.CommentsAdapter
import com.kutir.drtube.ui.adapter.VideoFeedAdapter
import com.kutir.drtube.ui.auth.AuthActivity
import com.kutir.drtube.ui.channel.ChannelActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private fun formatRealisticLikes(viewsCountStr: String): String {
        val lower = viewsCountStr.lowercase()
        val clean = viewsCountStr.replace("rb", "").replace("jt", "").replace("m", "").replace("k", "").replace("tayangan", "").trim()
        val numStr = clean.replace(',', '.')
        val baseNum = numStr.toDoubleOrNull() ?: 280.0
        val multiplier = when {
            lower.contains("jt") || lower.contains("m") -> 1_000_000.0
            lower.contains("rb") || lower.contains("k") -> 1_000.0
            else -> 1.0
        }
        val totalViews = (baseNum * multiplier).toLong().coerceAtLeast(1000L)
        val likes = (totalViews / 130L).coerceAtLeast(150L)
        return if (likes >= 1000) {
            String.format(java.util.Locale.US, "%.1f rb", likes / 1000.0).replace(".0", "")
        } else {
            "$likes"
        }
    }


    private var player: ExoPlayer? = null
    private var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
    private lateinit var playerView: PlayerView

    @Inject
    lateinit var repository: VideoRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private var video: Video? = null
    private var fallbackAttempted = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var relatedAdapter: VideoFeedAdapter

    private lateinit var etComment: EditText
    private lateinit var btnSendComment: ImageButton
    private lateinit var layoutReplying: LinearLayout
    private lateinit var tvReplyingTo: TextView
    private var replyParentId: String? = null
    private var replyToName: String? = null
    private var pendingCommentText: String? = null
    private var commentSending = false
    private var isFullscreen = false
    private var scrollDetails: ScrollView? = null
    private var playerHeight: Int = 0

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingCommentText?.let { text ->
                    etComment.setText(text)
                    pendingCommentText = null
                    sendComment()
                }
            } else {
                pendingCommentText = null
            }
        }

    private val progressSyncRunnable = object : Runnable {
        override fun run() {
            val currentPos = player?.currentPosition ?: 0L
            val currentVideoId = video?.id
            if (currentVideoId != null && currentPos > 0) {
                lifecycleScope.launch {
                    repository.saveWatchProgress(currentVideoId, currentPos)
                }
            }
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        playerView.keepScreenOn = true
        scrollDetails = findViewById(R.id.scroll_details)
        playerHeight = (230 * resources.displayMetrics.density).toInt()

        video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_VIDEO", Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_VIDEO")
        }

        val targetVideo = video
        if (targetVideo == null) {
            android.util.Log.d("DRTube", "Target video is null")
            Toast.makeText(this, "Video tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isWide = resources.configuration.smallestScreenWidthDp >= 540
        if (isWide) {
            val btnInfo = findViewById<View>(R.id.btn_info)
            btnInfo?.isFocusable = true
            btnInfo?.isFocusableInTouchMode = true
            btnInfo?.setOnClickListener {
                lifecycleScope.launch {
                    val comments = repository.getComments(targetVideo.id).getOrDefault(emptyList())
                    com.kutir.drtube.ui.details.TvVideoInfoDialog(this@PlayerActivity, targetVideo, comments).show()
                }
            }
        }

        android.util.Log.d("DRTube", "Calling setupDetails for video: ${targetVideo.id}")
        setupDetails(targetVideo)
        initializePlayer()
        setupFullscreenButton()
    }

    private fun setupFullscreenButton() {
        val isWide = resources.configuration.smallestScreenWidthDp >= 540
        if (isWide) return
        val fullscreenBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_fullscreen)
        fullscreenBtn?.setOnClickListener {
            toggleFullscreen()
        }
    }

    private fun toggleFullscreen() {
        val isWide = resources.configuration.smallestScreenWidthDp >= 540
        if (isWide) return
        val fullscreenBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_fullscreen)
        if (!isFullscreen) {
            isFullscreen = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val params = playerView.layoutParams as LinearLayout.LayoutParams
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.weight = 1f
            playerView.layoutParams = params
            scrollDetails?.visibility = View.GONE
            fullscreenBtn?.setImageResource(androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_exit)
        } else {
            isFullscreen = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val params = playerView.layoutParams as LinearLayout.LayoutParams
            params.height = playerHeight
            params.weight = 0f
            playerView.layoutParams = params
            scrollDetails?.visibility = View.VISIBLE
            fullscreenBtn?.setImageResource(androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_enter)
        }
    }

    private fun setupDetails(v: Video) {
        val titleView = findViewById<TextView>(R.id.tv_title)
        val isWide = resources.configuration.smallestScreenWidthDp >= 540
        titleView?.text = v.title

        val likeButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_like)
        val subscribeButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_subscribe)
        val dislikeButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dislike)
        val shareButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_share)
        val notificationsButton = findViewById<ImageView>(R.id.btn_notifications)
        val settingsButton = findViewById<View>(R.id.btn_player_settings)

        settingsButton?.setOnClickListener {
            showPlayerSettingsDialog()
        }

        likeButton?.text = formatRealisticLikes(v.viewsCount)

        if (!isWide) {
            val metaView = findViewById<TextView>(R.id.tv_video_meta)
            val descView = findViewById<TextView>(R.id.tv_description)
            val channelNameView = findViewById<TextView>(R.id.tv_channel_name)
            val avatarView = findViewById<ImageView>(R.id.iv_channel_avatar)
            val seeMoreView = findViewById<TextView>(R.id.tv_see_more)
            val subCountView = findViewById<TextView>(R.id.tv_subscriber_count)

            metaView?.text = listOf(v.viewsCount, v.publishedAt).filter { it.isNotBlank() }.joinToString(" • ")
            descView?.text = v.description.ifBlank { "Tidak ada deskripsi." }
            
            if (descView != null && seeMoreView != null) {
                var isExpanded = false
                val toggleDesc = {
                    isExpanded = !isExpanded
                    if (isExpanded) {
                        descView.maxLines = Int.MAX_VALUE
                        descView.ellipsize = null
                        seeMoreView.text = "ringkas"
                    } else {
                        descView.maxLines = 3
                        descView.ellipsize = android.text.TextUtils.TruncateAt.END
                        seeMoreView.text = "selengkapnya"
                    }
                }
                findViewById<View>(R.id.card_description)?.setOnClickListener { toggleDesc() }
                seeMoreView.setOnClickListener { toggleDesc() }
            }
            channelNameView?.text = v.channelTitle.ifBlank { "Kanal" }

            lifecycleScope.launch {
                if (v.channelId.isNotBlank()) {
                    val subCount = repository.getChannelSubscriberCount(v.channelId).getOrDefault("1jt+ subscriber")
                    subCountView?.text = subCount
                }
                val freshResult = repository.getVideoDetails(v.id)
                freshResult.onSuccess { fresh ->
                    if (fresh.title.isNotBlank()) {
                        likeButton?.text = formatRealisticLikes(fresh.viewsCount)
                        titleView?.text = fresh.title
                        metaView?.text = listOf(fresh.viewsCount, fresh.publishedAt).filter { it.isNotBlank() }.joinToString(" • ")
                        if (fresh.description.isNotBlank()) descView?.text = fresh.description
                        if (fresh.channelTitle.isNotBlank()) channelNameView?.text = fresh.channelTitle
                        if (fresh.channelAvatarUrl.isNotBlank() && avatarView != null) {
                            Glide.with(this@PlayerActivity)
                                .load(fresh.channelAvatarUrl)
                                .circleCrop()
                                .into(avatarView)
                        }
                    }
                }
            }

            if (avatarView != null) {
                Glide.with(this)
                    .load(v.channelAvatarUrl.ifBlank { null })
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .into(avatarView)
            }

            val openChannel = {
                if (v.channelId.isNotBlank()) {
                    startActivity(Intent(this, ChannelActivity::class.java).apply {
                        putExtra(ChannelActivity.EXTRA_CHANNEL_ID, v.channelId)
                        putExtra(ChannelActivity.EXTRA_CHANNEL_NAME, v.channelTitle)
                        putExtra(ChannelActivity.EXTRA_CHANNEL_AVATAR, v.channelAvatarUrl)
                    })
                }
            }
            avatarView?.setOnClickListener { openChannel() }
            channelNameView?.setOnClickListener { openChannel() }
        } else {
            lifecycleScope.launch {
                val freshResult = repository.getVideoDetails(v.id)
                freshResult.onSuccess { fresh ->
                    if (fresh.title.isNotBlank()) {
                        likeButton?.text = formatRealisticLikes(fresh.viewsCount)
                        titleView?.text = fresh.title
                    }
                }
            }
        }

        var isSubscribedState = false
        lifecycleScope.launch {
            val token = com.kutir.drtube.data.local.AuthSession.youtubeAccessToken
            if (!token.isNullOrBlank() && v.channelId.isNotBlank()) {
                val res = repository.checkYoutubeSubscription(v.channelId, token)
                isSubscribedState = res.getOrDefault(false)
                subscribeButton?.text = if (isSubscribedState) "Subscribed" else "Subscribe"
            } else {
                isSubscribedState = repository.isSubscribed(v.channelId)
                subscribeButton?.text = if (isSubscribedState) "Subscribed" else "Subscribe"
            }
        }

        subscribeButton?.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(this, "Masuk dulu untuk berlangganan", Toast.LENGTH_SHORT).show()
                authLauncher.launch(Intent(this, AuthActivity::class.java))
                return@setOnClickListener
            }
            val token = com.kutir.drtube.data.local.AuthSession.youtubeAccessToken
            lifecycleScope.launch {
                if (isSubscribedState) {
                    val res = if (!token.isNullOrBlank() && v.channelId.isNotBlank()) {
                        repository.unsubscribeYoutubeChannel(v.channelId, token)
                    } else {
                        repository.toggleSubscription(com.kutir.drtube.data.model.Channel(id = v.channelId, name = v.channelTitle, avatarUrl = v.channelAvatarUrl))
                    }
                    if (res.isSuccess) {
                        isSubscribedState = false
                        subscribeButton.text = "Subscribe"
                        Toast.makeText(this@PlayerActivity, "Berhenti berlangganan", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val res = if (!token.isNullOrBlank() && v.channelId.isNotBlank()) {
                        repository.subscribeYoutubeChannel(v.channelId, token)
                    } else {
                        repository.toggleSubscription(com.kutir.drtube.data.model.Channel(id = v.channelId, name = v.channelTitle, avatarUrl = v.channelAvatarUrl))
                    }
                    if (res.isSuccess) {
                        isSubscribedState = true
                        subscribeButton.text = "Subscribed"
                        Toast.makeText(this@PlayerActivity, "Berlangganan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        notificationsButton?.setOnClickListener { Toast.makeText(this, "Notifikasi diaktifkan", Toast.LENGTH_SHORT).show() }

        shareButton?.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Tonton video ini di KuctireTube: https://www.youtube.com/watch?v=${v.id}")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan video"))
        }

        var liked = false
        val tokenLike = com.kutir.drtube.data.local.AuthSession.youtubeAccessToken
        if (!tokenLike.isNullOrBlank() && likeButton != null) {
            lifecycleScope.launch {
                val ratingRes = repository.getYoutubeRating(v.id, tokenLike)
                val rating = ratingRes.getOrNull()
                if (rating == "like") {
                    liked = true
                    likeButton.setIconResource(R.drawable.ic_like_filled)
                    likeButton.iconTint = android.content.res.ColorStateList.valueOf(0xFFFFD700.toInt())
                    likeButton.setTextColor(0xFFFFD700.toInt())
                }
            }
        }
        likeButton?.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(this, "Masuk dulu untuk menyukai video", Toast.LENGTH_SHORT).show()
                authLauncher.launch(Intent(this, AuthActivity::class.java))
                return@setOnClickListener
            }
            val token = com.kutir.drtube.data.local.AuthSession.youtubeAccessToken
            if (liked) {
                liked = false
                likeButton.setIconResource(R.drawable.ic_like_thin)
                likeButton.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                likeButton.setTextColor(android.graphics.Color.WHITE)
                if (!token.isNullOrBlank()) {
                    lifecycleScope.launch { repository.rateYoutubeVideo(v.id, "none", token) }
                }
            } else {
                liked = true
                likeButton.setIconResource(R.drawable.ic_like_filled)
                likeButton.iconTint = android.content.res.ColorStateList.valueOf(0xFFFFD700.toInt())
                likeButton.setTextColor(0xFFFFD700.toInt())
                if (!token.isNullOrBlank()) {
                    lifecycleScope.launch { repository.rateYoutubeVideo(v.id, "like", token) }
                }
            }
        }

        dislikeButton?.setOnClickListener {
            Toast.makeText(this, "Tidak suka", Toast.LENGTH_SHORT).show()
        }

        if (!isWide) {
            setupCommentInput()
            val rvComments = findViewById<RecyclerView>(R.id.rv_comments)
            rvComments?.layoutManager = LinearLayoutManager(this)
            commentsAdapter = CommentsAdapter(emptyList()) { comment -> startReply(comment) }
            rvComments?.adapter = commentsAdapter
            loadComments(v.id)
        }

        // Setup Related Videos RecyclerView
        val rvRelated = findViewById<RecyclerView>(R.id.rv_related)
        rvRelated.layoutManager = if (isWide) {
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        } else {
            LinearLayoutManager(this)
        }
        rvRelated.isFocusable = true
        rvRelated.isFocusableInTouchMode = true
        relatedAdapter = VideoFeedAdapter(emptyList(), isWide) { relatedVideo ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", relatedVideo)
            }
            startActivity(intent)
            finish()
        }
        rvRelated.adapter = relatedAdapter
        loadRelatedVideos(v.id)
        if (isWide) {
            rvRelated.post {
                if (rvRelated.childCount > 0) {
                    rvRelated.getChildAt(0)?.requestFocus()
                }
            }
        }
    }

    private fun setupCommentInput() {
        etComment = findViewById(R.id.et_comment)
        btnSendComment = findViewById(R.id.btn_send_comment)
        layoutReplying = findViewById(R.id.layout_replying)
        tvReplyingTo = findViewById(R.id.tv_replying_to)

        val myAvatar = findViewById<ImageView>(R.id.iv_my_avatar)
        val user = auth.currentUser
        Glide.with(this)
            .load(user?.photoUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .into(myAvatar)

        if (user == null) {
            etComment.hint = "Masuk untuk berkomentar..."
        }

        btnSendComment.setOnClickListener { sendComment() }
        etComment.setOnEditorActionListener { _, actionId, event ->
            val isSend = actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN)
            if (isSend) {
                sendComment()
                true
            } else {
                false
            }
        }
        findViewById<TextView>(R.id.tv_cancel_reply).setOnClickListener { clearReplyTarget() }

        myAvatar.setOnClickListener {
            if (auth.currentUser == null) {
                authLauncher.launch(Intent(this, AuthActivity::class.java))
            }
        }
    }

    private fun startReply(comment: CommentModel) {
        replyToName = comment.authorName.ifBlank { "komentar" }
        replyParentId = if (comment.isReply && comment.parentId.isNotBlank()) {
            comment.parentId
        } else {
            comment.id
        }
        layoutReplying.visibility = View.VISIBLE
        tvReplyingTo.text = "Membalas $replyToName"
        etComment.hint = "Balas ${comment.authorName}..."
        etComment.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etComment, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearReplyTarget() {
        replyToName = null
        replyParentId = null
        layoutReplying.visibility = View.GONE
        etComment.hint = if (auth.currentUser == null) {
            "Masuk untuk berkomentar..."
        } else {
            "Tambahkan komentar..."
        }
    }

    private fun sendComment() {
        if (commentSending) return
        val text = etComment.text.toString().trim()
        if (text.isBlank()) return

        val user = auth.currentUser
        if (user == null) {
            pendingCommentText = text
            Toast.makeText(this, "Masuk dulu untuk berkomentar", Toast.LENGTH_SHORT).show()
            authLauncher.launch(Intent(this, AuthActivity::class.java))
            return
        }

        val token = AuthSession.youtubeAccessToken
        if (token.isNullOrBlank()) {
            showGoogleRequiredDialog(text)
            return
        }

        val currentVideo = video ?: return
        val parentId = replyParentId
        val replyingName = replyToName

        commentSending = true
        btnSendComment.isEnabled = false
        lifecycleScope.launch {
            val result = if (parentId.isNullOrBlank()) {
                repository.postComment(currentVideo.id, text, token)
            } else {
                repository.postReply(parentId, text, token)
            }
            commentSending = false
            btnSendComment.isEnabled = true

            if (result.isSuccess) {
                val optimistic = CommentModel(
                    id = "",
                    authorName = user.displayName ?: user.email ?: "Anda",
                    authorAvatarUrl = user.photoUrl?.toString().orEmpty(),
                    commentText = text,
                    publishedAt = "baru saja",
                    likeCount = 0L,
                    isReply = !parentId.isNullOrBlank(),
                    parentId = parentId.orEmpty()
                )
                etComment.setText("")
                clearReplyTarget()
                commentsAdapter.prepend(optimistic)
                Toast.makeText(this@PlayerActivity, "Komentar terkirim", Toast.LENGTH_SHORT).show()
                loadComments(currentVideo.id)
            } else {
                val error = result.exceptionOrNull()?.message.orEmpty()
                Toast.makeText(
                    this@PlayerActivity,
                    "Gagal mengirim komentar. ${if (error.isNotBlank()) error else "Coba lagi."}",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.d("DRTube", "Post comment failed: $error")
                if (error.contains("401") || error.contains("403")) {
                    AuthSession.youtubeAccessToken = null
                    showGoogleRequiredDialog(text)
                }
            }
        }
        android.util.Log.d("DRTube", "Sending comment (reply=${!parentId.isNullOrBlank()}, to=$replyingName)")
    }

    private fun showGoogleRequiredDialog(pendingText: String) {
        AlertDialog.Builder(this)
            .setTitle("Masuk dengan Google")
            .setMessage(
                "Untuk berkomentar di YouTube, Anda perlu masuk dengan akun Google " +
                    "yang memiliki kanal YouTube."
            )
            .setPositiveButton("Masuk dengan Google") { _, _ ->
                pendingCommentText = pendingText
                authLauncher.launch(Intent(this, AuthActivity::class.java))
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadComments(videoId: String) {
        lifecycleScope.launch {
            val commentsResult = repository.getComments(videoId)
            commentsResult.onFailure {
                android.util.Log.d("DRTube", "Comments failed: ${it.message}")
            }
            val comments = commentsResult.getOrDefault(emptyList())
            android.util.Log.d("DRTube", "Comments loaded: ${comments.size}")
            commentsAdapter.submitList(comments)
            findViewById<TextView>(R.id.tv_comments_header).text =
                if (comments.isEmpty()) "Komentar" else "${comments.size} Komentar"
        }
    }

    private fun loadRelatedVideos(videoId: String) {
        lifecycleScope.launch {
            val relatedResult = repository.getRelatedVideos(videoId)
            relatedResult.onFailure {
                android.util.Log.d("DRTube", "Related failed: ${it.message}")
            }
            val related = relatedResult.getOrDefault(emptyList())
            android.util.Log.d("DRTube", "Related loaded: ${related.size}")
            relatedAdapter.updateData(related)
        }
    }

    private fun initializePlayer() {
        trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector!!)
            .build()
            .also { exoPlayer ->
            playerView.player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (fallbackAttempted) return
                    fallbackAttempted = true
                    val fallbackUrl = video?.streamUrl?.takeIf { it.isNotBlank() }
                    if (fallbackUrl != null) {
                        play(exoPlayer, VideoStreamInfo(fallbackUrl), 0L)
                    } else {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Gagal memutar video ini",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

            loadStreamAndPlay(exoPlayer)
        }
    }

    private fun showPlayerSettingsDialog() {
        val options = arrayOf("Kecepatan", "Kualitas", "Subtitle")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pengaturan Pemutar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSpeedDialog()
                    1 -> Toast.makeText(this, "Fitur Kualitas dalam pengembangan", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Fitur Subtitle dalam pengembangan", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "1.0x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Kecepatan Pemutaran")
            .setItems(speeds) { _, which ->
                player?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speedValues[which]))
            }
            .show()
    }

    private fun loadStreamAndPlay(exoPlayer: ExoPlayer) {
        val targetVideo = video ?: return

        lifecycleScope.launch {
            val streamResult = repository.getVideoStream(targetVideo.id)
            val progressResult = repository.getWatchProgress(targetVideo.id)

            val stream = streamResult.getOrNull()
                ?: targetVideo.streamUrl.takeIf { it.isNotBlank() }?.let { VideoStreamInfo(it) }
            val savedPosition = progressResult.getOrDefault(0L)

            if (stream == null) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Video tidak dapat diputar",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            repository.recordWatchHistory(targetVideo)
            play(exoPlayer, stream, savedPosition)
        }
    }

    private fun play(exoPlayer: ExoPlayer, stream: VideoStreamInfo, position: Long) {
        val dataSourceFactory = DefaultDataSource.Factory(this@PlayerActivity)
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(stream.videoUrl))

        val mediaSource = stream.audioUrl?.let { audioUrl ->
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } ?: videoSource

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()

        if (position > 0) {
            exoPlayer.seekTo(position)
        }

        exoPlayer.playWhenReady = true
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed(progressSyncRunnable, 10000)
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressSyncRunnable)

        val currentPos = player?.currentPosition ?: 0L
        val currentVideoId = video?.id
        if (currentVideoId != null && currentPos > 0) {
            lifecycleScope.launch {
                repository.saveWatchProgress(currentVideoId, currentPos)
            }
        }
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        playerView.player = null
    }
}
