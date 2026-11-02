package com.signal.reader.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.signal.reader.MainActivity
import com.signal.reader.R
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.components.getSourceInitials
import kotlinx.coroutines.runBlocking

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
        val sourceIcon = getSourceIconBitmap(source)

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
                .setLargeIcon(sourceIcon)
                .setContentTitle(article.title)
                .setContentText("${source.name} • ${article.excerpt.take(90)}")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setColor(0xFF141414.toInt())

            val heroBitmap = if (!article.imageUrl.isNullOrBlank() && article.imageUrl != "none") {
                try {
                    val req = ImageRequest.Builder(context)
                        .data(article.imageUrl)
                        .allowHardware(false)
                        .build()
                    val res = runBlocking { context.imageLoader.execute(req) }
                    (res as? SuccessResult)?.drawable?.toBitmap()
                } catch (_: Exception) {
                    null
                }
            } else null

            if (heroBitmap != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(heroBitmap)
                        .setSummaryText(source.name)
                )
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(article.excerpt))
            }

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
                    .setLargeIcon(sourceIcon)
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
                .setLargeIcon(sourceIcon)
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

    private fun getSourceIconBitmap(source: SourceEntity, targetSize: Int = 128): Bitmap {
        val domain = source.siteUrl
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()

        val faviconUrl = if (domain.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        } else null

        if (!faviconUrl.isNullOrBlank()) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(faviconUrl)
                    .allowHardware(false)
                    .build()
                val result = runBlocking { loader.execute(request) }
                if (result is SuccessResult) {
                    val rawBitmap = result.drawable.toBitmap()
                    return createCircularIconBitmap(rawBitmap, targetSize)
                }
            } catch (_: Exception) {}
        }

        return createMonogramBitmap(source.name, targetSize)
    }

    private fun createCircularIconBitmap(rawBitmap: Bitmap, targetSize: Int = 128): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw solid white circular background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val radius = targetSize / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Subtle boundary stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(radius, radius, radius - 1f, borderPaint)

        // Draw the favicon centered with inset padding
        val inset = (targetSize * 0.16f).toInt()
        val destRect = Rect(inset, inset, targetSize - inset, targetSize - inset)
        val srcRect = Rect(0, 0, rawBitmap.width, rawBitmap.height)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rawBitmap, srcRect, destRect, bitmapPaint)

        return output
    }

    private fun createMonogramBitmap(sourceName: String, targetSize: Int = 128): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw solid dark circular background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF141414.toInt()
            style = Paint.Style.FILL
        }
        val radius = targetSize / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Draw initials centered
        val initials = getSourceInitials(sourceName)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = targetSize * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val yPos = radius - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initials, radius, yPos, textPaint)

        return output
    }
}
