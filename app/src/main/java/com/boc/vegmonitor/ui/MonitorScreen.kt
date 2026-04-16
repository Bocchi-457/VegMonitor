package com.boc.vegmonitor.ui

import androidx.compose.runtime.Composable


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boc.vegmonitor.R
import com.boc.vegmonitor.ui.theme.VegMonitorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel = viewModel(),
    snackbarHostState: SnackbarHostState
) {
    // 监听 ViewModel 中的状态
    val state by viewModel.uiState.collectAsState()
    
    // 监听失败事件和 pending 事件
    val failureEvent by viewModel.failureEvents.collectAsState()
    val pendingEvent by viewModel.pendingEvents.collectAsState()
    val throttleEvent by viewModel.throttleEvents.collectAsState()
    
    // 监听失败事件并显示 Snackbar
    LaunchedEffect(failureEvent) {
        failureEvent?.let { message ->
            snackbarHostState.showSnackbar(message)
            // 显示后清除事件，避免重复显示
            viewModel.clearFailureEvent()
        }
    }
    
    // 监听 pending 事件并显示 Snackbar
    LaunchedEffect(pendingEvent) {
        pendingEvent?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            // 显示后清除事件
            viewModel.clearPendingEvent()
        }
    }
    
    // 监听节流事件并显示 Snackbar
    LaunchedEffect(throttleEvent) {
        throttleEvent?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            // 显示后清除事件
            viewModel.clearThrottleEvent()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
                // 实时温湿度卡片
                item {
                    RealTimeTempHumCard(
                        currentTemp = state.currentTemp,
                        currentHumidity = state.currentHumidity,
                        hasReceivedData = state.hasReceivedData
                    )
                }

                // 阈值设置卡片
                item {
                    ThresholdSettingCard(
                        tempLower = state.tempLowerLimit,
                        tempUpper = state.tempUpperLimit,
                        humLower = state.humLowerLimit,
                        humUpper = state.humUpperLimit,
                        isOnline = state.isOnline,  // 传递在线状态
                        onSetThreshold = { newTempLower, newTempUpper, newHumLower, newHumUpper ->
                            viewModel.setThreshold(
                                newTempLower = newTempLower,
                                newTempUpper = newTempUpper,
                                newHumLower = newHumLower,
                                newHumUpper = newHumUpper
                            )
                        })
                }

                // 控制模式切换卡片
                item {
                    ControlModeSwitchCard(
                        isAutoMode = state.isAutoMode,
                        isOnline = state.isOnline,  // 传递在线状态
                        onToggleMode = { viewModel.toggleAutoMode(it) })
                }

            // 设备手动控制区卡片
            item {
                DeviceControlGridCard(
                    isAutoMode = state.isAutoMode,
                    isOnline = state.isOnline,  // 传递在线状态
                    isHeaterOn = state.isHeaterOn,
                    isCoolerOn = state.isCoolerOn,
                    isHumidifierOn = state.isHumidifierOn,
                    isDehumidifierOn = state.isDehumidifierOn,
                    onToggleHeater = { viewModel.toggleHeater(it) },
                    onToggleCooler = { viewModel.toggleCooler(it) },
                    onToggleHumidifier = { viewModel.toggleHumidifier(it) },
                    onToggleDehumidifier = { viewModel.toggleDehumidifier(it) })
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdSettingCard(
    tempLower: Float?,
    tempUpper: Float?,
    humLower: Float?,
    humUpper: Float?,
    isOnline: Boolean,  // 新增：在线状态
    onSetThreshold: (Float, Float, Float, Float) -> Unit  // 修改：接收 4 个参数
) {
    var minTempInput by remember { mutableStateOf(tempLower?.toString() ?: "") }
    var maxTempInput by remember { mutableStateOf(tempUpper?.toString() ?: "") }
    var minHumidityInput by remember { mutableStateOf(humLower?.toString() ?: "") }
    var maxHumidityInput by remember { mutableStateOf(humUpper?.toString() ?: "") }
    
    // 获取 FocusManager 用于清除焦点
    val focusManager = LocalFocusManager.current

    // 当外部传入的阈值数据更新时，同步到输入框
    LaunchedEffect(tempLower, tempUpper, humLower, humUpper) {
        minTempInput = tempLower?.toString() ?: ""
        maxTempInput = tempUpper?.toString() ?: ""
        minHumidityInput = humLower?.toString() ?: ""
        maxHumidityInput = humUpper?.toString() ?: ""
    }
    
    /**
     * 验证并过滤输入的数字字符串
     * @param input 用户输入的原始字符串
     * @param allowNegative 是否允许负数
     * @return 过滤后的合法字符串
     */
    fun validateNumberInput(input: String, allowNegative: Boolean = true): String {
        // 空字符串直接返回
        if (input.isEmpty()) return ""
        
        // 只允许数字、小数点、负号
        val filtered = input.filter { it.isDigit() || it == '.' || it == '-' }
        
        // 检查负号：只能在开头，且只能有一个
        val negativeCount = filtered.count { it == '-' }
        if (negativeCount > 1) return filtered.dropLastWhile { it == '-' } // 移除多余的负号
        if (negativeCount == 1 && !filtered.startsWith("-")) {
            // 负号不在开头，移除它
            return filtered.replace("-", "")
        }
        
        // 检查小数点：只能有一个
        val dotCount = filtered.count { it == '.' }
        if (dotCount > 1) {
            // 保留第一个小数点，移除后面的
            val firstDotIndex = filtered.indexOf('.')
            return filtered.substring(0, firstDotIndex + 1) + 
                   filtered.substring(firstDotIndex + 1).replace(".", "")
        }
        
        // 特殊处理：-. 或 -0. 等格式
        if (filtered == "-" || filtered == "-.") return filtered
        
        return filtered
    }
    
    /**
     * 检查输入是否在有效范围内
     * @param input 输入字符串
     * @param min 最小值
     * @param max 最大值
     * @return null 表示合法，否则返回错误消息
     */
    fun checkRange(input: String, min: Float, max: Float, fieldName: String): String? {
        if (input.isEmpty()) return null // 空输入不检查
        
        val value = input.toFloatOrNull()
        if (value == null) {
            return "$fieldName 格式不正确"
        }
        
        if (value < min || value > max) {
            return "$fieldName 必须在 $min 到 $max 之间"
        }
        
        return null
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.threshold_setting),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // 温度阈值设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = minTempInput,
                    onValueChange = {
                        minTempInput = validateNumberInput(it, allowNegative = true)
                    },
                    label = { Text("温度下限") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = checkRange(minTempInput, -40f, 80f, "温度下限") != null ||
                              (minTempInput.toFloatOrNull() != null && maxTempInput.toFloatOrNull() != null && 
                               minTempInput.toFloat() > maxTempInput.toFloat())
                )

                OutlinedTextField(
                    value = maxTempInput,
                    onValueChange = {
                        maxTempInput = validateNumberInput(it, allowNegative = true)
                    },
                    label = { Text("温度上限") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = checkRange(maxTempInput, -40f, 80f, "温度上限") != null ||
                              (minTempInput.toFloatOrNull() != null && maxTempInput.toFloatOrNull() != null && 
                               minTempInput.toFloat() > maxTempInput.toFloat())
                )
            }

            // 温度错误提示
            val tempLowerError = checkRange(minTempInput, -40f, 80f, "温度下限")
            val tempUpperError = checkRange(maxTempInput, -40f, 80f, "温度上限")
            if (tempLowerError != null) {
                Text(text = tempLowerError, color = Color.Red, fontSize = 12.sp)
            } else if (tempUpperError != null) {
                Text(text = tempUpperError, color = Color.Red, fontSize = 12.sp)
            } else if (minTempInput.toFloatOrNull() != null && maxTempInput.toFloatOrNull() != null && 
                       minTempInput.toFloat() > maxTempInput.toFloat()) {
                Text(text = "温度下限不能高于温度上限", color = Color.Red, fontSize = 12.sp)
            }

            // 湿度阈值设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = minHumidityInput,
                    onValueChange = {
                        minHumidityInput = validateNumberInput(it, allowNegative = false)
                    },
                    label = { Text("湿度下限") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = checkRange(minHumidityInput, 0f, 100f, "湿度下限") != null ||
                              (minHumidityInput.toFloatOrNull() != null && maxHumidityInput.toFloatOrNull() != null && 
                               minHumidityInput.toFloat() > maxHumidityInput.toFloat())
                )

                OutlinedTextField(
                    value = maxHumidityInput,
                    onValueChange = {
                        maxHumidityInput = validateNumberInput(it, allowNegative = false)
                    },
                    label = { Text("湿度上限") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = checkRange(maxHumidityInput, 0f, 100f, "湿度上限") != null ||
                              (minHumidityInput.toFloatOrNull() != null && maxHumidityInput.toFloatOrNull() != null && 
                               minHumidityInput.toFloat() > maxHumidityInput.toFloat())
                )
            }

            // 湿度错误提示
            val humLowerError = checkRange(minHumidityInput, 0f, 100f, "湿度下限")
            val humUpperError = checkRange(maxHumidityInput, 0f, 100f, "湿度上限")
            if (humLowerError != null) {
                Text(text = humLowerError, color = Color.Red, fontSize = 12.sp)
            } else if (humUpperError != null) {
                Text(text = humUpperError, color = Color.Red, fontSize = 12.sp)
            } else if (minHumidityInput.toFloatOrNull() != null && maxHumidityInput.toFloatOrNull() != null && 
                       minHumidityInput.toFloat() > maxHumidityInput.toFloat()) {
                Text(text = "湿度下限不能高于湿度上限", color = Color.Red, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    // 验证输入
                    val tempLowerError = checkRange(minTempInput, -40f, 80f, "温度下限")
                    val tempUpperError = checkRange(maxTempInput, -40f, 80f, "温度上限")
                    val humLowerError = checkRange(minHumidityInput, 0f, 100f, "湿度下限")
                    val humUpperError = checkRange(maxHumidityInput, 0f, 100f, "湿度上限")
                    
                    // 如果有任何错误，不执行下发
                    if (tempLowerError != null || tempUpperError != null || humLowerError != null || humUpperError != null) {
                        return@Button
                    }
                    
                    // 检查上下限关系
                    val minTemp = minTempInput.toFloatOrNull()
                    val maxTemp = maxTempInput.toFloatOrNull()
                    val minHum = minHumidityInput.toFloatOrNull()
                    val maxHum = maxHumidityInput.toFloatOrNull()
                    
                    if (minTemp != null && maxTemp != null && minTemp > maxTemp) {
                        return@Button
                    }
                    if (minHum != null && maxHum != null && minHum > maxHum) {
                        return@Button
                    }
                    
                    // 所有验证通过，清除焦点并执行下发
                    focusManager.clearFocus()
                    
                    val newMinTemp = minTemp ?: (tempLower ?: 20.0f)
                    val newMaxTemp = maxTemp ?: (tempUpper ?: 25.0f)
                    val newMinHumidity = minHum ?: (humLower ?: 60.0f)
                    val newMaxHumidity = maxHum ?: (humUpper ?: 70.0f)
                    onSetThreshold(newMinTemp, newMaxTemp, newMinHumidity, newMaxHumidity)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline && minTempInput.isNotEmpty() && maxTempInput.isNotEmpty() && 
                          minHumidityInput.isNotEmpty() && maxHumidityInput.isNotEmpty()
            ) {
                Icon(
                    Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.issue))
            }
        }
    }
}

