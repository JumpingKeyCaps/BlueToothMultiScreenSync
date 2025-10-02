package com.lebaillyapp.bluetoothmultiscreensync.navigation

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.PlaygroundScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.RoleSelectionScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.SetupScreen
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory.PlaygroundViewModelFactory
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory.RoleViewModelFactory

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object RoleSelection : Screen("role_selection")
    object Playground : Screen("playground")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    setupViewModel: SetupViewModel,
    repository: BluetoothRepository,
    bluetoothAdapter: BluetoothAdapter
) {

    NavHost(navController = navController, startDestination = Screen.Setup.route) {
        composable(Screen.Setup.route) {
            SetupScreen(
                viewModel = setupViewModel,
                onReady = {
                    navController.navigate(Screen.RoleSelection.route){
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RoleSelection.route) {
            val roleViewModel: RoleViewModel = viewModel(
                factory = RoleViewModelFactory(
                    repository = repository,
                    bluetoothAdapter = bluetoothAdapter
                )
            )
            RoleSelectionScreen(
                navController = navController,
                roleViewModel = roleViewModel
            )
        }

        composable(Screen.Playground.route) {
            // Playground VM créé ici, réutilise le même repo
            val playgroundViewModel: PlaygroundViewModel = viewModel(
                factory = PlaygroundViewModelFactory(repository)
            )
            PlaygroundScreen(playgroundViewModel)
        }
    }
}
