package com.kutir.drtube.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Video

class VideoFeedAdapter(
    private var items: List<Any>,
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var useHorizontalItem: Boolean = false
    private var isTelevision: Boolean = false

    fun setTelevision(isTv: Boolean) {
        this.isTelevision = isTv
    }

    constructor(items: List<Any>, useHorizontalItem: Boolean, onVideoClick: (Video) -> Unit) : this(items, onVideoClick) {
        this.useHorizontalItem = useHorizontalItem
    }

    companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_SHORTS = 1
        const val TYPE_VIDEO_HORIZONTAL = 2
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        if (item is Video && item.id.startsWith("sample_shorts")) return TYPE_SHORTS
        return if (useHorizontalItem) TYPE_VIDEO_HORIZONTAL else TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SHORTS -> {
                val view = inflater.inflate(R.layout.item_shorts_row, parent, false)
                ShortsRowViewHolder(view)
            }
            TYPE_VIDEO_HORIZONTAL -> {
                val view = inflater.inflate(R.layout.item_video_horizontal, parent, false)
                VideoHorizontalViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_video_feed, parent, false)
                VideoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VideoViewHolder) {
            val video = items[position] as Video
            holder.tvTitle.text = video.title
            holder.tvMetadata.text = listOf(video.channelTitle, video.viewsCount, video.publishedAt)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            holder.tvDuration.text = video.duration
            holder.tvDuration.visibility = if (video.duration.isBlank()) View.GONE else View.VISIBLE
            Glide.with(holder.itemView.context).load(video.thumbnailUrl).into(holder.ivThumbnail)
            Glide.with(holder.itemView.context)
                .load(video.channelAvatarUrl.ifBlank { null })
                .circleCrop()
                .placeholder(R.drawable.ic_launcher)
                .error(R.drawable.ic_launcher)
                .into(holder.ivChannelAvatar)
            holder.itemView.isClickable = true
            holder.itemView.isFocusable = isTelevision
            holder.itemView.isFocusableInTouchMode = isTelevision
            holder.itemView.setOnClickListener { onVideoClick(video) }
        } else if (holder is VideoHorizontalViewHolder) {
            val video = items[position] as Video
            holder.tvTitle.text = video.title
            holder.tvChannel.text = video.channelTitle
            holder.tvDuration.text = video.duration
            holder.tvDuration.visibility = if (video.duration.isBlank()) View.GONE else View.VISIBLE
            Glide.with(holder.itemView.context).load(video.thumbnailUrl).into(holder.ivThumbnail)
            holder.itemView.isClickable = true
            holder.itemView.isFocusable = isTelevision
            holder.itemView.isFocusableInTouchMode = isTelevision
            holder.itemView.setOnClickListener { onVideoClick(video) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun addData(newItems: List<Any>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items = items + newItems
        notifyItemRangeInserted(start, newItems.size)
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        val ivChannelAvatar: ImageView = itemView.findViewById(R.id.iv_channel_avatar)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvMetadata: TextView = itemView.findViewById(R.id.tv_metadata)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
    }

    class VideoHorizontalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvChannel: TextView = itemView.findViewById(R.id.tv_channel)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
    }

    class ShortsRowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
