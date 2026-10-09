package com.robotai.robot.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Music stays on the phone; dialogue Opus uses a separate StreamPlayer. */
data class MusicUiState(val title: String = "", val artist: String = "", val phase: String = "idle", val message: String = "", val youtubeId: String = "") {
    val active get() = phase in listOf("loading", "playing", "paused")
}

class MusicPlayer(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(MusicUiState())
    val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private var youtubeView: WebView? = null
    private var youtubeViewId = ""
    private var ducked = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop("Nhạc dừng do ứng dụng khác sử dụng loa")
            else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) player?.setVolume(.15f, .15f)
            else if (change == AudioManager.AUDIOFOCUS_GAIN) volume()
        }, handler).build()
    private var deadline: Runnable? = null

    fun play(title: String, artist: String, url: String, headers: Map<String, String> = emptyMap()) {
        require(title.isNotBlank() && title.length <= 300 && artist.length <= 300) { "Tên bài hát không hợp lệ" }
        validateUrl(url)
        stop()
        check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Chưa lấy được quyền phát loa" }
        val next = MediaPlayer()
        player = next
        mutable.value = MusicUiState(title, artist, "loading", "Đang tải nhạc…")
        try {
            next.setAudioAttributes(attributes)
            next.setOnPreparedListener { ready ->
                if (player !== ready) return@setOnPreparedListener
                deadline?.let(handler::removeCallbacks); deadline = null
                volume()
                try { ready.start() } catch (e: Exception) { stop("Không khởi động được nhạc", error = true); return@setOnPreparedListener }
                mutable.value = mutable.value.copy(phase = "playing", message = "Đang phát trên điện thoại")
            }
            next.setOnCompletionListener { if (player === it) stop("Đã phát hết bài") }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) stop("Không phát được link nhạc; kiểm tra kết nối hoặc định dạng audio", error = true)
                true
            }
            next.setDataSource(context, Uri.parse(url), headers)
            next.prepareAsync()
            deadline = Runnable { if (player === next && mutable.value.phase == "loading") stop("Tải nhạc quá thời gian chờ", error = true) }
                .also { handler.postDelayed(it, 20000) }
        } catch (e: Exception) { stop(e.message ?: "Không mở được link nhạc", error = true); throw e }
    }
    fun pauseOrResume() {
        if (mutable.value.youtubeId.isNotBlank()) {
            val command = if (mutable.value.phase == "playing") "pauseVideo" else "playVideo"
            youtubeView?.evaluateJavascript("player.$command()", null)
            return
        }
        val current = player ?: return
        when (mutable.value.phase) {
            "playing" -> { current.pause(); mutable.value = mutable.value.copy(phase = "paused", message = "Đã tạm dừng") }
            "paused" -> { current.start(); mutable.value = mutable.value.copy(phase = "playing", message = "Đang phát trên điện thoại") }
        }
    }
    fun duck(value: Boolean) { ducked = value; volume() }
    fun beginYouTubeSearch(title: String, artist: String) {
        require(title.isNotBlank() && title.length <= 300 && artist.length <= 300) { "Tên bài hát không hợp lệ" }
        stop()
        mutable.value = MusicUiState(title, artist, "loading", "App đang tìm video YouTube…")
    }
    fun playYouTube(title: String, artist: String, videoId: String) {
        require(title.isNotBlank() && title.length <= 300 && artist.length <= 300) { "Tên bài hát không hợp lệ" }
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Video YouTube không hợp lệ" }
        stop()
        mutable.value = MusicUiState(title, artist, "loading", "Đang mở YouTube…", videoId)
    }
    fun detachYouTube(view: WebView) {
        if (youtubeView !== view) return
        youtubeView = null
        val detachedId = youtubeViewId
        youtubeViewId = ""
        view.loadUrl("about:blank")
        view.destroy()
        if (mutable.value.youtubeId == detachedId && mutable.value.active) stop("Đã đóng trình phát YouTube")
    }
    fun attachYouTube(view: WebView) {
        val old = youtubeView
        val id = mutable.value.youtubeId
        if (id.isBlank() || old === view) return
        if (old != null) { old.loadUrl("about:blank"); old.destroy() }
        youtubeView = view
        youtubeViewId = id
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        view.webChromeClient = WebChromeClient()
        view.webViewClient = WebViewClient()
        view.addJavascriptInterface(YouTubeEvents(id), "RobotMusic")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"></head>
            <body style="margin:0;background:#000"><div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script><script>
            var player;
            function onYouTubeIframeAPIReady() { player = new YT.Player('player', {
                width:'100%', height:'210', videoId:'$id',
                playerVars:{playsinline:1,autoplay:1,controls:1,rel:0,origin:'https://appassets.androidplatform.net'},
                events:{onReady:function(e){RobotMusic.state('ready');e.target.playVideo()},
                    onStateChange:function(e){RobotMusic.state(String(e.data))},
                    onError:function(e){RobotMusic.state('error:'+e.data)},
                    onAutoplayBlocked:function(){RobotMusic.state('blocked')}}
            }); }
            </script></body></html>"""
        view.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
    }
    private inner class YouTubeEvents(private val id: String) {
        @JavascriptInterface fun state(value: String) {
            handler.post {
                if (mutable.value.youtubeId != id) return@post
                when (value) {
                    "1" -> mutable.value = mutable.value.copy(phase = "playing", message = "Đang phát YouTube trên điện thoại")
                    "2" -> mutable.value = mutable.value.copy(phase = "paused", message = "Đã tạm dừng")
                    "0" -> stop("Đã phát hết video")
                    "ready" -> mutable.value = mutable.value.copy(message = "Chạm Phát trên video nếu YouTube chặn tự phát")
                    "blocked" -> mutable.value = mutable.value.copy(phase = "paused", message = "Chạm Phát trên video để bắt đầu")
                    else -> if (value.startsWith("error:")) stop("YouTube không cho phát video này (mã ${value.removePrefix("error:")})", error = true)
                }
            }
        }
    }
    private fun volume() {
        player?.setVolume(if (ducked) .15f else 1f, if (ducked) .15f else 1f)
        if (youtubeView != null && youtubeViewId == mutable.value.youtubeId)
            youtubeView?.evaluateJavascript("if(window.player&&player.setVolume)player.setVolume(${if (ducked) 15 else 100})", null)
    }
    fun stop(message: String = "", error: Boolean = false) {
        deadline?.let(handler::removeCallbacks); deadline = null
        youtubeView?.loadUrl("about:blank")
        val old = player; player = null; ducked = false
        old?.setOnPreparedListener(null); old?.setOnCompletionListener(null); old?.setOnErrorListener(null)
        old?.release()
        manager.abandonAudioFocusRequest(focus)
        mutable.value = mutable.value.copy(phase = if (error) "error" else "idle", message = message)
    }
    companion object {
        fun validateUrl(url: String) {
            require(url.length <= 4096) { "Link nhạc quá dài" }
            val uri = URI(url)
            require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "Cần link audio http/https trực tiếp" }
            require(uri.host.lowercase() !in listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")) { "Link trang YouTube cần trình phát YouTube, không phải link audio trực tiếp" }
        }
    }
}
