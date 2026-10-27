package com.signal.reader

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.signal.reader.ui.components.SignalBottomBar
import com.signal.reader.ui.components.SignalTab
import com.signal.reader.ui.home.HomeScreen
import com.signal.reader.ui.reader.ReaderScreen
import com.signal.reader.ui.saved.SavedScreen
import com.signal.reader.ui.search.SearchScreen
import com.signal.reader.ui.settings.SettingsScreen
import com.signal.reader.ui.sources.AddSourcesScreen
import com.signal.reader.ui.sources.SourcePageScreen
import com.signal.reader.ui.sources.SourcesScreen
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SignalApplication

        setContent {
            val settings by app.settingsManager.settingsFlow.collectAsState()

            SignalTheme(themePreference = settings.theme) {
                SignalAppNav(
                    app = app,
                    isFirstLaunch = !settings.isFirstLaunchCompleted,
                    isCompactLayout = settings.homeLayout == "compact"
                )
            }
        }
    }
}

@Composable
fun SignalAppNav(
    app: SignalApplication,
    isFirstLaunch: Boolean,
    isCompactLayout: Boolean
) {
    val navController = rememberNavController()
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

    val startDestination = if (isFirstLaunch) "first_launch" else "home"

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                SignalBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
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
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            @OptIn(ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize(),
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
                    // First Launch Add Sources Screen
                    composable("first_launch") {
                        AddSourcesScreen(
                            isFirstLaunch = true,
                            repository = app.repository,
                            onFinish = {
                                app.settingsManager.setFirstLaunchCompleted(true)
                                navController.navigate("home") {
                                    popUpTo("first_launch") { inclusive = true }
                                }
                            },
                            onSkip = {
                                app.settingsManager.setFirstLaunchCompleted(true)
                                navController.navigate("home") {
                                    popUpTo("first_launch") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Home Screen
                    composable("home") {
                        HomeScreen(
                            repository = app.repository,
                            isCompactLayout = isCompactLayout,
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
                                navController.navigate("add_sources")
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // Saved Screen
                    composable("saved") {
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
                                navController.navigate("add_sources")
                            }
                        )
                    }

                    // Source Page Screen
                    composable(
                        route = "source_page/{sourceId}",
                        arguments = listOf(navArgument("sourceId") { type = NavType.LongType })
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
                            }
                        )
                    }

                    // Add Sources Screen (from Sources tab or Empty states)
                    composable("add_sources") {
                        AddSourcesScreen(
                            isFirstLaunch = false,
                            repository = app.repository,
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
                            onBack = {
                                navController.popBackStack()
                            },
                            onOpenSourcePage = { sourceId ->
                                val popped = navController.popBackStack("source_page/$sourceId", inclusive = false)
                                if (!popped) {
                                    navController.navigate("source_page/$sourceId")
                                }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // Search Screen
                    composable("search") {
                        SearchScreen(
                            repository = app.repository,
                            settingsManager = app.settingsManager,
                            onArticleClick = { articleId ->
                                navController.navigate("reader/$articleId")
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
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
