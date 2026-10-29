package com.kutir.drtube.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kutir.drtube.R
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.repository.VideoRepository
import com.kutir.drtube.ui.adapter.VideoFeedAdapter
import com.kutir.drtube.ui.player.PlayerActivity
import com.kutir.drtube.ui.util.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: VideoRepository

    private lateinit var feedAdapter: VideoFeedAdapter
    private lateinit var etSearch: EditText
    private var currentQuery = ""
    private var nextPageToken: String? = null
    private var isLoading = false
    private var results: List<Video> = emptyList()
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        etSearch = findViewById(R.id.et_search)
        val btnDoSearch = findViewById<ImageView>(R.id.btn_do_search)
        recyclerView = findViewById(R.id.rv_search_results)

        recyclerView.layoutManager = if (isTelevision()) {
            GridLayoutManager(this, 3)
        } else {
            LinearLayoutManager(this)
        }
        feedAdapter = VideoFeedAdapter(emptyList()) { video ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            }
            startActivity(intent)
        }
        recyclerView.adapter = feedAdapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                if (layoutManager.findLastVisibleItemPosition() >= feedAdapter.itemCount - 3) {
                    loadMore()
                }
            }
        })

        btnDoSearch.setOnClickListener {
            performSearch(etSearch.text.toString())
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(etSearch.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank() || isLoading) return
        currentQuery = query.trim()
        nextPageToken = null
        results = emptyList()
        isLoading = true
        feedAdapter.updateData(emptyList())

        lifecycleScope.launch {
            val page = repository.searchVideos(currentQuery).getOrNull()
            isLoading = false
            if (page == null) {
                Toast.makeText(
                    this@SearchActivity,
                    "Gagal mencari video",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            results = page.videos
            nextPageToken = page.nextPageToken
            feedAdapter.updateData(results)
            focusFirstOnTv()
            if (results.isEmpty()) {
                Toast.makeText(
                    this@SearchActivity,
                    "Tidak ada video ditemukan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadMore() {
        val pageToken = nextPageToken ?: return
        if (isLoading || currentQuery.isBlank()) return
        isLoading = true

        lifecycleScope.launch {
            val page = repository.searchVideos(currentQuery, pageToken).getOrNull()
            isLoading = false
            if (page == null) return@launch

            nextPageToken = page.nextPageToken
            if (page.videos.isNotEmpty()) {
                results = (results + page.videos).distinctBy { it.id }
                feedAdapter.updateData(results)
            }
        }
    }

    private fun focusFirstOnTv() {
        if (!isTelevision()) return
        recyclerView.post {
            recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }
}
