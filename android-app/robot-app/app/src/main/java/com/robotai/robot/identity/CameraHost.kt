package com.robotai.robot.identity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Lives above tabs. Analysis is bound independently of the optional preview. */
@Composable fun CameraHost(identity:IdentityController, awake:Boolean, previewVisible:Boolean, front:Boolean, permissionButtonVisible:Boolean = true) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ ->
            resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            allowed=context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val selector=if(front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    val active=awake && allowed && resumed
    DisposableEffect(active,front,identity) {
        var disposed=false
        val epoch=identity.startCamera()
        val executor=Executors.newSingleThreadExecutor()
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).enableTracking().build())
        val analysis=ImageAnalysis.Builder().setTargetResolution(android.util.Size(640,480))
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var bound:ProcessCameraProvider?=null
        var last=0L
        if(active) {
            identity.cameraStatus("Đang mở camera…")
            analysis.setAnalyzer(executor) { image ->
                try {
                    if(!disposed && SystemClock.elapsedRealtime()-last>=80) {
                        last=SystemClock.elapsedRealtime()
                        val bitmap=rgbaBitmap(image)
                        try {
                            val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),3,TimeUnit.SECONDS)
                            if(!disposed) runBlocking { identity.analyze(bitmap,faces,epoch); identity.rememberFrame(bitmap,epoch) }
                        } finally { bitmap.recycle() }
                    }
                } catch(e:Exception) { if(!disposed) { identity.clearFaces(); identity.cameraStatus("Lỗi camera: ${e.javaClass.simpleName}") } }
                finally { image.close() }
            }
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({ if(!disposed) try {
                bound=future.get(); bound!!.bindToLifecycle(owner,selector,analysis); provider=bound
            } catch(e:Exception) { identity.cameraStatus("Không mở được camera: ${e.javaClass.simpleName}") } },ContextCompat.getMainExecutor(context))
        } else { identity.clearFaces(); identity.clearFrame(); identity.cameraStatus(if(!awake) "Camera tắt · robot ngủ" else if(!allowed) "Cần cấp quyền camera" else "Camera tạm dừng · ứng dụng ở nền") }
        onDispose {
            disposed=true; analysis.clearAnalyzer(); bound?.unbind(analysis); provider=null
            identity.stopCamera(epoch)
            executor.execute { detector.close() }; executor.shutdown()
        }
    }
    if(permissionButtonVisible && awake && !allowed) TextButton(onClick={permission.launch(Manifest.permission.CAMERA)}) { Text("Cấp quyền camera để nhận diện khi Ken thức") }
    if(active && previewVisible && provider!=null) {
        val view=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
        DisposableEffect(provider,front) {
            val preview=Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val p=provider
            runCatching { p?.bindToLifecycle(owner,selector,preview) }
            onDispose { p?.unbind(preview) }
        }
        AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(170.dp))
    }
}

private fun rgbaBitmap(image:ImageProxy):Bitmap {
    val plane=image.planes[0]; val buffer=plane.buffer; val pixels=IntArray(image.width*image.height)
    for(y in 0 until image.height) for(x in 0 until image.width) {
        val offset=y*plane.rowStride+x*plane.pixelStride
        pixels[y*image.width+x]=Color.argb(buffer.get(offset+3).toInt() and 255,buffer.get(offset).toInt() and 255,
            buffer.get(offset+1).toInt() and 255,buffer.get(offset+2).toInt() and 255)
    }
    val raw=Bitmap.createBitmap(pixels,image.width,image.height,Bitmap.Config.ARGB_8888)
    val rotated=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },true)
    if(rotated!==raw) raw.recycle()
    return rotated
}
