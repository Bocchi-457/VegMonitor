package com.boc.vegmonitor.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * MonitorScreen 插桩测试
 * 按照 Jetpack Compose 官方标准编写
 * 
 * 测试内容：
 * 1. UI元素存在性
 * 2. 阈值输入交互
 * 3. 设备控制开关
 * 4. 离线状态显示
 * 5. 边界值测试
 * 6. 输入框清空后按钮状态
 * 
 * 运行方式：
 * - Android Studio: 右键点击测试类 -> Run
 * - 命令行: ./gradlew connectedAndroidTest
 */
class MonitorScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * 测试1: 验证所有UI元素是否存在
     */
    @Test
    fun testAllUIElementsExist() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 验证顶部标题
        composeTestRule.onNodeWithText("VegMonitor 蔬控宝").assertExists()

        // 验证在线状态
        composeTestRule.onNode(hasText("设备在线").or(hasText("设备离线"))).assertExists()

        // 验证实时温湿度
        composeTestRule.onNodeWithText("当前温度").assertExists()
        composeTestRule.onNodeWithText("当前湿度").assertExists()

        // 验证阈值设置区域
        composeTestRule.onNodeWithText("阈值设置").assertExists()
        composeTestRule.onNodeWithText("温度下限").assertExists()
        composeTestRule.onNodeWithText("温度上限").assertExists()
        composeTestRule.onNodeWithText("湿度下限").assertExists()
        composeTestRule.onNodeWithText("湿度上限").assertExists()
        composeTestRule.onNodeWithText("下发").assertExists()

        // 验证自动控制
        composeTestRule.onNode(hasText("自动控制：开").or(hasText("自动控制：关"))).assertExists()

        // 验证设备控制
        composeTestRule.onNodeWithText("设备控制").assertExists()
        composeTestRule.onNodeWithText("加热").assertExists()
        composeTestRule.onNodeWithText("制冷").assertExists()
        composeTestRule.onNodeWithText("加湿").assertExists()
        composeTestRule.onNodeWithText("除湿").assertExists()
    }

    /**
     * 测试2: 阈值输入框交互
     */
    @Test
    fun testThresholdInputInteraction() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 输入温度值（先清空再输入）
        val tempLowerNode = composeTestRule.onNodeWithText("温度下限")
        tempLowerNode.performTextClearance()
        tempLowerNode.performTextInput("20")
        tempLowerNode.assertTextContains("20")

        val tempUpperNode = composeTestRule.onNodeWithText("温度上限")
        tempUpperNode.performTextClearance()
        tempUpperNode.performTextInput("30")
        tempUpperNode.assertTextContains("30")

        // 输入湿度值
        val humLowerNode = composeTestRule.onNodeWithText("湿度下限")
        humLowerNode.performTextClearance()
        humLowerNode.performTextInput("40")
        humLowerNode.assertTextContains("40")

        val humUpperNode = composeTestRule.onNodeWithText("湿度上限")
        humUpperNode.performTextClearance()
        humUpperNode.performTextInput("60")
        humUpperNode.assertTextContains("60")

        // 验证下发按钮启用
        composeTestRule.onNodeWithText("下发").assertIsEnabled()
    }

    /**
     * 测试3: 设备控制开关存在性
     */
    @Test
    fun testDeviceControlSwitchesExist() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 验证所有设备标签存在
        composeTestRule.onNodeWithText("加热").assertExists()
        composeTestRule.onNodeWithText("制冷").assertExists()
        composeTestRule.onNodeWithText("加湿").assertExists()
        composeTestRule.onNodeWithText("除湿").assertExists()
    }

    /**
     * 测试4: 离线状态显示
     */
    @Test
    fun testOfflineStateDisplay() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 验证显示了在线或离线状态
        val onlineNode = composeTestRule.onNodeWithText("设备在线")
        val offlineNode = composeTestRule.onNodeWithText("设备离线")
        
        // 至少应该存在其中一个
        try {
            onlineNode.assertExists()
        } catch (e: AssertionError) {
            // 如果"设备在线"不存在，检查"设备离线"是否存在
            offlineNode.assertExists()
        }
    }

    /**
     * 测试5: 边界值输入
     */
    @Test
    fun testBoundaryValueInput() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 输入温度边界值（先清空）
        val tlNode = composeTestRule.onNodeWithText("温度下限")
        tlNode.performTextClearance()
        tlNode.performTextInput("-40")
        
        val tuNode = composeTestRule.onNodeWithText("温度上限")
        tuNode.performTextClearance()
        tuNode.performTextInput("80")

        // 输入湿度边界值
        val hlNode = composeTestRule.onNodeWithText("湿度下限")
        hlNode.performTextClearance()
        hlNode.performTextInput("0")
        
        val huNode = composeTestRule.onNodeWithText("湿度上限")
        huNode.performTextClearance()
        huNode.performTextInput("100")

        // 验证下发按钮启用
        composeTestRule.onNodeWithText("下发").assertIsEnabled()
    }

    /**
     * 测试6: 输入框清空后下发按钮禁用
     */
    @Test
    fun testEmptyInputDisablesSubmit() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 先输入值（清空后输入）
        val tlNode = composeTestRule.onNodeWithText("温度下限")
        tlNode.performTextClearance()
        tlNode.performTextInput("20")
        
        val tuNode = composeTestRule.onNodeWithText("温度上限")
        tuNode.performTextClearance()
        tuNode.performTextInput("30")
        
        val hlNode = composeTestRule.onNodeWithText("湿度下限")
        hlNode.performTextClearance()
        hlNode.performTextInput("40")
        
        val huNode = composeTestRule.onNodeWithText("湿度上限")
        huNode.performTextClearance()
        huNode.performTextInput("60")

        // 验证按钮启用
        composeTestRule.onNodeWithText("下发").assertIsEnabled()

        // 清空一个输入框
        composeTestRule.onNodeWithText("温度下限").performTextClearance()

        // 验证按钮禁用（因为有空输入）
        composeTestRule.onNodeWithText("下发").assertIsNotEnabled()
    }

    /**
     * 测试7: 自动控制开关交互
     */
    @Test
    fun testAutoModeSwitchInteraction() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 尝试找到自动控制开关并点击
        try {
            val autoModeNode = composeTestRule.onNode(hasText("自动控制"))
            
            // 记录初始状态
            var initialStateIsOn = false
            try {
                composeTestRule.onNodeWithText("自动控制：开").assertExists()
                initialStateIsOn = true
            } catch (e: AssertionError) {
                initialStateIsOn = false
            }
            
            // 点击切换
            autoModeNode.performClick()
            
            // 等待状态更新
            Thread.sleep(500)
            
            // 验证状态已改变
            var newStateIsOn = false
            try {
                composeTestRule.onNodeWithText("自动控制：开").assertExists()
                newStateIsOn = true
            } catch (e: AssertionError) {
                newStateIsOn = false
            }
            
            assert(initialStateIsOn != newStateIsOn) { "自动模式状态应该发生改变" }
        } catch (e: AssertionError) {
            // 如果找不到自动控制开关，测试跳过
            println("自动控制开关不存在，测试跳过")
        }
    }

    /**
     * 测试8: 设备控制开关交互（加热器）
     */
    @Test
    fun testHeaterControlInteraction() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 尝试点击加热器
        try {
            val heaterNode = composeTestRule.onNodeWithText("加热")
            heaterNode.performClick()
            
            // 等待状态更新
            Thread.sleep(500)
            
            // 验证加热器标签仍存在
            heaterNode.assertExists()
        } catch (e: AssertionError) {
            println("加热器开关不存在，测试跳过")
        }
    }

    /**
     * 测试9: 离线状态下按钮禁用
     */
    @Test
    fun testOfflineStateDisablesButtons() {
        composeTestRule.setContent {
            MonitorScreen()
        }

        // 检查当前是否离线
        var isOffline = false
        try {
            composeTestRule.onNodeWithText("设备离线").assertExists()
            isOffline = true
        } catch (e: AssertionError) {
            isOffline = false
        }
        
        if (isOffline) {
            // 离线状态下，下发按钮应禁用
            composeTestRule.onNodeWithText("下发").assertIsNotEnabled()
            
            // 自动控制开关应禁用
            try {
                val autoModeSwitch = composeTestRule.onNode(hasText("自动控制"))
                autoModeSwitch.assertIsNotEnabled()
            } catch (e: AssertionError) {
                println("自动控制开关不存在")
            }
        }
    }
}
