package com.drtube.ui.presenter

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.drtube.data.model.Video

class VideoCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as? Video ?: return
        val cardView = viewHolder.view as? ImageCardView ?: return

        cardView.titleText = video.title
        cardView.contentText = video.channelTitle
        cardView.setMainImageDimensions(320, 180) // 16:9 aspect ratio

        if (video.thumbnailUrl.isNotEmpty()) {
            Glide.with(cardView.context)
                .load(video.thumbnailUrl)
                .centerCrop()
                .into(cardView.mainImageView)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as? ImageCardView ?: return
        cardView.badgeImage = null
        cardView.mainImage = null
        Glide.with(cardView.context).clear(cardView.mainImageView)
    }
}
