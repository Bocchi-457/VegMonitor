package com.boc.vegmonitor.ui

data class MonitorUiState(
    // 状态
    val isOnline: Boolean = false,
    // 是否收到过硬件数据
    val hasReceivedData: Boolean = false,
    // 数据（可空，null 表示从未收到过数据）
    val currentTemp: Float? = null,
    val currentHumidity: Float? = null,
    // 阈值（可空，null 表示从未收到过数据）
    val tempUpperLimit: Float? = null,
    val tempLowerLimit: Float? = null,
    val humUpperLimit: Float? = null,
    val humLowerLimit: Float? = null,

    val isAutoMode: Boolean = true,
    // 设备状态
    val isHeaterOn: Boolean = false,
    val isCoolerOn: Boolean = false,
    val isHumidifierOn: Boolean = false,
    val isDehumidifierOn: Boolean = false
)