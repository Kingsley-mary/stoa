package com.signal.reader.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.signal.reader.MainActivity
import com.signal.reader.R
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity

class StoaNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val GROUP_SOURCES_ID = "stoa_publications_group"
        const val GROUP_SOURCES_NAME = "Publications"
        const val EXTRA_ARTICLE_ID = "notification_article_id"
        const val EXTRA_SOURCE_ID = "notification_source_id"
    }

    init {
        createChannelGroup()
    }

    private fun createChannelGroup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val group = NotificationChannelGroup(GROUP_SOURCES_ID, GROUP_SOURCES_NAME)
            notificationManager.createNotificationChannelGroup(group)
        }
    }

    fun getChannelIdForSource(sourceId: Long): String = "source_channel_$sourceId"

    fun syncSourceChannel(source: SourceEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelIdForSource(source.id)
            val channel = NotificationChannel(
                channelId,
                source.name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for new stories from ${source.name}"
                group = GROUP_SOURCES_ID
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun syncAllSourceChannels(sources: List<SourceEntity>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannelGroup()
            for (source in sources) {
                syncSourceChannel(source)
            }
        }
    }

    fun deleteSourceChannel(sourceId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(getChannelIdForSource(sourceId))
        }
    }

    fun isChannelEnabledInSystem(sourceId: Long): Boolean {
        if (!areNotificationsGloballyEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(getChannelIdForSource(sourceId))
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    fun areNotificationsGloballyEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    fun createChannelSettingsIntent(sourceId: Long): Intent {
        val channelId = getChannelIdForSource(sourceId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppNotificationSettingsIntent()
        }
    }

    fun createAppNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun postNewArticlesNotification(source: SourceEntity, newArticles: List<ArticleEntity>) {
        if (newArticles.isEmpty()) return
        if (!source.notificationsEnabled) return
        if (!isChannelEnabledInSystem(source.id)) return

        syncSourceChannel(source)
        val channelId = getChannelIdForSource(source.id)
        val groupKey = "group_${source.id}"

        if (newArticles.size == 1) {
            // Single article notification
            val article = newArticles.first()
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_ARTICLE_ID, article.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                article.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(article.title)
                .setContentText("${source.name} • ${article.excerpt.take(90)}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(article.excerpt))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setColor(0xFF141414.toInt())

            notificationManager.notify(article.id.toInt(), builder.build())
        } else {
            // Multiple articles: Group summary + top 2 articles
            val sourceIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SOURCE_ID, source.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sourcePendingIntent = PendingIntent.getActivity(
                context,
                source.id.toInt(),
                sourceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Post individual cards for top 2 stories
            for (article in newArticles.take(2)) {
                val articleIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_ARTICLE_ID, article.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val articlePendingIntent = PendingIntent.getActivity(
                    context,
                    article.id.toInt(),
                    articleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val itemNotification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(article.title)
                    .setContentText("${source.name} • ${article.excerpt.take(80)}")
                    .setContentIntent(articlePendingIntent)
                    .setGroup(groupKey)
                    .setAutoCancel(true)
                    .setColor(0xFF141414.toInt())
                    .build()

                notificationManager.notify(article.id.toInt(), itemNotification)
            }

            // Summary Notification
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${source.name}: ${newArticles.size} new stories")
                .setSummaryText(source.name)

            for (article in newArticles.take(4)) {
                inboxStyle.addLine(article.title)
            }

            val summaryNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(source.name)
                .setContentText("${newArticles.size} new stories published")
                .setStyle(inboxStyle)
                .setContentIntent(sourcePendingIntent)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setColor(0xFF141414.toInt())
                .build()

            notificationManager.notify((source.id * 10000).toInt(), summaryNotification)
        }
    }
}
