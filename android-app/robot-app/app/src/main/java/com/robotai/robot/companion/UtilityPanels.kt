package com.robotai.robot.companion
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.robotai.robot.auth.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable fun UtilityPanel(session: UserSession) {
    val context = LocalContext.current
    val store = remember(session.userId, session.baseUrl) { LocalCompanionStore(context, LocalCompanionStore.key(session)) }
    val scope = rememberCoroutineScope()
    var reminders by remember { mutableStateOf(store.reminders()) }
    var text by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }
    var lat by remember { mutableStateOf(store.prefs.getString("latitude", "10.7769")!!) }
    var lon by remember { mutableStateOf(store.prefs.getString("longitude", "106.7009")!!) }
    var weather by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) message = "Chưa được phép thông báo. Nhắc việc vẫn lưu nhưng cần bật quyền để nhận thông báo."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nhắc việc", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(text, { text = it.take(200) }, label = { Text("Nội dung") })
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Sau bao nhiêu phút") })
        Button(onClick = {
            try {
                val m = minutes.toLong(); require(m in 1..525600)
                store.add(text, System.currentTimeMillis() + m * 60000); reminders = store.reminders(); text = ""
                message = "Đã lưu nhắc việc."
                if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) { message = "Nhập nội dung và số phút từ 1 đến 525600." }
        }) { Text("Lưu nhắc việc") }
        Text(message)
        Text("Lưu theo tài khoản, khôi phục khi mở app/khởi động máy. Android có thể giao thông báo muộn khi tiết kiệm pin.", style = MaterialTheme.typography.bodySmall)
        reminders.forEach { r ->
            Row {
                Text("${r.text}\n${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(r.at))}", Modifier.weight(1f))
                TextButton(onClick = { store.remove(r.id); reminders = store.reminders() }) { Text("Xóa") }
            }
        }
        Divider()
        Text("Thời tiết", style = MaterialTheme.typography.titleLarge)
        Text("Mặc định: TP.HCM. Nhập tọa độ nơi bạn muốn xem; app không tự lấy vị trí.")
        OutlinedTextField(lat, { lat = it }, label = { Text("Vĩ độ") })
        OutlinedTextField(lon, { lon = it }, label = { Text("Kinh độ") })
        Button(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    val latitude = lat.toDouble(); val longitude = lon.toDouble()
                    weather = withContext(Dispatchers.IO) { CompanionApi().weather(session, latitude, longitude) }
                    store.prefs.edit().putString("latitude", lat).putString("longitude", lon).apply()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { weather = "Không có dữ liệu mới: ${e.message}" }
                finally { busy = false }
            }
        }) { Text(if (busy) "Đang tải…" else "Cập nhật thời tiết") }
        Text(weather)
    }
}

@Composable fun MemoryPanel(session: UserSession, roleId: Int?, disconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { AuthApi() }
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    suspend fun refresh() {
        if (roleId == null) { status = "Chọn nhân vật trước"; return }
        val data = withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/robot/memories?roleId=$roleId&pageNo=$page", session.token).getJSONObject("data") }
        val a = data.getJSONArray("list"); items = (0 until a.length()).map { a.getJSONObject(it) }; total = data.getLong("total")
        status = if (items.isEmpty()) "Chưa có ký ức tóm tắt cho nhân vật này." else "${total} ký ức · trang $page"
    }
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Không tải được ký ức" }
            finally { busy = false }
        }
    }
    LaunchedEffect(roleId, page) { operation { refresh() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ký ức của nhân vật", style = MaterialTheme.typography.titleLarge)
        Text("Sửa/xóa bản tóm tắt sẽ ngắt hội thoại để lần kết nối sau đọc lại. Lịch sử hội thoại gốc là dữ liệu riêng.")
        Text(status)
        Button(enabled = !busy, onClick = { operation { refresh() } }) { Text("Làm mới ký ức") }
        items.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.getString("summary"))
                    Row {
                        TextButton(enabled = !busy, onClick = { editing = item; draft = item.getString("summary") }) { Text("Sửa") }
                        TextButton(enabled = !busy, onClick = { deleting = item }) { Text("Xóa") }
                    }
                }
            }
        }
        Row {
            TextButton(enabled = !busy && page > 1, onClick = { page-- }) { Text("Trước") }
            TextButton(enabled = !busy && page * 20 < total, onClick = { page++ }) { Text("Sau") }
        }
    }
    if (editing != null) AlertDialog(onDismissRequest = { editing = null }, title = { Text("Sửa ký ức") }, text = {
        OutlinedTextField(draft, { draft = it.take(8000) })
    }, confirmButton = { TextButton(enabled = draft.isNotBlank() && !busy, onClick = {
        val id = editing!!.getLong("id"); editing = null; disconnect()
        operation { withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/robot/memories/$roleId/$id", session.token, "PUT", JSONObject().put("text", draft)) }; refresh() }
    }) { Text("Lưu") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Hủy") } })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Xóa bản tóm tắt này?") },
        text = { Text("Thao tác này không xóa lịch sử hội thoại gốc.") }, confirmButton = { TextButton(onClick = {
            val id = deleting!!.getLong("id"); deleting = null; disconnect()
            operation { withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/robot/memories/$roleId/$id", session.token, "DELETE") }; refresh() }
        }) { Text("Xóa") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Hủy") } })
}
