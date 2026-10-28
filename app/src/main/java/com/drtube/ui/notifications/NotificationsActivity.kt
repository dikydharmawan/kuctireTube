package com.drtube.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drtube.R
import com.drtube.data.model.Video
import com.drtube.data.repository.VideoRepository
import com.drtube.ui.adapter.VideoFeedAdapter
import com.drtube.ui.player.PlayerActivity
import com.drtube.ui.util.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: VideoRepository

    private lateinit var adapter: VideoFeedAdapter
    private lateinit var progressView: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        progressView = findViewById(R.id.pb_notifications)
        emptyView = findViewById(R.id.tv_notifications_empty)

        adapter = VideoFeedAdapter(emptyList(), { video: Video ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            })
        })
        recyclerView = findViewById(R.id.rv_notifications)
        recyclerView.layoutManager = if (isTelevision()) {
            GridLayoutManager(this, 3)
        } else {
            LinearLayoutManager(this)
        }
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            val hasSubscriptions = repository.getSubscriptions()
                .getOrDefault(emptyList())
                .isNotEmpty()

            val videos = if (hasSubscriptions) {
                repository.getSubscriptionFeed().getOrDefault(emptyList())
            } else {
                emptyList()
            }

            progressView.visibility = View.GONE
            adapter.updateData(videos)
            if (isTelevision() && videos.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
            if (videos.isEmpty()) {
                emptyView.text = if (hasSubscriptions) {
                    "Belum ada video terbaru dari kanal langganan."
                } else {
                    "Belum ada langganan. Tekan LANGGANAN pada kanal untuk mulai mengikuti."
                }
                emptyView.visibility = View.VISIBLE
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }
}
