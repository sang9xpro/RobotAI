package com.robotai.domain

import kotlin.math.ceil

enum class Mood { CALM, HAPPY, CURIOUS, SLEEPY, CARING }
class CompanionMind {
    private var touchedAt = -1L
    private var lastActive = 0L
    private var speechMood = Mood.CALM
    fun touch(now: Long) { touchedAt = now; lastActive = now }
    fun sentence(text: String, now: Long) {
        lastActive = now
        val words = text.lowercase()
        speechMood = when {
            listOf("buồn", "xin lỗi", "lo lắng").any { it in words } -> Mood.CARING
            listOf("vui", "tuyệt", "cảm ơn", "chúc mừng").any { it in words } -> Mood.HAPPY
            "?" in text -> Mood.CURIOUS
            else -> Mood.CALM
        }
    }
    fun emotion(value: String) {
        speechMood = when (value.lowercase()) {
            "happy", "funny", "laughing", "excited", "smile", "silly" -> Mood.HAPPY
            "curious", "confused", "thinking", "questioning" -> Mood.CURIOUS
            "sad", "loving", "caring", "embarrassed" -> Mood.CARING
            "sleepy", "tired" -> Mood.SLEEPY
            "neutral", "calm", "confident" -> Mood.CALM
            else -> speechMood
        }
    }
    fun mood(phase: Phase, now: Long, facePresent: Boolean = false): Mood {
        if (lastActive == 0L || phase in listOf(Phase.LISTENING,Phase.THINKING,Phase.FINALIZING,Phase.SPEAKING)) lastActive = now
        return when {
        phase == Phase.SLEEPING -> Mood.SLEEPY
        phase == Phase.SPEAKING -> speechMood
        phase in listOf(Phase.LISTENING, Phase.THINKING, Phase.FINALIZING) -> Mood.CURIOUS
        touchedAt >= 0 && now - touchedAt < 3500 -> Mood.HAPPY
        facePresent -> Mood.CURIOUS
        now - lastActive > 120_000 -> Mood.SLEEPY
        (now / 12_000) % 3 == 1L -> Mood.CURIOUS
        else -> Mood.CALM
        }
    }
}

/** Nearest-rank percentiles over completed successful turns, not provider latency. */
object LatencyStats {
    fun percentile(samples: List<Long>, percentile: Int): Long? {
        require(percentile in 1..100)
        if (samples.isEmpty()) return null
        return samples.sorted()[ceil(samples.size * percentile / 100.0).toInt() - 1]
    }
}

enum class RobotAction { STOP, FORWARD, BACKWARD, LEFT, RIGHT }
data class RobotCommand(val id: Long, val action: RobotAction, val expiresAt: Long, val durationMs: Long, val speed: Int)
data class RobotTelemetry(val receivedAt: Long, val connected: Boolean, val obstacle: Boolean = false,
                          val cliff: Boolean = false, val battery: Int = 100)
/** Monotonic local timestamps; STOP always wins, stale/replayed commands never reach a transport. */
class RobotCommandGate {
    private var lastId = -1L
    fun validate(command: RobotCommand, telemetry: RobotTelemetry, now: Long): String? {
        if (command.action == RobotAction.STOP) return null
        if (command.id <= lastId) return "Lệnh lặp hoặc sai thứ tự"
        if (!telemetry.connected || now - telemetry.receivedAt !in 0..750) return "Mất telemetry"
        if (telemetry.cliff || telemetry.obstacle || telemetry.battery < 15) return "Cảm biến yêu cầu dừng"
        if (command.expiresAt <= now || command.expiresAt - now > 2000) return "Lệnh hết hạn hoặc TTL quá dài"
        if (command.durationMs !in 1..1000 || command.speed !in 1..30) return "Vượt giới hạn chuyển động"
        lastId = command.id
        return null
    }
}
interface RobotTransport {
    fun send(command: RobotCommand)
    fun telemetry(now: Long): RobotTelemetry
    fun close()
}
class SimulatedRobotTransport : RobotTransport {
    var connected = true
    var obstacle = false
    var cliff = false
    var frozen = false
    private var lastTelemetry = 0L
    var action = RobotAction.STOP; private set
    private var stopAt = 0L
    override fun send(command: RobotCommand) {
        action = if (connected) command.action else RobotAction.STOP
        stopAt = command.expiresAt.coerceAtMost(lastTelemetry + command.durationMs)
    }
    override fun telemetry(now: Long): RobotTelemetry {
        if (connected && !frozen) lastTelemetry = now
        if (!connected || now - lastTelemetry > 750 || now >= stopAt || obstacle || cliff) action = RobotAction.STOP
        return RobotTelemetry(lastTelemetry, connected, obstacle, cliff)
    }
    override fun close() { action = RobotAction.STOP; connected = false }
}
/** All UI/tool proposals must pass this single entry point. No PWM API is exposed. */
class RobotLink(private val transport: RobotTransport) {
    private val gate = RobotCommandGate()
    fun tick(now: Long): RobotTelemetry {
        val t = transport.telemetry(now)
        if (!t.connected || now - t.receivedAt !in 0..750 || t.cliff || t.obstacle || t.battery < 15) stop(now)
        return t
    }
    fun propose(command: RobotCommand, now: Long): String? {
        val error = gate.validate(command, tick(now), now)
        if (error == null) transport.send(command) else stop(now)
        return error
    }
    fun stop(now: Long) { transport.send(RobotCommand(0, RobotAction.STOP, now, 0, 0)) }
    fun close(now: Long) { stop(now); transport.close() }
}
