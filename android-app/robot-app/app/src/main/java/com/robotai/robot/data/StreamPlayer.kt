package com.robotai.robot.data

import android.media.*
import android.os.SystemClock
import io.github.jaredmdobson.concentus.OpusDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One bounded queue and one decoder/AudioTrack per turn. */
class StreamPlayer(private val scope: CoroutineScope) {
    @Volatile private var outputRate = 24000
    fun configure(sampleRate: Int) {
        require(sampleRate in listOf(16000, 24000)) { "Sample rate không hỗ trợ" }
        outputRate = sampleRate
    }
    private val lock = Any()
    private var job: Job? = null
    @Volatile private var queue: Channel<ByteArray>? = null
    private var track: AudioTrack? = null

    fun start(onStarted: () -> Unit, onCompleted: () -> Unit, onError: (String) -> Unit) {
        stop()
        val rate = outputRate
        val input = Channel<ByteArray>(12) // 720 ms at 60 ms/frame; never unbounded.
        queue = input
        job = scope.launch(Dispatchers.IO) {
            var local: AudioTrack? = null
            try {
                val decoder = OpusDecoder(rate, 1)
                val format = AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                local = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, rate * 240 / 1000)).build()
                synchronized(lock) {
                    if (queue !== input) { local!!.release(); local = null; return@launch }
                    track = local
                    local!!.play()
                }
                var played = 0L
                var started = false
                for (packet in input) {
                    ensureActive()
                    if (packet.isEmpty()) break
                    val pcm = ShortArray(rate * 120 / 1000)
                    val count = decoder.decode(packet, 0, packet.size, pcm, 0, pcm.size, false)
                    var offset = 0
                    while (offset < count) {
                        ensureActive()
                        val n = synchronized(lock) {
                            if (queue !== input) return@launch
                            local!!.write(pcm, offset, count - offset, AudioTrack.WRITE_BLOCKING)
                        }
                        check(n > 0) { "Lỗi phát audio: $n" }
                        offset += n
                    }
                    val firstAudible = (0 until count).firstOrNull { kotlin.math.abs(pcm[it].toInt()) >= 200 }
                    val audiblePosition = played + (firstAudible ?: 0)
                    played += count
                    if (!started && firstAudible != null) {
                        started = true
                        // Measure presentation progress, not the earlier enqueue/write time.
                        launch monitor@{
                            val deadline = SystemClock.elapsedRealtime() + 3000
                            while (isActive && SystemClock.elapsedRealtime() < deadline) {
                                val head = synchronized(lock) {
                                    if (queue !== input) return@monitor
                                    local!!.playbackHeadPosition.toLong() and 0xffffffffL
                                }
                                if (head > audiblePosition) {
                                    withContext(Dispatchers.Main) { if (queue === input) onStarted() }
                                    return@monitor
                                }
                                delay(5)
                            }
                            withContext(Dispatchers.Main) { if (queue === input) onError("Loa không bắt đầu phát") }
                        }
                    }
                }
                // Server stop means all packets sent; wait for the speaker to drain.
                val deadline = SystemClock.elapsedRealtime() + 2000
                while (isActive && SystemClock.elapsedRealtime() < deadline) {
                    val head = synchronized(lock) {
                        if (queue !== input) return@launch
                        local!!.playbackHeadPosition.toLong() and 0xffffffffL
                    }
                    if (head >= played) break
                    delay(15)
                }
                val drained = synchronized(lock) { queue === input && (local!!.playbackHeadPosition.toLong() and 0xffffffffL) >= played }
                check(started && drained) { "Audio chưa phát hết hoặc phản hồi rỗng" }
                withContext(Dispatchers.Main) { if (queue === input) onCompleted() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { withContext(Dispatchers.Main) { if (queue === input) onError(e.message ?: "Lỗi phát") } }
            finally {
                synchronized(lock) {
                    if (track === local) {
                        runCatching { local?.stop() }; local?.release(); track = null
                    }
                }
            }
        }
    }
    fun offer(packet: ByteArray) = queue?.trySend(packet)?.isSuccess == true
    // Closing preserves buffered packets and cannot fail because the queue is full.
    fun finish() = queue?.close() == true
    fun stop() {
        synchronized(lock) {
            queue?.cancel(); queue = null
            job?.cancel(); job = null
            runCatching { track?.pause(); track?.flush() }
            track?.release(); track = null
        }
    }
}
