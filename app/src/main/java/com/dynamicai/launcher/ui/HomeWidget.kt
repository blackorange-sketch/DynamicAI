package com.dynamicai.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeWidget(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("uk", "UA"))
    }

    val dateText = now.format(dateFmt).replaceFirstChar { it.uppercase() }

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            now.format(timeFmt),
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(2.dp))
        Text(
            dateText,
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}
