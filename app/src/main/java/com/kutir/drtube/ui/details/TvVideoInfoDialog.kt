package com.kutir.drtube.ui.details

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kutir.drtube.R
import com.kutir.drtube.data.model.CommentModel
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.ui.adapter.CommentsAdapter

class TvVideoInfoDialog(
    context: Context,
    private val video: Video,
    private val comments: List<CommentModel>
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_tv_video_info)

        findViewById<TextView>(R.id.tv_info_title).text = video.title
        findViewById<TextView>(R.id.tv_info_meta).text = listOf(video.viewsCount, video.publishedAt).filter { it.isNotBlank() }.joinToString(" • ")
        findViewById<TextView>(R.id.tv_info_desc).text = video.description.ifBlank { "Tidak ada deskripsi." }
        findViewById<TextView>(R.id.tv_comments_header).text = if (comments.isEmpty()) "Komentar" else "${comments.size} Komentar"
        findViewById<TextView>(R.id.btn_close).setOnClickListener { dismiss() }

        val rv = findViewById<RecyclerView>(R.id.rv_dialog_comments)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = CommentsAdapter(comments) {}
    }
}
