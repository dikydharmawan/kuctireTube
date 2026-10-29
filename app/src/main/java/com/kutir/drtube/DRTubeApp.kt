package com.kutir.drtube

import android.app.Application
import com.kutir.drtube.data.api.NewPipeDownloader
import com.kutir.drtube.data.local.AuthSession
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe

@HiltAndroidApp
class DRTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AuthSession.init(this)
        NewPipe.init(NewPipeDownloader())
    }
}
