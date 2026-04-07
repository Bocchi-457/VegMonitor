package com.boc.vegmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.boc.vegmonitor.ui.VegMonitorApp
import com.boc.vegmonitor.ui.theme.VegMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VegMonitorTheme {
                VegMonitorApp()
            }
        }
    }
}
