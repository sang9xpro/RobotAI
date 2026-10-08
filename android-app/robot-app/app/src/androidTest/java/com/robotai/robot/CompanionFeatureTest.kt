package com.robotai.robot
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.robot.auth.*
import com.robotai.robot.companion.*
import com.robotai.domain.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real platform persistence, camera, local models, weather and authenticated memory APIs. */
class CompanionFeatureTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun companionUtilitiesVisionAndSafeRobotPort() {
        val i=InstrumentationRegistry.getInstrumentation(); val context=i.targetContext
        val fixture=JSONObject(File(context.filesDir,"acceptance.json").readText()); val a=fixture.getJSONArray("accounts").getJSONObject(0)
        val api=AuthApi();val s=api.login(fixture.getString("api"),fixture.getString("socket"),a.getString("username"),a.getString("password"))
        SessionStore(context).save(s.json()); val report=JSONObject(); val steps=org.json.JSONArray();report.put("checks",steps)
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("companion-face").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("companion-face").performClick()
            compose.waitUntil(3000) { compose.onAllNodesWithText("Vui").fetchSemanticsNodes().isNotEmpty() };steps.put("touch mood")
            lateinit var vm:RobotViewModel
            scenario.onActivity { vm=androidx.lifecycle.ViewModelProvider(it)[RobotViewModel::class.java] }
            i.runOnMainSync { vm.connect(s.socketUrl) }
            compose.waitUntil(15000) { vm.state.value.connected && vm.robotTools.initialized && vm.robotTools.listed }
            steps.put("real Java socket MCP initialize and tools/list")
            i.runOnMainSync { vm.disconnect() }
            val key=LocalCompanionStore.key(s);val store=LocalCompanionStore(context,key)
            store.add("acceptance reminder",System.currentTimeMillis()+3600000)
            val r=store.reminders().last()
            assertTrue(LocalCompanionStore(context,key).reminders().any { it.id==r.id })
            val foreign=LocalCompanionStore(context,key+"-other");assertTrue(foreign.reminders().none { it.id==r.id })
            ReminderReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED));assertTrue(store.reminders().any { it.id==r.id })
            store.remove(r.id);assertTrue(LocalCompanionStore(context,key).reminders().none { it.id==r.id });steps.put("reminder reload, boot scheduling and account isolation")
            var weatherFailure:String?=null
            try { val weather=CompanionApi().weather(s,10.7769,106.7009);assertTrue(weather.contains("Open-Meteo"));steps.put("real weather API") }
            catch (error: javax.net.ssl.SSLException) { weatherFailure=error.javaClass.simpleName;report.put("weatherError",weatherFailure);steps.put("weather blocked by device TLS trust") }
            catch (error: AuthFailure) { weatherFailure=error.message;report.put("weatherError",weatherFailure);steps.put("weather API unavailable; remaining independent checks continue") }
            val role=a.getJSONArray("roles").getInt(0)
            val data=api.request(s.baseUrl,"/api/robot/memories?roleId=$role",s.token).getJSONObject("data")
            val memories=data.getJSONArray("list")
            if(memories.length()>0) {
                val m=(0 until memories.length()).map { memories.getJSONObject(it) }.firstOrNull { it.getString("summary")=="Disposable acceptance memory" } ?: error("Missing disposable fixture")
                val old=m.getString("summary");val id=m.getLong("id")
                api.request(s.baseUrl,"/api/robot/memories/$role/$id",s.token,"PUT",JSONObject().put("text",old+"\nAcceptance edit."))
                val edited=api.request(s.baseUrl,"/api/robot/memories?roleId=$role",s.token).getJSONObject("data").getJSONArray("list")
                assertTrue((0 until edited.length()).any { edited.getJSONObject(it).getString("summary").contains("Acceptance edit.") })
                api.request(s.baseUrl,"/api/robot/memories/$role/$id",s.token,"PUT",JSONObject().put("text",old))
                assertEquals(1,api.request(s.baseUrl,"/api/robot/memories/$role/$id",s.token,"DELETE").getInt("data"))
                val after=api.request(s.baseUrl,"/api/robot/memories?roleId=$role",s.token).getJSONObject("data").getJSONArray("list")
                assertTrue((0 until after.length()).none { after.getJSONObject(it).getLong("id")==id })
                steps.put("memory read/edit/delete with real database")
            } else steps.put("memory list empty")
            val other=fixture.getJSONArray("accounts").getJSONObject(1)
            val foreignRole=other.getJSONArray("roles").getInt(0)
            assertThrows(AuthFailure::class.java) { api.request(s.baseUrl,"/api/robot/memories?roleId=$foreignRole",s.token) };steps.put("foreign role rejected")
            compose.onNodeWithTag("tab-Thị giác").performClick()
            compose.waitUntil(30000) { compose.onAllNodes(hasText("Camera bật", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            steps.put("awake camera frames and local face/gesture models")
            val screenshot=i.uiAutomation.takeScreenshot()
            File(context.filesDir,"companion-vision.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) };screenshot.recycle()
            compose.onNodeWithTag("camera-capture").performScrollTo().performClick()
            compose.onNodeWithTag("vision-send").performScrollTo().assertIsNotEnabled();steps.put("capture stays local and upload disabled without consent")
            val image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            val bytes=java.io.ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG,90,it) }.toByteArray();image.recycle()
            val answer=kotlinx.coroutines.runBlocking { CompanionApi().vision(s,bytes,"Hình này chủ yếu có màu gì? Trả lời ngắn bằng tiếng Việt.",true) }
            assertTrue("Vision answer: $answer",answer.lowercase().contains("đỏ"));steps.put("synthetic red image through real VisionService and vision model")
            val tools=DeviceRobotTools();tools.enabled=true
            val now=System.currentTimeMillis()
            fun request(id:Int,expires:Long)=JSONObject().put("id",id).put("method","tools/call").put("params",JSONObject().put("name","self.robot.action").put("arguments",JSONObject().put("action","FORWARD").put("duration_ms",500).put("speed",20).put("issued_at_ms",now).put("expires_at_ms",expires)))
            assertTrue(tools.handle(request(1,now+1000),true)!!.has("result"))
            assertTrue(tools.handle(request(1,now+1000),true)!!.has("error"))
            assertEquals(RobotAction.STOP,tools.transport.action)
            assertTrue(tools.handle(request(2,now-1),true)!!.has("error"))
            tools.enabled=false;assertTrue(tools.handle(request(3,now+1000),true)!!.has("error"));steps.put("MCP consent, replay, expiry and stop")
            assertNull("Weather HTTPS verification failed: $weatherFailure",weatherFailure)
            report.put("result","passed")
        } catch(error:Throwable) { report.put("result","failed").put("error",error.message);throw error }
        finally { File(context.filesDir,"acceptance-result.json").writeText(report.toString(2));scenario.close();SessionStore(context).clear() }
    }
}
