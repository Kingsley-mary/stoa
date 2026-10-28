package com.signal.reader.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object QuoteCardGenerator {

    fun generateAndShare(
        context: Context,
        quote: String,
        sourceName: String,
        articleTitle: String,
        author: String? = null,
        isDark: Boolean = false
    ) {
        val width = 1080
        val height = 1350

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Palette
        val bgColor = if (isDark) Color.parseColor("#10100F") else Color.parseColor("#EEEAE1")
        val inkColor = if (isDark) Color.parseColor("#F1EDE4") else Color.parseColor("#141413")
        val muteColor = if (isDark) Color.parseColor("#9C978B") else Color.parseColor("#6B665C")
        val accentColor = Color.parseColor("#E4472B") // Vermilion accent
        val lineColor = if (isDark) Color.parseColor("#33F1EDE4") else Color.parseColor("#29141413")

        // Draw background
        canvas.drawColor(bgColor)

        // Paint for border/margins
        val margin = 80f
        val contentWidth = (width - (margin * 2)).toInt()

        // 1. Top Bar: STOA · [SOURCE]
        val topPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muteColor
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        val topLabel = "STOA  ·  ${sourceName.uppercase()}"
        canvas.drawText(topLabel, margin, 120f, topPaint)

        // 2. Large Stylized Quote Mark
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 140f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", margin, 240f, quoteMarkPaint)

        // 3. Quote Body
        val cleanQuote = quote.trim().take(360) // Keep readable length
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = if (cleanQuote.length > 180) 44f else 52f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = 0.02f
        }

        val bodyLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(cleanQuote, 0, cleanQuote.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.4f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                cleanQuote,
                bodyPaint,
                contentWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.4f,
                0f,
                false
            )
        }

        canvas.save()
        canvas.translate(margin, 280f)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 4. Divider Line
        val dividerY = 280f + bodyLayout.height + 60f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = 3f
        }
        canvas.drawLine(margin, dividerY, width - margin, dividerY, linePaint)

        // 5. Article Title & Author
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val maxTitle = articleTitle.take(120)
        val titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(maxTitle, 0, maxTitle.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setMaxLines(3)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                maxTitle,
                titlePaint,
                contentWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.25f,
                0f,
                false
            )
        }

        canvas.save()
        canvas.translate(margin, dividerY + 50f)
        titleLayout.draw(canvas)
        canvas.restore()

        if (!author.isNullOrBlank()) {
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muteColor
                textSize = 28f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("By $author", margin, dividerY + 50f + titleLayout.height + 40f, authorPaint)
        }

        // 6. Bottom Brand Tagline
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muteColor
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        canvas.drawText("Read on Stoa", margin, height - 90f, brandPaint)

        // Save bitmap to file and share
        try {
            val cachePath = File(context.cacheDir, "shared_quotes")
            cachePath.mkdirs()
            val file = File(cachePath, "stoa_quote_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "“${cleanQuote.take(140)}…” — $articleTitle\nRead on Stoa")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Quote Card"))
        } catch (_: Exception) {}
    }
}
