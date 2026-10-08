package com.robotai.robot.identity

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
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
        val eyes = listOf(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE).map { face.getLandmark(it)?.position ?: return null }.sortedBy { it.x }
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouth = listOf(FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT).map { face.getLandmark(it)?.position ?: return null }.sortedBy { it.x }
        val source = eyes + listOf(nose) + mouth
        val target = listOf(PointF(38.2946f,51.6963f),PointF(73.5318f,51.5014f),PointF(56.0252f,71.7366f),PointF(41.5493f,92.3655f),PointF(70.7299f,92.2041f))
        val sx = source.map { it.x }.average().toFloat(); val sy = source.map { it.y }.average().toFloat()
        val tx = target.map { it.x }.average().toFloat(); val ty = target.map { it.y }.average().toFloat()
        var den=0f; var a=0f; var b=0f
        source.indices.forEach { i ->
            val x=source[i].x-sx; val y=source[i].y-sy; val u=target[i].x-tx; val v=target[i].y-ty
            den += x*x+y*y; a += x*u+y*v; b += x*v-y*u
        }
        if (den < 1f) return null
        a/=den; b/=den
        val transform = Matrix().apply { setValues(floatArrayOf(a,-b,tx-a*sx+b*sy,b,a,ty-b*sx-a*sy,0f,0f,1f)) }
        val crop = Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888)
        try {
            Canvas(crop).drawBitmap(bitmap,transform,Paint(Paint.FILTER_BITMAP_FLAG))
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
