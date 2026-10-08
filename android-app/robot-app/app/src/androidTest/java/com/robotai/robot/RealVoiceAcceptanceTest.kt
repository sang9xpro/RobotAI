package com.robotai.robot
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.robotai.domain.*
import com.robotai.robot.auth.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real mic captures a known WAV played through the phone speaker; actual Java providers and AudioTrack.
 * Private debug fixture credentials never enter instrumentation argv or logs. */
class RealVoiceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var vm: RobotViewModel
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(timeout: Long = 120000, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (::vm.isInitialized && vm.state.value.phase == Phase.ERROR) fail(vm.state.value.message)
            if (predicate()) return
            Thread.sleep(25)
        }
        fail("Timeout: ${if (::vm.isInitialized) vm.state.value.message else "initialization"}")
    }
    private fun input() {
        main { vm.listen() }; waitFor(15000) { vm.state.value.phase == Phase.LISTENING }; Thread.sleep(300)
        val player = MediaPlayer().apply { setDataSource(File(context.filesDir,"acceptance.wav").absolutePath); prepare() }
        try {
            if (InstrumentationRegistry.getArguments().getString("externalAudio") == "true") {
                val request=UUID.randomUUID().toString()
                val ack=File(context.filesDir,"acceptance-audio.done");ack.delete()
                instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","PLAY_INPUT:$request\n") })
                // ADB status delivery and speaker startup can lag. Wait for actual playback
                // completion instead of ending the recording after a guessed WAV duration.
                waitFor(20000) { ack.exists() && ack.readText().startsWith(request) }
                assertEquals("External speaker failed",request+":ok",ack.readText())
                Thread.sleep(500)
            } else {
                player.start();Thread.sleep(player.duration.toLong()+500)
            }
        } finally { player.release() }
        main { vm.finish() }
        waitFor(15000) { vm.state.value.caption.isNotBlank() }
        assertTrue("Acoustic input was not recognized: ${vm.state.value.caption}",vm.state.value.caption.count { it.isLetter() } >= 5)
        val transcript=vm.state.value.caption.lowercase()
        assertTrue("Acoustic input did not match the Vietnamese fixture: $transcript",
            listOf("dung lượng","lưu trữ","ram","bộ nhớ","tính toán").count { it in transcript } >= 2)
    }
    private fun completed(marker: String): JSONObject {
        waitFor { vm.state.value.phase == Phase.IDLE && vm.state.value.latencyMs != null && vm.state.value.downloaded > 0 }
        val s = vm.state.value
        assertTrue("Missing speech transcript: ${s.caption}",s.caption.count { it.isLetter() } >= 5)
        assertTrue("Wrong role/context: ${s.reply}",s.reply.startsWith(marker))
        assertTrue("Mixed role markers",listOf("KENA","KENB","KENC").filter { it!=marker }.none { it in s.reply })
        assertTrue(s.uploaded > 0 && s.downloaded > 0)
        return JSONObject().put("latencyMs",s.latencyMs).put("caption",s.caption).put("reply",s.reply)
            .put("marker",marker).put("uplink",s.uploaded).put("downlink",s.downloaded)
    }
    @Test fun realMicrophoneThirtyTurnsRoleAccountAbortAndNetworkRecovery() {
        val fixture=JSONObject(File(context.filesDir,"acceptance.json").readText()); val api=AuthApi()
        val account=fixture.getJSONArray("accounts").getJSONObject(0)
        var session=api.login(fixture.getString("api"),fixture.getString("socket"),account.getString("username"),account.getString("password"))
        val roles=account.getJSONArray("roles"); var role=roles.getInt(0)
        api.request(session.baseUrl,"/api/user/robot-profile/role",session.token,"PUT",JSONObject().put("roleId",role))
        SessionStore(context).save(session.json())
        val audio=context.getSystemService(AudioManager::class.java); val oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*.8).toInt(),0)
        val report=JSONObject().put("scope","phone microphone acoustic loopback; Java providers; AudioTrack progress")
            .put("latencyMethod","manual_end_input_to_first_non_silent_sample_presented")
        val turns=JSONArray(); report.put("turns",turns); val output=File(context.filesDir,"acceptance-result.json")
        val count=InstrumentationRegistry.getArguments().getString("turns","30")!!.toInt()
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        try {
            lateinit var auth:AuthViewModel
            scenario.onActivity { vm=ViewModelProvider(it)[RobotViewModel::class.java];auth=ViewModelProvider(it)[AuthViewModel::class.java] }
            waitFor(15000) { auth.state.value.session!=null && auth.state.value.roleId==role && vm.state.value.message=="Sẵn sàng kết nối" }
            main { vm.connect(session.socketUrl) }; waitFor(15000) { vm.state.value.connected }
            for (i in 0 until count) {
                val marker=if (i<count/2 || count==1) "KENA" else "KENB"
                if (marker=="KENB" && role!=roles.getInt(1)) {
                    role=roles.getInt(1)
                    main { vm.disconnect();auth.selectRole(role) }
                    waitFor(15000) { auth.state.value.roleId==role && !auth.state.value.busy && vm.state.value.message=="Sẵn sàng kết nối" }
                    main { vm.connect(session.socketUrl) }; waitFor(15000) { vm.state.value.connected }
                }
                input(); turns.put(completed(marker)); output.writeText(report.toString(2))
                instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","Completed real turn ${i+1}/$count\n") })
            }
            input(); waitFor { vm.state.value.phase==Phase.SPEAKING }; main { vm.interrupt() }
            waitFor(15000) { vm.state.value.connected && vm.state.value.phase==Phase.IDLE }
            assertEquals(0,vm.state.value.downloaded)
            input(); report.put("afterAbort",completed(if(role==roles.getInt(0)) "KENA" else "KENB"))
            main { vm.disconnect(); vm.connect("ws://127.0.0.1:1/ws/xiaozhi/v1/") }
            val deadline=SystemClock.elapsedRealtime()+15000
            while(SystemClock.elapsedRealtime()<deadline && vm.state.value.phase!=Phase.ERROR) Thread.sleep(25)
            assertEquals(Phase.ERROR,vm.state.value.phase); report.put("networkFailure","unavailable TCP endpoint")
            main { vm.connect(session.socketUrl) }; waitFor(15000) { vm.state.value.connected }
            input(); report.put("afterNetwork",completed(if(role==roles.getInt(0)) "KENA" else "KENB"))
            main { vm.disconnect();auth.logout() }
            waitFor(15000) { auth.state.value.session==null && vm.state.value.message=="Chưa đăng nhập" }
            val revokedEnd=SystemClock.elapsedRealtime()+15000
            var revoked=false
            while(!revoked && SystemClock.elapsedRealtime()<revokedEnd) {
                try { api.check(session);Thread.sleep(100) } catch(error:AuthFailure) { revoked=error.unauthorized }
            }
            assertTrue("Logout did not revoke the real token",revoked)
            val other=fixture.getJSONArray("accounts").getJSONObject(1)
            role=other.getJSONArray("roles").getInt(0)
            main { auth.login(fixture.getString("api"),fixture.getString("socket"),other.getString("username"),other.getString("password")) }
            waitFor(15000) { auth.state.value.session?.userId==other.getInt("userId") && auth.state.value.roleId==role && vm.state.value.message=="Sẵn sàng kết nối" }
            session=auth.state.value.session!!
            main { vm.connect(session.socketUrl) }; waitFor(15000) { vm.state.value.connected }
            input(); report.put("otherAccount",completed("KENC"))
            val latencies=(0 until turns.length()).map { turns.getJSONObject(it).getLong("latencyMs") }
            report.put("p50Ms",LatencyStats.percentile(latencies,50)).put("p95Ms",LatencyStats.percentile(latencies,95))
                .put("result","passed").put("voice","vi-VN-HoaiMyNeural")
        } catch(error:Throwable) { report.put("result","failed").put("error",error.message); throw error }
        finally {
            output.writeText(report.toString(2)); if(::vm.isInitialized) main { vm.disconnect() }
            scenario.close(); SessionStore(context).clear(); audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume,0)
        }
    }
}
