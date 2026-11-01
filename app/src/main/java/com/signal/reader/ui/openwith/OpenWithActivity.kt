package com.signal.reader.ui.openwith

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.MainActivity
import com.signal.reader.SignalApplication
import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.FeedDiscovery
import com.signal.reader.data.feed.FeedFetcher
import com.signal.reader.data.feed.ParsedArticle
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.browser.BrowserScreen
import com.signal.reader.ui.components.SourceLogo
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.reader.ReaderScreen
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.net.URI

/**
 * Dedicated lightweight activity for Feature 2 ("Open with Stoa") & Feature 3 ("Open article links in Stoa").
 * Preserves the calling app's backstack untouched by finishing back when closed.
 */
class OpenWithActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SignalApplication
        val repository = app.repository
        val settingsManager = app.settingsManager

        // Identify calling app label
        val fromLabel = resolveCallingAppLabel()

        setContent {
            val settings by settingsManager.settingsFlow.collectAsState()
            SignalTheme(themePreference = settings.theme) {
                OpenWithRoot(
                    intent = intent,
                    callingAppLabel = fromLabel,
                    repository = repository,
                    settingsManager = settingsManager,
                    onFinish = { finish() },
                    onOpenMainActivity = { articleId, sourceId ->
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (articleId != null) {
                                putExtra("article_id", articleId)
                            }
                            if (sourceId != null) {
                                putExtra("open_destination", "source_page/$sourceId")
                            }
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun resolveCallingAppLabel(): String {
        return try {
            val pkg = callingPackage ?: referrer?.host?.let { host ->
                if (host.contains("chrome")) "com.android.chrome"
                else if (host.contains("whatsapp")) "com.whatsapp"
                else null
            }
            if (pkg != null) {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } else {
                "Chrome"
            }
        } catch (_: Exception) {
            "Chrome"
        }
    }
}

sealed class OpenWithState {
    data class Looking(val url: String, val message: String = "Checking the page for a feed Stoa can read.") : OpenWithState()
    data class FeedFound(val discovery: DiscoveryResult.Found) : OpenWithState()
    data class FeedAdded(val sourceName: String, val articleCount: Int, val sourceId: Long) : OpenWithState()
    data class AlreadyFollowing(val source: SourceEntity) : OpenWithState()
    data class NoFeed(val url: String, val reason: String) : OpenWithState()
    data class AskFirst(
        val url: String,
        val ogTitle: String?,
        val ogImage: String?,
        val discovery: DiscoveryResult.Found?
    ) : OpenWithState()
    data class LoadingArticle(val sourceName: String, val host: String) : OpenWithState()
    data class ShowReader(val articleId: Long, val toastMsg: String? = null, val addedSourceId: Long? = null) : OpenWithState()
    data class ShowWebPage(val url: String, val title: String? = null, val toastMsg: String? = null, val addedSourceId: Long? = null) : OpenWithState()
}

@Composable
fun OpenWithRoot(
    intent: Intent,
    callingAppLabel: String,
    repository: com.signal.reader.data.repository.FeedRepository,
    settingsManager: com.signal.reader.data.local.SettingsManager,
    onFinish: () -> Unit,
    onOpenMainActivity: (articleId: Long?, sourceId: Long?) -> Unit
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<OpenWithState>(OpenWithState.Looking("", "Initializing...")) }
    var progressBarWidth by remember { mutableFloatStateOf(0f) }
    var activeToast by remember { mutableStateOf<String?>(null) }
    var addedSourceToUndo by remember { mutableStateOf<Long?>(null) }

    // Auto-dismiss the toast pill after 4 seconds so it isn't intrusive
    LaunchedEffect(activeToast) {
        if (activeToast != null) {
            delay(4000L)
            activeToast = null
            addedSourceToUndo = null
        }
    }

    // Reduced motion check
    val isReducedMotion = remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    fun startProgressAnimation() {
        scope.launch {
            progressBarWidth = 0f
            delay(150)
            progressBarWidth = 0.30f
            delay(450)
            progressBarWidth = 0.70f
        }
    }

    fun finishProgressBar() {
        progressBarWidth = 1.0f
    }

    // Process incoming intent
    LaunchedEffect(intent) {
        val rawUrl = extractUrlFromIntent(intent)
        if (rawUrl == null) {
            state = OpenWithState.NoFeed("", "No readable web or feed address found.")
            return@LaunchedEffect
        }

        val url = normalizeUrl(rawUrl)
        val scheme = intent.data?.scheme?.lowercase()
        val mimeType = intent.type

        val isFeed = isFeedUrl(url, mimeType, scheme)
        val isNonArticle = isNonArticleUrl(url)

        val feedDiscovery = FeedDiscovery()

        if (isFeed) {
            // Feature 2: Feed URL flow
            state = OpenWithState.Looking(url, "Checking the feed Stoa can read.")
            startProgressAnimation()

            val allFollowed = repository.getFollowedSourcesFlow().first()
            val incomingDomain = extractRegistrableDomain(url)
            val already = allFollowed.firstOrNull {
                it.feedUrl.equals(url, ignoreCase = true) || extractRegistrableDomain(it.siteUrl) == incomingDomain
            }

            if (already != null) {
                finishProgressBar()
                state = OpenWithState.AlreadyFollowing(already)
                return@LaunchedEffect
            }

            when (val result = feedDiscovery.discover(url)) {
                is DiscoveryResult.Found -> {
                    finishProgressBar()
                    state = OpenWithState.FeedFound(result)
                }
                is DiscoveryResult.NotFound -> {
                    finishProgressBar()
                    state = OpenWithState.NoFeed(url, result.reason)
                }
                is DiscoveryResult.Error -> {
                    finishProgressBar()
                    state = OpenWithState.NoFeed(url, result.message)
                }
            }
        } else if (isNonArticle) {
            // Feature 3: Non-article URL (video, social, bare homepage) -> Open directly in in-app web page without following
            state = OpenWithState.ShowWebPage(url = url, title = null)
        } else {
            // Feature 3: Article link flow
            val incomingDomain = extractRegistrableDomain(url)
            val allFollowed = repository.getFollowedSourcesFlow().first()
            val existingSource = allFollowed.firstOrNull {
                extractRegistrableDomain(it.siteUrl) == incomingDomain || extractRegistrableDomain(it.feedUrl) == incomingDomain
            }

            if (existingSource != null) {
                // A. Source already followed:
                val altUrl = if (url.endsWith("/")) url.dropLast(1) else "$url/"
                val existingArticle = repository.findArticleByLinkOrGuid(url, altUrl)
                if (existingArticle != null) {
                    state = OpenWithState.ShowReader(articleId = existingArticle.id)
                } else {
                    state = OpenWithState.ShowWebPage(url = url, title = existingSource.name)
                }
            } else {
                // B. Source not followed:
                val newSitesBehavior = settingsManager.settingsFlow.value.newSitesBehavior
                val hostName = try { URI(url).host ?: incomingDomain } catch (_: Exception) { incomingDomain }
                val sourceDisplayName = hostName.removePrefix("www.").replaceFirstChar { it.uppercase() }

                if (newSitesBehavior == "just_open") {
                    state = OpenWithState.ShowWebPage(url = url, title = null)
                } else if (newSitesBehavior == "ask_first") {
                    state = OpenWithState.Looking(url, "Reading article details…")
                    startProgressAnimation()
                    val pageMeta = fetchPageMeta(url)
                    val disc = feedDiscovery.discover(url)
                    finishProgressBar()
                    state = OpenWithState.AskFirst(
                        url = url,
                        ogTitle = pageMeta?.first,
                        ogImage = pageMeta?.second,
                        discovery = (disc as? DiscoveryResult.Found)
                    )
                } else {
                    // "follow_and_open" (default)
                    state = OpenWithState.LoadingArticle(sourceName = sourceDisplayName, host = hostName)

                    // Cap whole step at 8 seconds
                    var addedSourceId: Long? = null
                    var foundArticleId: Long? = null
                    var feedFound = false

                    withTimeoutOrNull(8000L) {
                        try {
                            when (val disc = feedDiscovery.discover(url)) {
                                is DiscoveryResult.Found -> {
                                    feedFound = true
                                    val newId = repository.addSource(
                                        name = disc.name,
                                        siteUrl = disc.siteUrl,
                                        feedUrl = disc.feedUrl,
                                        description = disc.description
                                    )
                                    addedSourceId = newId
                                    repository.refreshSource(newId)

                                    val altUrl = if (url.endsWith("/")) url.dropLast(1) else "$url/"
                                    val matchedArticle = repository.findArticleByLinkOrGuid(url, altUrl)
                                    if (matchedArticle != null) {
                                        foundArticleId = matchedArticle.id
                                    }
                                }
                                else -> {}
                            }
                        } catch (_: Exception) {}
                    }

                    if (foundArticleId != null) {
                        activeToast = "Following $sourceDisplayName"
                        addedSourceToUndo = addedSourceId
                        state = OpenWithState.ShowReader(
                            articleId = foundArticleId!!,
                            toastMsg = "Following $sourceDisplayName",
                            addedSourceId = addedSourceId
                        )
                    } else {
                        val toastMsg = if (feedFound) "Following $sourceDisplayName" else "$sourceDisplayName doesn't offer a feed."
                        if (feedFound) {
                            activeToast = "Following $sourceDisplayName"
                            addedSourceToUndo = addedSourceId
                        }
                        state = OpenWithState.ShowWebPage(
                            url = url,
                            title = sourceDisplayName,
                            toastMsg = toastMsg,
                            addedSourceId = addedSourceId
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        when (val s = state) {
            is OpenWithState.Looking -> {
                FeedStateLooking(
                    url = s.url,
                    message = s.message,
                    progress = progressBarWidth,
                    callingAppLabel = callingAppLabel,
                    onClose = onFinish
                )
            }
            is OpenWithState.FeedFound -> {
                FeedStateFound(
                    discovery = s.discovery,
                    callingAppLabel = callingAppLabel,
                    onAdd = {
                        haptics.pop()
                        scope.launch {
                            val newId = repository.addSource(
                                name = s.discovery.name,
                                siteUrl = s.discovery.siteUrl,
                                feedUrl = s.discovery.feedUrl,
                                description = s.discovery.description
                            )
                            repository.refreshSource(newId)
                            val count = s.discovery.parsedFeed.items.size
                            state = OpenWithState.FeedAdded(
                                sourceName = s.discovery.name,
                                articleCount = count,
                                sourceId = newId
                            )
                        }
                    },
                    onClose = onFinish
                )
            }
            is OpenWithState.FeedAdded -> {
                FeedStateAdded(
                    sourceName = s.sourceName,
                    articleCount = s.articleCount,
                    callingAppLabel = callingAppLabel,
                    isReducedMotion = isReducedMotion,
                    onOpenStoa = { onOpenMainActivity(null, s.sourceId) },
                    onClose = onFinish
                )
            }
            is OpenWithState.AlreadyFollowing -> {
                FeedStateAlready(
                    source = s.source,
                    callingAppLabel = callingAppLabel,
                    onOpenSource = { onOpenMainActivity(null, s.source.id) },
                    onClose = onFinish
                )
            }
            is OpenWithState.NoFeed -> {
                FeedStateNone(
                    url = s.url,
                    reason = s.reason,
                    callingAppLabel = callingAppLabel,
                    onLookDeep = {
                        state = OpenWithState.Looking(s.url, "Searching site homepage and feeds...")
                        startProgressAnimation()
                        scope.launch {
                            val feedDiscovery = FeedDiscovery()
                            when (val res = feedDiscovery.discover(s.url)) {
                                is DiscoveryResult.Found -> {
                                    finishProgressBar()
                                    state = OpenWithState.FeedFound(res)
                                }
                                else -> {
                                    finishProgressBar()
                                    state = OpenWithState.NoFeed(s.url, "No readable RSS or Atom feed found on this site.")
                                }
                            }
                        }
                    },
                    onClose = onFinish
                )
            }
            is OpenWithState.AskFirst -> {
                AskFirstScreen(
                    url = s.url,
                    ogTitle = s.ogTitle,
                    ogImage = s.ogImage,
                    discovery = s.discovery,
                    callingAppLabel = callingAppLabel,
                    onReadAndFollow = {
                        haptics.pop()
                        scope.launch {
                            val disc = s.discovery
                            val sourceName = disc?.name ?: extractRegistrableDomain(s.url)
                            var newSourceId: Long? = null
                            if (disc != null) {
                                newSourceId = repository.addSource(
                                    name = disc.name,
                                    siteUrl = disc.siteUrl,
                                    feedUrl = disc.feedUrl,
                                    description = disc.description
                                )
                                repository.refreshSource(newSourceId)
                            }
                            val altUrl = if (s.url.endsWith("/")) s.url.dropLast(1) else "${s.url}/"
                            val matchedArticle = repository.findArticleByLinkOrGuid(s.url, altUrl)
                            if (matchedArticle != null) {
                                state = OpenWithState.ShowReader(
                                    articleId = matchedArticle.id,
                                    toastMsg = "Following $sourceName",
                                    addedSourceId = newSourceId
                                )
                            } else {
                                state = OpenWithState.ShowWebPage(
                                    url = s.url,
                                    title = sourceName,
                                    toastMsg = "Following $sourceName",
                                    addedSourceId = newSourceId
                                )
                            }
                        }
                    },
                    onJustRead = {
                        state = OpenWithState.ShowWebPage(url = s.url, title = null)
                    },
                    onClose = onFinish
                )
            }
            is OpenWithState.LoadingArticle -> {
                LoadingArticleScreen(
                    sourceName = s.sourceName,
                    host = s.host
                )
            }
            is OpenWithState.ShowReader -> {
                ReaderScreen(
                    articleId = s.articleId,
                    repository = repository,
                    settingsManager = settingsManager,
                    onBack = onFinish,
                    onOpenSourcePage = { srcId -> onOpenMainActivity(null, srcId) },
                    onOpenWebUrl = { webUrl, title ->
                        state = OpenWithState.ShowWebPage(url = webUrl, title = title)
                    }
                )
            }
            is OpenWithState.ShowWebPage -> {
                BrowserScreen(
                    url = s.url,
                    titleHint = s.title,
                    repository = repository,
                    onBack = onFinish
                )
            }
        }

        // Ink Toast Pill with Undo action
        AnimatedVisibility(
            visible = activeToast != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = SignalMotion.IntOffsetSpringSpec
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = activeToast ?: "",
                        color = colors.bg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (addedSourceToUndo != null) {
                        Text(
                            text = "Undo",
                            color = colors.bg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable {
                                    val idToUndo = addedSourceToUndo
                                    if (idToUndo != null) {
                                        scope.launch {
                                            repository.unfollowSource(idToUndo)
                                        }
                                    }
                                    activeToast = null
                                    addedSourceToUndo = null
                                }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-screens for OpenWith states matching open-with-mockup.html
// -------------------------------------------------------------

@Composable
private fun OpenWithHeader(
    callingAppLabel: String,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .signalPressScale(0.92f) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✕", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        }
        Text(
            text = "From $callingAppLabel",
            fontSize = 12.sp,
            color = colors.mute,
            modifier = Modifier.padding(end = 10.dp)
        )
    }
}

@Composable
private fun FeedStateLooking(
    url: String,
    message: String,
    progress: Float,
    callingAppLabel: String,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = SignalMotion.EaseOutQuart),
        label = "progressBar"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        // Progress bar
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.Transparent)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(2.dp)
                    .background(colors.ink)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Looking for a feed…",
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.mute
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Skeleton source card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.line.copy(alpha = 0.4f))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(colors.line.copy(alpha = 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.line.copy(alpha = 0.3f))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.line.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .signalPressScale(0.96f) { onClose() }
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Text(text = "Cancel", fontSize = 14.sp, color = colors.mute)
            }
        }
    }
}

@Composable
private fun FeedStateFound(
    discovery: DiscoveryResult.Found,
    callingAppLabel: String,
    onAdd: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    val initials = remember(discovery.name) {
        discovery.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Add this feed?",
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "It will be added to your sources and its latest articles will appear on Home.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.mute,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Source card
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SourceLogo(
                            siteUrl = discovery.siteUrl,
                            sourceName = discovery.name,
                            size = 56.dp,
                            shape = RoundedCornerShape(16.dp),
                            fontSize = 18.sp
                        )
                        Column {
                            Text(
                                text = discovery.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                letterSpacing = (-0.01).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = discovery.feedUrl.removePrefix("https://").removePrefix("http://"),
                                fontSize = 13.sp,
                                color = colors.mute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (discovery.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = discovery.description,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = colors.mute
                        )
                    }
                }
            }

            // Latest rows preview
            val latest = discovery.parsedFeed.items.take(3)
            if (latest.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Latest from this feed",
                    fontSize = 13.sp,
                    color = colors.mute,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )

                latest.forEachIndexed { i, article ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Column {
                            Text(
                                text = article.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                lineHeight = 20.sp,
                                letterSpacing = (-0.01).sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = article.author?.ifBlank { null } ?: "Recent",
                                fontSize = 12.sp,
                                color = colors.mute
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Footer actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onAdd() }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add to Stoa",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.bg
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .signalPressScale(0.96f) { onClose() }
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Text(text = "Cancel", fontSize = 14.sp, color = colors.mute)
            }
        }
    }
}

@Composable
private fun FeedStateAdded(
    sourceName: String,
    articleCount: Int,
    callingAppLabel: String,
    isReducedMotion: Boolean,
    onOpenStoa: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    val popScale = remember { Animatable(if (isReducedMotion) 1f else 0.5f) }

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            popScale.animateTo(1.3f, tween(200, easing = SignalMotion.SpringEasing))
            popScale.animateTo(1.0f, tween(250, easing = SignalMotion.EaseOutQuart))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(26.dp))
            // Checkmark circle
            Box(
                modifier = Modifier
                    .scale(popScale.value)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Added",
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "$sourceName is in your sources. $articleCount articles are ready.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.mute
            )
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onOpenStoa() }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open Stoa",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.bg
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .signalPressScale(0.96f) { onClose() }
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Text(text = "Back to $callingAppLabel", fontSize = 14.sp, color = colors.mute)
            }
        }
    }
}

