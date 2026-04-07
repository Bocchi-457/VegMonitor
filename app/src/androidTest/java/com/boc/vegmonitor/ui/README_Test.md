# MonitorScreen 插桩测试说明

## 📋 测试概述

已按照 Jetpack Compose 官方标准编写插桩测试（Instrumented Tests），测试以下功能：

1. ✅ UI元素存在性验证
2. ✅ 阈值输入框交互
3. ✅ 设备控制开关存在性
4. ✅ 离线状态显示
5. ✅ 边界值输入测试
6. ✅ 输入框清空后按钮状态

## 🚀 运行测试

### 方法1: Android Studio
1. 打开 `app/src/androidTest/java/com/boc/vegmonitor/ui/MonitorScreenInstrumentedTest.kt`
2. 右键点击类名或单个测试方法
3. 选择 "Run 'MonitorScreenInstrumentedTest'"

### 方法2: 命令行
```bash
./gradlew connectedAndroidTest
```

### 方法3: 运行单个测试
```bash
./gradlew connectedAndroidTest --tests com.boc.vegmonitor.ui.MonitorScreenInstrumentedTest.testAllUIElementsExist
```

## 📱 测试要求

- **真实设备**或**Android模拟器**（API 24+）
- 设备需要连接到开发机器
- 启用USB调试（如果使用真机）

## 📊 测试覆盖范围

### 测试1: testAllUIElementsExist
验证所有UI组件是否正确渲染：
- 顶部标题栏
- 在线状态指示器
- 实时温湿度显示
- 阈值设置区域（4个输入框 + 下发按钮）
- 自动控制开关
- 4个设备控制卡片

### 测试2: testThresholdInputInteraction
测试阈值输入功能：
- 温度上下限输入
- 湿度上下限输入
- 输入后下发按钮状态

### 测试3: testDeviceControlSwitchesExist
验证设备控制区域：
- 加热、制冷、加湿、除湿标签
- 对应的开关组件

### 测试4: testOfflineStateDisplay
验证在线/离线状态显示逻辑

### 测试5: testBoundaryValueInput
测试边界值输入：
- 温度：-40°C ~ 80°C
- 湿度：0% ~ 100%

### 测试6: testEmptyInputDisablesSubmit
测试输入框清空后的按钮状态

## ⚠️ 注意事项

1. **测试环境**: 
   - 测试会启动真实的 MainActivity
   - 需要网络连接才能获取完整状态
   - 如果TCP未连接，可能显示"设备离线"

2. **测试隔离**:
   - 每个测试方法独立运行
   - 不依赖其他测试的状态
   - 使用 `composeTestRule.setContent` 重置UI

3. **已知限制**:
   - 无法模拟TCP连接状态（需要Mock框架支持）
   - 无法测试真实的网络重试机制
   - 互斥逻辑测试需要ViewModel状态控制

## 🔧 后续扩展建议

如需更完整的测试覆盖，可以考虑：

1. **添加ViewModel Mock**:
   - 使用 Mockito 或 MockK
   - 模拟不同的在线/离线状态
   - 模拟设备控制响应

2. **添加集成测试**:
   - 测试真实的TCP连接
   - 测试数据解析逻辑
   - 测试重试机制

3. **添加性能测试**:
   - UI重组性能
   - 内存泄漏检测
   - 网络请求优化

## 📝 测试结果解读

- ✅ **PASS**: 测试通过，功能正常
- ❌ **FAIL**: 测试失败，需要检查：
  - UI元素是否正确渲染
  - 文本内容是否匹配
  - 组件状态是否符合预期
  - 设备是否在线

## 🐛 常见问题

**Q: 测试显示"找不到元素"**
A: 检查：
- 应用是否正常启动
- 网络连接是否正常
- 文本内容是否与代码一致

**Q: 测试超时**
A: 检查：
- 设备响应速度
- 网络延迟
- 增加等待时间

**Q: IDE显示编译错误但文件已修改**
A: 清理缓存：
- Build -> Clean Project
- Build -> Rebuild Project
- File -> Invalidate Caches / Restart
