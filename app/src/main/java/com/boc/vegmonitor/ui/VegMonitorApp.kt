package com.boc.vegmonitor.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boc.vegmonitor.ui.navigation.Screen
import com.boc.vegmonitor.ui.theme.VegMonitorTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boc.vegmonitor.VegMonitorApplication
import com.boc.vegmonitor.data.network.BemfaApiService

@SuppressLint("ViewModelConstructorInComposable")
@Composable
fun VegMonitorApp() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Monitor,
        Screen.Mine
    )
    
    val isInPreview = LocalInspectionMode.current
    
    val userDao = if (isInPreview) {
        null
    } else {
        val context = LocalContext.current
        val application = context.applicationContext as VegMonitorApplication
        application.database.userDao()
    }
    // 实例化 Retrofit 的 API Service (
    val apiService = remember { BemfaApiService.create() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Monitor.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Monitor.route) {
                if (isInPreview) {
                    MonitorScreen(viewModel = MonitorViewModel())
                } else {
                    val monitorViewModel: MonitorViewModel = viewModel(
                        factory = MonitorViewModelFactory(userDao!!)
                    )
                    MonitorScreen(viewModel = monitorViewModel)
                }
            }
            composable(Screen.Mine.route) {
                if (isInPreview) {
                    MineScreen(viewModel = MineViewModel())
                } else {
                    val mineViewModel: MineViewModel = viewModel(
                        factory = MineViewModelFactory(userDao!!, apiService)
                    )
                    MineScreen(viewModel = mineViewModel)
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun VegMonitorAppPreview() {
    VegMonitorTheme {
        VegMonitorApp()
    }
}