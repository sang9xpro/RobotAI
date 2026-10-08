package com.robotai.robot
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.auth.*
import com.robotai.robot.companion.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Two separate instrumentation processes, with force-stop between them. */
class ReminderRestartTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun prepare() {
        val f=JSONObject(File(context.filesDir,"acceptance.json").readText());val a=f.getJSONArray("accounts").getJSONObject(0)
        val s=AuthApi().login(f.getString("api"),f.getString("socket"),a.getString("username"),a.getString("password"))
        SessionStore(context).save(s.json());LocalCompanionStore.activate(context,s)
        val store=LocalCompanionStore(context,LocalCompanionStore.key(s))
        store.add("Process restart acceptance",System.currentTimeMillis()+3600000)
        File(context.filesDir,"acceptance-reminder-id").writeText(store.reminders().last().id)
    }
    @Test fun verifyAfterForceStop() {
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        val report=JSONObject()
        try {
            val s=UserSession.fromJson(SessionStore(context).load()!!)
            val id=File(context.filesDir,"acceptance-reminder-id").readText()
            val store=LocalCompanionStore(context,LocalCompanionStore.key(s))
            assertTrue(store.reminders().any { it.id==id && it.text=="Process restart acceptance" })
            lateinit var auth:AuthViewModel
            scenario.onActivity { auth=androidx.lifecycle.ViewModelProvider(it)[AuthViewModel::class.java] }
            val end=SystemClock.elapsedRealtime()+15000
            while(SystemClock.elapsedRealtime()<end && (auth.state.value.session==null || auth.state.value.roleId==null)) Thread.sleep(50)
            assertNotNull("Account restore failed",auth.state.value.session)
            assertNotNull("Role restore failed",auth.state.value.roleId)
            assertEquals(store.scope,context.getSharedPreferences("companion-active",0).getString("scope",null))
            assertTrue(LocalCompanionStore(context,store.scope+"-other").reminders().none { it.id==id })
            store.remove(id)
            assertTrue(LocalCompanionStore(context,store.scope).reminders().none { it.id==id })
            report.put("result","passed").put("checks",org.json.JSONArray(listOf("reminder persisted across actual process force-stop/relaunch","active account restored","foreign scope isolated","delete persisted")))
        } catch(error:Throwable) { report.put("result","failed").put("error",error.message);throw error }
        finally { File(context.filesDir,"acceptance-result.json").writeText(report.toString(2));scenario.close();SessionStore(context).clear() }
    }
}
