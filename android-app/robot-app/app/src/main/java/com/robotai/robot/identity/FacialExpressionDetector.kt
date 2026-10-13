package com.robotai.robot.identity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.*
import com.google.mlkit.vision.face.Face
import com.robotai.domain.ExpressionScores
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.abs

/** Owned by one dedicated worker; the pinned graph outputs seven logits in a [1, 7] tensor. */
class FacialExpressionDetector(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        val bytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == MODEL_SHA256) { "Model biểu cảm không đúng phiên bản" }
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            env.createSession(bytes, options)
        }
        try {
            check((session.inputInfo["data"]?.info as? TensorInfo)?.shape?.contentEquals(longArrayOf(1,3,112,112)) == true)
            check((session.outputInfo["label"]?.info as? TensorInfo)?.shape?.contentEquals(longArrayOf(1,7)) == true)
        } catch (e: Exception) { session.close(); throw e }
    }

    fun infer(crop: Bitmap): FloatArray {
        require(crop.width == 112 && crop.height == 112 && !crop.isRecycled)
        val pixels = IntArray(112*112)
        crop.getPixels(pixels,0,112,0,0,112,112)
        val input = FloatArray(3*pixels.size)
        pixels.forEachIndexed { i, pixel ->
            input[i] = Color.red(pixel)/127.5f - 1f
            input[i+pixels.size] = Color.green(pixel)/127.5f - 1f
            input[i+2*pixels.size] = Color.blue(pixel)/127.5f - 1f
        }
        return OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,112,112)).use { tensor ->
            session.run(mapOf("data" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST") val rows = result.get("label").get().value as Array<FloatArray>
                ExpressionScores.fromLogits(rows.single()) ?: error("Điểm biểu cảm không hợp lệ")
            }
        }
    }

    override fun close() { session.close() }

    companion object {
        const val MODEL_PATH = "identity/facial_expression_mobilefacenet.onnx"
        const val MODEL_SHA256 = "4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9"
        /** Crop is independent of the CameraX bitmap and can outlive the analyzer call. */
        fun crop(bitmap: Bitmap, face: Face): Bitmap? {
            val box = face.boundingBox
            if (box.width()<120 || box.height()<120 || box.left<0 || box.top<0 ||
                box.right>bitmap.width || box.bottom>bitmap.height ||
                !face.headEulerAngleX.isFinite() || !face.headEulerAngleY.isFinite() ||
                abs(face.headEulerAngleY)>20 || abs(face.headEulerAngleX)>15) return null
            val crop = FaceCropper.align(bitmap,face) ?: return null
            val pixels = IntArray(112*112)
            crop.getPixels(pixels,0,112,0,0,112,112)
            val mean = pixels.map { (Color.red(it)+Color.green(it)+Color.blue(it))/3.0 }.average()
            if (mean !in 30.0..225.0) { crop.recycle(); return null }
            return crop
        }
    }
}
