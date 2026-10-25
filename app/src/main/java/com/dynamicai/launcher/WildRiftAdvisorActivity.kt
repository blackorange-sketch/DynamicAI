package com.dynamicai.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            WildRiftAdvisorScreen(vm, onClose = { finish() })
        }
    }
}

@Composable
fun WildRiftAdvisorScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { WildRiftRepository(ctx) }
    val champions = remember { repo.getChampions() }
    
    var myChampion by remember { mutableStateOf<Champion?>(null) }
    var enemies by remember { mutableStateOf<List<Champion>>(emptyList()) }
    var showMyChampionPicker by remember { mutableStateOf(false) }
    var showEnemyPicker by remember { mutableStateOf(false) }
    var advice by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBack(onClose)
            Spacer(Modifier.width(8.dp))
            Text("🎮 Wild Rift — поради", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Мій чемпіон
        Text("Твій чемпіон", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .clickable { showMyChampionPicker = true }
                .padding(16.dp)
        ) {
            Text(
                myChampion?.let { "${it.id} — ${it.displayRoles}" } ?: "Обрати…",
                color = if (myChampion != null) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = 15.sp
            )
        }
        
        // Вороги
        Text("Ворожа команда (${enemies.size})", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        enemies.forEach { enemy ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(enemy.id, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(enemy.displayRoles, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("✕", color = Color(0xFFFF8A80), fontSize = 16.sp,
                    modifier = Modifier.clickable { enemies = enemies - enemy })
            }
        }
        
        if (enemies.size < 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x224CAF50))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { showEnemyPicker = true }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Додати ворога", color = Color(0xFF4CAF50), fontSize = 14.sp)
            }
        }
        
        // Кнопка "Отримати поради"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (myChampion != null && enemies.isNotEmpty())
                        Color(0xFF2196F3)
                    else Color(0xFF37474F)
                )
                .clickable(enabled = myChampion != null && enemies.isNotEmpty() && !isLoading) {
                    isLoading = true
                    advice = ""
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            advice = vm.chatEngine.getWildRiftAdvice(
                                myChampion!!.id,
                                enemies.map { it.id }
                            )
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
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Отримати поради", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Результат
        if (advice.isNotBlank()) {
            Text("Поради", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D0D0D))
                    .padding(16.dp)
            ) {
                Text(advice, color = Color.White, fontSize = 14.sp)
            }
        }
    }
    
    // My champion picker
    if (showMyChampionPicker) {
        ChampionPickerDialog(
            title = "Твій чемпіон",
            champions = champions,
            onPick = { myChampion = it; showMyChampionPicker = false },
            onDismiss = { showMyChampionPicker = false }
        )
    }
    
    // Enemy picker
    if (showEnemyPicker) {
        ChampionPickerDialog(
            title = "Додати ворога",
            champions = champions.filter { it !in enemies },
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
        Text("←", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun ChampionPickerDialog(
    title: String,
    champions: List<Champion>,
    onPick: (Champion) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, champions) {
        if (query.isBlank()) champions
        else champions.filter { it.id.contains(query, ignoreCase = true) }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Пошук…", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
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
                            Text(champ.id, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(champ.displayRoles, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрити", color = Color(0xFF64B5F6)) }
        },
        containerColor = Color(0xFF1A1A1A)
    )
}
