package com.focussessions.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.focussessions.app.auth.SyncStatus
import com.focussessions.app.viewmodel.*

// ── Routes ────────────────────────────────────────────────────────────────────
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Focus     : Screen("focus",     "Focus",     Icons.Rounded.Timer)
    object Calendar  : Screen("calendar",  "Calendar",  Icons.Rounded.CalendarMonth)
    object Analytics : Screen("analytics", "Analytics", Icons.Rounded.BarChart)
    object Stopwatch : Screen("stopwatch", "Stopwatch", Icons.Rounded.Alarm)
}

object Routes {
    const val LOGIN   = "login"
    const val PROFILE = "profile"
    const val MAIN    = "main"
}

private val mainTabs = listOf(Screen.Focus, Screen.Calendar, Screen.Analytics, Screen.Stopwatch)

// ── Root navigation ───────────────────────────────────────────────────────────
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController    = navController,
        startDestination = if (authState.isSignedIn) Routes.MAIN else Routes.LOGIN,
        enterTransition  = { fadeIn(tween(250)) },
        exitTransition   = { fadeOut(tween(180)) }
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(authViewModel)
            LaunchedEffect(authState.isSignedIn) {
                if (authState.isSignedIn) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.MAIN) {
            val syncViewModel: SyncViewModel = hiltViewModel()

            LaunchedEffect(authState.isSignedIn) {
                if (!authState.isSignedIn) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            }

            MainScaffold(authViewModel = authViewModel, syncViewModel = syncViewModel)
        }
    }
}

// ── Main scaffold with tabs ───────────────────────────────────────────────────
@Composable
fun MainScaffold(authViewModel: AuthViewModel, syncViewModel: SyncViewModel) {
    val tabNavController = rememberNavController()
    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
    val isOnline  by syncViewModel.isOnline.collectAsStateWithLifecycle()
    var showProfile by remember { mutableStateOf(false) }

    if (showProfile) {
        ProfileScreen(
            authViewModel = authViewModel,
            syncViewModel = syncViewModel,
            onBack        = { showProfile = false }
        )
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            SyncStatusBar(
                syncStatus    = syncState.status,
                isOnline      = isOnline,
                authViewModel = authViewModel,
                onProfileTap  = { showProfile = true }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = Color(0xFF0D0D0D), thickness = 0.5.dp)
                NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
                    val entry by tabNavController.currentBackStackEntryAsState()
                    val current = entry?.destination
                    mainTabs.forEach { screen ->
                        val selected = current?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon     = { Icon(screen.icon, screen.label, modifier = Modifier.size(20.dp)) },
                            label    = { Text(screen.label, fontSize = if (selected) 10.5f.toSp() else 10f.toSp()) },
                            selected = selected,
                            onClick  = {
                                tabNavController.navigate(screen.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = Color.White,
                                selectedTextColor   = Color.White,
                                unselectedIconColor = Color(0xFF333333),
                                unselectedTextColor = Color(0xFF333333),
                                indicatorColor      = Color(0xFF111111)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = tabNavController,
            startDestination = Screen.Focus.route,
            modifier         = Modifier.padding(padding),
            enterTransition  = { fadeIn(tween(180)) },
            exitTransition   = { fadeOut(tween(120)) }
        ) {
            composable(Screen.Focus.route)     { FocusScreen(hiltViewModel()) }
            composable(Screen.Calendar.route)  { CalendarScreen(hiltViewModel()) }
            composable(Screen.Analytics.route) { AnalyticsScreen(hiltViewModel()) }
            composable(Screen.Stopwatch.route) { StopwatchScreen(hiltViewModel()) }
        }
    }
}

// ── Sync status top bar ───────────────────────────────────────────────────────
@Composable
private fun SyncStatusBar(
    syncStatus: SyncStatus,
    isOnline: Boolean,
    authViewModel: AuthViewModel,
    onProfileTap: () -> Unit
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val user = authState.user ?: return

    val (statusText, statusColor) = when {
        !isOnline                        -> "Offline"    to Color(0xFF444444)
        syncStatus == SyncStatus.SYNCING -> "Syncing…"   to Color(0xFF446688)
        syncStatus == SyncStatus.SYNCED  -> "Synced"     to Color(0xFF335533)
        syncStatus == SyncStatus.ERROR   -> "Sync error" to Color(0xFF663333)
        else                             -> "" to Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (statusText.isNotEmpty()) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Text(statusText, style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor, fontSize = 10.sp))
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        // Avatar chip
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onProfileTap
                ),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model              = user.photoUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text  = user.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

private fun Float.toSp() =
    androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
