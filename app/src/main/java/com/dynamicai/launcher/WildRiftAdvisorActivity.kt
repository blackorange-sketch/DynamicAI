package com.dynamicai.launcher

import com.dynamicai.launcher.ui.theme.AppTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.Champion
import com.dynamicai.launcher.data.WildRiftRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WildRiftAdvisorActivity : ComponentActivity() {
    private val vm: AppListViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Перевіряємо та завантажуємо ресурси Wild Rift
        lifecycleScope.launch {
            if (!com.dynamicai.launcher.data.AssetDownloader.downloadWildRiftAssets(this@WildRiftAdvisorActivity)) {
                android.widget.Toast.makeText(this@WildRiftAdvisorActivity, "Помилка завантаження ресурсів Wild Rift", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        enableEdgeToEdge()
        setContent {
            com.dynamicai.launcher.ui.theme.StoredAppTheme {
                WildRiftAdvisorScreen(vm, onClose = { finish() })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WildRiftAdvisorScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { WildRiftRepository(ctx) }
    val champions = remember { repo.getChampions() }
    
    var myChampion by remember { mutableStateOf<Champion?>(null) }
    var enemies by remember { mutableStateOf<List<Champion>>(emptyList()) }
    var showMyChampionPicker by remember { mutableStateOf(false) }
    var showEnemyPicker by remember { mutableStateOf(false) }
    var advice by remember { mutableStateOf("") }
    var recommendedItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.copy(alpha = 0.95f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBack(onClose)
            Spacer(Modifier.width(8.dp))
            Text("Wild Rift — поради", color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Мій чемпіон
        Text("Твій чемпіон", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppTheme.colors.surface)
                .border(1.dp, AppTheme.colors.glassBorder, RoundedCornerShape(14.dp))
                .clickable { showMyChampionPicker = true }
                .padding(16.dp)
        ) {
            Text(
                myChampion?.let { "${repo.localizedChampionName(it)} — ${it.displayRoles}" } ?: "Обрати…",
                color = if (myChampion != null) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                fontSize = 15.sp
            )
        }
        
        // Вороги
        Text("Ворожа команда (${enemies.size})", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
        enemies.forEach { enemy ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(enemy.id, color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(enemy.displayRoles, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("✕", color = AppTheme.colors.danger, fontSize = 16.sp,
                    modifier = Modifier.clickable { enemies = enemies - enemy })
            }
        }
        
        if (enemies.size < 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.success.copy(alpha = 0.13f))
                    .border(1.dp, AppTheme.colors.success.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { showEnemyPicker = true }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Додати ворога", color = AppTheme.colors.success, fontSize = 14.sp)
            }
        }
        
        // Кнопка "Отримати поради"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (myChampion != null && enemies.isNotEmpty())
                        AppTheme.colors.accentDark
                    else AppTheme.colors.textMuted
                )
                .clickable(enabled = myChampion != null && enemies.isNotEmpty() && !isLoading) {
                    isLoading = true
                    advice = ""
                    recommendedItems = emptyList()
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            advice = vm.chatEngine.getWildRiftAdvice(
                                myChampion!!.id,
                                enemies.map { it.id }
                            )
                            recommendedItems = repo.getRecommendedItems(enemies)
                        } catch (t: Throwable) {
                            advice = "Помилка: ${t.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = AppTheme.colors.textPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Отримати поради", color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Результат
        if (recommendedItems.isNotEmpty()) {
            Text("Рекомендовані предмети", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recommendedItems) { itemId ->
                    ItemIcon(itemId = itemId, size = 56.dp)
                }
            }
        }

        if (advice.isNotBlank()) {
            Text("Поради", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppTheme.colors.background)
                    .padding(16.dp)
            ) {
                Text(advice, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
            }
        }
    }
    
    // My champion picker
    if (showMyChampionPicker) {
        ChampionPickerDialog(
            title = "Твій чемпіон",
            champions = champions,
            repo = repo,
            onPick = { myChampion = it; showMyChampionPicker = false },
            onDismiss = { showMyChampionPicker = false }
        )
    }
    
    // Enemy picker
    if (showEnemyPicker) {
        ChampionPickerDialog(
            title = "Додати ворога",
            champions = champions.filter { it !in enemies },
            repo = repo,
            onPick = { if (enemies.size < 5) enemies = enemies + it; showEnemyPicker = false },
            onDismiss = { showEnemyPicker = false }
        )
    }
}

@Composable
private fun IconButtonBack(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp)
    }
}

@Composable
private fun ChampionPickerDialog(
    title: String,
    champions: List<Champion>,
    repo: WildRiftRepository,
    onPick: (Champion) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, champions) {
        if (query.isBlank()) champions
        else champions.filter { it.id.contains(query, ignoreCase = true) || repo.localizedChampionName(it).contains(query, ignoreCase = true) }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = AppTheme.colors.textPrimary) },
        text = {
            Column {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AppTheme.colors.accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Пошук…", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filtered, key = { it.id }) { champ ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(champ) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(repo.localizedChampionName(champ), color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(champ.displayRoles, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрити", color = AppTheme.colors.accent) }
        },
        containerColor = AppTheme.colors.surface
    )
}

@Composable
fun ItemIcon(itemId: String, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val bitmap = remember(itemId) {
        try {
            val extensions = listOf("webp", "png", "jpg")
            for (ext in extensions) {
                try {
                    ctx.assets.open("wildrift/item_icons/$itemId.$ext").use { stream ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                        if (bmp != null) return@remember bmp.asImageBitmap()
                    }
                } catch (_: Exception) { }
            }
            null
        } catch (_: Exception) { null }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(AppTheme.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text("📦", fontSize = (size.value * 0.4f).sp)
        }
    }
}
