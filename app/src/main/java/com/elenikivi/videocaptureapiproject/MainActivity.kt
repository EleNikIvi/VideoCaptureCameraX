package com.elenikivi.videocaptureapiproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elenikivi.videocaptureapiproject.screens.playback.PlaybackScreen
import com.elenikivi.videocaptureapiproject.screens.recording.camerax.RecordingScreen
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    private fun showMessage(message: Int) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    @Composable
    private fun App() {
        MaterialTheme {
            Surface {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = ScreenDestinations.VideoCameraX.route
                ) {

                    composable(route = ScreenDestinations.VideoCameraX.route) {
                        RecordingScreen(navController) {
                            showMessage(it)
                        }
                    }

                    composable(
                        route = ScreenDestinations.Playback.route, arguments = listOf(
                            navArgument(ScreenDestinations.ARG_FILE_PATH) {
                                nullable = false
                                type = NavType.StringType
                            })
                    ) {
                        val filePath = ScreenDestinations.Playback.getFilePath(it.arguments)
                        PlaybackScreen(
                            filePath = filePath,
                            navHostController = navController
                        )
                    }
                }

                BackHandler {
                    navController.popBackStack()
                }
            }
        }
    }
}

fun NavHostController.navigateTo(route: String) = navigate(route) {
    popUpTo(route)
    launchSingleTop = true
}

sealed class ScreenDestinations(val route: String) {
    data object VideoCameraX : ScreenDestinations("videox")
    data object Playback : ScreenDestinations("playback?${ARG_FILE_PATH}={$ARG_FILE_PATH}") {
        fun createRoute(filePath: String): String {
            return "playback?${ARG_FILE_PATH}=${filePath}"
        }

        fun getFilePath(bundle: Bundle?): String {
            return bundle?.getString(ARG_FILE_PATH)!!
        }
    }

    companion object {
        const val ARG_FILE_PATH: String = "arg_file_path"
    }
}