package com.boc.vegmonitor.ui

data class MonitorUiState(
    // 状态
    val isOnline: Boolean = true,
    // 数据
    val currentTemp: Float = 22.5f,
    val currentHumidity: Float = 65.2f,
    // 阈值
    val tempUpperLimit: Float = 25.0f,
    val tempLowerLimit: Float = 20.0f,
    val humUpperLimit: Float = 70.0f,
    val humLowerLimit: Float = 60.0f,

    val isAutoMode: Boolean = true,
    // 设备状态
    val isHeaterOn: Boolean = false,
    val isCoolerOn: Boolean = false,
    val isHumidifierOn: Boolean = false,
    val isDehumidifierOn: Boolean = false
)