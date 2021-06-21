package com.geeksville.mesh

import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.mapbox.mapboxsdk.Mapbox


class MeshUtilApplication : GeeksvilleApplication() {

    override fun onCreate() {
        super.onCreate()
        Logging.showLogs = BuildConfig.DEBUG
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
    }
}