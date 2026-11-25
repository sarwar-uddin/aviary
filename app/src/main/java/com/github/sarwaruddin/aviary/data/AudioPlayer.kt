package com.github.sarwaruddin.aviary.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One-at-a-time bird call player. Each recording is downloaded once into
 * cacheDir/audio (so replays and offline replays are free), then played from disk.
 */
object AudioPlayer {
    enum class Status { IDLE, LOADING, PLAYING, ERROR }
    data class State(val url: String? = null, val status: Status = Status.IDLE, val positionMs: Int = 0, val durationMs: Int = 0)

    private const val TAG = "AviaryAudio"
    private const val MAX_CACHE = 120L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private lateinit var dir: File
    private var player: MediaPlayer? = null
    private var job: Job? = null

    fun init(context: Context) {
        dir = File(context.cacheDir, "audio").apply { mkdirs() }
    }

    /** Play [url], or stop it if it's the one already playing/loading. */
    fun toggle(url: String) {
        val s = _state.value
        if (s.url == url && (s.status == Status.PLAYING || s.status == Status.LOADING)) { stop(); return }
        stop()
        _state.value = State(url, Status.LOADING)
        job = scope.launch {
            val file = runCatching { withContext(Dispatchers.IO) { fetch(url) } }
                .onFailure { Log.w(TAG, "download failed: $url", it) }.getOrNull()
            if (file == null || _state.value.url != url) {
                if (_state.value.url == url) _state.value = State(url, Status.ERROR)
                return@launch
            }
            try {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(file.path)
                    setOnCompletionListener { stop() }
                    setOnErrorListener { _, _, _ -> _state.value = State(url, Status.ERROR); release(); player = null; true }
                    prepare()
                    start()
                }
                player = mp
                _state.value = State(url, Status.PLAYING, 0, mp.duration)
                while (isActive && player === mp) {
                    runCatching { _state.value = _state.value.copy(positionMs = mp.currentPosition) }
                    delay(200)
                }
            } catch (e: Exception) {
                Log.w(TAG, "playback failed: $url", e)
                file.delete()   // maybe a truncated download
                _state.value = State(url, Status.ERROR)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _state.value = State()
    }

    private fun fetch(url: String): File {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, name)
        if (f.isFile && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        var conn = URL(url).openConnection() as HttpURLConnection
        var hops = 0
        while (true) {
            conn.setRequestProperty("User-Agent", "Aviary/0.1 (Android bird field guide)")
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {   // cross-protocol redirects aren't followed automatically
                val next = conn.getHeaderField("Location") ?: break
                conn.disconnect(); conn = URL(URL(url), next).openConnection() as HttpURLConnection; hops++
                continue
            }
            if (code != 200) throw java.io.IOException("HTTP $code")
            break
        }
        val tmp = File(dir, "$name.part")
        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        tmp.renameTo(f)
        trim()
        return f
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE) break
            total -= f.length(); f.delete()
        }
    }
}