@Composable
private fun FeedStateAlready(
    source: SourceEntity,
    callingAppLabel: String,
    onOpenSource: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    val initials = remember(source.name) {
        source.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Already in Stoa",
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "You follow ${source.name} already. Nothing to add.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.mute
            )

            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SourceLogo(
                            siteUrl = source.siteUrl,
                            sourceName = source.name,
                            size = 56.dp,
                            shape = RoundedCornerShape(16.dp),
                            fontSize = 18.sp
                        )
                        Column {
                            Text(
                                text = source.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                letterSpacing = (-0.01).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = source.feedUrl.removePrefix("https://").removePrefix("http://"),
                                fontSize = 13.sp,
                                color = colors.mute
                            )
                        }
                    }
                    if (!source.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = source.description,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = colors.mute
                        )
                    }
                }
            }
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onOpenSource() }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open ${source.name}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.bg
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .signalPressScale(0.96f) { onClose() }
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Text(text = "Close", fontSize = 14.sp, color = colors.mute)
            }
        }
    }
}

@Composable
private fun FeedStateNone(
    url: String,
    reason: String,
    callingAppLabel: String,
    onLookDeep: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "No feed here",
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = reason.ifBlank { "This link isn’t a feed Stoa can read. The site may still offer one." },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.mute
            )
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onLookDeep() }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Look for a feed on this site",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.bg
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .signalPressScale(0.96f) { onClose() }
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Text(text = "Close", fontSize = 14.sp, color = colors.mute)
            }
        }
    }
}

