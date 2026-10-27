package com.signal.reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale

enum class SignalTab(val title: String) {
    HOME("Home"),
    SAVED("Saved"),
    SOURCES("Sources")
}

@Composable
fun SignalBottomBar(
    currentTab: SignalTab,
    onTabSelected: (SignalTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val tabs = SignalTab.entries
    val selectedIndex = tabs.indexOf(currentTab)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tabWidth = screenWidth / tabs.size

    // Mockup indicator: width is 32% of tab width, centered at left + 34%
    val indicatorWidth = tabWidth * 0.32f
    val targetOffsetX = (tabWidth * selectedIndex) + (tabWidth * 0.34f)

    val animatedOffsetX by animateFloatAsState(
        targetValue = with(LocalDensity.current) { targetOffsetX.toPx() },
        animationSpec = if (SignalTheme.reducedMotion) tween(0) else SignalMotion.easeTween(SignalMotion.TabBarDuration),
        label = "tabIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg)
            .navigationBarsPadding()
    ) {
        // Top divider with sliding indicator line (.tind)
        Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            HorizontalDivider(
                color = colors.line,
                thickness = 1.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Sliding active indicator (2px ink line)
            Box(
                modifier = Modifier
                    .offset(x = with(LocalDensity.current) { animatedOffsetX.toDp() })
                    .width(indicatorWidth)
                    .height(2.dp)
                    .background(colors.ink)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == currentTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .signalPressScale(0.94f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(top = 16.dp, bottom = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        color = if (isSelected) colors.ink else colors.mute,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
