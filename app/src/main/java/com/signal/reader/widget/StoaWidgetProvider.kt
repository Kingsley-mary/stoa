package com.signal.reader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.signal.reader.MainActivity
import com.signal.reader.R
import com.signal.reader.SignalApplication
import com.signal.reader.ui.components.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class StoaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Immediately render the layout so Launcher never shows "Problem loading widget"
        for (widgetId in appWidgetIds) {
            val initialViews = RemoteViews(context.packageName, R.layout.widget_stoa_hero)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            initialViews.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, initialViews)
        }

        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager? = null,
            widgetIds: IntArray? = null
        ) {
            val manager = appWidgetManager ?: AppWidgetManager.getInstance(context)
            val ids = widgetIds ?: manager.getAppWidgetIds(
                ComponentName(context, StoaWidgetProvider::class.java)
            )

            if (ids.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? SignalApplication ?: SignalApplication.instance
                    val db = app.database
                    val settings = app.settingsManager.settingsFlow.value
                    val articles = db.articleDao().getHomeStreamFlow().firstOrNull() ?: emptyList()
                    val unreadCount = articles.count { !it.read }
                    val heroArticle = articles.firstOrNull { !it.read } ?: articles.firstOrNull()

                    val widgetBg = settings.widgetBackground

                    val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    val effectiveBg = when (widgetBg) {
                        "system" -> if (isSystemDark) "dark" else "light"
                        else -> widgetBg
                    }

                    for (widgetId in ids) {
                        val views = RemoteViews(context.packageName, R.layout.widget_stoa_hero)

                        if (heroArticle != null) {
                            views.setTextViewText(R.id.widget_title, heroArticle.title)
                            val relativeTime = TimeUtils.formatRelativeTime(heroArticle.publishedTime)
                            val metaText = "${heroArticle.sourceName.uppercase()} · $relativeTime"
                            views.setTextViewText(R.id.widget_meta, metaText)
                        } else {
                            views.setTextViewText(R.id.widget_title, "No stories yet. Open Stoa to add your favorite sources.")
                            views.setTextViewText(R.id.widget_meta, "STOA READER")
                        }

                        val badgeText = if (unreadCount > 0) "$unreadCount unread" else "All read"
                        views.setTextViewText(R.id.widget_unread_badge, badgeText)

                        // Apply background styling & high-contrast themes via safe RemoteViews methods
                        when (effectiveBg) {
                            "light" -> {
                                views.setViewVisibility(R.id.widget_bg_image, View.GONE)
                                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                                views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_white)
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                            }
                            "dark" -> {
                                views.setViewVisibility(R.id.widget_bg_image, View.GONE)
                                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                                views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_black)
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg_accent)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#F1EDE4"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#F1EDE4"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#9C978B"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                            }
                            "article_image" -> {
                                var loadedImage = false
                                if (!heroArticle?.imageUrl.isNullOrBlank()) {
                                    try {
                                        val req = ImageRequest.Builder(context)
                                            .data(heroArticle.imageUrl)
                                            .size(800, 450)
                                            .allowHardware(false)
                                            .build()
                                        val result = (Coil.imageLoader(context).execute(req) as? SuccessResult)?.drawable
                                        val bitmap = (result as? BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            views.setImageViewBitmap(R.id.widget_bg_image, bitmap)
                                            views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
                                            views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                                            views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_black)
                                            views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg_accent)
                                            views.setTextColor(R.id.widget_brand, Color.parseColor("#FFFFFF"))
                                            views.setTextColor(R.id.widget_title, Color.parseColor("#FFFFFF"))
                                            views.setTextColor(R.id.widget_meta, Color.parseColor("#E0DDD5"))
                                            views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                                            loadedImage = true
                                        }
                                    } catch (_: Exception) {}
                                }

                                if (!loadedImage) {
                                    views.setViewVisibility(R.id.widget_bg_image, View.GONE)
                                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                                    views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_paper)
                                    views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                    views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                    views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                    views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                    views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#EEEAE1"))
                                }
                            }
                            else -> {
                                // Default: "paper"
                                views.setViewVisibility(R.id.widget_bg_image, View.GONE)
                                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                                views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_paper)
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#EEEAE1"))
                            }
                        }

                        // Tap opens MainActivity with article_id
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (heroArticle != null) {
                                putExtra("article_id", heroArticle.id)
                            }
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            widgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                        manager.updateAppWidget(widgetId, views)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
