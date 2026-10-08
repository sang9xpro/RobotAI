package com.robotai.robot.identity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.robotai.domain.*
import kotlin.math.*

data class SceneObject(val label: String,val score: Float,val box: TrackBox)
data class SceneFrame(val people: List<PersonObservation>,val objects: List<SceneObject>,val changed: Boolean)

/** One worker owns the models. Body/scene descriptors are session-only, never persisted. */
class SceneVision(context: Context): AutoCloseable {
    private val detector=ObjectDetector.createFromOptions(context,ObjectDetector.ObjectDetectorOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("identity/efficientdet_lite0.tflite").build())
        .setRunningMode(RunningMode.IMAGE).setScoreThreshold(.30f).setMaxResults(20).build())
    private val pose=PoseLandmarker.createFromOptions(context,PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("identity/pose_landmarker_lite.task").build())
        .setRunningMode(RunningMode.VIDEO).setNumPoses(3)
        .setMinPoseDetectionConfidence(.5f).setMinPosePresenceConfidence(.5f).setMinTrackingConfidence(.6f).build())
    private var previousBackground: FloatArray?=null
    private var previousPeople=emptyList<TrackBox>()
    private var lastPoseAt=-1L
    fun reset() { previousBackground=null;previousPeople=emptyList() }
    fun detect(bitmap: Bitmap,now: Long): SceneFrame {
        val image=BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888,false)).build()
        try {
            val objects=detector.detect(image).detections().mapNotNull { detection ->
                val category=detection.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val b=detection.boundingBox()
                val box=TrackBox((b.left/bitmap.width).coerceIn(0f,1f),(b.top/bitmap.height).coerceIn(0f,1f),
                    (b.right/bitmap.width).coerceIn(0f,1f),(b.bottom/bitmap.height).coerceIn(0f,1f))
                if(!box.valid) null else SceneObject(category.categoryName(),category.score(),box)
            }
            // Suppress duplicate boxes, not distinct people whose bodies overlap.
            val boxes=mutableListOf<SceneObject>()
            objects.filter { it.label=="person" }.sortedByDescending { it.score }.forEach { o ->
                if(boxes.none { it.box.iou(o.box)>.75f }) boxes.add(o)
            }
            val poses=if(boxes.isNotEmpty() && (lastPoseAt<0 || now-lastPoseAt>=700)) pose.detectForVideo(image,max(now,lastPoseAt+1).also { lastPoseAt=it }).landmarks() else emptyList()
            val people=boxes.map { detection ->
                val wrists=poses.filter { points ->
                    listOf(11,12).all { i -> points.getOrNull(i)?.let { p ->
                        p.visibility().orElse(0f)>.55f && detection.box.contains(p.x(),p.y(),.04f)
                    }==true }
                }.singleOrNull()?.let { points -> listOf(15,16).mapNotNull { i ->
                    points.getOrNull(i)?.takeIf { it.visibility().orElse(0f)>.55f &&
                        it.x().isFinite() && it.y().isFinite() && it.x() in 0f..1f && it.y() in 0f..1f }
                        ?.let { HandPoint(it.x(),it.y()) }
                } }.orEmpty()
                val held=objects.filter { it.label in HELD_OBJECTS && detection.box.contains(it.box.cx,it.box.cy,.05f) }.map { it.label }.toSet()
                PersonObservation(detection.box,detection.score,appearance(bitmap,detection.box),wrists=wrists,objects=held)
            }
            val background=background(bitmap)
            val old=previousBackground
            val comparable=background.indices.filter { i ->
                val x=(i%12+.5f)/12;val y=(i/12+.5f)/8
                (previousPeople+boxes.map { it.box }).none { it.contains(x,y,.05f) }
            }
            val changed=old!=null && comparable.size>=24 && comparable.count { abs(old[it]-background[it])>.25f }>comparable.size*.65f
            previousBackground=background;previousPeople=boxes.map { it.box }
            return SceneFrame(people,objects.filter { it.label!="person" },changed)
        } finally { image.close() }
    }
    override fun close() { detector.close();pose.close() }
    companion object {
        private val HELD_OBJECTS=setOf("backpack","handbag","umbrella","bottle","cup","cell phone","book","sports ball")
        internal fun appearance(bitmap: Bitmap,box: TrackBox): FloatArray {
            val bins=FloatArray(72);val hsv=FloatArray(3)
            // Three vertical bands of the central body, excluding the head and most background.
            for(row in 0 until 24) for(col in 0 until 16) {
                val x=(box.left+box.width*(.2f+.6f*(col+.5f)/16)).coerceIn(0f,.999f)
                val y=(box.top+box.height*(.22f+.72f*(row+.5f)/24)).coerceIn(0f,.999f)
                Color.colorToHSV(bitmap.getPixel((x*bitmap.width).toInt(),(y*bitmap.height).toInt()),hsv)
                val color=if(hsv[1]<.18f) 16+(hsv[2]*7).toInt().coerceIn(0,7)
                    else (hsv[0]/45).toInt().coerceIn(0,7)+(if(hsv[2]>.5f) 8 else 0)
                bins[(row/8)*24+color]++
            }
            return IdentityMath.normalize(bins)
        }
        private fun background(bitmap: Bitmap)=FloatArray(96) { i ->
            val color=bitmap.getPixel(((i%12+.5f)/12*bitmap.width).toInt(),((i/12+.5f)/8*bitmap.height).toInt())
            (Color.red(color)+Color.green(color)+Color.blue(color))/765f
        }
    }
}
