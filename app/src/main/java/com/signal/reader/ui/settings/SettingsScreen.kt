package com.signal.reader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.opml.OpmlManager
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.data.sync.RefreshWorker
import com.signal.reader.data.update.UpdateManager
import com.signal.reader.data.update.UpdateState
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.SourceLogo
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.WhatsNewDialog
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPillButton
import com.signal.reader.ui.theme.signalPressScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    repository: FeedRepository,
    updateManager: UpdateManager,
    onBack: () -> Unit,
    onOpenWidgetSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsManager.settingsFlow.collectAsState()

    val fontSizes = listOf(16.sp, 18.sp, 20.sp, 23.sp)
    var showOpenLinksDialog by remember { mutableStateOf(false) }
    var showPublicationAlertsDialog by remember { mutableStateOf(false) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    val notifManager = remember { (context.applicationContext as com.signal.reader.SignalApplication).notificationManager }
    val followedSources by repository.getFollowedSourcesFlow().collectAsState(initial = emptyList())
    var notificationsGranted by remember {
        mutableStateOf(notifManager.areNotificationsGloballyEnabled())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = notifManager.areNotificationsGloballyEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationsGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "Publication alerts enabled", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher for OPML export
    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val dbSources = com.signal.reader.SignalApplication.instance.database.sourceDao().getAllSources()
                    val opmlString = OpmlManager.exportToOpml(dbSources)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(opmlString.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Exported sources to OPML", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Activity result launcher for OPML import
    val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val outlines = OpmlManager.parseOpml(stream)
                        var count = 0
                        for (outline in outlines) {
                            if (outline.xmlUrl.isNotBlank()) {
                                repository.addSource(
                                    name = outline.title,
                                    siteUrl = outline.htmlUrl ?: outline.xmlUrl,
                                    feedUrl = outline.xmlUrl
                                )
                                count++
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Imported $count sources from OPML", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .signalPressScale(0.96f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                BackChevron(color = colors.ink)
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                StaggeredItem(index = 0) {
                    Text(
                        text = "SETTINGS",
                        style = SignalTheme.typography.screenTitle,
                        color = colors.ink,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp)
                    )
                }
            }

            // Appearance Section
            item {
                StaggeredItem(index = 1) {
                    Column {
                        SettingsSectionHeader("Appearance")

                        // Theme Segmented Control
                        SettingsRow(label = "Theme") {
                            SlidingSegmentedControl(
                                options = listOf("Auto", "Light", "Dark"),
                                selectedIndex = when (settings.theme) {
                                    "light" -> 1
                                    "dark" -> 2
                                    else -> 0
                                },
                                onSelect = { idx ->
                                    haptics.tick()
                                    val newTheme = when (idx) {
                                        1 -> "light"
                                        2 -> "dark"
                                        else -> "auto"
                                    }
                                    settingsManager.updateTheme(newTheme)
                                }
                            )
                        }

                        // Text Size Step Control
                        val currentSizeIndex = settings.textSizeIndex.coerceIn(0, fontSizes.size - 1)
                        SettingsRow(
                            label = "Text size",
                            sublabel = "For reading articles"
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StepButton(
                                    text = "A−",
                                    enabled = currentSizeIndex > 0,
                                    onClick = {
                                        haptics.tick()
                                        if (currentSizeIndex > 0) {
                                            settingsManager.updateTextSizeIndex(currentSizeIndex - 1)
                                        }
                                    }
                                )
                                StepButton(
                                    text = "A+",
                                    enabled = currentSizeIndex < fontSizes.size - 1,
                                    onClick = {
                                        haptics.tick()
                                        if (currentSizeIndex < fontSizes.size - 1) {
                                            settingsManager.updateTextSizeIndex(currentSizeIndex + 1)
                                        }
                                    }
                                )
                            }
                        }

                        // Preview Text (.prev)
                        Text(
                            text = "Soft shadows, long exposures and no studio strobes. The results are darker, grainier and strangely calm.",
                            fontSize = fontSizes[currentSizeIndex],
                            lineHeight = (fontSizes[currentSizeIndex].value * 1.6f).sp,
                            color = colors.ink,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 22.dp)
                        )
                    }
                }
            }

            // Home Section
            item {
                StaggeredItem(index = 2) {
                    Column {
                        SettingsSectionHeader("Home")

                        SettingsRow(label = "Layout") {
                            SlidingSegmentedControl(
                                options = listOf("Cards", "Compact"),
                                selectedIndex = if (settings.homeLayout == "compact") 1 else 0,
                                onSelect = { idx ->
                                    haptics.tick()
                                    val newLayout = if (idx == 1) "compact" else "cards"
                                    settingsManager.updateHomeLayout(newLayout)
                                }
                            )
                        }
                    }
                }
            }

            // Widget Section
            item {
                StaggeredItem(index = 3) {
                    Column {
                        SettingsSectionHeader("Widget")

                        val styleLabel = when (settings.widgetBackground) {
                            "light" -> "Plain White"
                            "dark" -> "Plain Black"
                            "system" -> "Match System (OS)"
                            "article_image" -> "Article Hero Photo"
                            else -> "Warm Paper"
                        }
                        SettingsActionRow(
                            label = "Home screen widget",
                            sublabel = "Style: $styleLabel · Tap for live preview & customization",
                            onClick = {
                                onOpenWidgetSettings?.invoke()
                            }
                        )
                    }
                }
            }

            // Refresh Section
            item {
                StaggeredItem(index = 4) {
                    Column {
                        SettingsSectionHeader("Refresh")

                        // Background check switch
                        val isBgEnabled = settings.backgroundRefresh
                        SettingsRow(
                            label = "Check in the background",
                            sublabel = "So new articles are ready when you open the app"
                        ) {
                            SignalSwitch(
                                checked = isBgEnabled,
                                onCheckedChange = { checked ->
                                    haptics.tick()
                                    settingsManager.updateBackgroundRefresh(checked)
                                    RefreshWorker.schedule(
                                        context = context,
                                        intervalMinutes = settings.refreshIntervalMinutes,
                                        wifiOnly = settings.wifiOnly,
                                        enabled = checked
                                    )
                                }
                            )
                        }

                        // Frequency (dimmed if bg disabled)
                        Box(modifier = Modifier.alpha(if (isBgEnabled) 1.0f else 0.4f)) {
                            SettingsRow(label = "How often") {
                                SlidingSegmentedControl(
                                    options = listOf("30 min", "1 hr", "3 hr"),
                                    selectedIndex = when (settings.refreshIntervalMinutes) {
                                        30L -> 0
                                        180L -> 2
                                        else -> 1
                                    },
                                    onSelect = { idx ->
                                        if (isBgEnabled) {
                                            haptics.tick()
                                            val minutes = when (idx) {
                                                0 -> 30L
                                                2 -> 180L
                                                else -> 60L
                                            }
                                            settingsManager.updateRefreshInterval(minutes)
                                            RefreshWorker.schedule(
                                                context = context,
                                                intervalMinutes = minutes,
                                                wifiOnly = settings.wifiOnly,
                                                enabled = true
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // Wi-Fi only (dimmed if bg disabled)
                        Box(modifier = Modifier.alpha(if (isBgEnabled) 1.0f else 0.4f)) {
                            SettingsRow(label = "Wi-Fi only") {
                                SignalSwitch(
                                    checked = settings.wifiOnly,
                                    onCheckedChange = { checked ->
                                        if (isBgEnabled) {
                                            haptics.tick()
                                            settingsManager.updateWifiOnly(checked)
                                            RefreshWorker.schedule(
                                                context = context,
                                                intervalMinutes = settings.refreshIntervalMinutes,
                                                wifiOnly = checked,
                                                enabled = true
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // Battery target note (.note)
                        Text(
                            text = "These are targets. Android decides the exact timing to save battery, so a check can run later than you set.",
                            color = colors.mute,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Notifications Section
            item {
                StaggeredItem(index = 5) {
                    Column {
                        SettingsSectionHeader("Notifications")

                        val enabledSourcesCount = followedSources.count { it.notificationsEnabled }
                        val totalFollowed = followedSources.size

                        if (!notificationsGranted) {
                            SettingsActionRow(
                                label = "Publication alerts",
                                sublabel = "Notifications are disabled on this device · Tap to enable",
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        try {
                                            context.startActivity(notifManager.createAppNotificationSettingsIntent())
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        } else {
                            SettingsActionRow(
                                label = "Manage publication alerts",
                                sublabel = if (totalFollowed == 0) "No publications followed" else "$enabledSourcesCount of $totalFollowed publications active",
                                onClick = {
                                    showPublicationAlertsDialog = true
                                }
                            )
                        }

                        SettingsActionRow(
                            label = "Android notification settings",
                            sublabel = "Configure sounds, badges, and publication channels",
                            onClick = {
                                try {
                                    context.startActivity(notifManager.createAppNotificationSettingsIntent())
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }

            // Your Sources Section
            item {
                StaggeredItem(index = 6) {
                    Column {
                        SettingsSectionHeader("Your sources")

                        SettingsActionRow(
                            label = "Import sources",
                            sublabel = "From an OPML file",
                            onClick = { importOpmlLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) }
                        )

                        SettingsActionRow(
                            label = "Export sources",
                            sublabel = "Save your list as an OPML file",
                            onClick = { exportOpmlLauncher.launch("stoa_sources.opml") }
                        )
                    }
                }
            }

            // Opening Links Section
            item {
                StaggeredItem(index = 7) {
                    Column {
                        SettingsSectionHeader("Opening links")

                        HorizontalDivider(color = colors.line, thickness = 1.dp)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "New sites",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "When tapping links from sites you don't follow",
                                fontSize = 13.sp,
                                color = colors.mute
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            SlidingSegmentedControl(
                                options = listOf("Follow & open", "Ask first", "Just open"),
                                selectedIndex = when (settings.newSitesBehavior) {
                                    "ask_first" -> 1
                                    "just_open" -> 2
                                    else -> 0
                                },
                                onSelect = { idx ->
                                    haptics.tick()
                                    val behavior = when (idx) {
                                        1 -> "ask_first"
                                        2 -> "just_open"
                                        else -> "follow_and_open"
                                    }
                                    settingsManager.updateNewSitesBehavior(behavior)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                equalWeights = true
                            )
                        }

                        SettingsActionRow(
                            label = "Open links from your sources in Stoa",
                            sublabel = "Set Android to open followed links automatically",
                            onClick = { showOpenLinksDialog = true }
                        )
                    }
                }
            }

            // About Section
            item {
                StaggeredItem(index = 8) {
                    Column {
                        SettingsSectionHeader("About")

                        SettingsActionRow(
                            label = "What’s new in Stoa",
                            sublabel = "Release notes for v1.4.12, v1.4.11 & v1.4.10",
                            onClick = { showWhatsNewDialog = true }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version",
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                                color = colors.ink
                            )
                            val versionName = remember {
                                try {
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4"
                                } catch (_: Exception) {
                                    "1.4"
                                }
                            }
                            Text(
                                text = "Stoa $versionName",
                                fontSize = 14.sp,
                                color = colors.mute
                            )
                        }

                        HorizontalDivider(color = colors.line, thickness = 1.dp)

                        // In-App Update Section
                        val updateState by updateManager.updateState.collectAsState()
                        var showEndpointDialog by remember { mutableStateOf(false) }
                        var endpointInput by remember { mutableStateOf(updateManager.updateManifestUrl) }

                        UpdateSection(
                            updateState = updateState,
                            onCheck = {
                                haptics.tick()
                                coroutineScope.launch {
                                    updateManager.checkForUpdates()
                                }
                            },
                            onDownloadAndInstall = { available ->
                                haptics.pop()
                                coroutineScope.launch {
                                    updateManager.downloadAndInstall(available)
                                }
                            },
                            onInstallApk = { apkFile ->
                                haptics.pop()
                                updateManager.installApk(context, apkFile)
                            },
                            onReset = {
                                updateManager.resetState()
                            },
                            onConfigure = {
                                endpointInput = updateManager.updateManifestUrl
                                showEndpointDialog = true
                            }
                        )

                        HorizontalDivider(color = colors.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(48.dp))

                        if (showEndpointDialog) {
                            AlertDialog(
                                onDismissRequest = { showEndpointDialog = false },
                                containerColor = colors.bg,
                                titleContentColor = colors.ink,
                                textContentColor = colors.mute,
                                shape = RoundedCornerShape(22.dp),
                                title = {
                                    Text(
                                        text = "Update Server URL",
                                        style = SignalTheme.typography.screenTitle,
                                        fontSize = 20.sp,
                                        color = colors.ink
                                    )
                                },
                                text = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Enter a GitHub Releases API or version.json URL to check for updates:",
                                            fontSize = 14.sp,
                                            color = colors.mute,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                                .background(colors.card)
                                                .padding(12.dp)
                                        ) {
                                            BasicTextField(
                                                value = endpointInput,
                                                onValueChange = { endpointInput = it },
                                                textStyle = TextStyle(
                                                    color = colors.ink,
                                                    fontSize = 13.sp,
                                                    fontFamily = SignalTheme.typography.wordmark.fontFamily
                                                ),
                                                cursorBrush = SolidColor(colors.ink),
                                                singleLine = false,
                                                maxLines = 4,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Text(
                                        text = "Save",
                                        color = colors.ink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (endpointInput.isNotBlank()) {
                                                    updateManager.updateManifestUrl = endpointInput.trim()
                                                }
                                                showEndpointDialog = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                },
                                dismissButton = {
                                    Text(
                                        text = "Cancel",
                                        color = colors.mute,
                                        fontSize = 15.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showEndpointDialog = false }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showOpenLinksDialog) {
            AlertDialog(
                onDismissRequest = { showOpenLinksDialog = false },
                containerColor = colors.bg,
                titleContentColor = colors.ink,
                textContentColor = colors.mute,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Text(
                        text = "Open links in Stoa",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                },
                text = {
                    Text(
                        text = "When you tap an article link in another app (like WhatsApp or email), Android can open it directly in Stoa instead of your web browser.\n\nIn Android Settings, toggle 'Open supported links' on.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = colors.mute
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .signalPillButton(
                                backgroundColor = colors.ink,
                                borderColor = colors.ink,
                                onClick = {
                                    showOpenLinksDialog = false
                                    try {
                                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        } else {
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Open Settings", color = colors.bg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .signalPillButton(
                                backgroundColor = colors.bg,
                                borderColor = colors.line,
                                onClick = { showOpenLinksDialog = false }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", color = colors.mute, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showPublicationAlertsDialog) {
            AlertDialog(
                onDismissRequest = { showPublicationAlertsDialog = false },
                containerColor = colors.bg,
                titleContentColor = colors.ink,
                textContentColor = colors.mute,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Text(
                        text = "Publication Alerts",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Choose which publications can notify you when new stories are published.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = colors.mute
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (followedSources.isEmpty()) {
                            Text(
                                text = "You are not following any publications yet.",
                                fontSize = 14.sp,
                                color = colors.mute,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            val activeCount = followedSources.count { it.notificationsEnabled }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$activeCount active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.mute
                                )
                                Text(
                                    text = if (activeCount > 0) "Mute all" else "Enable all",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.ink,
                                    modifier = Modifier
                                        .clickable {
                                            haptics.tick()
                                            val enableAll = activeCount == 0
                                            coroutineScope.launch {
                                                followedSources.forEach { s ->
                                                    repository.setSourceNotificationsEnabled(s.id, enableAll)
                                                }
                                            }
                                        }
                                        .padding(4.dp)
                                )
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                            ) {
                                items(followedSources.size) { index ->
                                    val source = followedSources[index]
                                    val isSystemChannelOn = notifManager.isChannelEnabledInSystem(source.id)
                                    HorizontalDivider(color = colors.line, thickness = 1.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    try {
                                                        context.startActivity(notifManager.createChannelSettingsIntent(source.id))
                                                    } catch (_: Exception) {}
                                                }
                                                .padding(end = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            SourceLogo(
                                                siteUrl = source.siteUrl,
                                                sourceName = source.name,
                                                size = 28.dp,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = source.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colors.ink,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = if (!isSystemChannelOn) {
                                                        "Muted in Android settings · Tap to unmute"
                                                    } else {
                                                        "Tap to configure sound & alert style"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = colors.mute,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        SignalSwitch(
                                            checked = source.notificationsEnabled,
                                            onCheckedChange = { checked ->
                                                haptics.tick()
                                                coroutineScope.launch {
                                                    repository.setSourceNotificationsEnabled(source.id, checked)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .signalPillButton(
                                backgroundColor = colors.ink,
                                borderColor = colors.ink,
                                onClick = {
                                    showPublicationAlertsDialog = false
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            color = colors.bg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }

        if (showWhatsNewDialog) {
            WhatsNewDialog(onDismiss = { showWhatsNewDialog = false })
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = SignalTheme.colors.mute,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsRow(
    label: String,
    sublabel: String? = null,
    control: @Composable () -> Unit
) {
    val colors = SignalTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        fontSize = 13.sp,
                        color = colors.mute,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            control()
        }
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    sublabel: String? = null,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .signalPressScale(0.985f)
            .clickable(onClick = onClick)
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        fontSize = 13.sp,
                        color = colors.mute,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Hairline chevron icon (.chev: 9x9, border-right: 1.5px solid, border-top: 1.5px solid, rotate 45deg)
            Canvas(modifier = Modifier.size(width = 8.dp, height = 12.dp)) {
                val strokeWidth = 1.5.dp.toPx()
                val path = Path().apply {
                    moveTo(1.dp.toPx(), 1.dp.toPx())
                    lineTo(size.width - 1.dp.toPx(), size.height / 2f)
                    lineTo(1.dp.toPx(), size.height - 1.dp.toPx())
                }
                drawPath(
                    path = path,
                    color = colors.ink,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Square,
                        join = StrokeJoin.Miter
                    )
                )
            }
        }
    }
}

/**
 * Segmented Control with a single sliding indicator pill (.seg .ind)
 * matching: transition: left .32s var(--ease), width .32s var(--ease)
 */
@Composable
private fun SlidingSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWeights: Boolean = false
) {
    val colors = SignalTheme.colors
    val density = LocalDensity.current

    val itemBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val activeBounds = itemBounds[selectedIndex] ?: Pair(0f, 0f)

    val animatedLeft by animateFloatAsState(
        targetValue = activeBounds.first,
        animationSpec = SignalMotion.easeTween(320),
        label = "segPillLeft"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = activeBounds.second,
        animationSpec = SignalMotion.easeTween(320),
        label = "segPillWidth"
    )

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(colors.bg)
    ) {
        // Sliding indicator (.seg .ind): top: 0, bottom: 0, background: var(--ink), border-radius: 999px
        if (activeBounds.second > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(animatedLeft.roundToInt(), 0) }
                    .width(with(density) { animatedWidth.toDp() })
                    .clip(CircleShape)
                    .background(colors.ink)
            )
        }

        Row(
            modifier = if (equalWeights) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val itemModifier = if (equalWeights) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                }
                Box(
                    modifier = itemModifier
                        .onGloballyPositioned { coords ->
                            val x = coords.positionInParent().x
                            val w = coords.size.width.toFloat()
                            itemBounds[index] = Pair(x, w)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .padding(horizontal = if (equalWeights) 6.dp else 15.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) colors.bg else colors.ink,
                        fontSize = if (equalWeights) 12.5.sp else 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // Crisp 1px container overlay border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, colors.ink, CircleShape)
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, if (enabled) colors.ink else colors.line, CircleShape)
            .signalPressScale(0.96f)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.ink else colors.mute,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Tactile Switch (.sw) matching CSS:
 * width: 48px, height: 28px, thumb: 20px, spring translate 20px
 */
@Composable
private fun SignalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = SignalTheme.colors

    val knobOffsetX by animateFloatAsState(
        targetValue = if (checked) 20f else 0f,
        animationSpec = SignalMotion.SpringSpec,
        label = "switchKnob"
    )

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) colors.ink else colors.bg)
            .border(1.dp, colors.ink, CircleShape)
            .signalPressScale(0.96f)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((knobOffsetX * density).roundToInt(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) colors.bg else colors.ink)
        )
    }
}

@Composable
private fun UpdateSection(
    updateState: UpdateState,
    onCheck: () -> Unit,
    onDownloadAndInstall: (UpdateState.Available) -> Unit,
    onInstallApk: (File) -> Unit,
    onReset: () -> Unit,
    onConfigure: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Updates",
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = colors.ink
                )
                Text(
                    text = when (updateState) {
                        is UpdateState.Idle -> "Check for new versions directly"
                        is UpdateState.Checking -> "Checking for updates…"
                        is UpdateState.UpToDate -> "You're on the latest version"
                        is UpdateState.Available -> "Version ${updateState.versionName} is ready to install"
                        is UpdateState.Downloading -> "Downloading update…"
                        is UpdateState.ReadyToInstall -> "Download complete. Ready to install."
                        is UpdateState.Error -> updateState.message
                    },
                    fontSize = 13.sp,
                    color = when (updateState) {
                        is UpdateState.Error -> colors.accent
                        is UpdateState.Available -> colors.ink
                        else -> colors.mute
                    },
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (updateState) {
                is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Error -> {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, colors.ink, CircleShape)
                            .signalPressScale(0.96f) { onCheck() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (updateState is UpdateState.Error) "Retry" else "Check now",
                            color = colors.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is UpdateState.Checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.ink,
                        strokeWidth = 2.dp
                    )
                }
                is UpdateState.Available -> {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.ink)
                            .signalPressScale(0.96f) { onDownloadAndInstall(updateState) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Update",
                            color = colors.bg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    Text(
                        text = "${updateState.progressPercent}%",
                        color = colors.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.ink)
                            .signalPressScale(0.96f) { onInstallApk(updateState.apkFile) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Install",
                            color = colors.bg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Expanded card for Available or Downloading state
        if (updateState is UpdateState.Available) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STOA ${updateState.versionName.uppercase()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "New release",
                            fontSize = 11.sp,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (updateState.changelog.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.changelog,
                            fontSize = 13.sp,
                            color = colors.mute,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(colors.ink)
                            .signalPressScale(0.96f) { onDownloadAndInstall(updateState) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Download & Install Update",
                            color = colors.bg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (updateState is UpdateState.Downloading) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { updateState.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = colors.ink,
                trackColor = colors.line
            )
            Spacer(modifier = Modifier.height(6.dp))
            val mbDownloaded = String.format(java.util.Locale.US, "%.1f", updateState.downloadedBytes / (1024f * 1024f))
            val mbTotal = String.format(java.util.Locale.US, "%.1f", updateState.totalBytes / (1024f * 1024f))
            Text(
                text = "$mbDownloaded MB / $mbTotal MB",
                fontSize = 12.sp,
                color = colors.mute
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Configure update server",
            fontSize = 12.sp,
            color = colors.mute,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { onConfigure() }
                .padding(vertical = 4.dp)
        )
    }
}
