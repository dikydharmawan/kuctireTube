package com.drtube.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drtube.R
import com.drtube.data.model.Channel

class ChannelRailAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelRailAdapter.ChannelViewHolder>() {

    private var items: List<Channel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_rail, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = items[position]
        holder.name.text = channel.name
        Glide.with(holder.itemView.context)
            .load(channel.avatarUrl.ifBlank { null })
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .into(holder.avatar)
        holder.itemView.setOnClickListener { onChannelClick(channel) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Channel>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.iv_channel_avatar)
        val name: TextView = itemView.findViewById(R.id.tv_channel_name)
    }
}
