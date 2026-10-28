package com.drtube.ui.main

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.drtube.R
import com.drtube.ui.fragment.HomeFragment
import com.drtube.ui.fragment.LibraryFragment
import com.drtube.ui.fragment.ShortsFragment
import com.drtube.ui.fragment.SubscriptionsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var currentNavId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commitNow()
            currentNavId = R.id.nav_home
        }

        findViewById<LinearLayout>(R.id.nav_home).setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, HomeFragment()).commit()
            updateNavSelection(R.id.nav_home)
        }
        findViewById<LinearLayout>(R.id.nav_shorts).setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, ShortsFragment()).commit()
            updateNavSelection(R.id.nav_shorts)
        }
        findViewById<LinearLayout>(R.id.nav_subscriptions).setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, SubscriptionsFragment()).commit()
            updateNavSelection(R.id.nav_subscriptions)
        }
        findViewById<LinearLayout>(R.id.nav_library).setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, LibraryFragment()).commit()
            updateNavSelection(R.id.nav_library)
        }

        updateNavSelection(currentNavId)
    }

    private fun updateNavSelection(selectedId: Int) {
        currentNavId = selectedId
        val gold = Color.parseColor("#FFD700")
        val gray = Color.parseColor("#AAAAAA")

        val navHome = findViewById<LinearLayout>(R.id.nav_home)
        val navShorts = findViewById<LinearLayout>(R.id.nav_shorts)
        val navSubscriptions = findViewById<LinearLayout>(R.id.nav_subscriptions)
        val navLibrary = findViewById<LinearLayout>(R.id.nav_library)

        fun setSelected(nav: LinearLayout, selected: Boolean) {
            val icon = nav.getChildAt(0) as? ImageView
            val text = nav.getChildAt(1) as? TextView
            icon?.setColorFilter(if (selected) gold else gray)
            text?.setTextColor(if (selected) gold else gray)
        }

        setSelected(navHome, selectedId == R.id.nav_home)
        setSelected(navShorts, selectedId == R.id.nav_shorts)
        setSelected(navSubscriptions, selectedId == R.id.nav_subscriptions)
        setSelected(navLibrary, selectedId == R.id.nav_library)
    }
}
