package com.whispercppdemo.tts

import android.app.Application
import android.media.MediaPlayer
import android.media.AudioTrack
import android.media.AudioAttributes
import android.media.AudioFormat
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class TtsState(val text: String = "Xin chào, mình là robot của bạn. Hôm nay bạn cảm thấy thế nào?",
                    val busy: Boolean = false, val playing: Boolean = false,
                    val status: String = "VieNeu v3 Turbo · Hải Đăng · offline", val metrics: String = "", val hasAudio: Boolean = false)
class TtsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(TtsState())
    val state = mutable.asStateFlow()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: VieneuEngine? = null
    private var player: MediaPlayer? = null
    @Volatile private var track: AudioTrack? = null
    private var audioQueue: Channel<FloatArray>? = null
    private val cancelled = AtomicBoolean()
    private val wav = File(app.cacheDir, "vieneu-haidang.wav")
    fun setText(text: String) { if (!state.value.busy) mutable.update { it.copy(text = text.take(200)) } }
    fun speak() {
        if (state.value.busy || state.value.text.isBlank()) return
        stop(); cancelled.set(false)
        val text = state.value.text
        mutable.update { it.copy(busy = true, hasAudio = false, status = "Đang chuẩn bị model…", metrics = "") }
        viewModelScope.launch {
            try {
                val queue = Channel<FloatArray>(4); audioQueue = queue
                val firstPlayed = AtomicLong(-1)
                var start = 0L
                coroutineScope {
                    val playback = async(Dispatchers.IO) {
                        val minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                        require(minimum > 0) { "Thiết bị không hỗ trợ PCM float" }
                        val output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                            .setBufferSizeInBytes(maxOf(minimum * 2, 48000 / 4 * 4)).setTransferMode(AudioTrack.MODE_STREAM).build()
                        track = output
                        try {
                            require(output.state == AudioTrack.STATE_INITIALIZED)
                            var written = 0L
                            for (chunk in queue) {
                                if (cancelled.get()) throw CancellationException()
                                if (firstPlayed.get() < 0) {
                                    output.play(); firstPlayed.set(SystemClock.elapsedRealtime() - start)
                                    mutable.update { it.copy(playing = true, status = "Đang phát và tạo phần tiếp theo…") }
                                }
                                var offset = 0
                                while (offset < chunk.size) {
                                    if (cancelled.get()) throw CancellationException()
                                    val n = output.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                                    require(n >= 0) { "AudioTrack.write: $n" }
                                    if (n == 0) delay(5) else { offset += n; written += n }
                                }
                            }
                            while ((output.playbackHeadPosition.toLong() and 0xffffffffL) < written) {
                                if (cancelled.get()) throw CancellationException()
                                delay(10)
                            }
                        } finally { queue.cancel(); runCatching { output.stop() }; output.release(); if (track === output) track = null }
                    }
                    try {
                        val metrics = withContext(dispatcher) {
                            val loadStart = SystemClock.elapsedRealtime()
                            if (engine == null) engine = VieneuEngine(app)
                            val loadMs = SystemClock.elapsedRealtime() - loadStart
                            start = SystemClock.elapsedRealtime()
                            var firstPcm = -1L
                            val audio = engine!!.synthesize(text, cancelled, onAudio = { chunk ->
                                if (firstPcm < 0) firstPcm = SystemClock.elapsedRealtime() - start
                                val pcm = FloatArray(chunk.size) { chunk[it].coerceIn(-1f, 1f) }
                                runBlocking { queue.send(pcm) }
                            }, progress = { n -> mutable.update { it.copy(status = "Đang tạo Hải Đăng · $n frame · phát từng phần") } })
                            val inferenceMs = SystemClock.elapsedRealtime() - start
                            writeWave(audio)
                            val duration = audio.size / 48000.0
                            "Nạp %d ms · audio đầu %d ms · bắt đầu phát %d ms · tạo %d ms · audio %.2f s · RTF %.2f · RAM %.0f MiB".format(
                                loadMs, firstPcm, firstPlayed.get(), inferenceMs, duration, inferenceMs / 1000.0 / duration, android.os.Debug.getPss() / 1024.0)
                        }
                        queue.close(); mutable.update { it.copy(hasAudio = true, metrics = metrics, status = "Đang phát phần cuối…") }
                        playback.await()
                        mutable.update { it.copy(playing = false, status = "Phát xong") }
                    } finally { queue.cancel(); playback.cancel(); audioQueue = null }
                }
            } catch (e: CancellationException) { mutable.update { it.copy(status = "Đã dừng TTS") }
            } catch (e: Throwable) { mutable.update { it.copy(status = if (cancelled.get()) "Đã dừng TTS" else "Lỗi TTS: ${e.message}") }
            } finally { mutable.update { it.copy(busy = false, playing = false) } }
        }
    }
    fun replay() {
        if (!state.value.hasAudio) return
        player?.release()
        try {
            player = MediaPlayer().apply {
                setDataSource(wav.absolutePath)
                setOnCompletionListener { release(); player = null; mutable.update { it.copy(playing = false, status = "Phát xong") } }
                setOnErrorListener { _, _, _ -> stop(); mutable.update { it.copy(status = "Không phát được audio") }; true }
                prepare(); start()
            }
            mutable.update { it.copy(playing = true, status = "Đang phát giọng Hải Đăng") }
        } catch (e: Exception) { player?.release(); player = null; mutable.update { it.copy(playing = false, status = "Lỗi phát: ${e.message}") } }
    }
    fun stop() { cancelled.set(true); audioQueue?.cancel(); runCatching { track?.pause(); track?.flush() }; player?.release(); player = null; mutable.update { it.copy(playing = false) } }
    fun export(uri: Uri) {
        if (state.value.busy || !state.value.hasAudio) return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri)?.use { out -> wav.inputStream().use { it.copyTo(out) } } ?: error("Không mở được file") }
                mutable.update { it.copy(status = "Đã lưu WAV") }
            } catch (e: Exception) { mutable.update { it.copy(status = "Lỗi lưu: ${e.message}") } }
        }
    }
    private fun writeWave(audio: FloatArray) {
        val gain = 1f
        val b = ByteBuffer.allocate(44 + audio.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + audio.size * 2); b.put("WAVEfmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(48000); b.putInt(96000); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(audio.size * 2)
        audio.forEach { b.putShort((it * gain * 32767).toInt().coerceIn(-32768, 32767).toShort()) }
        wav.writeBytes(b.array())
    }
    override fun onCleared() {
        stop()
        CoroutineScope(dispatcher).launch { engine?.close(); engine = null; dispatcher.close() }
        super.onCleared()
    }
}
