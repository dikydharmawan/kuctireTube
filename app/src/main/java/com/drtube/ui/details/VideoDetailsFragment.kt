package com.drtube.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drtube.R
import com.drtube.data.model.Channel
import com.drtube.data.model.Video
import com.drtube.data.repository.VideoRepository
import com.drtube.ui.adapter.VideoFeedAdapter
import com.drtube.ui.channel.ChannelActivity
import com.drtube.ui.player.PlayerActivity
import com.drtube.ui.util.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VideoDetailsFragment : Fragment() {
    // Video details fragment with grid layout for related videos

    @Inject
    lateinit var repository: VideoRepository

    companion object {
        const val EXTRA_VIDEO = "EXTRA_VIDEO"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val video = arguments?.getParcelable<Video>(EXTRA_VIDEO) ?: return

        view.findViewById<TextView>(R.id.tv_title).text = video.title
        val thumbnailView = view.findViewById<ImageView>(R.id.iv_thumbnail)
        
        Glide.with(this)
            .load(video.thumbnailUrl)
            .into(thumbnailView)

        Glide.with(this)
            .load(video.channelAvatarUrl.ifBlank { null })
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .into(view.findViewById(R.id.iv_channel_avatar))

        view.findViewById<TextView>(R.id.tv_channel_name).text =
            video.channelTitle.ifBlank { "YouTube" }

        val meta = listOf(video.viewsCount, video.publishedAt)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        val metaView = view.findViewById<TextView>(R.id.tv_video_meta)
        metaView.text = meta
        metaView.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        val descriptionView = view.findViewById<TextView>(R.id.tv_description)
        descriptionView.text = video.description
        descriptionView.visibility =
            if (video.description.isBlank()) View.GONE else View.VISIBLE

        val playIntent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("EXTRA_VIDEO", video)
        }
        thumbnailView.setOnClickListener { startActivity(playIntent) }
        view.findViewById<ImageView>(R.id.iv_play_overlay).setOnClickListener {
            startActivity(playIntent)
        }
        view.findViewById<Button>(R.id.btn_play).setOnClickListener {
            startActivity(playIntent)
        }

        val favoriteButton = view.findViewById<Button>(R.id.btn_favorite)
        val subscribeButton = view.findViewById<Button>(R.id.btn_subscribe)

        if (video.channelId.isBlank()) {
            subscribeButton.visibility = View.GONE
        } else {
            val openChannel = {
                startActivity(Intent(requireContext(), ChannelActivity::class.java).apply {
                    putExtra(ChannelActivity.EXTRA_CHANNEL_ID, video.channelId)
                    putExtra(ChannelActivity.EXTRA_CHANNEL_NAME, video.channelTitle)
                    putExtra(ChannelActivity.EXTRA_CHANNEL_AVATAR, video.channelAvatarUrl)
                })
            }
            view.findViewById<ImageView>(R.id.iv_channel_avatar).setOnClickListener { openChannel() }
            view.findViewById<TextView>(R.id.tv_channel_name).setOnClickListener { openChannel() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            favoriteButton.text = if (repository.isFavorite(video.id)) "TERSIMPAN" else "SIMPAN"
            if (video.channelId.isNotBlank()) {
                val token = com.drtube.data.local.AuthSession.youtubeAccessToken
                val subscribed = if (!token.isNullOrBlank()) {
                    repository.checkYoutubeSubscription(video.channelId, token).getOrDefault(repository.isSubscribed(video.channelId))
                } else {
                    repository.isSubscribed(video.channelId)
                }
                subscribeButton.text = if (subscribed) "Subscribed" else "Subscribe"
            }
        }

        favoriteButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.toggleFavorite(video).onSuccess { saved ->
                    favoriteButton.text = if (saved) "TERSIMPAN" else "SIMPAN"
                    Toast.makeText(
                        requireContext(),
                        if (saved) "Disimpan ke favorit" else "Dihapus dari favorit",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        subscribeButton.setOnClickListener {
            if (video.channelId.isBlank()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val channel = Channel(
                    id = video.channelId,
                    name = video.channelTitle.ifBlank { "Kanal" },
                    avatarUrl = video.channelAvatarUrl
                )
                val token = com.drtube.data.local.AuthSession.youtubeAccessToken
                val current = if (!token.isNullOrBlank()) {
                    repository.checkYoutubeSubscription(video.channelId, token).getOrDefault(repository.isSubscribed(video.channelId))
                } else {
                    repository.isSubscribed(video.channelId)
                }
                val result = if (current) {
                    if (!token.isNullOrBlank()) repository.unsubscribeYoutubeChannel(video.channelId, token)
                    else repository.toggleSubscription(channel)
                } else {
                    if (!token.isNullOrBlank()) repository.subscribeYoutubeChannel(video.channelId, token)
                    else repository.toggleSubscription(channel)
                }
                result.onSuccess { subscribed ->
                    subscribeButton.text = if (subscribed) "Subscribed" else "Subscribe"
                    Toast.makeText(
                        requireContext(),
                        if (subscribed) "Berlangganan ${channel.name}" else "Berhenti berlangganan",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val cfg = resources.configuration
        if (requireContext().isTelevision() || cfg.screenWidthDp >= 960 || cfg.smallestScreenWidthDp >= 540) {
            val btnInfo = view.findViewById<Button>(R.id.btn_info)
            btnInfo?.visibility = View.VISIBLE
            btnInfo?.isFocusable = true
            btnInfo?.isClickable = true
            btnInfo?.isFocusableInTouchMode = true
            btnInfo?.requestFocus()
            btnInfo?.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val comments = repository.getComments(video.id).getOrDefault(emptyList())
                    TvVideoInfoDialog(requireContext(), video, comments).show()
                }
            }
            val rvRelated = view.findViewById<RecyclerView>(R.id.rv_tv_related)
            rvRelated?.visibility = View.VISIBLE
            rvRelated?.isFocusable = true
            rvRelated?.isFocusableInTouchMode = true
            rvRelated?.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
            val relatedAdapter = VideoFeedAdapter(emptyList()) { relatedVideo ->
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("EXTRA_VIDEO", relatedVideo)
                }
                startActivity(intent)
            }
            rvRelated?.adapter = relatedAdapter
            viewLifecycleOwner.lifecycleScope.launch {
                val related = repository.getRelatedVideos(video.id).getOrDefault(emptyList())
                relatedAdapter.updateData(related)
                rvRelated?.post {
                    if ((rvRelated?.childCount ?: 0) > 0) {
                        rvRelated?.getChildAt(0)?.requestFocus()
                    }
                }
            }
            descriptionView.visibility = View.GONE
            view.findViewById<TextView>(R.id.tv_video_meta)?.visibility = View.GONE
        } else {
            val btnInfo = view.findViewById<Button>(R.id.btn_info)
            btnInfo?.visibility = View.GONE
            view.findViewById<RecyclerView>(R.id.rv_tv_related)?.visibility = View.GONE
        }

        descriptionView.setOnClickListener {
            descriptionView.maxLines = Int.MAX_VALUE
            descriptionView.ellipsize = null
        }
    }
}
