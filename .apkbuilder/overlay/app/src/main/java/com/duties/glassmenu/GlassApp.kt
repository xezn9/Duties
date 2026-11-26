package com.duties.glassmenu

import android.app.Application

class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
