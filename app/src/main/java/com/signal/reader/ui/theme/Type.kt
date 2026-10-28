package com.signal.reader.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.signal.reader.R

val ArchivoFontFamily = FontFamily(
    Font(R.font.archivo)
)

object SignalTypography {
    val screenTitle = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).sp
    )

    val heroHeadline = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.02).sp
    )

    val readerTitle = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).sp
    )

    fun readerTitle(isSerif: Boolean = false): TextStyle = TextStyle(
        fontFamily = if (isSerif) FontFamily.Serif else ArchivoFontFamily,
        fontWeight = if (isSerif) FontWeight.Bold else FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.02).sp
    )

    val rowHeadline = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.01).sp
    )

    fun readerBody(fontSize: TextUnit, isSerif: Boolean = false): TextStyle = TextStyle(
        fontFamily = if (isSerif) FontFamily.Serif else ArchivoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize,
        lineHeight = fontSize * (if (isSerif) 1.65f else 1.6f),
        letterSpacing = if (isSerif) 0.1.sp else 0.sp
    )

    val metaSource = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp
    )

    val metaTime = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp
    )

    val wordmark = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.sp
    )

    val pillLabel = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 14.sp
    )

    val smallLabel = TextStyle(
        fontFamily = ArchivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 15.sp
    )
}
