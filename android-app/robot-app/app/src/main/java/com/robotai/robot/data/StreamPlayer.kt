package com.robotai.robot.data

import android.media.*
import android.os.SystemClock
import io.github.jaredmdobson.concentus.OpusDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.robotai.domain.PcmSpeechEnvelope

/** One bounded queue and one decoder/AudioTrack per turn. */
class StreamPlayer(private val scope: CoroutineScope) {
    private val level=MutableStateFlow(0f)
    val speechLevel=level.asStateFlow()
    private data class Packet(val bytes: ByteArray, val sequence: Long)
    private data class Presentation(val end: Long, val sequence: Long)
    private data class LevelWindow(val start:Long,val end:Long,val level:Float)
    @Volatile private var outputRate = 24000
    fun configure(sampleRate: Int) {
        require(sampleRate in listOf(16000, 24000)) { "Sample rate không hỗ trợ" }
        outputRate = sampleRate
    }
    private val lock = Any()
    private var job: Job? = null
    @Volatile private var queue: Channel<Packet>? = null
    private var track: AudioTrack? = null

    fun start(onStarted: () -> Unit, onCompleted: () -> Unit, onError: (String) -> Unit, onProgress: (Long) -> Unit = {}) {
        stop()
        val rate = outputRate
        val input = Channel<Packet>(12) // 720 ms at 60 ms/frame; never unbounded.
        queue = input
        job = scope.launch(Dispatchers.IO) {
            var local: AudioTrack? = null
            var meter: Job? = null
            val windows=ArrayDeque<LevelWindow>()
            val presentations=ArrayDeque<Presentation>()
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
                var lastHead=-1L
                var progressedAt=SystemClock.elapsedRealtime()
                // Called under lock, both after blocking writes and between packets. The
                // writer must publish too: a busy AudioTrack can otherwise starve its meter.
                fun updateLevel() {
                    val head=local!!.playbackHeadPosition.toLong() and 0xffffffffL
                    val now=SystemClock.elapsedRealtime()
                    if(head!=lastHead){lastHead=head;progressedAt=now}
                    var ack: Long? = null
                    while(presentations.isNotEmpty() && presentations.first().end<=head) ack=presentations.removeFirst().sequence
                    ack?.takeIf { it>=0 }?.let(onProgress)
                    while(windows.isNotEmpty() && windows.first().end<=head)windows.removeFirst()
                    level.value=windows.firstOrNull()?.takeIf { head>=it.start && now-progressedAt<100 }?.level ?: 0f
                }
                meter=launch meter@{
                    while(isActive) {
                        synchronized(lock) {
                            if(queue!==input)return@meter
                            updateLevel()
                        }
                        delay(20)
                    }
                }
                var played = 0L
                var started = false
                for (item in input) {
                    val packet = item.bytes
                    ensureActive()
                    if (packet.isEmpty()) break
                    val pcm = ShortArray(rate * 120 / 1000)
                    val count = decoder.decode(packet, 0, packet.size, pcm, 0, pcm.size, false)
                    synchronized(lock) {
                        if(queue!==input)return@launch
                        val window=maxOf(1,rate/50)
                        var start=0
                        while(start<count) {
                            val length=minOf(window,count-start)
                            windows.addLast(LevelWindow(played+start,played+start+length,PcmSpeechEnvelope.level(pcm,start,length)))
                            start+=length
                        }
                    }
                    var offset = 0
                    while (offset < count) {
                        ensureActive()
                        val n = synchronized(lock) {
                            if (queue !== input) return@launch
                            val written=local!!.write(pcm, offset, count - offset, AudioTrack.WRITE_BLOCKING)
                            updateLevel()
                            written
                        }
                        check(n > 0) { "Lỗi phát audio: $n" }
                        offset += n
                    }
                    val firstAudible = (0 until count).firstOrNull { kotlin.math.abs(pcm[it].toInt()) >= 200 }
                    val audiblePosition = played + (firstAudible ?: 0)
                    played += count
                    synchronized(lock) { presentations.addLast(Presentation(played, item.sequence)); updateLevel() }
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
                synchronized(lock) { if(queue === input) updateLevel() }
                withContext(Dispatchers.Main) { if (queue === input) onCompleted() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { withContext(Dispatchers.Main) { if (queue === input) onError(e.message ?: "Lỗi phát") } }
            finally {
                meter?.cancel()
                synchronized(lock) {
                    if (track === local) {
                        runCatching { local?.stop() }; local?.release(); track = null
                        level.value=0f
                    }
                }
            }
        }
    }
    fun offer(packet: ByteArray, sequence: Long = -1) = queue?.trySend(Packet(packet,sequence))?.isSuccess == true
    // Closing preserves buffered packets and cannot fail because the queue is full.
    fun finish() = queue?.close() == true
    fun stop() {
        synchronized(lock) {
            queue?.cancel(); queue = null
            job?.cancel(); job = null
            runCatching { track?.pause(); track?.flush() }
            track?.release(); track = null
            level.value=0f
        }
    }
}
