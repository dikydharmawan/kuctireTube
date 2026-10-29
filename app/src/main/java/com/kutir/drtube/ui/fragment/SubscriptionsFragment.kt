package com.kutir.drtube.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.repository.VideoRepository
import com.kutir.drtube.ui.adapter.ChannelRailAdapter
import com.kutir.drtube.ui.adapter.VideoFeedAdapter
import com.kutir.drtube.ui.channel.ChannelActivity
import com.kutir.drtube.ui.player.PlayerActivity
import com.kutir.drtube.ui.util.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionsFragment : Fragment() {

    @Inject
    lateinit var repository: VideoRepository

    private lateinit var channelAdapter: ChannelRailAdapter
    private lateinit var feedAdapter: VideoFeedAdapter
    private lateinit var emptyView: TextView
    private lateinit var progressView: ProgressBar
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_subscriptions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.tv_subscriptions_empty)
        progressView = view.findViewById(R.id.pb_subscriptions)

        channelAdapter = ChannelRailAdapter { channel ->
            startActivity(Intent(requireContext(), ChannelActivity::class.java).apply {
                putExtra(ChannelActivity.EXTRA_CHANNEL_ID, channel.id)
                putExtra(ChannelActivity.EXTRA_CHANNEL_NAME, channel.name)
                putExtra(ChannelActivity.EXTRA_CHANNEL_AVATAR, channel.avatarUrl)
            })
        }
        view.findViewById<RecyclerView>(R.id.rv_subscription_channels).apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = channelAdapter
        }

        feedAdapter = VideoFeedAdapter(emptyList(), { video: Video ->
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            })
        })
        view.findViewById<RecyclerView>(R.id.rv_subscription_feed).apply {
            layoutManager = if (requireContext().isTelevision()) {
                GridLayoutManager(requireContext(), 3)
            } else {
                LinearLayoutManager(requireContext())
            }
            adapter = feedAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val channels = repository.getSubscriptions().getOrDefault(emptyList())
            channelAdapter.submitList(channels)

            if (channels.isEmpty()) {
                feedAdapter.updateData(emptyList())
                progressView.visibility = View.GONE
                showEmpty("Belum ada langganan. Buka video lalu tekan LANGGANAN pada kanal.")
                return@launch
            }

            progressView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            val videos = repository.getSubscriptionFeed().getOrDefault(emptyList())
            feedAdapter.updateData(videos)
            progressView.visibility = View.GONE

            if (videos.isEmpty()) {
                showEmpty("Belum ada video terbaru dari kanal langganan.")
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }

    private fun showEmpty(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
    }
}
