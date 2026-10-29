package com.kutir.drtube.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Channel

class SubscriptionsAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder>() {

    private var items: List<Channel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onChannelClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Channel>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.iv_channel_avatar)
        private val tvName: TextView = itemView.findViewById(R.id.tv_channel_name)

        fun bind(channel: Channel, onChannelClick: (Channel) -> Unit) {
            tvName.text = channel.name
            Glide.with(itemView.context)
                .load(channel.avatarUrl.ifBlank { null })
                .circleCrop()
                .placeholder(R.drawable.ic_launcher)
                .error(R.drawable.ic_launcher)
                .into(ivAvatar)
            itemView.setOnClickListener { onChannelClick(channel) }
        }
    }
}
