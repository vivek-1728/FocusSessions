package com.focussessions.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focussessions.app.ui.components.AppLogoIcon
import com.focussessions.app.viewmodel.AuthViewModel

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    // Legacy Sign-In Launcher
    val legacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleLegacySignInResult(result.data)
        }
    }

    // Trigger legacy flow if intent is present in state
    LaunchedEffect(state.legacySignInIntent) {
        state.legacySignInIntent?.let { intent ->
            legacyLauncher.launch(intent)
        }
    }

    // Fade-in entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Logo / Icon area
                AppLogoIcon(size = 88.dp)

                Spacer(Modifier.height(28.dp))

                Text(
                    text      = "Focus Sessions",
                    style     = MaterialTheme.typography.headlineLarge.copy(
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text      = "Your personal productivity companion",
                    style     = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF444444)),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(56.dp))

                // Google Sign-In button
                GoogleSignInButton(
                    isLoading = state.isLoading,
                    onClick   = { viewModel.signInWithGoogle(context) }
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text      = "Your data is stored securely on your device\nand optionally synced to your Google account",
                    style     = MaterialTheme.typography.labelSmall.copy(
                        color    = Color(0xFF2A2A2A),
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // Error message
                AnimatedVisibility(visible = state.error.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A0000))
                                .border(1.dp, Color(0xFF440000), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text  = state.error,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF886666),
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissError() },
                                    modifier = Modifier.size(24.dp).padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF440000)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "btn_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                enabled           = !isLoading
            ) { if (!isLoading) onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color    = Color.Black,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Google G logo
                Box(
                    modifier         = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4285F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "G",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp
                        )
                    )
                }
                Text(
                    text  = "Continue with Google",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color      = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp
                    )
                )
            }
        }
    }
}
