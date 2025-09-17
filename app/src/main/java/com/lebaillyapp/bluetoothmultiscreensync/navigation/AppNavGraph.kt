package com.lebaillyapp.bluetoothmultiscreensync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.RoleSelectionScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.SetupScreen
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object RoleSelection : Screen("role_selection")
    object Canvas : Screen("canvas")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    setupViewModel: SetupViewModel,
    // injecter ici d'autres VM si nécessaire
) {
    NavHost(navController = navController, startDestination = Screen.Setup.route) {
        composable(Screen.Setup.route) {
            SetupScreen(
                viewModel = setupViewModel,
                onReady = { navController.navigate(Screen.RoleSelection.route) }
            )
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(navController)
        }

        composable(Screen.Canvas.route) {
          //  CanvasScreen()
        }
    }
}