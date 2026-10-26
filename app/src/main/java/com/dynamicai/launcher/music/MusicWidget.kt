package com.dynamicai.launcher.music

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MusicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            MusicWidgetContent()
        }
    }

    @Composable
    fun MusicWidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E1E2E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Зараз грає",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Натисніть, щоб відкрити плеєр",
                style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 10.sp)
            )
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
