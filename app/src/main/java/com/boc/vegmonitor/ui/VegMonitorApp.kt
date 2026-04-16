package com.boc.vegmonitor.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boc.vegmonitor.ui.navigation.Screen
import com.boc.vegmonitor.ui.theme.VegMonitorTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boc.vegmonitor.VegMonitorApplication
import com.boc.vegmonitor.data.network.BemfaApiService
import com.boc.vegmonitor.data.repository.LoginFailureRepository

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // 实例化 LoginFailureRepository
    val failureRepository = if (isInPreview) {
        null
    } else {
        val context = LocalContext.current
        remember { LoginFailureRepository(context) }
    }

    // 获取当前路由以确定 TopAppBar 标题
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Snackbar 宿主状态
    val snackbarHostState = remember { SnackbarHostState() }

    // 创建 MonitorViewModel 实例以获取在线状态（仅在 Monitor 页面需要）
    val monitorViewModel = if (!isInPreview && userDao != null) {
        viewModel<MonitorViewModel>(factory = MonitorViewModelFactory(userDao))
    } else {
        null
    }

    // 获取在线状态
    val isOnline = monitorViewModel?.uiState?.collectAsState()?.value?.isOnline ?: false

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (currentRoute) {
                Screen.Monitor.route -> {
                    // Monitor 页面的 TopAppBar
                    TopAppBar(
                        title = { Text("VegMonitor 蔬控宝") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            // 显示在线状态
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                    contentDescription = "Online Status",
                                    tint = if (isOnline) Color(0xFF4CAF50) else Color.Red
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isOnline) "设备在线" else "设备离线",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    )
                }

                Screen.Mine.route -> {
                    // Mine 页面的 TopAppBar
                    TopAppBar(
                        title = { Text("用户中心") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Monitor.route) {
                if (isInPreview) {
                    MonitorScreen(
                        viewModel = MonitorViewModel(),
                        snackbarHostState = snackbarHostState
                    )
                } else {
                    val monitorViewModel: MonitorViewModel = viewModel(
                        factory = MonitorViewModelFactory(userDao!!)
                    )
                    MonitorScreen(
                        viewModel = monitorViewModel,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
            composable(Screen.Mine.route) {
                if (isInPreview) {
                    MineScreen(
                        viewModel = MineViewModel(),
                        snackbarHostState = snackbarHostState
                    )
                } else {
                    val mineViewModel: MineViewModel = viewModel(
                        factory = MineViewModelFactory(userDao!!, apiService, failureRepository)
                    )
                    MineScreen(
                        viewModel = mineViewModel,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun VegMonitorAppPreview() {
    VegMonitorTheme(dynamicColor = false) {
        VegMonitorApp()
    }
}