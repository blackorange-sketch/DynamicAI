package com.dynamicai.launcher.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.WeatherState
import com.dynamicai.launcher.note.Note
import com.dynamicai.launcher.note.NotesStorage
import com.dynamicai.launcher.ui.HomeWidget
import com.dynamicai.launcher.ui.NotesWidget

data class WidgetType(
    val id: String,
    val label: String,
    val icon: String
)

object WidgetRegistry {

    val available = listOf(
        WidgetType("weather_clock", "Погода та годинник", "🌤️"),
        WidgetType("notes", "Нотатки", "📝")
        // майбутні: "calendar", "battery", "quick_actions"
    )

    fun labelFor(type: String): String =
        available.find { it.id == type }?.label ?: type

    fun iconFor(type: String): String =
        available.find { it.id == type }?.icon ?: "📦"
}

@Composable
fun RenderPlacedWidget(
    widget: PlacedWidget,
    weather: WeatherState,
    showWeather: Boolean,
    notes: List<Note>,
    notesStorage: NotesStorage,
    onWeatherTap: () -> Unit,
    onSettingsLongPress: () -> Unit,
    onNotesToggle: (String) -> Unit,
    onNotesDelete: (String) -> Unit,
    onNotesAdd: (String) -> Unit,
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (widget.type) {
        "weather_clock" -> {
            HomeWidget(
                modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
                onSettingsLongPress = onSettingsLongPress,
                weather = weather,
                showWeather = showWeather,
                onWeatherTap = onWeatherTap
            )
        }
        "notes" -> {
            NotesWidget(
                notes = notes,
                onToggle = onNotesToggle,
                onDelete = onNotesDelete,
                onAdd = onNotesAdd,
                onOpenAll = onOpenAllNotes,
                modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}
