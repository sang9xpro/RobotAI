package com.robotai.robot

import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.auth.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Explicit operator action: log into an existing isolated local acceptance account. */
class RobotStartTest {
    @Test fun loginLocalTestAccountForManualUse() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"robot-start.json")
        try {
            val fixture=JSONObject(file.readText())
            val account=fixture.getJSONObject("account")
            require(account.getString("username") in listOf("ken_accept_a","ken_accept_b"))
            require(fixture.getString("api")=="http://127.0.0.1:8091")
            require(fixture.getString("socket")=="ws://127.0.0.1:8092/ws/xiaozhi/v1/")
            val api=AuthApi()
            val session=api.login(fixture.getString("api"),fixture.getString("socket"),account.getString("username"),account.getString("password"))
            api.request(session.baseUrl,"/api/user/robot-profile/role",session.token,"PUT",JSONObject().put("roleId",account.getInt("roleId")))
            SessionStore(context).save(session.json())
            context.getSharedPreferences("login-endpoints",0).edit().putString("api",session.baseUrl).putString("socket",session.socketUrl).commit()
            assertNotNull(SessionStore(context).load())
            assertEquals(session.userId,api.check(session).userId)
        } finally { file.delete() }
    }
}