@Composable
private fun AskFirstScreen(
    url: String,
    ogTitle: String?,
    ogImage: String?,
    discovery: DiscoveryResult.Found?,
    callingAppLabel: String,
    onReadAndFollow: () -> Unit,
    onJustRead: () -> Unit,
    onClose: () -> Unit
) {
    val colors = SignalTheme.colors
    val sourceName = discovery?.name ?: extractRegistrableDomain(url).replaceFirstChar { it.uppercase() }
    val initials = remember(sourceName) {
        sourceName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OpenWithHeader(callingAppLabel = callingAppLabel, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Article preview image if present
            if (!ogImage.isNullOrBlank()) {
                AsyncImage(
                    model = ogImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.card),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!ogTitle.isNullOrBlank()) {
                Text(
                    text = ogTitle,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.ink,
                    lineHeight = 28.sp,
                    letterSpacing = (-0.01).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Source card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SourceLogo(
                        siteUrl = discovery?.siteUrl ?: url,
                        sourceName = sourceName,
                        size = 52.dp,
                        shape = RoundedCornerShape(16.dp),
                        fontSize = 17.sp
                    )
                    Column {
                        Text(
                            text = sourceName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = discovery?.siteUrl ?: url,
                            fontSize = 12.sp,
                            color = colors.mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Footer buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onReadAndFollow() }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Read and follow $sourceName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.bg
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .border(1.dp, colors.ink, CircleShape)
                    .signalPressScale(0.96f) { onJustRead() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Just read",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
            }
        }
    }
}

@Composable
private fun LoadingArticleScreen(
    sourceName: String,
    host: String
) {
    val colors = SignalTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.ink)
                )
                Text(
                    text = "Opening $host",
                    fontSize = 13.sp,
                    color = colors.mute
                )
            }
            Text(
                text = sourceName,
                style = SignalTheme.typography.screenTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink,
                lineHeight = 38.sp,
                letterSpacing = (-0.02).sp
            )
        }
    }
}

