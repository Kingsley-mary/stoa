package com.signal.reader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.TypedValue
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
import kotlin.math.roundToInt

class StoaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Immediately render initial layout with physical 24dp rounded bitmap so Launcher never shows sharp corners or "Problem loading widget"
        for (widgetId in appWidgetIds) {
            val initialViews = RemoteViews(context.packageName, R.layout.widget_stoa_hero)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                initialViews.setViewOutlinePreferredRadius(
                    android.R.id.background,
                    24f,
                    TypedValue.COMPLEX_UNIT_DIP
                )
            }

            val density = context.resources.displayMetrics.density
            val initialW = (320 * density).roundToInt()
            val initialH = (140 * density).roundToInt()
            val initialBitmap = renderWidgetCardBitmap(
                widthPx = initialW,
                heightPx = initialH,
                fillColor = Color.parseColor("#EEEAE1"),
                strokeColor = Color.parseColor("#29141413"),
                strokeWidthPx = (1f * density).coerceAtLeast(1f),
                cornerRadiusPx = 24f * density
            )
            initialViews.setImageViewBitmap(R.id.widget_background, initialBitmap)
            
            // Story tap intent: opens article or Stoa Home
            val storyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val storyPendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                storyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            initialViews.setOnClickPendingIntent(R.id.widget_content, storyPendingIntent)

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

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            views.setViewOutlinePreferredRadius(
                                android.R.id.background,
                                24f,
                                TypedValue.COMPLEX_UNIT_DIP
                            )
                        }

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

                        // Compute widget target pixel dimensions for exact crisp rendering
                        val options = manager.getAppWidgetOptions(widgetId)
                        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                        val density = context.resources.displayMetrics.density

                        val wDp = if (minWidthDp > 100) minWidthDp else 320
                        val hDp = if (minHeightDp > 50) minHeightDp else 140

                        val widthPx = (wDp * density).roundToInt().coerceIn(400, 1400)
                        val heightPx = (hDp * density).roundToInt().coerceIn(180, 800)
                        val cornerRadiusPx = 24f * density
                        val strokeWidthPx = (1f * density).coerceAtLeast(1f)

                        // Load hero article image if article_image mode
                        var heroBitmap: Bitmap? = null
                        if (effectiveBg == "article_image" && !heroArticle?.imageUrl.isNullOrBlank()) {
                            try {
                                val req = ImageRequest.Builder(context)
                                    .data(heroArticle.imageUrl)
                                    .size(widthPx, heightPx)
                                    .allowHardware(false)
                                    .build()
                                val result = (Coil.imageLoader(context).execute(req) as? SuccessResult)?.drawable
                                heroBitmap = (result as? BitmapDrawable)?.bitmap
                            } catch (_: Exception) {}
                        }

                        val cardBitmap: Bitmap
                        when (effectiveBg) {
                            "light" -> {
                                cardBitmap = renderWidgetCardBitmap(
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    fillColor = Color.parseColor("#FFFFFF"),
                                    strokeColor = Color.parseColor("#1F000000"),
                                    strokeWidthPx = strokeWidthPx,
                                    cornerRadiusPx = cornerRadiusPx
                                )
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                            }
                            "dark" -> {
                                cardBitmap = renderWidgetCardBitmap(
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    fillColor = Color.parseColor("#10100F"),
                                    strokeColor = Color.parseColor("#33F1EDE4"),
                                    strokeWidthPx = strokeWidthPx,
                                    cornerRadiusPx = cornerRadiusPx
                                )
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg_accent)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#F1EDE4"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#F1EDE4"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#9C978B"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                            }
                            "article_image" -> {
                                if (heroBitmap != null) {
                                    cardBitmap = renderWidgetCardBitmap(
                                        widthPx = widthPx,
                                        heightPx = heightPx,
                                        fillColor = Color.parseColor("#10100F"),
                                        strokeColor = Color.parseColor("#4DF1EDE4"),
                                        strokeWidthPx = strokeWidthPx,
                                        cornerRadiusPx = cornerRadiusPx,
                                        heroImage = heroBitmap
                                    )
                                    views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg_accent)
                                    views.setTextColor(R.id.widget_brand, Color.parseColor("#FFFFFF"))
                                    views.setTextColor(R.id.widget_title, Color.parseColor("#FFFFFF"))
                                    views.setTextColor(R.id.widget_meta, Color.parseColor("#E0DDD5"))
                                    views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#FFFFFF"))
                                } else {
                                    // Fallback to paper
                                    cardBitmap = renderWidgetCardBitmap(
                                        widthPx = widthPx,
                                        heightPx = heightPx,
                                        fillColor = Color.parseColor("#EEEAE1"),
                                        strokeColor = Color.parseColor("#29141413"),
                                        strokeWidthPx = strokeWidthPx,
                                        cornerRadiusPx = cornerRadiusPx
                                    )
                                    views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                    views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                    views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                    views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                    views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#EEEAE1"))
                                }
                            }
                            else -> {
                                // Default: "paper"
                                cardBitmap = renderWidgetCardBitmap(
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    fillColor = Color.parseColor("#EEEAE1"),
                                    strokeColor = Color.parseColor("#29141413"),
                                    strokeWidthPx = strokeWidthPx,
                                    cornerRadiusPx = cornerRadiusPx
                                )
                                views.setImageViewResource(R.id.widget_badge_bg, R.drawable.widget_badge_bg)
                                views.setTextColor(R.id.widget_brand, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_title, Color.parseColor("#141413"))
                                views.setTextColor(R.id.widget_meta, Color.parseColor("#6B665C"))
                                views.setTextColor(R.id.widget_unread_badge, Color.parseColor("#EEEAE1"))
                            }
                        }

                        // Physically rendered 24dp rounded bitmap set directly on the ImageView
                        views.setImageViewBitmap(R.id.widget_background, cardBitmap)

                        // Story click: Opens article in Reader
                        val storyIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (heroArticle != null) {
                                putExtra("article_id", heroArticle.id)
                            }
                        }
                        val storyPendingIntent = PendingIntent.getActivity(
                            context,
                            widgetId,
                            storyIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_content, storyPendingIntent)

                        manager.updateAppWidget(widgetId, views)
                    }
                } catch (_: Exception) {}
            }
        }

        private fun renderWidgetCardBitmap(
            widthPx: Int,
            heightPx: Int,
            fillColor: Int,
            strokeColor: Int,
            strokeWidthPx: Float,
            cornerRadiusPx: Float,
            heroImage: Bitmap? = null
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val halfStroke = strokeWidthPx / 2f
            val rect = RectF(
                halfStroke,
                halfStroke,
                widthPx.toFloat() - halfStroke,
                heightPx.toFloat() - halfStroke
            )

            val path = Path().apply {
                addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }

            // 1. Draw rounded base card
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, fillPaint)

            // 2. If hero image is present, clip and draw with contrast gradient scrim
            if (heroImage != null) {
                canvas.save()
                canvas.clipPath(path)

                val scale: Float
                val dx: Float
                val dy: Float
                if (heroImage.width * heightPx > widthPx * heroImage.height) {
                    scale = heightPx.toFloat() / heroImage.height.toFloat()
                    dx = (widthPx - heroImage.width * scale) * 0.5f
                    dy = 0f
                } else {
                    scale = widthPx.toFloat() / heroImage.width.toFloat()
                    dx = 0f
                    dy = (heightPx - heroImage.height * scale) * 0.5f
                }
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                }
                val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(heroImage, matrix, imgPaint)

                // Dark gradient scrim overlay
                val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, heightPx.toFloat(),
                        intArrayOf(
                            Color.argb(100, 0, 0, 0),
                            Color.argb(180, 0, 0, 0),
                            Color.argb(235, 0, 0, 0)
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), scrimPaint)

                canvas.restore()
            }

            // 3. Draw border stroke around the exact rounded rectangle
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
            }
            canvas.drawPath(path, strokePaint)

            return bitmap
        }
    }
}
