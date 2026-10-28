package com.drtube.ui.details

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.drtube.data.model.Video

class VideoDetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val video = item as? Video ?: return
        viewHolder.title.text = video.title
        viewHolder.subtitle.text =
            "${video.channelTitle}  •  ${video.viewsCount}  •  ${video.publishedAt}"
        viewHolder.body.text = video.description
    }
}
