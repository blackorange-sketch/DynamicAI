package com.dynamicai.launcher.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.RowScope
import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.system.ShizukuManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.dynamicai.launcher.music.GoogleAuthManager
import androidx.compose.material.icons.filled.AccountCircle
import com.dynamicai.launcher.morning.WakeUpDetector
import com.dynamicai.launcher.morning.MorningBriefingService
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.model.AppInfo
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import com.dynamicai.launcher.suggestion.BrowserBridge

/** Налаштування → Інструменти: застосунки за замовчуванням, приховані, нотатки/макроси, віджети, погода. */
@Composable
internal fun ColumnScope.SettingsToolsSection(
    vm: com.dynamicai.launcher.AppListViewModel,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMacroManager: () -> Unit
) {
    val ctx = LocalContext.current
        var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        LaunchedEffect(Unit) { installedApps = AppRepository(ctx).loadCached() }
        val pagesPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) Toast.makeText(ctx, "Без дозволу закладки браузера не показуються", Toast.LENGTH_LONG).show()
        }

        GlassSectionHeader("Застосунки за замовчуванням")
        GlassCard {
            AppPickerRow("Музика", settings.defaultMusicApp, installedApps, Icons.Default.PlayArrow, Color(0xFF1DB954)) {
                onSettingsChange(settings.copy(defaultMusicApp = it))
            }
            AppPickerRow("Браузер", settings.defaultBrowser, installedApps, Icons.Default.Search, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(defaultBrowser = it))
            }
            SwitchRow("Підказки пошуку з інтернету", settings.webSuggest, Icons.Default.Search, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(webSuggest = it))
            }
            SwitchRow("Закладки й історія OperaFast", settings.browserSuggest, Icons.Default.Search, AppTheme.colors.accentDark) { on ->
                if (on) {
                    if (!BrowserBridge.installed(ctx)) {
                        Toast.makeText(ctx, "OperaFast не встановлено", Toast.LENGTH_LONG).show()
                    } else if (!BrowserBridge.hasAccess(ctx)) {
                        pagesPermLauncher.launch(BrowserBridge.PERM)
                    }
                }
                onSettingsChange(settings.copy(browserSuggest = on))
            }
            AppPickerRow("Телефон", settings.defaultDialer, installedApps, Icons.Default.Call, AppTheme.colors.success) {
                onSettingsChange(settings.copy(defaultDialer = it))
            }
            AppPickerRow("SMS", settings.defaultSms, installedApps, Icons.Default.Email, AppTheme.colors.purple) {
                onSettingsChange(settings.copy(defaultSms = it))
            }
            AppPickerRow("Email", settings.defaultEmail, installedApps, Icons.Default.Email, Color(0xFFF44336)) {
                onSettingsChange(settings.copy(defaultEmail = it))
            }
            AppPickerRow("Карти", settings.defaultMaps, installedApps, Icons.Default.Place, Color(0xFF00BCD4)) {
                onSettingsChange(settings.copy(defaultMaps = it))
            }
            AppPickerRow("Камера", settings.defaultCamera, installedApps, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(defaultCamera = it))
            }
        }

        HiddenAppsSection(vm)

        GlassSectionHeader("Нотатки та макроси")
        GlassCard {
            ActionRow("Керування нотатками", Icons.Default.Edit, AppTheme.colors.accent, onOpenNotes)
            ActionRow("Керування макросами", Icons.Default.PlayArrow, AppTheme.colors.warning, onOpenMacroManager)
        }

        GlassSectionHeader("Віджети і модулі")
        GlassCard {
            ActionRow("Менеджер модулів", Icons.Default.Build, AppTheme.colors.accent) {
                val intent = Intent(ctx, ModuleManagerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
        }

        GlassSectionHeader("Погода")
        GlassCard {
            SwitchRow("Показувати погоду", settings.showWeather, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(showWeather = it))
            }
            SwitchRow("Використовувати GPS", settings.weatherUseGps, Icons.Default.Place, AppTheme.colors.success) {
                onSettingsChange(settings.copy(weatherUseGps = it))
            }
            CityInputRow(
                cityName = settings.cityName,
                onCityChange = { onSettingsChange(settings.copy(cityName = it)) }
            )
            UnitRow(
                current = settings.weatherUnit,
                onChange = { onSettingsChange(settings.copy(weatherUnit = it)) }
            )
        }

        // ==== Фон лаунчера ====
}
