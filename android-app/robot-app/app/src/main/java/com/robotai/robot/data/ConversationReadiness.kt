package com.robotai.robot.data

import android.content.Context
import android.os.Build
import com.robotai.domain.ConversationBenchmarks
import org.json.JSONObject

/** Calibration is tied to this handset and this app version, never an optimistic preference. */
object ConversationReadiness {
    @Volatile private var calibrating=false
    /** Instrumentation can measure real inputs before qualification, only in debug builds. */
    fun calibrationSession(): AutoCloseable {
        check(com.robotai.robot.BuildConfig.DEBUG)
        check(!calibrating)
        calibrating=true
        return AutoCloseable { calibrating=false }
    }
    fun calibrationMode() = com.robotai.robot.BuildConfig.DEBUG && calibrating
    fun passed(context: Context, capability: String, modelHash: String? = null, allowCalibration: Boolean = true): Boolean = runCatching {
        if(allowCalibration && calibrationMode()) return true
        val file = java.io.File(context.filesDir,"conversation-readiness.json")
        if(file.length() !in 1..16384) return false
        val report = JSONObject(file.readText())
        if(report.optString("device") != Build.FINGERPRINT || report.optInt("versionCode") != com.robotai.robot.BuildConfig.VERSION_CODE) return false
        val data = report.getJSONObject(capability)
        if(modelHash != null && data.optString("modelSha256") != modelHash) return false
        ConversationBenchmarks(data.getInt("utterances"),data.getInt("interruptions"),data.getInt("runMinutes"),
            data.getDouble("precision"),data.getDouble("recall"),data.getLong("firstAudioP95Ms"),data.getLong("interruptP95Ms")).passed
    }.getOrDefault(false)
}
