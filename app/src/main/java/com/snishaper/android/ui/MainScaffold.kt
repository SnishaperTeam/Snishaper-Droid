package com.snishaper.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.snishaper.android.R
import com.snishaper.android.data.AppPreferences
import com.snishaper.android.data.AppearanceMode
import com.snishaper.android.ui.components.LocalSnackbarHostState
import com.snishaper.android.ui.nav.AppDestination
import com.snishaper.android.ui.nav.AppDrawer
import com.snishaper.android.ui.pages.AboutScreen
import com.snishaper.android.ui.pages.DashboardScreen
import com.snishaper.android.ui.pages.DnsScreen
import com.snishaper.android.ui.pages.EvolutionScreen
import com.snishaper.android.ui.pages.LogsScreen
import com.snishaper.android.ui.pages.ProxiesScreen
import com.snishaper.android.ui.pages.RoutingScreen
import com.snishaper.android.ui.pages.RulesScreen
import com.snishaper.android.ui.pages.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Single-activity shell: TopAppBar with the ☰ drawer toggle, ModalNavigationDrawer, NavHost with
 * the 9 desktop routes (no CA entry) and a global Snackbar host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    preferences: AppPreferences,
    darkTheme: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onAutoStartProxyChange: (Boolean) -> Unit,
    onAutoUpdateRulesChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var restoredDrawerState by rememberSaveable { mutableStateOf(false) }

    // Restore the persisted drawer visibility once on cold start (DataStore persistence requirement).
    LaunchedEffect(Unit) {
        if (!restoredDrawerState) {
            restoredDrawerState = true
            if (preferences.drawerOpen) drawerState.open()
        }
    }
    // Persist every open/close transition back to DataStore.
    LaunchedEffect(drawerState.currentValue) {
        onDrawerOpenChange(drawerState.currentValue == DrawerValue.Open)
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                AppDrawer(
                    currentRoute = currentRoute,
                    darkTheme = darkTheme,
                    onNavigate = { destination: AppDestination ->
                        scope.launch { drawerState.close() }
                        navController.navigate(destination.route) {
                            popUpTo(AppDestination.start.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onToggleTheme = onToggleTheme,
                )
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_snishaper_logo),
                                    contentDescription = stringResource(id = R.string.cd_logo),
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(id = R.string.cd_open_drawer),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { contentPadding ->
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.start.route,
                ) {
                    composable(AppDestination.Dashboard.route) {
                        DashboardScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Proxies.route) {
                        ProxiesScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Rules.route) {
                        RulesScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Routing.route) {
                        RoutingScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Dns.route) {
                        DnsScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Evolution.route) {
                        EvolutionScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Logs.route) {
                        LogsScreen(contentPadding = contentPadding)
                    }
                    composable(AppDestination.Settings.route) {
                        SettingsScreen(
                            contentPadding = contentPadding,
                            preferences = preferences,
                            onLanguageChange = onLanguageChange,
                            onThemeChange = onThemeChange,
                            onAppearanceChange = onAppearanceChange,
                            onAutoStartProxyChange = onAutoStartProxyChange,
                            onAutoUpdateRulesChange = onAutoUpdateRulesChange,
                        )
                    }
                    composable(AppDestination.About.route) {
                        AboutScreen(contentPadding = contentPadding)
                    }
                }
            }
        }
    }
}
