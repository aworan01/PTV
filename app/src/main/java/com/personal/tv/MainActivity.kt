package com.personal.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.tv.ui.Screen
import com.personal.tv.ui.components.SideNav
import com.personal.tv.ui.screens.MoviesScreen
import com.personal.tv.ui.screens.ShowsScreen
import com.personal.tv.ui.screens.epg.EpgScreen
import com.personal.tv.ui.screens.playlists.PlaylistsScreen
import com.personal.tv.ui.screens.settings.SettingsScreen
import com.personal.tv.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            PersonalTVTheme {
                MainShell(onExit = { finishAffinity() })
            }
        }
    }
}

@Composable
fun MainShell(onExit: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.TV.route

    Row(modifier = Modifier.fillMaxSize().background(NavyMid)) {
        SideNav(
            currentRoute = currentRoute,
            onNavigate = { screen ->
                navController.navigate(screen.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onExit = onExit
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            NavHost(navController = navController, startDestination = Screen.TV.route) {
                composable(Screen.TV.route)        { EpgScreen()       }
                composable(Screen.Movies.route)    { MoviesScreen()    }
                composable(Screen.Shows.route)     { ShowsScreen()     }
                composable(Screen.Playlists.route) { PlaylistsScreen() }
                composable(Screen.Settings.route)  { SettingsScreen()  }
            }
        }
    }
}
