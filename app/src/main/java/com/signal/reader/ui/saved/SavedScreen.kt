package com.signal.reader.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val savedArticles by repository.getSavedArticlesFlow().collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        StaggeredItem(index = 0) {
            Text(
                text = "SAVED",
                style = SignalTheme.typography.screenTitle,
                color = colors.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 18.dp)
            )
        }

        if (savedArticles.isEmpty()) {
            StaggeredItem(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(
                        text = "Nothing saved yet. Swipe a row to the right to save it.",
                        color = colors.mute,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(savedArticles, key = { _, item -> item.id }) { index, article ->
                    StaggeredItem(index = index + 1) {
                        ArticleRow(
                            article = article,
                            onClick = { onArticleClick(article.id) },
                            onToggleSave = {
                                coroutineScope.launch {
                                    repository.toggleSaved(article.id)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
