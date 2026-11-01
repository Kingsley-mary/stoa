package com.signal.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.ui.theme.SignalTheme

@Composable
fun SignalHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMarkAllAsReadClick: () -> Unit,
    onLogoClick: () -> Unit = {},
    onJumpToReadClick: (() -> Unit)? = null
) {
    val colors = SignalTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Menu Button (3 horizontal bars)
            Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.width(22.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(colors.ink)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(colors.ink)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(colors.ink)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(colors.bg)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Refresh feeds",
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onLogoClick()
                        }
                    )
                    HorizontalDivider(color = colors.line)
                    if (onJumpToReadClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Jump to earlier read",
                                    color = colors.ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onJumpToReadClick()
                            }
                        )
                        HorizontalDivider(color = colors.line)
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Mark all as read",
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMarkAllAsReadClick()
                        }
                    )
                    HorizontalDivider(color = colors.line)
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Settings",
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSettingsClick()
                        }
                    )
                }
            }

            // Wordmark Logo
            Text(
                text = "Stoa",
                style = SignalTheme.typography.wordmark,
                color = colors.ink,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onLogoClick() }
            )

            // Search Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSearchClick() },
                contentAlignment = Alignment.Center
            ) {
                // Magnifying glass icon in ink
                SearchGlassIcon(color = colors.ink)
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
}

@Composable
fun SearchGlassIcon(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
        val radius = 5.5f * density
        val stroke = 1.5f * density
        drawCircle(
            color = color,
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(7f * density, 7f * density),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(11f * density, 11f * density),
            end = androidx.compose.ui.geometry.Offset(15.5f * density, 15.5f * density),
            strokeWidth = stroke
        )
    }
}
