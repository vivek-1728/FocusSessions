package com.focussessions.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.R

@Composable
fun AppLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_app_logo),
        contentDescription = "App Logo",
        modifier = modifier.size(size)
    )
}

@Composable
fun AppLogoHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
    showBrandText: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppLogoIcon(size = iconSize)

        if (showBrandText) {
            Text(
                text = "time",
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF2979FF),
                            Color(0xFF8E24AA),
                            Color(0xFFA855F7)
                        )
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (iconSize.value * 0.75f).sp,
                    letterSpacing = (-1.5).sp
                )
            )
        }
    }
}
