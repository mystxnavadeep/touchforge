package com.touchforge.toolkit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean?> =
        settings.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TouchForgeTheme { AppRoot() }
        }
    }
}

private data class Tab(val route: String, val labelRes: Int, val icon: @Composable () -> Unit)

@Composable
fun AppRoot(mainVm: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val onboardingDone by mainVm.onboardingCompleted.collectAsState()

    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) {
            nav.navigate("onboarding") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    val tabs = listOf(
        Tab("home", R.string.tab_home) { Icon(Icons.Filled.Home, null) },
        Tab("games", R.string.tab_games) { Icon(Icons.Filled.Gamepad, null) },
        Tab("connect", R.string.tab_connect) { Icon(Icons.Filled.Build, null) },
        Tab("account", R.string.tab_account) { Icon(Icons.Filled.Person, null) },
        Tab("settings", R.string.tab_settings) { Icon(Icons.Filled.Settings, null) },
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"
    val showBars = tabs.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar(
                    containerColor = CyberColors.Surface,
                    tonalElevation = 8.dp,
                ) {
                    tabs.forEach { t ->
                        val isSelected = current == t.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = t.icon,
                            label = { Text(stringResource(t.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberColors.Cyan,
                                selectedTextColor = CyberColors.Cyan,
                                unselectedIconColor = CyberColors.TextSecondary,
                                unselectedTextColor = CyberColors.TextSecondary,
                                indicatorColor = CyberColors.Cyan.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        },
        containerColor = CyberColors.Background,
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
        ) {
            // Main Bottom Bar Destinations
            composable("home") {
                HomeScreen(
                    onOpenConnect = { nav.navigate("connect") },
                    onOpenGames = { nav.navigate("games") },
                    onOpenEditor = { pkg -> nav.navigate("editor/$pkg") },
                    onOpenAimCurve = { pkg -> nav.navigate("aim_curve/$pkg") },
                    onOpenTriggers = { pkg -> nav.navigate("triggers/$pkg") },
                    onOpenKeyMap = { pkg -> nav.navigate("keymap/$pkg") },
                    onOpenMacros = { nav.navigate("macros") },
                    onOpenCrosshair = { nav.navigate("crosshair") },
                    onOpenHud = { nav.navigate("hud") },
                )
            }
            composable("games") {
                GamesScreen(
                    onOpenGameDetail = { pkg -> nav.navigate("game_detail/$pkg") },
                    onOpenEditor = { pkg -> nav.navigate("editor/$pkg") },
                )
            }
            composable("connect") { ConnectScreen() }
            composable("account") { AccountScreen() }
            composable("settings") {
                SettingsScreen(
                    onOpenCrosshair = { nav.navigate("crosshair") },
                    onOpenHud = { nav.navigate("hud") },
                )
            }

            // Sub-destinations
            composable(
                "game_detail/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                GameDetailScreen(
                    pkg = pkg,
                    onBack = { nav.popBackStack() },
                    onOpenEditor = { p -> nav.navigate("editor/$p") },
                    onOpenAimCurve = { p -> nav.navigate("aim_curve/$p") },
                    onOpenTriggers = { p -> nav.navigate("triggers/$p") },
                    onOpenKeyMap = { p -> nav.navigate("keymap/$p") },
                )
            }

            composable(
                "editor/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                EditorScreen(
                    pkg = entry.arguments?.getString("pkg").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }

            composable(
                "aim_curve/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                AimCurveScreen(
                    pkg = entry.arguments?.getString("pkg").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                "triggers/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                TriggerScreen(
                    pkg = entry.arguments?.getString("pkg").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                "keymap/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                KeyMapScreen(
                    pkg = entry.arguments?.getString("pkg").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }

            composable("macros") {
                MacroScreen(onBack = { nav.popBackStack() })
            }

            composable("crosshair") {
                CrosshairScreen(onBack = { nav.popBackStack() })
            }

            composable("hud") {
                HudScreen(onBack = { nav.popBackStack() })
            }

            composable("onboarding") {
                OnboardingScreen(onFinished = {
                    nav.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                })
            }
        }
    }
}
