package com.focussessions.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.focussessions.app.auth.SyncStatus
import com.focussessions.app.viewmodel.AuthViewModel
import com.focussessions.app.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    syncViewModel: SyncViewModel,
    onBack: () -> Unit
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
    val user      = authState.user
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
            }
            Text("Profile", style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }

        // Avatar + name
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile photo
                if (!user?.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model             = user!!.photoUrl,
                        contentDescription = "Profile photo",
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier.size(64.dp).clip(CircleShape)
                            .border(1.dp, Color(0xFF1A1A1A), CircleShape)
                    )
                } else {
                    Box(
                        modifier         = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF111111))
                            .border(1.dp, Color(0xFF1A1A1A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
                    }
                }
                Column {
                    Text(user?.displayName ?: "User",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                    Text(user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444)))
                }
            }
        }

        // Stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatCard("Focus",    formatMinutes(user?.totalFocusMinutes ?: 0), Icons.Rounded.Timer,    Modifier.weight(1f))
            ProfileStatCard("Streak",  "${user?.currentStreak ?: 0}d",               Icons.Rounded.Whatshot, Modifier.weight(1f))
        }

        // Sync status card
        SyncStatusCard(syncState, onSync = syncViewModel::triggerSync)

        // Last sync
        if (syncState.lastSyncTime > 0L) {
            Text(
                text  = "Last synced: ${formatTime(syncState.lastSyncTime)}",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp)
            )
        }

        Spacer(Modifier.weight(1f).heightIn(min = 32.dp))

        // Logout button
        Button(
            onClick  = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0A0A0A),
                contentColor   = Color(0xFF666666)
            )
        ) {
            Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign out", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest  = { showLogoutDialog = false },
            containerColor    = Color(0xFF0A0A0A),
            title             = { Text("Sign out?", style = MaterialTheme.typography.titleMedium.copy(color = Color.White)) },
            text              = { Text("Your local data stays on this device.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF555555))) },
            confirmButton     = {
                TextButton(onClick = { authViewModel.signOut(); showLogoutDialog = false }) {
                    Text("Sign out", style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF888888)))
                }
            },
            dismissButton     = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = Color.White))
                }
            }
        )
    }
}

@Composable
private fun ProfileStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier            = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0A0A)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color(0xFF333333), modifier = Modifier.size(14.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 10.sp))
    }
}

@Composable
private fun SyncStatusCard(state: com.focussessions.app.auth.SyncState, onSync: () -> Unit) {
    val (dot, label, labelColor) = when (state.status) {
        SyncStatus.SYNCED  -> Triple(Color(0xFF1A3A1A), "Synced",      Color(0xFF448844))
        SyncStatus.SYNCING -> Triple(Color(0xFF1A2A3A), "Syncing…",    Color(0xFF4488AA))
        SyncStatus.ERROR   -> Triple(Color(0xFF3A1A1A), "Sync failed", Color(0xFF884444))
        SyncStatus.OFFLINE -> Triple(Color(0xFF1A1A1A), "Offline",     Color(0xFF555555))
        SyncStatus.IDLE    -> Triple(Color(0xFF111111), "Not synced",  Color(0xFF444444))
    }

    Row(
        modifier              = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(dot).padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(labelColor))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(color = labelColor))
                if (state.pendingOps > 0)
                    Text("${state.pendingOps} pending", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp))
            }
        }
        if (state.status != SyncStatus.SYNCING) {
            IconButton(onClick = onSync, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Sync, null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
            }
        } else {
            CircularProgressIndicator(color = Color(0xFF4488AA), modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
        }
    }
}

private fun formatMinutes(m: Int): String { val h = m/60; val min = m%60; return when { h==0->"${m}m"; min==0->"${h}h"; else->"${h}h ${min}m" } }
private fun formatTime(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
