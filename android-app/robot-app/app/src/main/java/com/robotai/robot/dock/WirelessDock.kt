package com.robotai.robot.dock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Use the power source, including a full battery; STATUS_CHARGING alone is insufficient. */
internal fun isWirelessPower(plugged: Int): Boolean =
    plugged >= 0 && plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0

@Composable
fun rememberWirelessDock(): State<Boolean> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val docked = remember(context) {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        mutableStateOf(isWirelessPower(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0))
    }
    DisposableEffect(context, owner) {
        var registered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED)
                    docked.value = isWirelessPower(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
            }
        }
        fun start() {
            if (registered) return
            val battery = ContextCompat.registerReceiver(context, receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            // Sticky broadcast also detects launching the app while already on the stand.
            if (battery != null) receiver.onReceive(context, battery)
        }
        fun stop() {
            if (registered) { context.unregisterReceiver(receiver); registered = false }
        }
        val observer = LifecycleEventObserver { _, _ ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start() else stop()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
    return docked
}
