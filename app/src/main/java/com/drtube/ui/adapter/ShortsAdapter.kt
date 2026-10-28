package com.drtube.ui.adapter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drtube.R
import com.drtube.data.local.AuthSession
import com.drtube.data.model.Channel
import com.drtube.data.model.Video
import com.drtube.data.repository.VideoRepository
import com.drtube.ui.channel.ChannelActivity
import com.drtube.ui.player.PlayerActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShortsAdapter(
    private val videos: List<Video>,
    private val exoPlayer: ExoPlayer,
    private val repository: VideoRepository,
    private val auth: FirebaseAuth,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<ShortsAdapter.ShortsViewHolder>() {

    class ShortsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.pv_shorts)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_shorts_title)
        val tvChannel: TextView = itemView.findViewById(R.id.tv_shorts_channel)
        val ivAvatar: ImageView = itemView.findViewById(R.id.iv_shorts_avatar)
        val btnSubscribe: MaterialButton = itemView.findViewById(R.id.btn_shorts_subscribe)
        val btnLike: TextView = itemView.findViewById(R.id.btn_shorts_like)
        val btnDislike: TextView = itemView.findViewById(R.id.btn_shorts_dislike)
        val btnComment: TextView = itemView.findViewById(R.id.btn_shorts_comment)
        val btnShare: TextView = itemView.findViewById(R.id.btn_shorts_share)
        val btnMore: TextView = itemView.findViewById(R.id.btn_shorts_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shorts, parent, false)
        return ShortsViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortsViewHolder, position: Int) {
        val video = videos[position]
        val context = holder.itemView.context
        holder.tvTitle.text = video.title
        holder.tvChannel.text = video.channelTitle
        holder.btnLike.text = video.viewsCount.ifBlank { "Like" }
        holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_like_thin, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnLike, ColorStateList.valueOf(Color.WHITE))

        holder.btnDislike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_dislike_thin, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnDislike, ColorStateList.valueOf(Color.WHITE))

        holder.btnComment.setCompoundDrawablesRelativeWithIntrinsicBounds(0, android.R.drawable.ic_menu_edit, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnComment, ColorStateList.valueOf(Color.WHITE))

        holder.btnShare.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_share_thin, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnShare, ColorStateList.valueOf(Color.WHITE))

        holder.btnMore.setCompoundDrawablesRelativeWithIntrinsicBounds(0, android.R.drawable.ic_menu_more, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnMore, ColorStateList.valueOf(Color.WHITE))

        holder.playerView.player = exoPlayer

        Glide.with(context)
            .load(video.channelAvatarUrl.ifBlank { null })
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .into(holder.ivAvatar)

        var liked = false
        var disliked = false
        var subscribed = false
        val token = AuthSession.youtubeAccessToken

        scope.launch {
            if (!token.isNullOrBlank() && video.channelId.isNotBlank()) {
                subscribed = repository.checkYoutubeSubscription(video.channelId, token).getOrDefault(false)
            } else if (video.channelId.isNotBlank()) {
                subscribed = repository.isSubscribed(video.channelId)
            }
            holder.btnSubscribe.text = if (subscribed) "Subscribed" else "Subscribe"
        }

        scope.launch {
            if (!token.isNullOrBlank()) {
                val rating = repository.getYoutubeRating(video.id, token).getOrNull()
                liked = rating == "like"
                disliked = rating == "dislike"
                if (liked) {
                    holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_like_filled, 0, 0)
                    androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnLike, ColorStateList.valueOf(0xFFFFD700.toInt()))
                }
                if (disliked) {
                    holder.btnDislike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_dislike_thin, 0, 0)
                    androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnDislike, ColorStateList.valueOf(0xFFFFD700.toInt()))
                }
            }
        }

        holder.btnSubscribe.setOnClickListener {
            if (video.channelId.isBlank()) return@setOnClickListener
            scope.launch {
                val res = if (subscribed) {
                    if (!token.isNullOrBlank()) repository.unsubscribeYoutubeChannel(video.channelId, token)
                    else repository.toggleSubscription(Channel(id = video.channelId, name = video.channelTitle, avatarUrl = video.channelAvatarUrl))
                } else {
                    if (!token.isNullOrBlank()) repository.subscribeYoutubeChannel(video.channelId, token)
                    else repository.toggleSubscription(Channel(id = video.channelId, name = video.channelTitle, avatarUrl = video.channelAvatarUrl))
                }
                res.onSuccess { s ->
                    subscribed = s
                    holder.btnSubscribe.text = if (s) "Subscribed" else "Subscribe"
                    Toast.makeText(context, if (s) "Berlangganan" else "Berhenti berlangganan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.btnLike.setOnClickListener {
            if (!token.isNullOrBlank()) {
                liked = !liked
                scope.launch {
                    repository.rateYoutubeVideo(video.id, if (liked) "like" else "none", token)
                }
            }
            if (liked) {
                holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_like_filled, 0, 0)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnLike, ColorStateList.valueOf(0xFFFFD700.toInt()))
            } else {
                holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_like_thin, 0, 0)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnLike, ColorStateList.valueOf(Color.WHITE))
            }
        }

        holder.btnDislike.setOnClickListener {
            if (!token.isNullOrBlank()) {
                disliked = !disliked
                if (disliked) liked = false
                scope.launch {
                    repository.rateYoutubeVideo(video.id, if (disliked) "dislike" else "none", token)
                }
            } else {
                disliked = !disliked
            }
            if (disliked) {
                holder.btnDislike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_dislike_thin, 0, 0)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnDislike, ColorStateList.valueOf(0xFFFFD700.toInt()))
                if (liked) {
                    liked = false
                    holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_like_thin, 0, 0)
                    androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnLike, ColorStateList.valueOf(Color.WHITE))
                }
            } else {
                holder.btnDislike.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_dislike_thin, 0, 0)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(holder.btnDislike, ColorStateList.valueOf(Color.WHITE))
            }
            Toast.makeText(context, if (disliked) "Disliked" else "Dislike dibatalkan", Toast.LENGTH_SHORT).show()
        }

        holder.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Tonton video ini di KuctireTube: https://www.youtube.com/shorts/${video.id}")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Short"))
        }

        holder.btnComment.setOnClickListener {
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            })
        }

        val openChannel = {
            if (video.channelId.isNotBlank()) {
                context.startActivity(Intent(context, ChannelActivity::class.java).apply {
                    putExtra(ChannelActivity.EXTRA_CHANNEL_ID, video.channelId)
                    putExtra(ChannelActivity.EXTRA_CHANNEL_NAME, video.channelTitle)
                    putExtra(ChannelActivity.EXTRA_CHANNEL_AVATAR, video.channelAvatarUrl)
                })
            }
        }
        holder.ivAvatar.setOnClickListener { openChannel() }
        holder.tvChannel.setOnClickListener { openChannel() }
        holder.btnMore.setOnClickListener { Toast.makeText(context, "Lainnya", Toast.LENGTH_SHORT).show() }
    }

    override fun getItemCount(): Int = videos.size

    fun getVideo(position: Int): Video = videos[position]
}
