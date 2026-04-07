package com.boc.vegmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boc.vegmonitor.data.dao.UserDao
import com.boc.vegmonitor.data.entity.User
import com.boc.vegmonitor.data.network.BemfaApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MineViewModel(
    private val userDao: UserDao? = null,
    private val apiService: BemfaApiService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MineUiState())
    val uiState: StateFlow<MineUiState> = _uiState.asStateFlow()

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
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = apiService.login(username, password)
                if (response.code == 0 && response.uid != null) {
                    userDao.insertUser(User(username = username, bemfaUid = response.uid))
                    _uiState.update { it.copy(successMessage = "登录成功", usernameInput = "", passwordInput = "") }
                } else {
                    _uiState.update { it.copy(errorMessage = response.message ?: "登录失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "网络错误：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun register() {
        val username = _uiState.value.usernameInput
        val password = _uiState.value.passwordInput
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "账号或密码不能为空") }
            return
        }

        if (apiService == null) {
            _uiState.update { it.copy(errorMessage = "预览模式不支持真实注册") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = apiService.register(username, password)
                if (response.code == 0) {
                    _uiState.update { it.copy(successMessage = "注册成功，请点击登录") }
                } else {
                    _uiState.update { it.copy(errorMessage = response.message ?: "注册失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "网络错误：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
    private val apiService: BemfaApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MineViewModel(userDao, apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}