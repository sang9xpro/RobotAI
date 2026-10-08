package com.robotai.robot.dock

import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test

class WirelessDockTest {
    @Test fun onlyWirelessPowerActivatesDock() {
        assertFalse(isWirelessPower(0))
        assertFalse(isWirelessPower(-1))
        assertFalse(isWirelessPower(BatteryManager.BATTERY_PLUGGED_AC))
        assertFalse(isWirelessPower(BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(isWirelessPower(BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }
}
