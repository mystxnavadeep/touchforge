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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.touchforge.toolkit.R
import dagger.hilt.android.AndroidEntryPoint

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
fun AppRoot() {
    val nav = rememberNavController()
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
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = t.icon,
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(onOpenConnect = { nav.navigate("connect") }) }
            composable("games") { GamesScreen(onOpenEditor = { pkg -> nav.navigate("editor/$pkg") }) }
            composable(
                "editor/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                ProfileEditorScreen(pkg = entry.arguments?.getString("pkg").orEmpty(), onDone = { nav.popBackStack() })
            }
            composable("connect") { ConnectScreen() }
            composable("account") { AccountScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
