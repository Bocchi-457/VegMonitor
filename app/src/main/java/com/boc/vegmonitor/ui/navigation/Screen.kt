package com.boc.vegmonitor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Monitor : Screen("monitor", "监控", Icons.Rounded.Home)
    object Mine : Screen("mine", "我的", Icons.Rounded.Person)
}