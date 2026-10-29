package com.kutir.drtube.ui.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.viewpager2.widget.ViewPager2
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.model.VideoStreamInfo
import com.kutir.drtube.data.repository.VideoRepository
import com.kutir.drtube.ui.adapter.ShortsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class ShortsFragment : Fragment(), View.OnKeyListener {

    @Inject
    lateinit var repository: VideoRepository

    @Inject
    lateinit var auth: com.google.firebase.auth.FirebaseAuth

    private var exoPlayer: ExoPlayer? = null
    private lateinit var viewPager: ViewPager2
    private var videoList: List<Video> = emptyList()
    private var playJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shorts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.vp_shorts)
        viewPager.isFocusable = true
        viewPager.isFocusableInTouchMode = true
        viewPager.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        viewPager.requestFocus()
        viewPager.setOnKeyListener(this)
        exoPlayer = ExoPlayer.Builder(requireContext()).build()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                playVideo(position)
                viewPager.isFocusable = true
                viewPager.isFocusableInTouchMode = true
                viewPager.requestFocus()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            videoList = repository.getShorts().getOrDefault(emptyList())
            if (!isAdded || videoList.isEmpty()) return@launch
            val player = exoPlayer ?: return@launch
            viewPager.adapter = ShortsAdapter(videoList, player, repository, auth, viewLifecycleOwner.lifecycleScope)
            playVideo(viewPager.currentItem)
            viewPager.isFocusable = true
            viewPager.isFocusableInTouchMode = true
            viewPager.requestFocus()
        }
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                val next = viewPager.currentItem + 1
                if (next < videoList.size) {
                    viewPager.setCurrentItem(next, true)
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                val prev = viewPager.currentItem - 1
                if (prev >= 0) {
                    viewPager.setCurrentItem(prev, true)
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                val next = viewPager.currentItem + 1
                if (next < videoList.size) {
                    viewPager.setCurrentItem(next, true)
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                val prev = viewPager.currentItem - 1
                if (prev >= 0) {
                    viewPager.setCurrentItem(prev, true)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        viewPager.isFocusable = true
        viewPager.isFocusableInTouchMode = true
        viewPager.requestFocus()
        exoPlayer?.play()
    }

    private fun playVideo(position: Int) {
        val video = videoList.getOrNull(position) ?: return
        playJob?.cancel()
        exoPlayer?.stop()
        playJob = viewLifecycleOwner.lifecycleScope.launch {
            val stream = repository.getVideoStream(video.id).getOrNull()
                ?: video.streamUrl.takeIf { it.isNotBlank() }?.let { VideoStreamInfo(it) }
            if (!isAdded) return@launch
            val context = context ?: return@launch
            val player = exoPlayer ?: return@launch
            if (stream == null) {
                Toast.makeText(context, "Video tidak dapat diputar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dataSourceFactory = DefaultDataSource.Factory(context)
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(stream.videoUrl))

            val mediaSource = stream.audioUrl?.let { audioUrl ->
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                MergingMediaSource(videoSource, audioSource)
            } ?: videoSource

            player.stop()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
