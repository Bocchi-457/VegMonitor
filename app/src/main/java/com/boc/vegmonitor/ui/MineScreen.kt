package com.boc.vegmonitor.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boc.vegmonitor.ui.theme.VegMonitorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(viewModel: MineViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(title = { Text("用户中心") },
            modifier = Modifier)

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
                    onLogout = { viewModel.logout() })
            } else {
                // 未登录界面（登录/注册 或 直接输入UID）
                LoginCard(state = state, viewModel = viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                DirectUidCard(state = state, viewModel = viewModel)
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState)
}

@Composable
fun LoggedInCard(username: String, uid: String, onLogout: () -> Unit) {
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
            Text(uid, fontSize = 14.sp)
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
                singleLine = true
            )
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.register() }, modifier = Modifier.weight(1f)
                    ) {
                        Text("注册")
                    }
                    Button(onClick = { viewModel.login() }, modifier = Modifier.weight(1f)) {
                        Text("登录")
                    }
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
                singleLine = true
            )

            Button(
                onClick = { viewModel.saveDirectUid() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("绑定")
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun MineScreenPreview() {
    VegMonitorTheme {
        MineScreen(viewModel = MineViewModel())
    }
}