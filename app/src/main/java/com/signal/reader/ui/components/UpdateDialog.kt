package com.signal.reader.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.signal.reader.data.update.UpdateManager
import com.signal.reader.data.update.UpdateState
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTactile
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPillButton
import com.signal.reader.ui.theme.signalTactileClick
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    updateManager: UpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.ReadyToInstall) {
            updateManager.installApk(context, updateState.apkFile, updateState.apkUrl)
        }
    }

    val isVisible = updateState is UpdateState.Available ||
            updateState is UpdateState.Downloading ||
            updateState is UpdateState.ReadyToInstall ||
            updateState is UpdateState.Error

    if (!isVisible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = updateState !is UpdateState.Downloading,
            dismissOnClickOutside = updateState !is UpdateState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Eyebrow label
                Text(
                    text = "SOFTWARE UPDATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colors.mute
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (updateState) {
                    is UpdateState.Available -> {
                        Text(
                            text = "Stoa v${updateState.versionName}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Changelog container with rich Markdown formatting
                        if (updateState.changelog.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.bg)
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownContent(
                                    markdown = updateState.changelog,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Later button (hairline pill)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .signalPillButton(
                                        backgroundColor = colors.card,
                                        borderColor = colors.line,
                                        onClick = onDismiss
                                    )
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Later",
                                    color = colors.mute,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Update Now button (solid ink pill)
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .signalPillButton(
                                        backgroundColor = colors.ink,
                                        borderColor = colors.ink,
                                        onClick = {
                                            updateManager.startDownloadAndInstall(updateState)
                                        }
                                    )
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Update now",
                                    color = colors.bg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (updateState.apkUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .signalTactileClick(
                                        targetScale = 0.98f,
                                        onClick = {
                                            try {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateState.apkUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(browserIntent)
                                                onDismiss()
                                            } catch (_: Exception) {}
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Or download direct APK in browser",
                                    color = colors.mute,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "Downloading (${updateState.progressPercent}%)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = colors.ink,
                            trackColor = colors.line,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val mbDownloaded = updateState.downloadedBytes / (1024f * 1024f)
                        val mbTotal = updateState.totalBytes / (1024f * 1024f)
                        Text(
                            text = if (updateState.totalBytes > 0) {
                                String.format("%.1f MB / %.1f MB", mbDownloaded, mbTotal)
                            } else "Downloading...",
                            fontSize = 12.sp,
                            color = colors.mute
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "Ready to Install",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "The update is ready. Tap install to finish updating Stoa.",
                            fontSize = 13.sp,
                            color = colors.mute
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(colors.ink)
                                    .signalTactileClick(
                                        targetScale = SignalTactile.ButtonPressScale,
                                        onClick = {
                                            updateManager.installApk(context, updateState.apkFile, updateState.apkUrl)
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Install Update",
                                    color = colors.bg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (updateState.apkUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape)
                                        .border(1.dp, colors.line, CircleShape)
                                        .signalTactileClick(
                                            targetScale = SignalTactile.ButtonPressScale,
                                            onClick = {
                                                try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateState.apkUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(browserIntent)
                                                    onDismiss()
                                                } catch (_: Exception) {}
                                            }
                                        )
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Download via browser instead",
                                        color = colors.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Text(
                            text = "Update Notice",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = updateState.message,
                            fontSize = 13.sp,
                            color = colors.mute
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (updateState.apkUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape)
                                        .background(colors.ink)
                                        .signalTactileClick(
                                            targetScale = SignalTactile.ButtonPressScale,
                                            onClick = {
                                                try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateState.apkUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(browserIntent)
                                                    onDismiss()
                                                } catch (_: Exception) {}
                                            }
                                        )
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Download via browser",
                                        color = colors.bg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .border(1.dp, colors.line, CircleShape)
                                    .signalTactileClick(
                                        targetScale = SignalTactile.ButtonPressScale,
                                        onClick = onDismiss
                                    )
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Dismiss",
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
