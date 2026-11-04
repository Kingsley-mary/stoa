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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignalApplication : Application(), ImageLoaderFactory {

    lateinit var database: SignalDatabase
        private set

    lateinit var repository: FeedRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var updateManager: UpdateManager
        private set

    lateinit var notificationManager: com.signal.reader.data.notification.StoaNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SignalDatabase.getInstance(this)
        repository = FeedRepository(database)
        settingsManager = SettingsManager(this)
        updateManager = UpdateManager(this)
        notificationManager = com.signal.reader.data.notification.StoaNotificationManager(this)

        val settings = settingsManager.settingsFlow.value
        RefreshWorker.schedule(
            context = this,
            intervalMinutes = settings.refreshIntervalMinutes,
            wifiOnly = settings.wifiOnly,
            enabled = settings.backgroundRefresh
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Deduplicate any duplicate sources and articles (e.g. trailing slash differences)
                repository.deduplicateSourcesAndArticles()
                // 2. Reset any stale "none" image URLs so they are properly re-resolved
                repository.resetFailedArticleImages()
                val followed = database.sourceDao().getFollowedSources()
                notificationManager.syncAllSourceChannels(followed)
                // 3. Immediately refresh all followed sources in the background so fresh feeds, CDATA images, and enclosures are parsed
                repository.refreshAllFollowedSources()
                // 4. Resolve any remaining missing images
                repository.resolveMissingArticleImages(50)
            } catch (_: Exception) {}
        }
    }

    override fun newImageLoader(): ImageLoader {
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
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
