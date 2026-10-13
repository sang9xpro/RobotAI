package com.robotai.robot.identity

import android.content.Context
import ai.onnxruntime.*
import com.robotai.domain.TalkNetMfcc
import com.robotai.robot.data.ConversationReadiness
import java.nio.FloatBuffer
import java.security.MessageDigest

/** Fixed 1 second export: audio [1,100,13], gray face [1,25,112,112], logits [25,2]. */
class TalkNetActiveSpeaker(context: Context) : AutoCloseable {
    private val environment=OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val qualified: Boolean
    val calibrated get() = qualified || ConversationReadiness.calibrationMode()
    init {
        require(ConversationReadiness.passed(context,"activeSpeaker",allowCalibration=false) || ConversationReadiness.calibrationMode()) { "Active speaker requires handset qualification" }
        val bytes=context.assets.open("speaker/talknet.onnx").use { it.readBytes() }
        require(bytes.size in 1..100_000_000)
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        qualified=ConversationReadiness.passed(context,"activeSpeaker",hash,allowCalibration=false)
        session=OrtSession.SessionOptions().use { options -> options.setIntraOpNumThreads(2);environment.createSession(bytes,options) }
        require(session.inputNames==setOf("audio","visual"))
    }
    fun speaking(pcm: FloatArray, faces: List<FloatArray>): Boolean {
        if(!calibrated || pcm.size<16000 || faces.size!=25 || faces.any { it.size!=112*112 }) return false
        val mfcc=TalkNetMfcc.compute(pcm)
        val video=FloatArray(25*112*112)
        faces.forEachIndexed { index, frame -> frame.copyInto(video,index*112*112) }
        return OnnxTensor.createTensor(environment,FloatBuffer.wrap(mfcc),longArrayOf(1,100,13)).use { audio ->
            OnnxTensor.createTensor(environment,FloatBuffer.wrap(video),longArrayOf(1,25,112,112)).use { visual ->
                session.run(mapOf("audio" to audio,"visual" to visual)).use { output ->
                    val values=(output[0] as OnnxTensor).floatBuffer
                    if(values.remaining()!=50) return false
                    var count=0
                    repeat(25) { val silent=values.get();val speech=values.get();if(silent.isFinite() && speech.isFinite() && speech-silent>1f) count++ }
                    count>=20
                }
            }
        }.also { mfcc.fill(0f);video.fill(0f) }
    }
    override fun close() { session.close() }
}
