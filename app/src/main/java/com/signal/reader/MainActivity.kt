package com.signal.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.signal.reader.data.notification.StoaNotificationManager
import com.signal.reader.data.update.UpdateState
import com.signal.reader.ui.browser.BrowserScreen
import com.signal.reader.ui.components.SignalBottomBar
import com.signal.reader.ui.components.SignalTab
import com.signal.reader.ui.components.UpdateDialog
import com.signal.reader.ui.home.HomeScreen
import com.signal.reader.ui.onboarding.InterestsOnboardingScreen
import com.signal.reader.ui.reader.ReaderScreen
import com.signal.reader.ui.saved.SavedScreen
import com.signal.reader.ui.search.SearchScreen
import com.signal.reader.ui.settings.SettingsScreen
import com.signal.reader.ui.settings.WidgetSettingsScreen
import com.signal.reader.ui.sources.AddSourcesScreen
import com.signal.reader.ui.sources.SourcePageScreen
import com.signal.reader.ui.sources.SourcesScreen
import com.signal.reader.ui.splash.SplashScreen
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val navDestination = mutableStateOf<String?>(null)
    private val navArticleId = mutableStateOf<Long?>(null)
    private val navShareQuote = mutableStateOf<String?>(null)

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val notifArticleId = intent.getLongExtra(StoaNotificationManager.EXTRA_ARTICLE_ID, -1L).takeIf { it > 0 }
            ?: intent.getLongExtra("article_id", -1L).takeIf { it > 0 }
        val notifSourceId = intent.getLongExtra(StoaNotificationManager.EXTRA_SOURCE_ID, -1L).takeIf { it > 0 }

        if (notifArticleId != null) {
            navArticleId.value = notifArticleId
        } else if (notifSourceId != null) {
            navDestination.value = "source_page/$notifSourceId"
        } else if (intent.hasExtra("open_destination")) {
            navDestination.value = intent.getStringExtra("open_destination")
        }

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                navShareQuote.value = text
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SignalApplication
        handleIntent(intent)

        setContent {
            val settings by app.settingsManager.settingsFlow.collectAsState()

            SignalTheme(themePreference = settings.theme) {
                SignalAppNav(
                    app = app,
                    isFirstLaunch = !settings.isFirstLaunchCompleted,
                    isCompactLayout = settings.homeLayout == "compact",
                    initialArticleId = navArticleId.value,
                    initialDestination = navDestination.value,
                    initialShareQuote = navShareQuote.value,
                    onQuoteConsumed = { navShareQuote.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}

@Composable
fun SignalAppNav(
    app: SignalApplication,
    isFirstLaunch: Boolean,
    isCompactLayout: Boolean,
    initialArticleId: Long? = null,
    initialDestination: String? = null,
    initialShareQuote: String? = null,
    onQuoteConsumed: (() -> Unit)? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Tab bar is visible on Home, Saved, Sources, and Source Page (Sources stays active there per brief)
    val isBottomBarVisible = currentRoute in listOf(
        "home",
        "saved",
        "sources",
        "source_page/{sourceId}"
    )

    val currentTab = when (currentRoute) {
        "saved" -> SignalTab.SAVED
        "sources", "source_page/{sourceId}" -> SignalTab.SOURCES
        else -> SignalTab.HOME
    }

    val colors = SignalTheme.colors
    val startDestination = "splash"

    val context = LocalContext.current
    val app = context.applicationContext as com.signal.reader.SignalApplication
    val updateManager = remember { app.updateManager }
    val updateState by updateManager.updateState.collectAsState()
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var homeReselectTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        updateManager.checkForUpdates(isSilent = true)
    }

    LaunchedEffect(initialArticleId) {
        if (initialArticleId != null && initialArticleId > 0) {
            navController.navigate("reader/$initialArticleId")
        }
    }

    LaunchedEffect(initialDestination) {
        if (initialDestination == "widget_settings") {
            navController.navigate("widget_settings")
        }
    }

    fun openWebBrowser(url: String, title: String? = null) {
        if (url.isBlank()) return
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val encodedTitle = title?.let { java.net.URLEncoder.encode(it, "UTF-8") }
        val route = if (encodedTitle != null) {
            "browser?url=$encodedUrl&title=$encodedTitle"
        } else {
            "browser?url=$encodedUrl"
        }
        navController.navigate(route)
    }

    Scaffold(
        containerColor = colors.bg,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                SignalBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        if (tab == currentTab && tab == SignalTab.HOME) {
                            homeReselectTrigger++
                        } else {
                            val targetRoute = when (tab) {
                                SignalTab.HOME -> "home"
                                SignalTab.SAVED -> "saved"
                                SignalTab.SOURCES -> "sources"
                            }
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(bottom = if (isBottomBarVisible) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            @OptIn(ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bg),
                    enterTransition = {
                        fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration)) +
                        slideInVertically(
                            initialOffsetY = { 30 },
                            animationSpec = SignalMotion.easeTween(SignalMotion.ContentEnterDuration)
                        )
                    },
                    exitTransition = {
                        fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                        slideOutVertically(
                            targetOffsetY = { 30 },
                            animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                        )
                    },
                    popEnterTransition = {
                        fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration))
                    },
                    popExitTransition = {
                        fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                        slideOutVertically(
                            targetOffsetY = { 30 },
                            animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                        )
                    }
                ) {
                    // Editorial Starter Splash Screen
                    composable("splash") {
                        SplashScreen(
                            onFinished = {
                                val destination = if (isFirstLaunch) "first_launch" else "home"
                                navController.navigate(destination) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    // First Launch Interest-Based Onboarding Screen
                    composable("first_launch") {
                        InterestsOnboardingScreen(
                            repository = app.repository,
                            onFinish = { sources, pickedTopics ->
                                scope.launch {
                                    app.repository.addCatalogSourcesBatch(sources)
                                    app.settingsManager.updateUserInterests(pickedTopics)
                                    app.settingsManager.setFirstLaunchCompleted(true)
                                }
                                navController.navigate("home") {
                                    popUpTo("first_launch") { inclusive = true }
                                }
                            },
                            onPasteSiteInstead = {
                                navController.navigate("add_sources")
                            },
                            onSkip = {
                                scope.launch {
                                    app.settingsManager.setFirstLaunchCompleted(true)
                                }
                                navController.navigate("home") {
                                    popUpTo("first_launch") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Home Screen
                    composable(
                        route = "home",
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("reader") == true) {
                                fadeOut(tween(durationMillis = 200, delayMillis = 100))
                            } else {
                                fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                                slideOutVertically(
                                    targetOffsetY = { 30 },
                                    animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("reader") == true) {
                                fadeIn(SignalMotion.easeTween(250))
                            } else {
                                fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration))
                            }
                        }
                    ) {
                        HomeScreen(
                            repository = app.repository,
                            isCompactLayout = isCompactLayout,
                            homeReselectTrigger = homeReselectTrigger,
                            onArticleClick = { articleId ->
                                navController.navigate("reader/$articleId")
                            },
                            onSearchClick = {
                                navController.navigate("search")
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            },
                            onAddSourcesClick = {
                                val userInterests = app.settingsManager.settingsFlow.value.userInterests
                                if (userInterests.isEmpty()) {
                                    navController.navigate("first_launch")
                                } else {
                                    navController.navigate("add_sources")
                                }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // Saved Screen
                    composable(
                        route = "saved",
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("reader") == true) {
                                fadeOut(tween(durationMillis = 200, delayMillis = 100))
                            } else {
                                fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                                slideOutVertically(
                                    targetOffsetY = { 30 },
                                    animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("reader") == true) {
                                fadeIn(SignalMotion.easeTween(250))
                            } else {
                                fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration))
                            }
                        }
                    ) {
                        SavedScreen(
                            repository = app.repository,
                            onArticleClick = { articleId ->
                                navController.navigate("reader/$articleId")
                            }
                        )
                    }

                    // Sources Tab Screen
                    composable("sources") {
                        SourcesScreen(
                            repository = app.repository,
                            onSourceClick = { sourceId ->
                                navController.navigate("source_page/$sourceId")
                            },
                            onAddSourcesClick = {
                                val userInterests = app.settingsManager.settingsFlow.value.userInterests
                                if (userInterests.isEmpty()) {
                                    navController.navigate("first_launch")
                                } else {
                                    navController.navigate("add_sources")
                                }
                            }
                        )
                    }

                    // Source Page Screen
                    composable(
                        route = "source_page/{sourceId}",
                        arguments = listOf(navArgument("sourceId") { type = NavType.LongType }),
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("reader") == true) {
                                fadeOut(tween(durationMillis = 200, delayMillis = 100))
                            } else {
                                fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                                slideOutVertically(
                                    targetOffsetY = { 30 },
                                    animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("reader") == true) {
                                fadeIn(SignalMotion.easeTween(250))
                            } else {
                                fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration))
                            }
                        }
                    ) { backStackEntry ->
                        val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
                        SourcePageScreen(
                            sourceId = sourceId,
                            repository = app.repository,
                            onArticleClick = { articleId ->
                                navController.navigate("reader/$articleId")
                            },
                            onBack = {
                                navController.popBackStack()
                            },
                            onOpenWebsite = { url, title ->
                                openWebBrowser(url, title)
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // Add Sources Screen (from Sources tab or Empty states)
                    composable("add_sources") {
                        AddSourcesScreen(
                            isFirstLaunch = false,
                            repository = app.repository,
                            onOpenInterests = {
                                navController.navigate("first_launch")
                            },
                            onFinish = {
                                navController.popBackStack()
                            },
                            onSkip = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Article Reader Screen with shared 500ms ease-out entrance
                    composable(
                        route = "reader/{articleId}",
                        arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
                        enterTransition = {
                            fadeIn(SignalMotion.easeTween(SignalMotion.SharedElementDuration)) +
                            slideInVertically(
                                initialOffsetY = { 60 },
                                animationSpec = SignalMotion.easeTween(SignalMotion.SharedElementDuration)
                            )
                        },
                        exitTransition = {
                            fadeOut(SignalMotion.easeTween(250))
                        },
                        popExitTransition = {
                            fadeOut(SignalMotion.easeTween(250)) +
                            slideOutVertically(
                                targetOffsetY = { 60 },
                                animationSpec = SignalMotion.easeTween(250)
                            )
                        }
                    ) { backStackEntry ->
                        val articleId = backStackEntry.arguments?.getLong("articleId") ?: return@composable
                        ReaderScreen(
                            articleId = articleId,
                            repository = app.repository,
                            settingsManager = app.settingsManager,
                            initialShareQuote = initialShareQuote,
                            onQuoteShared = onQuoteConsumed,
                            onBack = {
                                navController.popBackStack()
                            },
                            onOpenArticle = { nextId ->
                                navController.navigate("reader/$nextId")
                            },
                            onOpenSourcePage = { sourceId ->
                                val popped = navController.popBackStack("source_page/$sourceId", inclusive = false)
                                if (!popped) {
                                    navController.navigate("source_page/$sourceId")
                                }
                            },
                            onOpenWebUrl = { url, title ->
                                openWebBrowser(url, title)
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // Search Screen
                    composable(
                        route = "search",
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("reader") == true) {
                                fadeOut(tween(durationMillis = 200, delayMillis = 100))
                            } else {
                                fadeOut(SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)) +
                                slideOutVertically(
                                    targetOffsetY = { 30 },
                                    animationSpec = SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("reader") == true) {
                                fadeIn(SignalMotion.easeTween(250))
                            } else {
                                fadeIn(SignalMotion.easeTween(SignalMotion.ContentEnterDuration))
                            }
                        }
                    ) {
                        SearchScreen(
                            repository = app.repository,
                            settingsManager = app.settingsManager,
                            onArticleClick = { articleId ->
                                navController.navigate("reader/$articleId")
                            },
                            onSourceClick = { sourceId ->
                                navController.navigate("source_page/$sourceId")
                            },
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Settings Screen
                    composable("settings") {
                        SettingsScreen(
                            settingsManager = app.settingsManager,
                            repository = app.repository,
                            updateManager = app.updateManager,
                            onBack = {
                                navController.popBackStack()
                            },
                            onOpenWidgetSettings = {
                                navController.navigate("widget_settings")
                            }
                        )
                    }

                    // Widget Settings Screen
                    composable("widget_settings") {
                        WidgetSettingsScreen(
                            settingsManager = app.settingsManager,
                            repository = app.repository,
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // In-App Browser Screen
                    composable(
                        route = "browser?url={url}&title={title}",
                        arguments = listOf(
                            navArgument("url") { type = NavType.StringType },
                            navArgument("title") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        ),
                        enterTransition = {
                            fadeIn(SignalMotion.easeTween(250)) +
                            slideInVertically(
                                initialOffsetY = { 60 },
                                animationSpec = SignalMotion.easeTween(250)
                            )
                        },
                        exitTransition = {
                            fadeOut(SignalMotion.easeTween(200))
                        },
                        popExitTransition = {
                            fadeOut(SignalMotion.easeTween(200)) +
                            slideOutVertically(
                                targetOffsetY = { 60 },
                                animationSpec = SignalMotion.easeTween(200)
                            )
                        }
                    ) { backStackEntry ->
                        val rawUrl = backStackEntry.arguments?.getString("url") ?: return@composable
                        val decodedUrl = try {
                            java.net.URLDecoder.decode(rawUrl, "UTF-8")
                        } catch (_: Exception) {
                            rawUrl
                        }
                        val rawTitle = backStackEntry.arguments?.getString("title")
                        val decodedTitle = rawTitle?.let {
                            try {
                                java.net.URLDecoder.decode(it, "UTF-8")
                            } catch (_: Exception) {
                                it
                            }
                        }
                        BrowserScreen(
                            url = decodedUrl,
                            titleHint = decodedTitle,
                            repository = app.repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Centered Animated Update Dialog (auto-detected on launch or via manual trigger)
        if (updateState is UpdateState.Available && (updateState as UpdateState.Available).versionName != dismissedUpdateVersion) {
            UpdateDialog(
                updateState = updateState,
                updateManager = updateManager,
                onDismiss = {
                    dismissedUpdateVersion = (updateState as UpdateState.Available).versionName
                }
            )
        } else if (updateState is UpdateState.Downloading || updateState is UpdateState.ReadyToInstall) {
            UpdateDialog(
                updateState = updateState,
                updateManager = updateManager,
                onDismiss = {
                    updateManager.resetState()
                }
            )
        }
    }
}
