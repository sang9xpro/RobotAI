package com.robotai.robot.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(auth: AuthViewModel) {
    val state by auth.state.collectAsState()
    val prefs = LocalContext.current.getSharedPreferences("login-endpoints", 0)
    var base by remember { mutableStateOf(prefs.getString("api", "http://127.0.0.1:8091")!!) }
    var socket by remember { mutableStateOf(prefs.getString("socket", "ws://127.0.0.1:8092/ws/xiaozhi/v1/")!!) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Đăng nhập RobotAI", style = MaterialTheme.typography.headlineMedium)
        Text("Dùng tài khoản backend để tiếp tục hội thoại và cấu hình của bạn.")
        OutlinedTextField(base, { base = it }, label = { Text("Backend API") }, singleLine = true,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("login-api"))
        OutlinedTextField(socket, { socket = it }, label = { Text("WebSocket") }, singleLine = true,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("login-socket"))
        OutlinedTextField(username, { username = it }, label = { Text("Tài khoản") }, singleLine = true,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("login-username"))
        OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("login-password"))
        Text(state.message, modifier = Modifier.testTag("auth-status"))
        Button(onClick = {
            prefs.edit().putString("api", base.trim()).putString("socket", socket.trim()).apply()
            auth.login(base, socket, username, password)
            password = ""
        }, enabled = !state.busy && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.testTag("login-submit")) {
            Text(if (state.busy) "Đang xử lý…" else "Đăng nhập")
        }
        if (state.canRestore) OutlinedButton(onClick = { auth.restore() }, enabled = !state.busy, modifier = Modifier.testTag("restore-session")) { Text("Thử khôi phục phiên") }
        if (state.busy) CircularProgressIndicator()
    }
}

@Composable
fun AccountPanel(auth: AuthViewModel, disconnect: () -> Unit) {
    val state by auth.state.collectAsState()
    val session = state.session ?: return
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(session.name, modifier = Modifier.testTag("account-name"))
            TextButton(onClick = { disconnect(); auth.logout() }, modifier = Modifier.testTag("logout")) { Text("Đăng xuất") }
        }
        Text(state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-status"))
        Row {
            Box {
                TextButton(onClick = { expanded = true }, enabled = state.roles.isNotEmpty() && !state.busy) {
                    Text(state.roles.find { it.id == state.roleId }?.name ?: "Chọn cấu hình")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    state.roles.forEach { role -> DropdownMenuItem(text = { Text(role.name) }, onClick = {
                        expanded = false; disconnect(); auth.selectRole(role.id)
                    }) }
                }
            }
            TextButton(onClick = { auth.refreshProfile() }, enabled = !state.busy) { Text("Làm mới") }
        }
    }
}
