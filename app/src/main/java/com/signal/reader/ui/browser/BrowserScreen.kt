package com.signal.reader.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BookmarkIcon
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.DarkBg
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.launch

/**
 * Editorial In-App Browser matching design/web-page-mockup.html:
 * Edge-to-edge web page with bottom floating dock (.cap pill),
 * auto-collapsing to domain address on scroll down, progress line inside pill,
 * editorial loading/error states, back/forward/save/reload/external controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    url: String,
    titleHint: String? = null,
    articleId: Long? = null,
    repository: FeedRepository? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val isDark = colors.bg == DarkBg
    val dockBg = if (isDark) colors.card else colors.ink
    val dockContent = if (isDark) colors.ink else colors.bg
    val dockSubContent = if (isDark) colors.mute else colors.bg.copy(alpha = 0.65f)
    val progressColor = if (isDark) colors.accent else colors.bg

    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    var webView: WebView? by remember { mutableStateOf(null) }
    var currentTitle by remember { mutableStateOf(titleHint ?: "Article") }
    var currentUrl by remember { mutableStateOf(url) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    var isNoteDismissed by remember { mutableStateOf(false) }
    val isMeteredSite = remember(currentUrl) {
        val lower = currentUrl.lowercase()
        lower.contains("wired.com") || lower.contains("technologyreview.com")
    }

    val bookmarkPop = remember { Animatable(1.0f) }
    val reloadRotation = remember { Animatable(0f) }

    // Pulsating dot for loading screen
    val pulseScale = remember { Animatable(0.7f) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            pulseScale.animateTo(
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    val cleanDomain = remember(currentUrl) {
        try {
            val uri = Uri.parse(currentUrl)
            val host = uri.host ?: ""
            host.removePrefix("www.").ifBlank { currentUrl }
        } catch (_: Exception) {
            currentUrl
        }
    }

    // System back gesture / Esc key steps back through web history, then leaves
    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    val screenWidth = configuration.screenWidthDp.dp
    val pillTargetWidth = if (isMinimized) 196.dp else (screenWidth - 32.dp).coerceAtLeast(196.dp)
    val animatedPillWidth by animateDpAsState(
        targetValue = pillTargetWidth,
        animationSpec = SignalMotion.easeTween(400),
        label = "pillWidth"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressPercent,
        animationSpec = SignalMotion.easeTween(350),
        label = "webProgress"
    )

    fun openExternal() {
        haptics.tick()
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun triggerReload() {
        haptics.tick()
        coroutineScope.launch {
            reloadRotation.snapTo(0f)
            reloadRotation.animateTo(360f, SignalMotion.easeTween(500))
        }
        hasError = false
        webView?.reload()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // 1. Edge-to-edge WebView Layer (with 130dp bottom breathing space matching mockup .site)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            try {
                                @Suppress("DEPRECATION")
                                forceDark = if (isDark) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
                            } catch (_: Exception) {}
                        }
                    }

                    // Scrolling down shrinks pill to 196px address; scrolling up restores controls
                    var lastScrollY = 0
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        if (scrollY > lastScrollY + 12 && scrollY > 80) {
                            isMinimized = true
                        } else if (scrollY < lastScrollY - 12) {
                            isMinimized = false
                        }
                        lastScrollY = scrollY
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progressPercent = newProgress / 100f
                            if (newProgress >= 100) {
                                isLoading = false
                                hasLoadedOnce = true
                            }
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
                            hasError = false
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            hasLoadedOnce = true
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                isLoading = false
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val nextUrl = request?.url?.toString() ?: return false
                            if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                                currentUrl = nextUrl
                                return false // Load within WebView
                            }
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        @Suppress("DEPRECATION")
                        view.settings.forceDark = if (isDark) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
                    } catch (_: Exception) {}
                }
            }
        )

        // 2. Editorial Loading Screen Overlay (.ld)
        if (isLoading && !hasLoadedOnce) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.card)
                    .padding(horizontal = 24.dp, vertical = 140.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Pulsating dot + opening host
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(pulseScale.value)
                                .clip(CircleShape)
                                .background(colors.ink)
                        )
                        Text(
                            text = "Opening $cleanDomain",
                            color = colors.mute,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Huge bold editorial publication title
                    Text(
                        text = (titleHint ?: cleanDomain).uppercase(),
                        style = SignalTheme.typography.heroHeadline,
                        color = colors.ink,
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Editorial Error Screen Overlay (.err)
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .padding(horizontal = 24.dp, vertical = 140.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "CAN’T LOAD THIS PAGE",
                        style = SignalTheme.typography.heroHeadline,
                        color = colors.ink,
                        fontSize = 32.sp,
                        lineHeight = 34.sp
                    )

                    Text(
                        text = "Check your connection and try again. $cleanDomain didn’t respond.",
                        color = colors.mute,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Retry pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.ink)
                                .signalPressScale(0.96f) { triggerReload() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Retry",
                                color = colors.bg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Open in external browser pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, colors.ink, CircleShape)
                                .signalPressScale(0.96f) { openExternal() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Open in browser",
                                color = colors.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 4. Floating Control Pill Dock (.dock & .cap)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Optional Metered Site Note banner (.note)
            if (isMeteredSite && !isNoteDismissed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) colors.card else colors.bg)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$cleanDomain may limit free articles. Sign in to keep reading.",
                            color = colors.ink,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { isNoteDismissed = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "×",
                                color = colors.mute,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // Capsule Pill (.cap)
            Box(
                modifier = Modifier
                    .width(animatedPillWidth)
                    .height(56.dp)
                    .clip(CircleShape)
                    .then(if (isDark) Modifier.border(1.dp, colors.line, CircleShape) else Modifier)
                    .background(dockBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isMinimized) isMinimized = false
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button (✕)
                    if (!isMinimized) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .signalPressScale(0.92f) {
                                    haptics.tick()
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = dockContent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Back button (‹)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .alpha(if (canGoBack) 1f else 0.3f)
                                .then(
                                    if (canGoBack) {
                                        Modifier.signalPressScale(0.92f) {
                                            haptics.tick()
                                            webView?.goBack()
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "‹",
                                color = dockContent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Forward button (›)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .alpha(if (canGoForward) 1f else 0.3f)
                                .then(
                                    if (canGoForward) {
                                        Modifier.signalPressScale(0.92f) {
                                            haptics.tick()
                                            webView?.goForward()
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "›",
                                color = dockContent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Host / Domain & Reload Button (.host)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isMinimized) {
                                    isMinimized = false
                                } else {
                                    triggerReload()
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = (titleHint ?: cleanDomain),
                                    color = dockContent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = cleanDomain,
                                    color = dockSubContent,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isMinimized) {
                                // Circular reload arrow
                                Text(
                                    text = "↻",
                                    color = dockContent,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .rotate(reloadRotation.value)
                                        .padding(start = 6.dp)
                                        .clickable { triggerReload() }
                                )
                            }
                        }
                    }

                    if (!isMinimized) {
                        // Bookmark / Save button (#save)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .scale(bookmarkPop.value)
                                .signalPressScale(0.92f) {
                                    haptics.tick()
                                    isSaved = !isSaved
                                    coroutineScope.launch {
                                        bookmarkPop.snapTo(0.6f)
                                        bookmarkPop.animateTo(1.0f, SignalMotion.PopSpringSpec)
                                        articleId?.let { id ->
                                            repository?.toggleSaved(id)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BookmarkIcon(
                                isFilled = isSaved,
                                color = dockContent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Open in external browser (#ext: ↗)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .signalPressScale(0.92f) { openExternal() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↗",
                                color = dockContent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Progress line inside pill (#bar: bottom edge 2dp line)
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(animatedProgress.coerceIn(0.08f, 1f))
                            .height(2.dp)
                            .background(progressColor)
                    )
                }
            }
        }
    }
}
