package com.boc.vegmonitor.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boc.vegmonitor.data.dao.UserDao
import com.boc.vegmonitor.data.entity.User
import com.boc.vegmonitor.data.network.BemfaApiService
import com.boc.vegmonitor.data.network.LoginRequest
import com.boc.vegmonitor.data.network.PhoneLoginRequest
import com.boc.vegmonitor.data.repository.LoginFailureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MineViewModel(
    private val userDao: UserDao? = null,
    private val apiService: BemfaApiService? = null,
    private val failureRepository: LoginFailureRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MineUiState())
    val uiState: StateFlow<MineUiState> = _uiState.asStateFlow()

    // 登录失败次数限制
    private var failedLoginCount = 0
    private var lastFailedTime: Long = 0
    private val maxFailedAttempts = 3
    
    // 锁定时间配置（毫秒）
    private val lockDurations = listOf(
        60_000L,   // 第1次锁定：1分钟
        120_000L,  // 第2次锁定：2分钟
        300_000L   // 第3次及以后：5分钟
    )

    init {
        if (userDao != null) {
            viewModelScope.launch {
                userDao.getLoggedInUser().collect { user ->
                    if (user != null && user.bemfaUid.isNotEmpty()) {
                        _uiState.update {
                            it.copy(isLoggedIn = true, currentUid = user.bemfaUid, currentUsername = user.username)
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoggedIn = false, currentUid = "", currentUsername = "")
                        }
                    }
                }
            }
        }
    }

    fun onUsernameChange(newValue: String) = _uiState.update { it.copy(usernameInput = newValue) }
    fun onPasswordChange(newValue: String) = _uiState.update { it.copy(passwordInput = newValue) }
    fun onDirectUidChange(newValue: String) = _uiState.update { it.copy(directUidInput = newValue) }
    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    
    /**
     * 显示复制成功提示
     */
    fun showCopySuccessMessage() {
        _uiState.update { it.copy(successMessage = "私钥已复制到剪贴板") }
    }

    fun login() {
        val username = _uiState.value.usernameInput
        val password = _uiState.value.passwordInput
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "账号或密码不能为空") }
            return
        }

        if (apiService == null || userDao == null) {
            _uiState.update { it.copy(errorMessage = "预览模式不支持真实登录") }
            return
        }

        viewModelScope.launch {
            // 1. 检查是否有持久化的失败记录
            val record = failureRepository?.getFailureRecord(username)
            if (record != null) {
                val now = System.currentTimeMillis()
                if (now < record.lockEndTime) {
                    // 还在锁定期内
                    val remainingSeconds = ((record.lockEndTime - now) / 1000).toInt()
                    Log.d("MineViewModel", "从持久化记录恢复锁定状态 - 剩余: ${remainingSeconds}秒")
                    _uiState.update { 
                        it.copy(
                            isLocked = true,
                            lockRemainingSeconds = remainingSeconds,
                            errorMessage = "登录失败次数过多，请${remainingSeconds}秒后重试"
                        ) 
                    }
                    failedLoginCount = record.failureCount
                    lastFailedTime = record.lastFailureTime
                    startLockCountdownFrom(record.lockEndTime)
                    return@launch
                } else {
                    // 锁定已过期，但保留失败计数用于递增锁定等级
                    Log.d("MineViewModel", "锁定已过期，恢复失败计数: ${record.failureCount}")
                    failedLoginCount = record.failureCount
                }
            }

            // 2. 执行登录逻辑
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 判断输入类型：邮箱或手机号
                val response = if (isEmail(username)) {
                    // 邮箱登录
                    apiService.login(LoginRequest(email = username, password = password))
                } else {
                    // 手机号登录
                    apiService.phoneLogin(PhoneLoginRequest(phone = username, password = password))
                }
                
                // 检查响应：外层 code 和内层 data.code 都为 0 才表示成功
                if (response.code == 0 && response.data?.code == 0 && response.data.uid != null) {
                    // 登录成功，重置失败计数并清除持久化记录
                    failedLoginCount = 0
                    _uiState.update { it.copy(isLocked = false, lockRemainingSeconds = 0) }
                    userDao.insertUser(User(username = username, bemfaUid = response.data.uid))
                    _uiState.update { it.copy(successMessage = "登录成功", usernameInput = "", passwordInput = "") }
                    
                    // 清除持久化的失败记录
                    failureRepository?.clearFailureRecord(username)
                    Log.d("MineViewModel", "登录成功，清除失败记录")
                } else {
                    // 登录失败，增加计数
                    handleLoginFailure(username, "账号或密码有误")
                }
            } catch (e: Exception) {
                // 网络错误也计入失败次数
                handleLoginFailure(username, "网络错误：${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * 处理登录失败
     */
    private fun handleLoginFailure(account: String, baseMessage: String) {
        failedLoginCount++
        lastFailedTime = System.currentTimeMillis()
        
        Log.d("MineViewModel", "登录失败 - 账号: $account, 计数: $failedLoginCount, 消息: $baseMessage")
        
        if (failedLoginCount >= maxFailedAttempts) {
            // 达到最大失败次数，开始锁定
            val lockDuration = getLockDuration()
            val lockMinutes = lockDuration / 60_000
            val lockEndTime = lastFailedTime + lockDuration
            val lockLevel = minOf(failedLoginCount - maxFailedAttempts + 1, 3)
            
            Log.d("MineViewModel", "触发锁定 - 等级: $lockLevel, 时长: ${lockMinutes}分钟, 结束时间: $lockEndTime")
            
            _uiState.update { 
                it.copy(
                    errorMessage = "$baseMessage，已锁定${lockMinutes}分钟",
                    isLocked = true,
                    lockRemainingSeconds = (lockDuration / 1000).toInt()
                ) 
            }
            
            // 保存到 DataStore
            viewModelScope.launch {
                val record = com.boc.vegmonitor.data.repository.LoginFailureRecord(
                    account = account,
                    failureCount = failedLoginCount,
                    lockLevel = lockLevel,
                    lastFailureTime = lastFailedTime,
                    lockEndTime = lockEndTime
                )
                failureRepository?.updateFailureRecord(record)
            }
            
            // 启动倒计时
            startLockCountdown(lockDuration)
        } else {
            // 还未达到最大次数，显示剩余机会
            val remainingAttempts = maxFailedAttempts - failedLoginCount
            val errorMsg = "$baseMessage，还剩${remainingAttempts}次尝试机会"
            Log.d("MineViewModel", "显示错误提示: $errorMsg")
            _uiState.update { 
                it.copy(errorMessage = errorMsg) 
            }
            
            // 保存中间状态到 DataStore
            viewModelScope.launch {
                val record = com.boc.vegmonitor.data.repository.LoginFailureRecord(
                    account = account,
                    failureCount = failedLoginCount,
                    lockLevel = 0,
                    lastFailureTime = lastFailedTime,
                    lockEndTime = 0
                )
                failureRepository?.updateFailureRecord(record)
            }
        }
    }
    
    /**
     * 获取当前锁定时间
     */
    private fun getLockDuration(): Long {
        val lockIndex = minOf(failedLoginCount - maxFailedAttempts, lockDurations.size - 1)
        return lockDurations[maxOf(lockIndex, 0)]
    }
    
    /**
     * 启动锁定倒计时
     */
    private fun startLockCountdown(totalDuration: Long) {
        viewModelScope.launch {
            var remainingMs = totalDuration
            while (remainingMs > 0) {
                kotlinx.coroutines.delay(1000) // 每秒更新
                remainingMs -= 1000
                val remainingSeconds = (remainingMs / 1000).toInt()
                
                _uiState.update { 
                    it.copy(lockRemainingSeconds = maxOf(remainingSeconds, 0)) 
                }
            }
            
            // 倒计时结束，解锁
            _uiState.update { 
                it.copy(isLocked = false, lockRemainingSeconds = 0, errorMessage = null) 
            }
        }
    }
    
    /**
     * 从指定结束时间启动倒计时（用于恢复持久化状态）
     */
    private fun startLockCountdownFrom(lockEndTime: Long) {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= lockEndTime) {
                    // 倒计时结束
                    _uiState.update { 
                        it.copy(isLocked = false, lockRemainingSeconds = 0, errorMessage = null) 
                    }
                    break
                }
                
                val remainingSeconds = ((lockEndTime - now) / 1000).toInt()
                _uiState.update { 
                    it.copy(lockRemainingSeconds = maxOf(remainingSeconds, 0)) 
                }
                
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    /**
     * 判断字符串是否为邮箱格式
     */
    private fun isEmail(input: String): Boolean {
        // 简单的邮箱正则表达式
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(input)
    }

    fun saveDirectUid() {
        val uid = _uiState.value.directUidInput
        if (uid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "私钥不能为空") }
            return
        }
        
        if (userDao == null) {
            _uiState.update { it.copy(errorMessage = "预览模式不支持保存") }
            return
        }
        
        viewModelScope.launch {
            userDao.insertUser(User(username = "本地直接绑定", bemfaUid = uid))
            _uiState.update { it.copy(successMessage = "绑定成功", directUidInput = "") }
        }
    }

    fun logout() {
        if (userDao != null) {
            viewModelScope.launch {
                userDao.clearUser()
            }
        }
    }
}

// 对应的 Factory
class MineViewModelFactory(
    private val userDao: UserDao,
    private val apiService: BemfaApiService,
    private val failureRepository: LoginFailureRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MineViewModel(userDao, apiService, failureRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
