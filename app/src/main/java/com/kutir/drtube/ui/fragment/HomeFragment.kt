package com.kutir.drtube.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.ui.adapter.VideoFeedAdapter
import com.kutir.drtube.ui.player.PlayerActivity
import com.kutir.drtube.ui.util.isTelevision
import com.kutir.drtube.ui.viewmodel.HomeUiState
import com.kutir.drtube.ui.viewmodel.HomeViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var feedAdapter: VideoFeedAdapter
    private lateinit var recyclerView: RecyclerView
    private var allCategories: Map<String, List<Video>> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_video_feed)
        recyclerView.layoutManager = if (requireContext().isTelevision()) {
            GridLayoutManager(requireContext(), 3)
        } else {
            LinearLayoutManager(requireContext())
        }
        feedAdapter = VideoFeedAdapter(emptyList()) { video ->
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            }
            startActivity(intent)
        }
        recyclerView.adapter = feedAdapter

        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group)
        val categories = listOf("Semua", "Trending", "Musik", "Gaming", "Live", "Berita")
        
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isClickable = true
                setChipBackgroundColorResource(R.color.chip_background_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                if (category == "Semua") isChecked = true
                setOnClickListener { showVideosForCategory(if (category == "Semua") null else category) }
            }
            chipGroup.addView(chip)
        }

        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            startActivity(Intent(requireContext(), com.kutir.drtube.ui.search.SearchActivity::class.java))
        }

        view.findViewById<View>(R.id.btn_notifications).setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    com.kutir.drtube.ui.notifications.NotificationsActivity::class.java
                )
            )
        }

        view.findViewById<View>(R.id.btn_info).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Tentang KuctireTube")
                .setMessage("KuctireTube (DRTUBE)\nVersi 1.0.0\n\nDibuat oleh: Ahmad Diky Dharmawan\n\nKuctireTube adalah aplikasi klien YouTube modern untuk Android & Android TV. Nikmati tontonan YouTube tanpa iklan, penjelajahan video yang mulus, dukungan Shorts, manajemen langganan, serta pemutaran video yang dioptimalkan.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        observeViewModel()
    }

    private fun showVideosForCategory(categoryName: String?) {
        if (allCategories.isEmpty()) return
        val videos = if (categoryName == null) {
            allCategories.values.flatten()
        } else {
            allCategories[categoryName] ?: allCategories.values.flatten()
        }
        feedAdapter.updateData(videos.distinctBy { it.id })
        if (requireContext().isTelevision()) {
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is HomeUiState.Success) {
                        allCategories = state.categories
                        showVideosForCategory(null)
                    }
                }
            }
        }
    }
}
