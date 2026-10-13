package com.robotai.robot.identity

import android.graphics.*
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/** Same five-point similarity transform as SFace. Each caller normalizes the resulting pixels separately. */
object FaceCropper {
    fun align(bitmap: Bitmap, face: Face): Bitmap? {
        val eyes = listOf(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE)
            .map { face.getLandmark(it)?.position ?: return null }.sortedBy { it.x }
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouth = listOf(FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)
            .map { face.getLandmark(it)?.position ?: return null }.sortedBy { it.x }
        val source = eyes + listOf(nose) + mouth
        if (source.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val target = listOf(PointF(38.2946f,51.6963f),PointF(73.5318f,51.5014f),PointF(56.0252f,71.7366f),PointF(41.5493f,92.3655f),PointF(70.7299f,92.2041f))
        val sx = source.map { it.x }.average().toFloat(); val sy = source.map { it.y }.average().toFloat()
        val tx = target.map { it.x }.average().toFloat(); val ty = target.map { it.y }.average().toFloat()
        var den=0f; var a=0f; var b=0f
        source.indices.forEach { i ->
            val x=source[i].x-sx; val y=source[i].y-sy; val u=target[i].x-tx; val v=target[i].y-ty
            den += x*x+y*y; a += x*u+y*v; b += x*v-y*u
        }
        if (den < 1f || !den.isFinite()) return null
        a/=den; b/=den
        val transform = Matrix().apply { setValues(floatArrayOf(a,-b,tx-a*sx+b*sy,b,a,ty-b*sx-a*sy,0f,0f,1f)) }
        return Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(bitmap,transform,Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}
