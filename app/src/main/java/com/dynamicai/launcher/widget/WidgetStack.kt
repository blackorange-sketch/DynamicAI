package com.dynamicai.launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.ui.theme.AppTheme

/** Стек віджетів: кілька віджетів в одному місці, гортаються свайпом убік, внизу крапки. */
@Composable
fun WidgetStack(members: List<PlacedWidget>, content: @Composable (PlacedWidget) -> Unit) {
    val state = rememberPagerState(pageCount = { members.size })
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = state,
            verticalAlignment = Alignment.Top,
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) { i -> content(members[i]) }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in members.indices) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == state.currentPage) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == state.currentPage) AppTheme.colors.accent
                            else AppTheme.colors.textPrimary.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}
