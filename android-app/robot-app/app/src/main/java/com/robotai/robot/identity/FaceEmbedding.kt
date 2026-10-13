package com.robotai.robot.identity

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import com.google.mlkit.vision.face.Face
import com.robotai.domain.IdentityMath
import java.nio.FloatBuffer
import kotlin.math.abs

/** SFace's graph includes normalization: input is RGB float 0..255, NCHW 112x112. */
class FaceEmbedding(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        env.createSession(context.assets.open("identity/sface.onnx").use { it.readBytes() }, options)
    }
    fun extract(bitmap: Bitmap, face: Face): FloatArray? {
        if (face.boundingBox.width() < 90 || abs(face.headEulerAngleY) > 28 || abs(face.headEulerAngleX) > 25) return null
        val crop = FaceCropper.align(bitmap,face) ?: return null
        try {
            val pixels=IntArray(112*112); crop.getPixels(pixels,0,112,0,0,112,112)
            val mean=pixels.map { (Color.red(it)+Color.green(it)+Color.blue(it))/3.0 }.average()
            if (mean < 25 || mean > 235) return null
            val input=FloatArray(3*112*112)
            pixels.forEachIndexed { i,p -> input[i]=Color.red(p).toFloat(); input[i+12544]=Color.green(p).toFloat(); input[i+25088]=Color.blue(p).toFloat() }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1,3,112,112)).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST") val output = result[0].value as Array<FloatArray>
                    return IdentityMath.normalize(output[0])
                }
            }
        } finally { crop.recycle() }
    }
    override fun close() { session.close() }
}
