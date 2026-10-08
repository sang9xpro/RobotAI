package com.robotai.robot.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.robotai.domain.WakePhrase
import com.robotai.domain.WakeWordPort

/** Test adapter only: offline Android ASR, not a low-power keyword model.
 * Never call createSpeechRecognizer or use PREFER_OFFLINE as a cloud fallback.
 */
class LocalWakeWord(private val context: Context) : WakeWordPort {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var errors = 0
    private var attempt = 0

    override fun start(phrase: String, onWake: () -> Unit, onUnavailable: (String) -> Unit) {
        stop()
        if (phrase.isBlank()) { onUnavailable("Nhập câu gọi đánh thức trước khi bật nghe tên."); return }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onUnavailable("Máy chưa có nhận dạng on-device. Chạm Đánh thức; không gửi audio ngủ lên mạng.")
            return
        }
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = speech
            active = true
            errors = 0
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            fun retry(waitMs: Long = 600) {
                val token = attempt
                handler.postDelayed({
                    if (active && recognizer === speech && token == attempt) {
                        attempt++
                        try { speech.startListening(intent) }
                        catch (e: Exception) { stop(); onUnavailable("Không bật được nghe local: ${e.message}") }
                    }
                }, waitMs)
            }
            fun detect(results: Bundle?): Boolean {
                if (!active) return false
                val values = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (!WakePhrase.matches(phrase, values)) return false
                stop() // Release recognizer microphone before the conversation recorder starts.
                onWake()
                return true
            }
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { errors = 0 }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onResults(results: Bundle?) { if (!detect(results) && active) retry() }
                override fun onPartialResults(partialResults: Bundle?) { detect(partialResults) }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    if (!active || recognizer !== speech) return
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        retry()
                    } else if (++errors < 3 && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        retry(1200)
                    } else {
                        stop()
                        onUnavailable("Nhận dạng local chưa sẵn sàng (mã $error). Kiểm tra gói tiếng Việt; chạm Đánh thức.")
                    }
                }
            })
            speech.startListening(intent)
        } catch (e: Exception) {
            stop()
            onUnavailable("Không mở được nhận dạng local: ${e.message}")
        }
    }

    override fun stop() {
        active = false
        attempt++
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
