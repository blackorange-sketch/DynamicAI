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
import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import kotlinx.coroutines.delay

/** Налаштування → Система: дозволи, живлення, дані, резервна копія, пилосос, авто. */
@Composable
internal fun ColumnScope.SettingsSystemSection(
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshContacts: () -> Unit
) {
    val ctx = LocalContext.current
        GlassSectionHeader("Доступ і дозволи")
        ShizukuTestRow()
        GlassCard {
            val ctx = LocalContext.current
                var hasWriteSettings by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= 23)
                        Settings.System.canWrite(ctx)
                    else true
                )
            }
            LaunchedEffect(Unit) {
                while (true) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        hasWriteSettings = Settings.System.canWrite(ctx)
                    }
                    delay(1000)
                }
            }
            SwitchRow(
                label = if (hasWriteSettings) "Зміна налаштувань ✓" else "Дозволити зміну (яскравість)",
                checked = hasWriteSettings,
                icon = Icons.Default.Settings,
                tint = if (hasWriteSettings) AppTheme.colors.success else AppTheme.colors.warning,
                onChange = {
                    if (!hasWriteSettings) {
                        try {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${ctx.packageName}")
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Throwable) {}
                    }
                }
            )
            Text(
                "Потрібно для команд 'яскравіше' / 'темніше'",
                color = AppTheme.colors.glassBorder, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        BluetoothTestRow()

        GlassSectionHeader("Живлення")
        GlassCard {
            SwitchRow("Пауза при вимкненому екрані", settings.wpPauseOnScreenOff, Icons.Default.Star, AppTheme.colors.textMuted) {
                onSettingsChange(settings.copy(wpPauseOnScreenOff = it))
            }
            SwitchRow("Пауза при низькому заряді", settings.wpPauseOnLowBattery, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(wpPauseOnLowBattery = it))
            }
            FpsRow(
                current = settings.wpFps,
                onChange = { onSettingsChange(settings.copy(wpFps = it)) }
            )
        }

        GlassSectionHeader("Дані та дії")
        GlassCard {
            ActionRow("Оновити контакти", Icons.Default.Person, AppTheme.colors.success, onRefreshContacts)
            ActionRow("Очистити історію команд", Icons.Default.Delete, AppTheme.colors.danger, onClearHistory)
        }
        GlassSectionHeader("Резервна копія")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                BackupSettingsBlock()
            }
        }

        GlassSectionHeader("Робот-пилосос")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                VacuumSettingsBlock(settings, onSettingsChange)
            }
        }
        GlassSectionHeader("Авто")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                CarModeSettingsBlock(settings, onSettingsChange)
            }
        }

        // ==== ШІ: асистент ====
}