// 实时温湿度显示卡片
@Composable
fun RealTimeTempHumCard(
    currentTemp: Float?,
    currentHumidity: Float?,
    hasReceivedData: Boolean
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 温度显示
            TempDisplayColumn(currentTemp = currentTemp, hasReceivedData = hasReceivedData)
            // 湿度显示
            HumidityDisplayColumn(currentHumidity = currentHumidity, hasReceivedData = hasReceivedData)
        }
    }
}

// 温度显示列
@Composable
private fun TempDisplayColumn(currentTemp: Float?, hasReceivedData: Boolean) {
    Column(modifier = Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Thermostat, contentDescription = null, tint = Color(0xFFFF5722)
            )
            Text(stringResource(R.string.current_temp), color = Color.Gray)
        }
        Text(
            text = if (currentTemp != null) "$currentTemp °C" else "-",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 湿度显示列
@Composable
private fun HumidityDisplayColumn(currentHumidity: Float?, hasReceivedData: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.WaterDrop, contentDescription = null, tint = Color(0xFF03A9F4)
            )
            Text(stringResource(R.string.current_hum), color = Color.Gray)
        }
        Text(
            text = if (currentHumidity != null) "$currentHumidity %" else "-",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 控制模式切换卡片
@Composable
fun ControlModeSwitchCard(
    isAutoMode: Boolean,
    isOnline: Boolean,  // 新增：在线状态
    onToggleMode: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自动控制：${if (isAutoMode) "开" else "关"}",
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = isAutoMode,
                onCheckedChange = onToggleMode,
                enabled = isOnline  // 离线时禁用
            )
        }
    }
}

