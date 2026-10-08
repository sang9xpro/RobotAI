package com.robotai.robot.dock

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun DockWindow(activity: Activity, active: Boolean) {
    DisposableEffect(activity, active) {
        val window = activity.window
        val orientation = activity.requestedOrientation
        val brightness = window.attributes.screenBrightness
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val behavior = controller.systemBarsBehavior
        if (active) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (active) {
                activity.requestedOrientation = orientation
                controller.systemBarsBehavior = behavior
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.attributes = window.attributes.apply { screenBrightness = brightness }
            }
        }
    }
}
