package com.signal.reader

import android.app.Application
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.data.sync.RefreshWorker

class SignalApplication : Application() {

    lateinit var database: SignalDatabase
        private set

    lateinit var repository: FeedRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SignalDatabase.getInstance(this)
        repository = FeedRepository(database)
        settingsManager = SettingsManager(this)

        val settings = settingsManager.settingsFlow.value
        RefreshWorker.schedule(
            context = this,
            intervalMinutes = settings.refreshIntervalMinutes,
            wifiOnly = settings.wifiOnly,
            enabled = settings.backgroundRefresh
        )
    }

    companion object {
        lateinit var instance: SignalApplication
            private set
    }
}
