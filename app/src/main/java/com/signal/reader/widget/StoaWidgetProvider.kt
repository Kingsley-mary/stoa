package com.signal.reader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
                    val db = SignalApplication.instance.database
                    val articles = db.articleDao().getHomeStreamFlow().firstOrNull() ?: emptyList()
                    val unreadCount = articles.count { !it.read }
                    val heroArticle = articles.firstOrNull { !it.read } ?: articles.firstOrNull()

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

                        // Tap opens MainActivity
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
