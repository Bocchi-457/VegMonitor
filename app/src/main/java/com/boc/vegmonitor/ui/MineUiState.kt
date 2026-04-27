package com.boc.vegmonitor.ui

data class MineUiState(
    val isLoggedIn: Boolean = false,
    val currentUid: String = "",
    val currentUsername: String = "",

    // 界面输入状态
    val usernameInput: String = "",
    val passwordInput: String = "",
    val directUidInput: String = "",

    // 网络请求与提示状态
    val isLoading: Boolean = false,
    val isUidValidating: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    
    // 登录锁定状态
    val isLocked: Boolean = false,
    val lockRemainingSeconds: Int = 0
)