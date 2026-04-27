package com.boc.vegmonitor.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boc.vegmonitor.ui.theme.VegMonitorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    viewModel: MineViewModel,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.uiState.collectAsState()

    // 处理提示信息
    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isLoggedIn) {
            // 已登录界面
            LoggedInCard(
                username = state.currentUsername,
                uid = state.currentUid,
                onLogout = { viewModel.logout() },
                onCopySuccess = {
                    viewModel.showCopySuccessMessage()
                }
            )
        } else {
            // 未登录界面（登录/注册 或 直接输入UID）
            LoginCard(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
            DirectUidCard(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun LoggedInCard(username: String, uid: String, onLogout: () -> Unit, onCopySuccess: () -> Unit) {
    var isUidVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("当前账号", color = Color.Gray)
            Text(username, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("巴法云私钥 (UID):", color = Color.Gray)
            
            // 私钥显示区域（支持可见性切换和复制）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = isUidVisible) {
                        // 点击复制私钥
                        clipboardManager.setText(AnnotatedString(uid))
                        // 通知父组件显示提示
                        onCopySuccess()
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = if (isUidVisible) uid else "•".repeat(32),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { isUidVisible = !isUidVisible }) {
                    Icon(
                        imageVector = if (isUidVisible) 
                            Icons.Rounded.VisibilityOff 
                        else 
                            Icons.Rounded.Visibility,
                        contentDescription = if (isUidVisible) "隐藏私钥" else "显示私钥",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录 / 解除绑定")
            }
        }
    }
}

@Composable
fun LoginCard(state: MineUiState, viewModel: MineViewModel) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("巴法云账号登录", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = state.usernameInput,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("邮箱/手机号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.errorMessage?.contains("账号") == true,
                supportingText = if (state.errorMessage?.contains("账号") == true) {
                    { Text(state.errorMessage!!) }
                } else null
            )
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) 
                                Icons.Rounded.VisibilityOff 
                            else 
                                Icons.Rounded.Visibility,
                            contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                }
            )
            Button(
                onClick = { viewModel.login() },
                enabled = !state.isLoading && !state.isLocked,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(if (state.isLocked) "请等待${state.lockRemainingSeconds}秒" else "登录")
                }
            }
        }
    }
}

@Composable
fun DirectUidCard(state: MineUiState, viewModel: MineViewModel) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("跳过登录，直接绑定私钥", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("如果您已有巴法云私钥(UID)，可直接输入绑定。", color = Color.Gray, fontSize = 12.sp)

            OutlinedTextField(
                value = state.directUidInput,
                onValueChange = { viewModel.onDirectUidChange(it) },
                label = { Text("输入巴法云 UID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isUidValidating,
                isError = state.errorMessage?.contains("私钥") == true,
                supportingText = if (state.errorMessage?.contains("私钥") == true) {
                    { Text(state.errorMessage!!) }
                } else null
            )

            Button(
                onClick = { viewModel.saveDirectUid() },
                enabled = !state.isUidValidating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isUidValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("绑定")
                }
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun MineScreenPreview() {
    VegMonitorTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        MineScreen(
            viewModel = MineViewModel(),
            snackbarHostState = snackbarHostState
        )
    }
}