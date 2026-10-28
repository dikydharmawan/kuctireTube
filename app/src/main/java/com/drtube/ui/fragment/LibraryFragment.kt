package com.drtube.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drtube.R
import com.drtube.data.local.AuthSession
import com.drtube.data.model.Video
import com.drtube.data.repository.VideoRepository
import com.drtube.ui.adapter.HorizontalVideoAdapter
import com.drtube.ui.auth.AuthActivity
import com.drtube.ui.player.PlayerActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    @Inject
    lateinit var repository: VideoRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var favoritesAdapter: HorizontalVideoAdapter
    private lateinit var historyAdapter: HorizontalVideoAdapter
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileSubtitle: TextView
    private lateinit var btnAuth: Button
    private lateinit var tvFavoritesTitle: TextView
    private lateinit var tvFavoritesEmpty: TextView
    private lateinit var rvFavorites: RecyclerView
    private lateinit var tvHistoryTitle: TextView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var rvHistory: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProfileName = view.findViewById(R.id.tv_profile_name)
        tvProfileSubtitle = view.findViewById(R.id.tv_profile_subtitle)
        btnAuth = view.findViewById(R.id.btn_auth)
        tvFavoritesTitle = view.findViewById(R.id.tv_favorites_title)
        tvFavoritesEmpty = view.findViewById(R.id.tv_favorites_empty)
        rvFavorites = view.findViewById(R.id.rv_favorites)
        tvHistoryTitle = view.findViewById(R.id.tv_history_title)
        tvHistoryEmpty = view.findViewById(R.id.tv_history_empty)
        rvHistory = view.findViewById(R.id.rv_history)

        val onVideoClick: (Video) -> Unit = { video ->
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO", video)
            })
        }

        favoritesAdapter = HorizontalVideoAdapter(onVideoClick)
        rvFavorites.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvFavorites.adapter = favoritesAdapter

        historyAdapter = HorizontalVideoAdapter(onVideoClick)
        rvHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvHistory.adapter = historyAdapter

        btnAuth.setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(requireContext(), AuthActivity::class.java))
            } else {
                auth.signOut()
                AuthSession.clear()
                updateProfileHeader()
                loadLibrary()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfileHeader()
        loadLibrary()
    }

    private fun updateProfileHeader() {
        val user = auth.currentUser
        tvProfileName.text = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email
            ?: "Belum masuk"
        tvProfileSubtitle.text = if (user == null) {
            "Favorit dan riwayat tersimpan di perangkat"
        } else {
            "Masuk sebagai ${user.email.orEmpty()}"
        }
        btnAuth.text = if (user == null) "MASUK" else "KELUAR"
    }

    private fun loadLibrary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val favorites = repository.getFavorites().getOrDefault(emptyList())
            val history = repository.getWatchHistory().getOrDefault(emptyList())
            bindSection(
                favorites = favorites,
                emptyView = tvFavoritesEmpty,
                titleView = tvFavoritesTitle,
                recyclerView = rvFavorites,
                adapter = favoritesAdapter
            )
            bindSection(
                favorites = history,
                emptyView = tvHistoryEmpty,
                titleView = tvHistoryTitle,
                recyclerView = rvHistory,
                adapter = historyAdapter
            )
        }
    }

    private fun bindSection(
        favorites: List<Video>,
        emptyView: TextView,
        titleView: TextView,
        recyclerView: RecyclerView,
        adapter: HorizontalVideoAdapter
    ) {
        val hasItems = favorites.isNotEmpty()
        titleView.visibility = View.VISIBLE
        emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
        adapter.submitList(favorites)
    }
}
