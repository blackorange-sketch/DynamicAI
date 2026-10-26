package com.dynamicai.launcher.music

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            MusicWidgetContent()
        }
    }

    @Composable
    fun MusicWidgetContent() {
        // Стиль Liquid Glass: темний напівпрозорий фон
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xCC0A0A0A)) // Напівпрозорий чорний
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = MusicPlayerService.currentTrackName,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Кнопка Play/Pause
                Text(
                    text = if (MusicPlayerService.isPlaying) "⏸" else "▶️",
                    modifier = GlanceModifier
                        .padding(8.dp)
                        .clickable(actionRunCallback<TogglePlaybackAction>()),
                    style = TextStyle(fontSize = 24.sp, color = ColorProvider(Color.White))
                )
            }
        }
    }
    
    // Функція для оновлення віджета звідусіль
    companion object {
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(MusicWidget::class.java)
            ids.forEach { id ->
                MusicWidget().update(context, id)
            }
        }
    }
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        MusicPlayerService.instance?.togglePlayPause()
        // Віджет оновиться автоматично через слухач у сервісі
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