// -------------------------------------------------------------
// Helper utility functions
// -------------------------------------------------------------

private fun extractUrlFromIntent(intent: Intent): String? {
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            val urlRegex = """https?://[^\s<>"{}|\\^`]+""".toRegex()
            urlRegex.find(text)?.value
        }
        Intent.ACTION_VIEW -> {
            val uri = intent.data ?: return null
            val scheme = uri.scheme?.lowercase()
            val uriStr = uri.toString()
            when {
                scheme == "feed" || scheme == "feeds" -> {
                    when {
                        uriStr.startsWith("feed:https://", ignoreCase = true) -> uriStr.substring(5)
                        uriStr.startsWith("feed:http://", ignoreCase = true) -> uriStr.substring(5)
                        uriStr.startsWith("feed://", ignoreCase = true) -> "https://" + uriStr.substring(7)
                        uriStr.startsWith("feeds:https://", ignoreCase = true) -> uriStr.substring(6)
                        uriStr.startsWith("feeds:http://", ignoreCase = true) -> uriStr.substring(6)
                        uriStr.startsWith("feeds://", ignoreCase = true) -> "https://" + uriStr.substring(8)
                        else -> uriStr.replaceFirst("^(feed|feeds):/*".toRegex(RegexOption.IGNORE_CASE), "https://")
                    }
                }
                scheme == "http" || scheme == "https" -> uriStr
                else -> null
            }
        }
        else -> null
    }
}

