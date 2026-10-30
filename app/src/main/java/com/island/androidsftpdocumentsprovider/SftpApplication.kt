package com.island.androidsftpdocumentsprovider

import android.app.Application
import com.google.android.material.color.DynamicColors

class SftpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Material You: on Android 12+ the colours come from the wallpaper.
        // Older versions (and devices without dynamic colour) use the brand
        // colours from res/values/colors.xml. Remove this line (and the
        // android:name in the manifest) to always use the brand colours.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
