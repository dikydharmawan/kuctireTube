package com.drtube.ui.details

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.drtube.R
import com.drtube.data.model.Video
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoDetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_details)

        if (savedInstanceState != null) return

        val video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(VideoDetailsFragment.EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(VideoDetailsFragment.EXTRA_VIDEO)
        }

        if (video == null) {
            finish()
            return
        }

        val fragment = VideoDetailsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(VideoDetailsFragment.EXTRA_VIDEO, video)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
