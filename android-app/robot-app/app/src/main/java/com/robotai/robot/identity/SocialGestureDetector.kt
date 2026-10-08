package com.robotai.robot.identity

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.robotai.domain.GestureHand
import com.robotai.domain.HandPoint

/** Created, used, and closed on the identity worker with the existing bundled model. */
class SocialGestureDetector(context: Context) : AutoCloseable {
    private val appContext=context.applicationContext
    private var model: GestureRecognizer? = null
    private fun createModel() = GestureRecognizer.createFromOptions(appContext,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build())
            .setRunningMode(RunningMode.VIDEO).setNumHands(2)
            .setMinHandDetectionConfidence(.5f).setMinHandPresenceConfidence(.5f)
            .setMinTrackingConfidence(.5f).build())
    private var lastTimestamp = -1L
    private var width = 0
    private var height = 0
    fun reset() { model?.close();model=null;lastTimestamp=-1;width=0;height=0 }

    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.elapsedRealtime()): List<GestureHand> {
        if(width!=0 && (width!=bitmap.width || height!=bitmap.height ||
            timestampMs-lastTimestamp !in 0..1500)) reset()
        width=bitmap.width;height=bitmap.height
        val recognizer=model ?: createModel().also { model=it }
        // MPImage owns the copy; closing it must not recycle the face analysis source.
        val mp = BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888, false)).build()
        return try {
            val timestamp=maxOf(timestampMs,lastTimestamp+1)
            lastTimestamp=timestamp
            val result = recognizer.recognizeForVideo(mp,timestamp)
            result.landmarks().mapIndexed { index, points ->
                val category = result.gestures().getOrNull(index)?.maxByOrNull { it.score() }
                GestureHand(points.map { HandPoint(it.x(), it.y(),it.z()) }, category?.categoryName().orEmpty(),
                    category?.score() ?: 0f, result.handedness().getOrNull(index)?.firstOrNull()?.categoryName().orEmpty(),
                    bitmap.height.toFloat()/bitmap.width)
            }
        } finally { mp.close() }
    }
    override fun close() = reset()
}
