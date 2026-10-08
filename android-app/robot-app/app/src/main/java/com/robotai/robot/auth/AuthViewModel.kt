package com.robotai.robot.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class RoleOption(val id: Int, val name: String, val voice: String)
data class AuthUiState(
    val busy: Boolean = true, val session: UserSession? = null,
    val message: String = "Đang kiểm tra phiên đăng nhập…",
    val canRestore: Boolean = false, val roles: List<RoleOption> = emptyList(), val roleId: Int? = null
)
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val api = AuthApi()
    private val mutable = MutableStateFlow(AuthUiState())
    val state = mutable.asStateFlow()
    private var epoch = 0
    private var job: Job? = null
    init { restore() }

    fun restore() {
        val attempt = ++epoch
        job?.cancel()
        job = viewModelScope.launch {
            mutable.value = AuthUiState()
            try {
                val stored = withContext(Dispatchers.IO) { store.load() }
                if (stored == null) {
                    if (attempt == epoch) mutable.value = AuthUiState(busy = false, message = "Đăng nhập để dùng cấu hình và hội thoại của bạn")
                    return@launch
                }
                val checked = withContext(Dispatchers.IO) { api.check(UserSession.fromJson(stored)) }
                if (attempt == epoch) accept(checked)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt != epoch) return@launch
                val expired = e is AuthFailure && e.unauthorized
                if (expired) store.clear()
                mutable.value = AuthUiState(busy = false, canRestore = !expired,
                    message = if (expired) "Phiên đã hết hạn. Hãy đăng nhập lại." else "Chưa kết nối được backend. Thử lại để khôi phục phiên.")
            }
        }
    }
    fun login(base: String, socket: String, username: String, password: String) {
        if (state.value.busy) return
        val attempt = ++epoch
        job?.cancel()
        store.clear()
        mutable.value = AuthUiState(busy = true, message = "Đang đăng nhập…")
        job = viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { api.login(base.trim(), socket.trim(), username.trim(), password) }
                if (attempt == epoch) accept(session)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt == epoch) mutable.value = AuthUiState(busy = false,
                    message = if (e is AuthFailure || e is IllegalArgumentException) e.message ?: "Đăng nhập thất bại" else "Không kết nối được backend")
            }
        }
    }
    private suspend fun accept(session: UserSession) {
        store.save(session.json())
        mutable.value = AuthUiState(busy = false, session = session, message = "Đã đăng nhập")
        refreshProfile()
    }
    fun refreshProfile() {
        val session = state.value.session ?: return
        val attempt = epoch
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/user/robot-profile", session.token).getJSONObject("data") }
                if (attempt == epoch) applyProfile(data)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt != epoch) return@launch
                if (e is AuthFailure && e.unauthorized) expire()
                else mutable.value = state.value.copy(message = "Chưa tải được cấu hình. Nhấn Làm mới.")
            }
        }
    }
    private fun applyProfile(data: JSONObject) {
        val list = data.getJSONArray("roles")
        val roles = (0 until list.length()).map {
            val role = list.getJSONObject(it)
            RoleOption(role.getInt("roleId"), role.getString("roleName"), role.optString("voiceName", ""))
        }
        mutable.value = state.value.copy(roles = roles,
            roleId = if (data.isNull("roleId")) null else data.getInt("roleId"),
            message = if (roles.isEmpty()) "Tài khoản chưa có cấu hình robot. Tạo role ở trang quản trị rồi Làm mới." else "Cấu hình đã đồng bộ")
    }
    fun selectRole(id: Int) {
        val session = state.value.session ?: return
        if (state.value.busy) return
        val attempt = epoch
        mutable.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/user/robot-profile/role", session.token, "PUT", JSONObject().put("roleId", id)).getJSONObject("data") }
                if (attempt == epoch) { applyProfile(data); mutable.value = state.value.copy(busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt == epoch) {
                    if (e is AuthFailure && e.unauthorized) expire()
                    else mutable.value = state.value.copy(busy = false, message = "Không đổi được cấu hình")
                }
            }
        }
    }
    fun expire() {
        epoch++
        job?.cancel()
        store.clear()
        mutable.value = AuthUiState(busy = false, message = "Phiên đã hết hạn. Hãy đăng nhập lại.")
    }
    fun logout() {
        val session = state.value.session
        val attempt = ++epoch
        job?.cancel()
        store.clear()
        mutable.value = AuthUiState(busy = false, message = "Đã đăng xuất")
        if (session != null) viewModelScope.launch {
            try { withContext(Dispatchers.IO) { api.request(session.baseUrl, "/api/user/logout", session.token, "POST") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt == epoch) mutable.value = state.value.copy(message = "Đã đăng xuất trên máy. Chưa thu hồi được phiên ở server do lỗi kết nối.")
            }
        }
    }
}
