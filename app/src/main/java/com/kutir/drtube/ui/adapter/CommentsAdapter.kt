package com.kutir.drtube.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kutir.drtube.R
import com.kutir.drtube.data.model.CommentModel

class CommentsAdapter(
    private var comments: List<CommentModel> = emptyList(),
    private val onReply: (CommentModel) -> Unit = {}
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: View = itemView
        val avatar: ImageView = itemView.findViewById(R.id.iv_comment_avatar)
        val name: TextView = itemView.findViewById(R.id.tv_comment_author)
        val text: TextView = itemView.findViewById(R.id.tv_comment_text)
        val date: TextView = itemView.findViewById(R.id.tv_comment_date)
        val like: TextView = itemView.findViewById(R.id.tv_comment_like)
        val reply: TextView = itemView.findViewById(R.id.tv_comment_reply)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.name.text = comment.authorName
        holder.text.text = comment.commentText
        holder.date.text = comment.publishedAt

        val density = holder.itemView.resources.displayMetrics.density
        val basePadding = (0 * density).toInt()
        val replyIndent = (44 * density).toInt()
        holder.root.setPadding(
            if (comment.isReply) replyIndent else basePadding,
            holder.root.paddingTop,
            holder.root.paddingEnd,
            holder.root.paddingBottom
        )

        if (comment.likeCount > 0) {
            holder.like.visibility = View.VISIBLE
            holder.like.text = "👍 ${formatCount(comment.likeCount)}"
        } else {
            holder.like.visibility = View.GONE
        }

        holder.reply.visibility = View.VISIBLE
        holder.reply.setOnClickListener { onReply(comment) }

        Glide.with(holder.itemView.context)
            .load(comment.authorAvatarUrl.ifBlank { null })
            .circleCrop()
            .placeholder(R.drawable.ic_launcher)
            .into(holder.avatar)
    }

    override fun getItemCount(): Int = comments.size

    fun submitList(list: List<CommentModel>) {
        comments = list
        notifyDataSetChanged()
    }

    fun prepend(comment: CommentModel) {
        comments = listOf(comment) + comments
        notifyItemInserted(0)
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f jt", count / 1_000_000.0)
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1f rb", count / 1_000.0)
            else -> count.toString()
        }.replace(".0 ", " ")
    }
}
