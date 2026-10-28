package com.signal.reader.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.opml.OpmlManager
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.data.sync.RefreshWorker
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    repository: FeedRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsManager.settingsFlow.collectAsState()

    val fontSizes = listOf(16.sp, 18.sp, 20.sp, 23.sp)

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

                        SettingsRow(
                            label = "Background",
                            sublabel = "Home screen 4×2 hero widget"
                        ) {
                            SlidingSegmentedControl(
                                options = listOf("Paper", "White", "Dark", "Auto", "Image"),
                                selectedIndex = when (settings.widgetBackground) {
                                    "light" -> 1
                                    "dark" -> 2
                                    "system" -> 3
                                    "article_image" -> 4
                                    else -> 0
                                },
                                onSelect = { idx ->
                                    haptics.tick()
                                    val newBg = when (idx) {
                                        1 -> "light"
                                        2 -> "dark"
                                        3 -> "system"
                                        4 -> "article_image"
                                        else -> "paper"
                                    }
                                    settingsManager.updateWidgetBackground(newBg)
                                    com.signal.reader.widget.StoaWidgetProvider.updateAllWidgets(context)
                                }
                            )
                        }
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

            // Your Sources Section
            item {
                StaggeredItem(index = 4) {
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

            // About Section
            item {
                StaggeredItem(index = 5) {
                    Column {
                        SettingsSectionHeader("About")

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
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.3"
                                } catch (_: Exception) {
                                    "1.3"
                                }
                            }
                            Text(
                                text = versionName,
                                fontSize = 14.sp,
                                color = colors.mute
                            )
                        }

                        HorizontalDivider(color = colors.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
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
    onSelect: (Int) -> Unit
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
        modifier = Modifier
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
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val x = coords.positionInParent().x
                            val w = coords.size.width.toFloat()
                            itemBounds[index] = Pair(x, w)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) colors.bg else colors.ink,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        // Crisp 1px container outline overlay
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
