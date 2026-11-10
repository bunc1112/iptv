package com.bunc.tv.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bunc.tv.data.NavState
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.ui.screen.EpisodesScreen
import com.bunc.tv.ui.screen.HomeScreen
import com.bunc.tv.ui.screen.LoginScreen
import com.bunc.tv.ui.screen.PlayerScreen
import com.bunc.tv.ui.screen.ProfilesScreen
import com.bunc.tv.ui.screen.SplashScreen

object Routes {
    const val PROFILES = "profiles"
    const val LOGIN = "login"
    const val HOME = "home"
    const val PLAYER = "player"
    const val EPISODES = "episodes"
}

@Composable
fun BuncRoot() {
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Profiles exist → show the picker; none yet → go straight to login.
        start = if (ServiceLocator.prefs.loadProfiles().isNotEmpty()) Routes.PROFILES else Routes.LOGIN
    }

    val startRoute = start
    if (startRoute == null) {
        SplashScreen()
        return
    }

    NavHost(navController = nav, startDestination = startRoute) {

        composable(Routes.PROFILES) {
            ProfilesScreen(
                onUseProfile = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.PROFILES) { inclusive = true }
                    }
                },
                onAddProfile = { nav.navigate(Routes.LOGIN) }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(onConnected = {
                // Clear login/picker from the back stack; HOME becomes the root.
                nav.navigate(Routes.HOME) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onPlay = { url, title ->
                    PlaybackQueue.setSingle(url, title)
                    nav.navigate(Routes.PLAYER)
                },
                onPlayList = { list, startIndex ->
                    PlaybackQueue.setList(list, startIndex)
                    nav.navigate(Routes.PLAYER)
                },
                onOpenSeries = { seriesId, title ->
                    NavState.seriesTitle = title
                    nav.navigate("${Routes.EPISODES}/$seriesId")
                },
                onLogout = {
                    // Back to the profile picker (profiles are kept).
                    nav.navigate(Routes.PROFILES) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Routes.EPISODES}/{seriesId}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.IntType }
            )
        ) { entry ->
            val seriesId = entry.arguments?.getInt("seriesId") ?: 0
            EpisodesScreen(
                seriesId = seriesId,
                title = NavState.seriesTitle,
                onPlay = { url, t ->
                    PlaybackQueue.setSingle(url, t)
                    nav.navigate(Routes.PLAYER)
                },
                onBack = { nav.popBackStack() }
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
    }
}
