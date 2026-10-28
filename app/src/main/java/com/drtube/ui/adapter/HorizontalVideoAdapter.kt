package com.drtube.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drtube.R
import com.drtube.data.model.Video

class HorizontalVideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<HorizontalVideoAdapter.ViewHolder>() {

    private var items: List<Video> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_horizontal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onVideoClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Video>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvChannel: TextView = itemView.findViewById(R.id.tv_channel)

        fun bind(video: Video, onVideoClick: (Video) -> Unit) {
            tvTitle.text = video.title
            tvChannel.text = video.channelTitle
            tvDuration.text = video.duration
            tvDuration.visibility = if (video.duration.isBlank()) View.GONE else View.VISIBLE
            Glide.with(itemView.context)
                .load(video.thumbnailUrl)
                .into(ivThumbnail)
            itemView.setOnClickListener { onVideoClick(video) }
        }
    }
}
