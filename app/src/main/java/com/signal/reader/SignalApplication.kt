package com.signal.reader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.data.sync.RefreshWorker
import com.signal.reader.data.update.UpdateManager

class SignalApplication : Application(), ImageLoaderFactory {

    lateinit var database: SignalDatabase
        private set

    lateinit var repository: FeedRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var updateManager: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SignalDatabase.getInstance(this)
        repository = FeedRepository(database)
        settingsManager = SettingsManager(this)
        updateManager = UpdateManager(this)

        val settings = settingsManager.settingsFlow.value
        RefreshWorker.schedule(
            context = this,
            intervalMinutes = settings.refreshIntervalMinutes,
            wifiOnly = settings.wifiOnly,
            enabled = settings.backgroundRefresh
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(120L * 1024 * 1024) // 120 MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Cache images aggressively for offline reading
            .build()
    }

    companion object {
        lateinit var instance: SignalApplication
            private set
    }
}
