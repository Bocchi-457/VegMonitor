package com.boc.vegmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boc.vegmonitor.data.dao.UserDao
import com.boc.vegmonitor.data.network.BemfaTcpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonitorViewModel(
    private val userDao: UserDao? = null,
    private val tcpClient: BemfaTcpClient = BemfaTcpClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var currentUserUid: String = ""
    
    // 硬件在线状态判断：记录最后一次收到数据的时间
    private var lastDataReceivedTime: Long = System.currentTimeMillis() // 初始化为当前时间，避免启动时误判离线
    private val HARDWARE_OFFLINE_TIMEOUT = 20000L // 20秒未收到数据视为离线
    
    // 重连管理：防止并发重连
    private var reconnectJob: Job? = null
    private var isReconnecting = false
    
    // 设备 pending 状态管理（用于重试和防重复点击）
    data class PendingOperation(
        val deviceKey: String,      // 设备标识："mode", "heater", "cooler" 等
        val targetState: Boolean,   // 目标状态
        val retryCount: Int = 0,    // 已重试次数
        val timeoutJob: Job? = null, // 超时任务
        // 阈值下发的期望值（用于数据比对确认）
        val expectedTempLower: Float? = null,
        val expectedTempUpper: Float? = null,
        val expectedHumLower: Float? = null,
        val expectedHumUpper: Float? = null
    )
    private val _pendingOperations = MutableStateFlow<Map<String, PendingOperation>>(emptyMap())
    
    // 失败事件（用于显示 Snackbar）
    private val _failureEvents = MutableStateFlow<String?>(null)
    val failureEvents: StateFlow<String?> = _failureEvents.asStateFlow()
    
    // Pending 提示事件
    private val _pendingEvents = MutableStateFlow<String?>(null)
    val pendingEvents: StateFlow<String?> = _pendingEvents.asStateFlow()
    
    // 点击节流提示事件
    private val _throttleEvents = MutableStateFlow<String?>(null)
    val throttleEvents: StateFlow<String?> = _throttleEvents.asStateFlow()
    
    // 点击节流管理（防止短时间内连续点击）
    private val _lastClickTimes = mutableMapOf<String, Long>()
    private val CLICK_THROTTLE_MS = 2000L // 2s 内不允许重复点击
    
    /**
     * 清除节流事件（Snackbar 显示后调用）
     */
    fun clearThrottleEvent() {
        _throttleEvents.value = null
    }
    
    /**
     * 清除失败事件（Snackbar 显示后调用）
     */
    fun clearFailureEvent() {
        _failureEvents.value = null
    }
    
    /**
     * 清除 pending 事件（Snackbar 显示后调用）
     */
    fun clearPendingEvent() {
        _pendingEvents.value = null
    }
    
    /**
     * 检查点击节流（防止短时间内连续点击）
     * @param actionKey 操作标识
     * @return true 表示允许执行，false 表示被节流拦截
     */
    private fun checkClickThrottle(actionKey: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastClickTime = _lastClickTimes[actionKey] ?: 0L
        
        if (currentTime - lastClickTime < CLICK_THROTTLE_MS) {
            // 在节流时间内，显示提示
            if (_throttleEvents.value == null) {
                _throttleEvents.value = "操作过于频繁，请稍后再试"
            }
            return false
        }
        
        // 更新最后点击时间
        _lastClickTimes[actionKey] = currentTime
        return true
    }

    init {
        if (userDao != null) {
            // 1. 初始化时，从数据库读取用户信息并连接 TCP
            viewModelScope.launch {
                userDao.getLoggedInUser().collect { user ->
                    if (user != null && user.bemfaUid.isNotEmpty()) {
                        // 获取用户信息
                        currentUserUid = user.bemfaUid
                        connectToBemfa()
                    } else {
                        // 断线
                        _uiState.update { it.copy(isOnline = false) }
                    }
                }
            }
        }

        // 2. 持续监听巴法云 TCP 发来的数据
        viewModelScope.launch {
            tcpClient.incomingMessages.collect { rawMsg ->
                parseBemfaMessage(rawMsg)
            }
        }
        
        // 3. 监听TCP连接状态变化，实现自动重连
        tcpClient.onConnectionStateChanged = { isConnected ->
            if (!isConnected && currentUserUid.isNotEmpty()) {
                // 连接断开，尝试重连
                android.util.Log.d("TcpDebug", "检测到TCP连接断开，准备重连...")
                
                // 取消之前的重连任务（如果有）
                reconnectJob?.cancel()
                
                // 启动新的重连任务
                reconnectJob = viewModelScope.launch {
                    delay(2000) // 等待2秒后重连
                    connectToBemfa()
                }
            }
        }
        
        // 4. 启动硬件在线状态检测定时器
        startHardwareOnlineCheck()
    }
    
    /**
     * 连接到巴法云（封装为独立函数，便于重连调用）
     */
    private fun connectToBemfa() {
        if (currentUserUid.isNotEmpty()) {
            // 如果已经在重连中，直接返回
            if (isReconnecting) {
                android.util.Log.d("TcpDebug", "已在重连中，忽略重复请求")
                return
            }
            
            viewModelScope.launch {
                isReconnecting = true
                try {
                    // 如果已连接，先断开
                    if (tcpClient.isConnected()) {
                        tcpClient.disconnect()
                        delay(500)
                    }
                    // 重新连接
                    tcpClient.connectAndSubscribe(currentUserUid)
                    android.util.Log.d("TcpDebug", "正在连接巴法云...")
                } finally {
                    isReconnecting = false
                }
            }
        }
    }

    /**
     * 启动硬件在线状态检测定时器
     * 每5秒检查一次，如果超过20秒未收到数据，则判定为离线
     */
    private fun startHardwareOnlineCheck() {
        viewModelScope.launch {
            while (true) {
                delay(5000) // 每5秒检查一次
                val currentTime = System.currentTimeMillis()
                val timeSinceLastData = currentTime - lastDataReceivedTime
                val isHardwareOnline = timeSinceLastData < HARDWARE_OFFLINE_TIMEOUT
                
                android.util.Log.d("HardwareStatus", "距离上次数据: ${timeSinceLastData}ms, 超时阈值: ${HARDWARE_OFFLINE_TIMEOUT}ms, 在线状态: $isHardwareOnline")
                
                // 只有当状态发生变化时才更新UI
                if (_uiState.value.isOnline != isHardwareOnline) {
                    _uiState.update { it.copy(isOnline = isHardwareOnline) }
                    if (!isHardwareOnline) {
                        android.util.Log.d("HardwareStatus", "⚠️ 硬件已离线（超过20秒未收到数据）")
                    } else {
                        android.util.Log.d("HardwareStatus", "✅ 硬件已上线")
                    }
                }
            }
        }
    }

    /**
     * 解析巴法云返回的协议数据
     */
    private fun parseBemfaMessage(rawMsg: String) {
        // 更新最后收到数据的时间（用于判断硬件在线状态）
        lastDataReceivedTime = System.currentTimeMillis()

        // 温湿度和模式数据 (格式：Mode:1 th:29 tl:23 hh:55 hl:40 jr:0 zl:0 cs:0 js:0 temp:26.7 humi:42.7)
        if (rawMsg.contains("topic=data")) {
            val msgPart = rawMsg.substringAfter("msg=")

            // 解析当前温湿度
            val tempMatch = Regex("temp:([+-]?\\d+\\.?\\d*)").find(msgPart)
            val humiMatch = Regex("humi:([\\d\\.]+)").find(msgPart)
            
            // 解析控制模式
            val modeMatch = Regex("Mode:(\\d+)").find(msgPart)
            
            // 解析阈值：th(温度上限), tl(温度下限), hh(湿度上限), hl(湿度下限)
            val thMatch = Regex("th:([\\d\\.]+)").find(msgPart)
            val tlMatch = Regex("tl:([\\d\\.]+)").find(msgPart)
            val hhMatch = Regex("hh:([\\d\\.]+)").find(msgPart)
            val hlMatch = Regex("hl:([\\d\\.]+)").find(msgPart)
            
            // 解析设备状态：jr(加热), zl(制冷), cs(除湿), js(加湿)，0为关，1为开
            val jrMatch = Regex("jr:(\\d+)").find(msgPart)
            val zlMatch = Regex("zl:(\\d+)").find(msgPart)
            val csMatch = Regex("cs:(\\d+)").find(msgPart)
            val jsMatch = Regex("js:(\\d+)").find(msgPart)

            _uiState.update { state ->
                val newState = state.copy(
                    // 不再在此处设置 isOnline，由定时器统一判断
                    currentTemp = tempMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.currentTemp,
                    currentHumidity = humiMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.currentHumidity,
                    // 防抖逻辑：如果设备处于 pending 状态，不更新其状态，避免弹跳
                    isAutoMode = if (_pendingOperations.value.containsKey("mode")) {
                        state.isAutoMode  // pending 期间保持原状态
                    } else {
                        modeMatch?.groupValues?.get(1) == "1" // 非 pending 期间接受服务器状态
                    },
                    // 防抖逻辑：阈值在 pending 期间不更新，避免被旧数据覆盖
                    tempUpperLimit = if (_pendingOperations.value.containsKey("threshold")) {
                        state.tempUpperLimit  // pending 期间保持乐观更新的值
                    } else {
                        thMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.tempUpperLimit
                    },
                    tempLowerLimit = if (_pendingOperations.value.containsKey("threshold")) {
                        state.tempLowerLimit
                    } else {
                        tlMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.tempLowerLimit
                    },
                    humUpperLimit = if (_pendingOperations.value.containsKey("threshold")) {
                        state.humUpperLimit
                    } else {
                        hhMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.humUpperLimit
                    },
                    humLowerLimit = if (_pendingOperations.value.containsKey("threshold")) {
                        state.humLowerLimit
                    } else {
                        hlMatch?.groupValues?.get(1)?.toFloatOrNull() ?: state.humLowerLimit
                    },
                    // 防抖逻辑：设备状态在 pending 期间不更新
                    isHeaterOn = if (_pendingOperations.value.containsKey("heater")) {
                        state.isHeaterOn  // pending 期间保持乐观更新的状态
                    } else {
                        jrMatch?.groupValues?.get(1) == "1" // 非 pending 期间接受服务器状态
                    },
                    isCoolerOn = if (_pendingOperations.value.containsKey("cooler")) {
                        state.isCoolerOn
                    } else {
                        zlMatch?.groupValues?.get(1) == "1"
                    },
                    isDehumidifierOn = if (_pendingOperations.value.containsKey("dehumidifier")) {
                        state.isDehumidifierOn
                    } else {
                        csMatch?.groupValues?.get(1) == "1"
                    },
                    isHumidifierOn = if (_pendingOperations.value.containsKey("humidifier")) {
                        state.isHumidifierOn
                    } else {
                        jsMatch?.groupValues?.get(1) == "1"
                    }
                )
                
                // 检查是否有 pending 操作需要确认
                if (_pendingOperations.value.containsKey("mode") && modeMatch != null) {
                    confirmDeviceState("mode")
                }
                if (_pendingOperations.value.containsKey("heater") && jrMatch != null) {
                    confirmDeviceState("heater")
                }
                if (_pendingOperations.value.containsKey("cooler") && zlMatch != null) {
                    confirmDeviceState("cooler")
                }
                if (_pendingOperations.value.containsKey("humidifier") && jsMatch != null) {
                    confirmDeviceState("humidifier")
                }
                if (_pendingOperations.value.containsKey("dehumidifier") && csMatch != null) {
                    confirmDeviceState("dehumidifier")
                }
                // 阈值确认：检查服务器返回的阈值是否与期望值一致
                if (_pendingOperations.value.containsKey("threshold")) {
                    val pendingOp = _pendingOperations.value["threshold"]
                    android.util.Log.d("ThresholdDebug", "========== 阈值确认检查 ==========")
                    android.util.Log.d("ThresholdDebug", "pendingOp存在: ${pendingOp != null}")
                    android.util.Log.d("ThresholdDebug", "thMatch存在: ${thMatch != null}, 值: ${thMatch?.groupValues?.get(1)}")
                    android.util.Log.d("ThresholdDebug", "tlMatch存在: ${tlMatch != null}, 值: ${tlMatch?.groupValues?.get(1)}")
                    android.util.Log.d("ThresholdDebug", "hhMatch存在: ${hhMatch != null}, 值: ${hhMatch?.groupValues?.get(1)}")
                    android.util.Log.d("ThresholdDebug", "hlMatch存在: ${hlMatch != null}, 值: ${hlMatch?.groupValues?.get(1)}")
                    
                    if (pendingOp != null) {
                        android.util.Log.d("ThresholdDebug", "期望值 - th:${pendingOp.expectedTempUpper}, tl:${pendingOp.expectedTempLower}, hh:${pendingOp.expectedHumUpper}, hl:${pendingOp.expectedHumLower}")
                    }
                    
                    if (pendingOp != null && 
                        thMatch != null && tlMatch != null && hhMatch != null && hlMatch != null) {
                        
                        val serverTempUpper = thMatch.groupValues.get(1).toFloatOrNull()
                        val serverTempLower = tlMatch.groupValues.get(1).toFloatOrNull()
                        val serverHumUpper = hhMatch.groupValues.get(1).toFloatOrNull()
                        val serverHumLower = hlMatch.groupValues.get(1).toFloatOrNull()
                        
                        android.util.Log.d("ThresholdDebug", "解析后 - th:$serverTempUpper, tl:$serverTempLower, hh:$serverHumUpper, hl:$serverHumLower")
                        
                        // 比对是否一致（允许 0.1 的误差）
                        val tempUpperMatch = serverTempUpper?.let { 
                            Math.abs(it - (pendingOp.expectedTempUpper ?: 0f)) < 0.1f 
                        } ?: false
                        val tempLowerMatch = serverTempLower?.let { 
                            Math.abs(it - (pendingOp.expectedTempLower ?: 0f)) < 0.1f 
                        } ?: false
                        val humUpperMatch = serverHumUpper?.let { 
                            Math.abs(it - (pendingOp.expectedHumUpper ?: 0f)) < 0.1f 
                        } ?: false
                        val humLowerMatch = serverHumLower?.let { 
                            Math.abs(it - (pendingOp.expectedHumLower ?: 0f)) < 0.1f 
                        } ?: false
                        
                        android.util.Log.d("ThresholdDebug", "比对结果 - th匹配:$tempUpperMatch, tl匹配:$tempLowerMatch, hh匹配:$humUpperMatch, hl匹配:$humLowerMatch")
                        
                        // 所有阈值都匹配才确认成功
                        if (tempUpperMatch && tempLowerMatch && humUpperMatch && humLowerMatch) {
                            android.util.Log.d("ThresholdDebug", "✅ 阈值确认成功！")
                            confirmDeviceState("threshold")
                        } else {
                            android.util.Log.d("ThresholdDebug", "❌ 阈值比对失败，不确认")
                        }
                    } else {
                        android.util.Log.d("ThresholdDebug", "❌ 条件不满足，跳过确认")
                    }
                    android.util.Log.d("ThresholdDebug", "====================================")
                }
                
                newState
            }
        }
    }

    /**
     * 统一的指令发送辅助函数
     */
    private fun sendCommandToDevice(topic: String, msg: String) {
        if (currentUserUid.isNotEmpty()) {
            viewModelScope.launch {
                tcpClient.sendCommand(currentUserUid, topic, msg)
            }
        }
    }
    
    /**
     * 带重试机制的指令发送（乐观更新 + 超时重试 + 失败回滚）
     * @param deviceKey 设备标识
     * @param command 要发送的指令
     * @param updateUi 立即更新UI的回调（乐观更新）
     * @param rollbackUi 失败时回滚UI的回调
     * @param pendingMsg pending 状态下的提示消息
     * @param maxRetries 最大重试次数，默认3次
     * @param timeoutMs 每次超时时间，默认2000ms
     */
    private fun sendCommandWithRetry(
        deviceKey: String,
        command: String,
        updateUi: () -> Unit,
        rollbackUi: () -> Unit,
        pendingMsg: String,
        maxRetries: Int = 3,
        timeoutMs: Long = 2000
    ) {
        // 0. 检查点击节流（防止短时间内连续点击）
        if (!checkClickThrottle(deviceKey)) {
            return
        }
        
        // 检查是否已有 pending 操作
        val currentPending = _pendingOperations.value[deviceKey]
        if (currentPending != null) {
            // 正在处理中，只在首次显示提示，避免重复弹出
            if (_pendingEvents.value == null) {
                _pendingEvents.value = pendingMsg
            }
            return
        }
        
        // 1. 乐观更新 UI
        updateUi()
        
        // 2. 标记为 pending
        val operation = PendingOperation(deviceKey = deviceKey, targetState = !getCurrentState(deviceKey))
        _pendingOperations.update { it + (deviceKey to operation) }
        
        // 3. 启动带重试的发送流程
        viewModelScope.launch {
            var success = false
            var attempt = 0
            
            while (attempt <= maxRetries && !success) {
                // 发送指令
                sendCommandToDevice("control", command)
                
                // 等待服务器确认（超时检测）
                val confirmed = waitForConfirmation(deviceKey, timeoutMs)
                
                if (confirmed) {
                    success = true
                    // 清除 pending 状态（已在 parseBemfaMessage 中清除）
                } else {
                    attempt++
                    if (attempt <= maxRetries) {
                        // 更新重试计数
                        _pendingOperations.update { pendingMap ->
                            pendingMap[deviceKey]?.let { op ->
                                pendingMap + (deviceKey to op.copy(retryCount = attempt))
                            } ?: pendingMap
                        }
                    }
                }
            }
            
            // 4. 如果所有重试都失败，回滚 UI
            if (!success) {
                rollbackUi()
                clearPendingOperation(deviceKey)
                _failureEvents.value = "${getDeviceName(deviceKey)}控制失败，请检查网络连接"
            }
        }
    }
    
    /**
     * 等待服务器确认（通过检查 pending 状态是否被清除）
     */
    private suspend fun waitForConfirmation(deviceKey: String, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(100) // 每100ms检查一次
            if (_pendingOperations.value[deviceKey] == null) {
                return true // 已被 parseBemfaMessage 清除，说明收到确认
            }
        }
        return false // 超时
    }
    
    /**
     * 清除 pending 操作
     */
    private fun clearPendingOperation(deviceKey: String) {
        _pendingOperations.update { it - deviceKey }
    }
    
    /**
     * 确认设备状态（由 parseBemfaMessage 调用）
     */
    private fun confirmDeviceState(deviceKey: String) {
        clearPendingOperation(deviceKey)
    }
    
    /**
     * 获取设备当前状态
     */
    private fun getCurrentState(deviceKey: String): Boolean {
        return when (deviceKey) {
            "mode" -> _uiState.value.isAutoMode
            "heater" -> _uiState.value.isHeaterOn
            "cooler" -> _uiState.value.isCoolerOn
            "humidifier" -> _uiState.value.isHumidifierOn
            "dehumidifier" -> _uiState.value.isDehumidifierOn
            else -> false
        }
    }
    
    /**
     * 获取设备中文名称
     */
    private fun getDeviceName(deviceKey: String): String {
        return when (deviceKey) {
            "mode" -> "控制模式"
            "heater" -> "加热器"
            "cooler" -> "制冷器"
            "humidifier" -> "加湿器"
            "dehumidifier" -> "除湿器"
            else -> "设备"
        }
    }

    // ================= UI 操作触发的网络下发 ================= //

    // 切换控制模式 (自动 ZD / 手动 SD)
    fun toggleAutoMode(isAuto: Boolean) {
        val command = if (isAuto) "ZD" else "SD"
        val currentState = _uiState.value.isAutoMode
        
        sendCommandWithRetry(
            deviceKey = "mode",
            command = command,
            updateUi = {
                // 乐观更新：立即改变 UI
                _uiState.update { it.copy(isAutoMode = isAuto) }
            },
            rollbackUi = {
                // 失败回滚：恢复原状态
                _uiState.update { it.copy(isAutoMode = currentState) }
            },
            pendingMsg = "正在切换控制模式，请稍候..."
        )
    }

    // 设定阈值
    fun setThreshold(newTempLower: Float, newTempUpper: Float, newHumLower: Float, newHumUpper: Float) {
        // ViewModel 层二次验证
        if (newTempLower < -40f || newTempLower > 80f) {
            _failureEvents.value = "温度下限必须在 -40 到 80 之间"
            return
        }
        if (newTempUpper < -40f || newTempUpper > 80f) {
            _failureEvents.value = "温度上限必须在 -40 到 80 之间"
            return
        }
        if (newHumLower < 0f || newHumLower > 100f) {
            _failureEvents.value = "湿度下限必须在 0 到 100 之间"
            return
        }
        if (newHumUpper < 0f || newHumUpper > 100f) {
            _failureEvents.value = "湿度上限必须在 0 到 100 之间"
            return
        }
        if (newTempLower > newTempUpper) {
            _failureEvents.value = "温度下限不能高于温度上限"
            return
        }
        if (newHumLower > newHumUpper) {
            _failureEvents.value = "湿度下限不能高于湿度上限"
            return
        }
        
        // 检查点击节流
        if (!checkClickThrottle("threshold")) {
            return
        }
        
        // 检查是否已有 pending 操作
        if (_pendingOperations.value.containsKey("threshold")) {
            if (_pendingEvents.value == null) {
                _pendingEvents.value = "正在下发阈值，请稍候..."
            }
            return
        }
        
        // 保存旧值用于回滚
        val oldTempLower = _uiState.value.tempLowerLimit
        val oldTempUpper = _uiState.value.tempUpperLimit
        val oldHumLower = _uiState.value.humLowerLimit
        val oldHumUpper = _uiState.value.humUpperLimit
        
        // 乐观更新 UI
        _uiState.update {
            it.copy(
                tempLowerLimit = newTempLower,
                tempUpperLimit = newTempUpper,
                humLowerLimit = newHumLower,
                humUpperLimit = newHumUpper
            )
        }
        
        // 标记为 pending（保存期望值用于比对确认）
        val operation = PendingOperation(
            deviceKey = "threshold",
            targetState = true,
            expectedTempLower = newTempLower,
            expectedTempUpper = newTempUpper,
            expectedHumLower = newHumLower,
            expectedHumUpper = newHumUpper
        )
        _pendingOperations.update { it + ("threshold" to operation) }
        
        // 启动带重试的发送流程
        viewModelScope.launch {
            var success = false
            var attempt = 0
            val maxRetries = 3
            val timeoutMs = 2000L
            
            // 构造指令（使用正确的格式：分号分隔）
            val msg = "wendu_low=$newTempLower;wendu_high=$newTempUpper;shidu_low=$newHumLower;shidu_high=$newHumUpper"
            
            while (attempt < maxRetries && !success) {  // 修正：attempt < maxRetries（最多3次）
                // 发送指令
                sendCommandToDevice("control", msg)
                
                // 等待服务器确认（通过检查 pending 状态是否被清除）
                val confirmed = waitForConfirmation("threshold", timeoutMs)
                
                if (confirmed) {
                    success = true
                    // pending 已在 parseBemfaMessage 中清除
                    // 显示成功提示
                    _failureEvents.value = "阈值下发成功"
                } else {
                    attempt++
                }
            }
            
            // 循环结束后，再检查一次是否成功（给最后一次尝试留出确认时间）
            if (!success) {
                val finalCheck = waitForConfirmation("threshold", timeoutMs)
                if (finalCheck) {
                    success = true
                    _failureEvents.value = "阈值下发成功"
                }
            }
            
            // 如果所有重试都失败，回滚 UI
            if (!success) {
                _uiState.update {
                    it.copy(
                        tempLowerLimit = oldTempLower,
                        tempUpperLimit = oldTempUpper,
                        humLowerLimit = oldHumLower,
                        humUpperLimit = oldHumUpper
                    )
                }
                clearPendingOperation("threshold")
                _failureEvents.value = "阈值下发失败，请检查网络连接"
            }
        }
    }

    // 手动控制设备 (发送 KJR/GJR 等指令)
    fun toggleHeater(isOn: Boolean) {
        val currentState = _uiState.value.isHeaterOn
        
        sendCommandWithRetry(
            deviceKey = "heater",
            command = if (isOn) "KJR" else "GJR",
            updateUi = {
                _uiState.update { state ->
                    state.copy(
                        isHeaterOn = isOn,
                        // 互斥逻辑：打开加热时，关闭制冷
                        isCoolerOn = if (isOn) false else state.isCoolerOn
                    )
                }
            },
            rollbackUi = {
                _uiState.update { it.copy(isHeaterOn = currentState) }
            },
            pendingMsg = "正在控制加热器，请稍候..."
        )
    }

    fun toggleCooler(isOn: Boolean) {
        val currentState = _uiState.value.isCoolerOn
        
        sendCommandWithRetry(
            deviceKey = "cooler",
            command = if (isOn) "KZL" else "GZL",
            updateUi = {
                _uiState.update { state ->
                    state.copy(
                        isCoolerOn = isOn,
                        // 互斥逻辑：打开制冷时，关闭加热
                        isHeaterOn = if (isOn) false else state.isHeaterOn
                    )
                }
            },
            rollbackUi = {
                _uiState.update { it.copy(isCoolerOn = currentState) }
            },
            pendingMsg = "正在控制制冷器，请稍候..."
        )
    }

    fun toggleHumidifier(isOn: Boolean) {
        val currentState = _uiState.value.isHumidifierOn
        
        sendCommandWithRetry(
            deviceKey = "humidifier",
            command = if (isOn) "KJS" else "GJS",
            updateUi = {
                _uiState.update { state ->
                    state.copy(
                        isHumidifierOn = isOn,
                        // 互斥逻辑：打开加湿时，关闭除湿
                        isDehumidifierOn = if (isOn) false else state.isDehumidifierOn
                    )
                }
            },
            rollbackUi = {
                _uiState.update { it.copy(isHumidifierOn = currentState) }
            },
            pendingMsg = "正在控制加湿器，请稍候..."
        )
    }

    fun toggleDehumidifier(isOn: Boolean) {
        val currentState = _uiState.value.isDehumidifierOn
        
        sendCommandWithRetry(
            deviceKey = "dehumidifier",
            command = if (isOn) "KCS" else "GCS",
            updateUi = {
                _uiState.update { state ->
                    state.copy(
                        isDehumidifierOn = isOn,
                        // 互斥逻辑：打开除湿时，关闭加湿
                        isHumidifierOn = if (isOn) false else state.isHumidifierOn
                    )
                }
            },
            rollbackUi = {
                _uiState.update { it.copy(isDehumidifierOn = currentState) }
            },
            pendingMsg = "正在控制除湿器，请稍候..."
        )
    }

    // ViewModel 销毁时断开 TCP 连接
    override fun onCleared() {
        super.onCleared()
        // 取消重连任务
        reconnectJob?.cancel()
        // 断开连接并通知状态变化
        tcpClient.disconnect(notifyStateChange = true)
    }
}