// 设备控制网格卡片
@Composable
fun DeviceControlGridCard(
    isAutoMode: Boolean,
    isOnline: Boolean,  // 新增：在线状态
    isHeaterOn: Boolean,
    isCoolerOn: Boolean,
    isHumidifierOn: Boolean,
    isDehumidifierOn: Boolean,
    onToggleHeater: (Boolean) -> Unit,
    onToggleCooler: (Boolean) -> Unit,
    onToggleHumidifier: (Boolean) -> Unit,
    onToggleDehumidifier: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.device_control),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // FlowRow 会自动根据宽度换行
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DeviceControlCard(
                    deviceName = stringResource(R.string.heat),
                    icon = Icons.Rounded.LocalFireDepartment,
                    isOn = isHeaterOn,
                    isEnabled = isOnline && !isAutoMode,  // 离线或自动模式下禁用
                    onToggle = onToggleHeater,
                    iconColor = Color(0xFFFA2929),
                    modifier = Modifier.weight(1f)
                )
                DeviceControlCard(
                    deviceName = stringResource(R.string.refrigeration),
                    icon = Icons.Rounded.AcUnit,
                    isOn = isCoolerOn,
                    isEnabled = isOnline && !isAutoMode,  // 离线或自动模式下禁用
                    onToggle = onToggleCooler,
                    iconColor = Color(0xFF03A9F4),
                    modifier = Modifier.weight(1f)
                )
                DeviceControlCard(
                    deviceName = stringResource(R.string.humidification),
                    icon = Icons.Rounded.Opacity,
                    isOn = isHumidifierOn,
                    isEnabled = isOnline && !isAutoMode,  // 离线或自动模式下禁用
                    onToggle = onToggleHumidifier,
                    iconColor = Color(0xFFFBC02D),
                    modifier = Modifier.weight(1f)
                )
                DeviceControlCard(
                    deviceName = stringResource(R.string.dehumidification),
                    icon = Icons.Rounded.Air,
                    isOn = isDehumidifierOn,
                    isEnabled = isOnline && !isAutoMode,  // 离线或自动模式下禁用
                    onToggle = onToggleDehumidifier,
                    iconColor = Color(0xFF388E3C),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// 抽取出来的小组件：设备控制卡片
@Composable
fun DeviceControlCard(
    modifier: Modifier = Modifier,
    deviceName: String,
    icon: ImageVector,
    isOn: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    iconColor: Color
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f))
            {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(deviceName)
            }
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonitorScreenPreview() {
    VegMonitorTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        MonitorScreen(snackbarHostState = snackbarHostState)
    }
}