package com.dynamicai.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlphabetSidebar(
    letters: List<Char>,
    onLetterClick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { onLetterClick(letter) }
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}
