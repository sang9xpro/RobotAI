package com.robotai.robot.identity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robotai.robot.auth.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
fun PersonalMemoryPanel(session: UserSession, roleId: Int, personId: String, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { AuthApi() }
    var snapshot by remember(personId, roleId) { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val path = "/api/robot/people/$personId/memory"
    fun request(method: String = "GET", suffix: String = "", body: JSONObject? = null) {
        scope.launch {
            busy=true;error=null
            try {
                val next=withContext(Dispatchers.IO) {
                    api.request(session.baseUrl,"$path$suffix?roleId=$roleId",session.token,method,body)
                    api.request(session.baseUrl,"$path?roleId=$roleId",session.token).getJSONObject("data")
                }
                snapshot=next
                if(method!="GET") onChanged()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error=e.message ?: "Không tải được ký ức" }
            finally { busy=false }
        }
    }
    LaunchedEffect(personId,roleId) { request() }
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Ký ức riêng của người này",style=MaterialTheme.typography.titleSmall)
        Text("Ken chỉ ghi nhận sở thích hoặc thói quen bạn tự nói rõ. Tắt ghi nhớ sẽ ngừng dùng và học ký ức này.")
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        snapshot?.let { data ->
            Row {
                Switch(data.optBoolean("enabled"), { request("PATCH",body=JSONObject().put("enabled",it)) },enabled=!busy)
                Text("Ghi nhớ")
            }
            val facts=data.optJSONArray("facts")
            if(facts==null || facts.length()==0) Text("Chưa có ký ức riêng")
            else for(index in 0 until facts.length()) {
                val fact=facts.getJSONObject(index)
                val key=fact.getString("key")
                var value by remember(personId,key,fact.optString("value")) { mutableStateOf(fact.optString("value")) }
                val label=when(key) {"preference"->"Sở thích";"hobby"->"Hoạt động yêu thích";"routine"->"Thói quen";else->"Cách trả lời"}
                OutlinedTextField(value,{value=it.take(160)},label={Text(label)},enabled=!busy)
                Row {
                    TextButton(enabled=!busy && value.isNotBlank(),onClick={request("PUT",body=JSONObject().put("key",key).put("value",value))}) { Text("Lưu") }
                    TextButton(enabled=!busy,onClick={request("DELETE","/$key")}) { Text("Xóa") }
                }
            }
            TextButton(enabled=!busy,onClick={request("DELETE")}) { Text("Xóa toàn bộ ký ức và tắt ghi nhớ") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
