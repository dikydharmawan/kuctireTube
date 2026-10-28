package com.drtube.ui.channel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drtube.R
import com.drtube.data.model.Channel
import com.drtube.data.model.Video
import com.drtube.data.repository.VideoRepository
import com.drtube.ui.adapter.VideoFeedAdapter
import com.drtube.ui.player.PlayerActivity
import com.drtube.ui.util.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChannelActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID = "EXTRA_CHANNEL_ID"
        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_CHANNEL_AVATAR = "EXTRA_CHANNEL_AVATAR"
    }

    @Inject
    lateinit var repository: VideoRepository

    private lateinit var adapter: VideoFeedAdapter
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private var currentChannelId = ""
    private var nextPageToken: String? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel)

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty().ifBlank { "Kanal" }
        val channelAvatar = intent.getStringExtra(EXTRA_CHANNEL_AVATAR).orEmpty()

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_toolbar_title).text = channelName
        findViewById<TextView>(R.id.tv_channel_name).text = channelName
        Glide.with(this)
            .load(channelAvatar.ifBlank { null })
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .into(findViewById(R.id.iv_channel_avatar))

        val subscribeButton = findViewById<Button>(R.id.btn_channel_subscribe)
        if (channelId.isBlank()) {
            subscribeButton.visibility = View.GONE
        } else {
            val channel = Channel(channelId, channelName, channelAvatar)
            var channelSubscribed = false
            lifecycleScope.launch {
                val token = com.drtube.data.local.AuthSession.youtubeAccessToken
                channelSubscribed = if (!token.isNullOrBlank()) {
                    repository.checkYoutubeSubscription(channelId, token).getOrDefault(repository.isSubscribed(channelId))
                } else {
                    repository.isSubscribed(channelId)
                }
                subscribeButton.text = if (channelSubscribed) "Subscribed" else "Subscribe"
            }
            subscribeButton.setOnClickListener {
                lifecycleScope.launch {
                    val token = com.drtube.data.local.AuthSession.youtubeAccessToken
                    val res = if (channelSubscribed) {
                        if (!token.isNullOrBlank()) repository.unsubscribeYoutubeChannel(channelId, token)
                        else repository.toggleSubscription(channel)
                    } else {
                        if (!token.isNullOrBlank()) repository.subscribeYoutubeChannel(channelId, token)
                        else repository.toggleSubscription(channel)
                    }
                    res.onSuccess { subscribed ->
                        channelSubscribed = subscribed
                        subscribeButton.text = if (subscribed) "Subscribed" else "Subscribe"
                        Toast.makeText(
                            this@ChannelActivity,
                            if (subscribed) "Berlangganan $channelName" else "Berhenti berlangganan",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        emptyView = findViewById(R.id.tv_channel_empty)
        recyclerView = findViewById(R.id.rv_channel_videos)
        adapter = VideoFeedAdapter(emptyList()) { video: Video ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            })
        }
        recyclerView.layoutManager = if (isTelevision()) {
            GridLayoutManager(this, 3)
        } else {
            LinearLayoutManager(this)
        }
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
                    loadMoreVideos()
                }
            }
        })

        currentChannelId = channelId
        if (channelId.isBlank()) {
            emptyView.visibility = View.VISIBLE
            return
        }

        loadInitialVideos()
    }

    private fun loadInitialVideos() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            val page = repository.getChannelVideos(currentChannelId).getOrNull()
            isLoading = false
            if (page == null) return@launch

            nextPageToken = page.nextPageToken
            adapter.updateData(page.videos)
            emptyView.visibility =
                if (page.videos.isEmpty()) View.VISIBLE else View.GONE
            if (isTelevision() && page.videos.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    private fun loadMoreVideos() {
        val pageToken = nextPageToken ?: return
        if (isLoading || currentChannelId.isBlank()) return
        isLoading = true
        lifecycleScope.launch {
            val page = repository.getChannelVideos(currentChannelId, pageToken).getOrNull()
            isLoading = false
            if (page == null) return@launch

            nextPageToken = page.nextPageToken
            if (page.videos.isNotEmpty()) {
                adapter.addData(page.videos)
            }
        }
    }
}
