package com.boc.vegmonitor.data.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class BemfaTcpClient {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    // 用于管理接收消息和心跳的协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var listenJob: Job? = null

    // 向外暴露接收到的消息
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()
    
    // 连接状态回调
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    // 连接巴法云并订阅主题
    suspend fun connectAndSubscribe(uid: String) = withContext(Dispatchers.IO) {
        try {
            // 巴法云创客云 TCP 地址和端口
            socket = Socket("bemfa.com", 8344)
            writer = PrintWriter(socket!!.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            // 1. 发送订阅指令 (订阅 control, data, online 主题， 主题后添加/app不会占用设备在线状态)
            val subCmd = "cmd=1&uid=$uid&topic=control/app,data/app,online/app\r\n"
            writer?.print(subCmd)
            writer?.flush()

            // 2. 开启监听循环
            startListening()

            // 3. 开启心跳机制 (防止被服务器踢下线)
            startHeartbeat()
            
            // 4. 通知连接成功
            onConnectionStateChanged?.invoke(true)

        } catch (e: Exception) {
            e.printStackTrace()
            // 连接失败时清理资源（不通知，因为后面会单独通知）
            disconnect(notifyStateChange = false)
            // 通知连接失败
            onConnectionStateChanged?.invoke(false)
        }
    }

    // 发送指令 (推送到巴法云)
    suspend fun sendCommand(uid: String, topic: String, msg: String) = withContext(Dispatchers.IO) {
        try {
            // 根据官方文档，推送数据格式为：cmd=2&uid=xxx&topic=xxx&msg=xxx\r\n
            val cmd = "cmd=2&uid=$uid&topic=$topic&msg=$msg\r\n"
            writer?.print(cmd)
            writer?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 持续监听服务器消息
    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                while (isActive && socket?.isConnected == true) {
                    val line = reader?.readLine()
                    if (line != null) {
                        _incomingMessages.emit(line)
                    } else {
                        // 返回 null 代表连接可能断开，退出循环
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 监听异常，通知断开（但不再次调用 disconnect，避免递归）
                onConnectionStateChanged?.invoke(false)
            }
            // 注意：不在 finally 中调用 disconnect，由外部控制资源清理
            // 这样可以避免 readLine 抛出异常时再次关闭流导致的递归调用
        }
    }

    // 心跳机制：每 60 秒发送一次 ping（官方建议 60 秒，超过 65 秒会断线）
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && socket?.isConnected == true) {
                delay(60000) // 官方建议 60 秒
                try {
                    writer?.print("ping\r\n")
                    writer?.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 心跳发送失败，通知断开
                    onConnectionStateChanged?.invoke(false)
                    break
                }
            }
        }
    }

    // 主动断开连接
    fun disconnect(notifyStateChange: Boolean = false) {
        heartbeatJob?.cancel()
        listenJob?.cancel()
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        socket = null
        // 只有在显式要求时才通知状态变化，避免递归调用
        if (notifyStateChange) {
            onConnectionStateChanged?.invoke(false)
        }
    }
    
    /**
     * 检查当前连接状态
     * @return true 表示连接正常，false 表示已断开
     */
    fun isConnected(): Boolean {
        return socket?.isConnected == true && !socket?.isClosed!!
    }
}