private fun normalizeUrl(rawUrl: String): String {
    return try {
        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase() ?: return rawUrl
        val path = uri.path ?: ""

        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "ref", "source", "spm", "_hsenc", "_hsmi", "mc_cid", "mc_eid", "igshid"
        )

        val newQuery = uri.queryParameterNames
            ?.filter { it.lowercase() !in trackingParams }
            ?.joinToString("&") { key ->
                val values = uri.getQueryParameters(key)
                values.joinToString("&") { v -> "$key=${Uri.encode(v)}" }
            }

        val builder = Uri.Builder()
            .scheme(scheme)
            .encodedAuthority(uri.encodedAuthority)
            .encodedPath(path)

        if (!newQuery.isNullOrBlank()) {
            builder.encodedQuery(newQuery)
        }
        if (!uri.fragment.isNullOrBlank()) {
            builder.encodedFragment(uri.encodedFragment)
        }
        builder.build().toString()
    } catch (_: Exception) {
        rawUrl
    }
}

private fun isFeedUrl(url: String, mimeType: String?, scheme: String?): Boolean {
    if (scheme == "feed" || scheme == "feeds") return true
    if (mimeType in listOf("application/rss+xml", "application/atom+xml", "text/xml")) return true
    val path = try { Uri.parse(url).path?.lowercase() ?: "" } catch (_: Exception) { "" }
    return path.endsWith("/feed") || path.endsWith("/feed.xml") || path.endsWith("/rss") ||
           path.endsWith("/rss.xml") || path.endsWith(".atom") || path.endsWith(".rss")
}

