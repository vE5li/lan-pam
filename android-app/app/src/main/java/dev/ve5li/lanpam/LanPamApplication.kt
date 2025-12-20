package dev.ve5li.lanpam

import android.app.Application

class LanPamApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize singletons
        RequestHistoryManager.initialize(this)
    }
}
