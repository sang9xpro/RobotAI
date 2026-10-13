package com.robotai.robot.data

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.*
import com.robotai.domain.DirectRobotCall
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One on-device recognizer, fed from the same PCM captured for the request. */
class CapturedWakeRecognizer(private val context: Context, private val scope: CoroutineScope) {
    private var recognizer: SpeechRecognizer? = null
    private var descriptors: Array<ParcelFileDescriptor>? = null
    private var writer: Job? = null
    private var input: Channel<ByteArray>? = null
    private var result = CompletableDeferred<Boolean>()
    @Volatile var available = false
        private set
    @Volatile var called = false
        private set
    @Volatile var onlyCall = false
        private set

    fun start(name: String, onCall: () -> Unit = {}): Boolean {
        stop()
        available=false;called = false; onlyCall = false; result = CompletableDeferred()
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return false
        return runCatching {
            val pipe = ParcelFileDescriptor.createPipe()
            descriptors = pipe
            val completion=result
            val channel = Channel<ByteArray>(64,onUndeliveredElement={ it.fill(0) })
            input = channel
            writer = scope.launch(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        for (bytes in channel) { output.write(bytes); bytes.fill(0) }
                    }
                }
            }
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = speech
            fun detect(bundle: Bundle?, final: Boolean = false) {
                if(recognizer!==speech) return
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (DirectRobotCall.matches(name, text) && (final || text.lowercase().contains("ơi"))) {
                    if(!called) { called=true;onCall() }
                    onlyCall = DirectRobotCall.onlyCall(name, text)
                }
            }
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onResults(results: Bundle?) { detect(results,true); completion.complete(called) }
                override fun onPartialResults(partialResults: Bundle?) { detect(partialResults) }
                override fun onSegmentResults(segmentResults: Bundle) { detect(segmentResults,true) }
                override fun onEndOfSegmentedSession() { completion.complete(called) }
                override fun onError(error: Int) { if(recognizer===speech) { available=false;completion.complete(false) } }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            speech.checkRecognitionSupport(intent,context.mainExecutor,object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if(recognizer!==speech) return
                    if(support.installedOnDeviceLanguages.none { java.util.Locale.forLanguageTag(it).language=="vi" }) {
                        available=false;completion.complete(false);return
                    }
                    available=true
                    runCatching { speech.startListening(intent) }.onFailure { available=false;completion.complete(false) }
                }
                override fun onError(error: Int) { if(recognizer===speech) { available=false;completion.complete(false) } }
            })
            true
        }.getOrElse { stop(); false }
    }
    fun audio(samples: ShortArray) {
        val channel = input ?: return
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, value -> bytes[i*2] = value.toByte(); bytes[i*2+1] = (value.toInt() shr 8).toByte() }
        if (!channel.trySend(bytes).isSuccess) { bytes.fill(0); result.complete(false) }
    }
    suspend fun finish(): Boolean {
        input?.close()
        val accepted = withTimeoutOrNull(2000) { result.await() } ?: called
        stop()
        return accepted
    }
    fun stop() {
        input?.cancel(); input = null
        writer?.cancel(); writer = null
        descriptors?.forEach { runCatching { it.close() } }; descriptors = null
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
    }
}
