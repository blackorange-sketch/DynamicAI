package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiquidSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassDark(cornerRadius = 28.dp)
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(AppTheme.colors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "Пошук або команда…",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (query.isNotEmpty()) {
                GlassIconButton(
                    icon = Icons.Default.Send,
                    tint = AppTheme.colors.accent,
                    onClick = onSearch
                )
            }
            GlassIconButton(
                icon = micIcon(),
                tint = AppTheme.colors.danger,
                onClick = onVoice
            )
            GlassIconButton(
                icon = Icons.Default.Close,
                tint = AppTheme.colors.textPrimary,
                onClick = onClose
            )
        }
    }
}


@Composable
private fun micIcon(): ImageVector = ImageVector.Builder(
    name = "Mic",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(AppTheme.colors.textPrimary)) {
        moveTo(12f, 14f)
        curveTo(14.21f, 14f, 16f, 12.21f, 16f, 10f)
        verticalLineTo(4f)
        curveTo(16f, 1.79f, 14.21f, 0f, 12f, 0f)
        curveTo(9.79f, 0f, 8f, 1.79f, 8f, 4f)
        verticalLineTo(10f)
        curveTo(8f, 12.21f, 9.79f, 14f, 12f, 14f)
        close()
        moveTo(19f, 10f)
        curveTo(19f, 13.53f, 16.39f, 16.44f, 13f, 16.93f)
        verticalLineTo(20f)
        horizontalLineTo(10f)
        verticalLineTo(16.93f)
        curveTo(7.61f, 16.44f, 5f, 13.53f, 5f, 10f)
        horizontalLineTo(7f)
        curveTo(7f, 12.76f, 9.24f, 15f, 12f, 15f)
        curveTo(14.76f, 15f, 17f, 12.76f, 17f, 10f)
        horizontalLineTo(19f)
        close()
    }
}.build()

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.18f),
                        AppTheme.colors.glassBorder.copy(alpha = 0.4f)
                    )
                )
            )
            .pointerInput(icon) {
                detectTapGestures { onClick() }
            }
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