private fun isNonArticleUrl(url: String): Boolean {
    val uri = try { Uri.parse(url) } catch (_: Exception) { return true }
    val host = uri.host?.lowercase() ?: return true
    val path = uri.path?.trimEnd('/') ?: ""

    if (path.isEmpty() || path == "/") return true

    val videoDomains = listOf("youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com", "twitch.tv")
    if (videoDomains.any { host == it || host.endsWith(".$it") }) return true

    val socialDomains = listOf("twitter.com", "x.com", "instagram.com", "reddit.com", "threads.net", "bsky.app", "facebook.com", "linkedin.com")
    if (socialDomains.any { host == it || host.endsWith(".$it") }) return true

    return false
}

private fun extractRegistrableDomain(urlOrHost: String): String {
    val host = try {
        if (urlOrHost.contains("://")) Uri.parse(urlOrHost).host ?: urlOrHost else urlOrHost
    } catch (_: Exception) {
        urlOrHost
    }.removePrefix("www.").lowercase()

    val parts = host.split(".")
    return if (parts.size >= 2) {
        if (parts.size >= 3 && parts[parts.size - 2] in listOf("co", "com", "org", "gov", "net", "edu")) {
            "${parts[parts.size - 3]}.${parts[parts.size - 2]}.${parts.last()}"
        } else {
            "${parts[parts.size - 2]}.${parts.last()}"
        }
    } else {
        host
    }
}

private suspend fun fetchPageMeta(url: String): Pair<String?, String?>? = withContext(Dispatchers.IO) {
    try {
        val fetcher = FeedFetcher()
        val (body, _) = fetcher.fetch(url)
        val doc = Jsoup.parse(body, url)
        val ogTitle = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
        val ogImage = doc.select("meta[property=og:image]").attr("content")
        Pair(ogTitle.ifBlank { null }, ogImage.ifBlank { null })
    } catch (_: Exception) {
        null
    }
}
