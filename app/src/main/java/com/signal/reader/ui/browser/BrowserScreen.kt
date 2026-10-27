package com.signal.reader.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale

/**
 * Editorial In-App Browser wrapping external sites in Signal's typography, palette, and progress chrome.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    url: String,
    titleHint: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()

    var webView: WebView? by remember { mutableStateOf(null) }
    var currentTitle by remember { mutableStateOf(titleHint ?: "Article") }
    var currentUrl by remember { mutableStateOf(url) }
    var progressPercent by remember { mutableFloatStateOf(0.1f) }
    var isLoading by remember { mutableStateOf(true) }

    val cleanDomain = remember(currentUrl) {
        try {
            val uri = Uri.parse(currentUrl)
            val host = uri.host ?: ""
            host.removePrefix("www.").ifBlank { currentUrl }
        } catch (_: Exception) {
            currentUrl
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressPercent,
        animationSpec = SignalMotion.easeTween(200),
        label = "webProgress"
    )

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Sticky Header bar matching Signal editorial design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back chevron
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .signalPressScale(0.94f) {
                                if (webView?.canGoBack() == true) {
                                    webView?.goBack()
                                } else {
                                    onBack()
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BackChevron(color = colors.ink)
                    }

                    // Title & Domain
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = currentTitle,
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🔒",
                                fontSize = 10.sp,
                                color = colors.mute
                            )
                            Text(
                                text = cleanDomain,
                                color = colors.mute,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Share button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .signalPressScale(0.94f) {
                                haptics.tick()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, currentTitle)
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share article link"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↗",
                            color = colors.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Open in Chrome / external browser
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .signalPressScale(0.94f) {
                                haptics.tick()
                                try {
                                    val externalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                    context.startActivity(externalIntent)
                                } catch (_: Exception) {
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⋯",
                            color = colors.ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Loading progress bar (#prog)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(colors.line)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0.05f, 1f))
                                .height(2.dp)
                                .background(colors.ink)
                        )
                    }
                }
            }
        }

        // Embedded WebView with matched background color
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(colors.bg.toArgb())

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progressPercent = newProgress / 100f
                            isLoading = newProgress < 100
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank() && title != currentUrl) {
                                currentTitle = title
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            url?.let { currentUrl = it }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            url?.let { currentUrl = it }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val nextUrl = request?.url?.toString() ?: return false
                            if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                                currentUrl = nextUrl
                                return false // Load within this WebView
                            }
                            // Hand off mailto, tel, intents to system
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl))
                                ctx.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }

                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                view.setBackgroundColor(colors.bg.toArgb())
            }
        )
    }
}
