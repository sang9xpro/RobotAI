package com.robotai.robot.data

import android.annotation.SuppressLint
import android.media.*
import io.github.jaredmdobson.concentus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Microphone(private val scope: CoroutineScope) {
    private val stopMutex=Mutex()
    private var job: Job? = null
    @Volatile private var record: AudioRecord? = null
    @Volatile private var stopping = false

    @SuppressLint("MissingPermission")
    fun start(onPacket: (ByteArray) -> Unit, onLimit: () -> Unit, onError: (String) -> Unit, onPcm: (ShortArray) -> Unit = {}, automatic: Boolean = false, onSpeech: () -> Unit = {}) {
        check(job == null)
        stopping = false
        job = scope.launch(Dispatchers.IO) {
            var r: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minimum > 0) { "Thiết bị không hỗ trợ PCM 16 kHz" }
                r = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 3840))
                require(r.state == AudioRecord.STATE_INITIALIZED) { "Không mở được micro" }
                record = r
                val encoder = OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_VOIP)
                encoder.bitrate = 24000
                encoder.complexity = 3
                val pcm = ShortArray(960)
                val packet = ByteArray(4096)
                r.startRecording()
                var offset = 0
                var frames = 0
                val endpoint = com.robotai.domain.SpeechEndpoint()
                val preRoll = java.util.ArrayDeque<ShortArray>()
                var ended = false
                while (isActive && !stopping && (automatic || frames < 500) && !ended) {
                    val n = r.read(pcm, offset, pcm.size - offset, AudioRecord.READ_BLOCKING)
                    if (n <= 0) {
                        if (!stopping) error("Lỗi đọc micro: $n")
                        break
                    }
                    offset += n
                    if (offset == pcm.size) {
                        val event = if (automatic) endpoint.accept(pcm) else null
                        if (automatic && !endpoint.started) {
                            preRoll.addLast(pcm.copyOf())
                            if (preRoll.size > 5) preRoll.removeFirst()
                        } else {
                            if (event == com.robotai.domain.SpeechEndpoint.Event.START) {
                                for (buffer in preRoll) {
                                    onPcm(buffer)
                                    val count = encoder.encode(buffer, 0, buffer.size, packet, 0, packet.size)
                                    onPacket(packet.copyOf(count))
                                }
                                preRoll.clear()
                            }
                            onPcm(pcm.copyOf())
                            val size = encoder.encode(pcm, 0, pcm.size, packet, 0, packet.size)
                            onPacket(packet.copyOf(size))
                            if (event == com.robotai.domain.SpeechEndpoint.Event.START || event == com.robotai.domain.SpeechEndpoint.Event.SPEECH)
                                withContext(Dispatchers.Main) { onSpeech() }
                            ended = event == com.robotai.domain.SpeechEndpoint.Event.END
                        }
                        offset = 0
                        frames++
                    }
                }
                if (offset > 0 && isActive && (!automatic || endpoint.started)) {
                    onPcm(pcm.copyOf(offset))
                    pcm.fill(0, offset)
                    val size = encoder.encode(pcm, 0, pcm.size, packet, 0, packet.size)
                    onPacket(packet.copyOf(size))
                }
                if ((ended || (!automatic && frames == 500)) && !stopping) withContext(Dispatchers.Main) { onLimit() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (!stopping) withContext(Dispatchers.Main) { onError(e.message ?: "Lỗi micro") } }
            finally {
                runCatching { r?.stop() }
                r?.release()
                record = null
            }
        }
    }

    suspend fun stop() = stopMutex.withLock {
        stopping = true
        withContext(Dispatchers.IO) { runCatching { record?.stop() }; job?.join() }
        job = null
    }
}
