package com.whispercppdemo.tts

import android.content.Context
import ai.onnxruntime.*
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.random.Random

/** Fixed Hải Đăng preset; FP32 CPU inference, with the same cached decoder as the SDK. */
class VieneuEngine(context: Context) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val sessions = mutableListOf<OrtSession>()
    private var g2p = 0L
    private val root = File(context.filesDir, "vieneu-v3-haidang-1")
    private lateinit var textEmb: FloatArray
    private lateinit var audioEmb: FloatArray
    private lateinit var anchor: FloatArray
    private lateinit var reference: Array<IntArray>
    private lateinit var tokenizer: PhoneTokenizer
    private lateinit var pre: OrtSession
    private lateinit var dec: OrtSession
    private lateinit var acoustic: OrtSession
    private lateinit var codec: OrtSession
    private lateinit var streamCodec: OrtSession
    private lateinit var streamMeta: JSONObject
    val sampleRate = 48000
    init {
        try {
            val marker = File(root, "complete")
            if (!marker.exists() || marker.readText() != "2") {
                fun copyAsset(path: String, dest: File) {
                    val children = context.assets.list(path).orEmpty()
                    if (children.isNotEmpty()) { dest.mkdirs(); children.forEach { copyAsset("$path/$it", File(dest, it)) } }
                    else { dest.parentFile!!.mkdirs(); context.assets.open(path).use { src -> dest.outputStream().use { src.copyTo(it) } } }
                }
                copyAsset("tts", root)
                marker.writeText("2")
            }
            g2p = NativeTts.open(File(root, "sea_g2p.bin").absolutePath)
            textEmb = floats(File(root, "text_emb.f32"))
            audioEmb = floats(File(root, "audio_emb.f32"))
            require(textEmb.size == 419 * H && audioEmb.size == 16 * 1024 * H)
            val voice = JSONObject(File(root, "voice.json").readText())
            val a = voice.getJSONArray("anchor")
            anchor = FloatArray(H) { a.getDouble(it).toFloat() }
            val refs = voice.getJSONArray("codes")
            reference = Array(refs.length()) { i -> IntArray(16) { refs.getJSONArray(i).getInt(it) } }
            tokenizer = PhoneTokenizer(File(root, "backbone/tokenizer.json").readText())
            fun load(path: String): OrtSession = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(4); options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.addConfigEntry("session.intra_op.allow_spinning", "0")
                env.createSession(File(root, path).absolutePath, options).also { sessions += it }
            }
            pre = load("backbone/vieneu_prefill.onnx")
            dec = load("backbone/vieneu_decode_step.onnx")
            acoustic = load("backbone/vieneu_acoustic_cached.onnx")
            codec = load("codec/moss_audio_tokenizer_decode_full.onnx")
            streamCodec = load("codec/moss_audio_tokenizer_decode_step.onnx")
            streamMeta = JSONObject(File(root, "codec/codec_browser_onnx_meta.json").readText()).getJSONObject("streaming_decode")
        } catch (e: Throwable) { close(); throw e }
    }
    fun phonemes(text: String) = NativeTts.phonemize(g2p, text.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
    fun tokens(phones: String) = tokenizer.encode(phones)
    private fun floats(file: File): FloatArray = file.inputStream().channel.use { ch ->
        val bytes = ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, ch.size()).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.remaining() / 4).also { bytes.asFloatBuffer().get(it) }
    }
    private fun ft(values: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)
    private fun lt(values: LongArray, vararg shape: Long) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
    private fun it(values: IntArray, vararg shape: Long) = OnnxTensor.createTensor(env, IntBuffer.wrap(values), shape)
    private fun hidden(result: OrtSession.Result, last: Boolean = false): FloatArray {
        val buffer = (result[0] as OnnxTensor).floatBuffer
        if (last) buffer.position(buffer.limit() - H)
        return FloatArray(H).also { buffer.get(it) }
    }
    private fun past(feed: MutableMap<String, OnnxTensor>, result: OrtSession.Result, layers: Int) {
        for (i in 0 until layers) {
            feed["past_k_$i"] = result[1 + i] as OnnxTensor
            feed["past_v_$i"] = result[1 + layers + i] as OnnxTensor
        }
    }
    private fun embed(text: Int, codes: IntArray? = null): FloatArray = FloatArray(H) { h ->
        var v = textEmb[text * H + h]
        if (codes != null) for (c in 0..15) v += audioEmb[(c * 1024 + codes[c]) * H + h]
        v + anchor[h]
    }
    private fun sample(logits: FloatArray, history: ArrayDeque<Int>, greedy: Boolean): Int {
        history.toSet().forEach { logits[it] = if (logits[it] < 0) logits[it] * 1.2f else logits[it] / 1.2f }
        if (greedy) return logits.indices.maxBy { logits[it] }
        val candidates = logits.indices.sortedByDescending { logits[it] }.take(25)
        val max = logits[candidates[0]]
        val weights = candidates.map { exp(((logits[it] - max) / 0.8f).toDouble()) }
        val sum = weights.sum()
        var keep = 0; var total = 0.0
        while (keep < candidates.size && total / sum < 0.95) { total += weights[keep]; keep++ }
        var draw = Random.nextDouble() * total
        for (i in 0 until keep) { draw -= weights[i]; if (draw <= 0) return candidates[i] }
        return candidates[keep - 1]
    }
    private fun frame(h: FloatArray, histories: Array<ArrayDeque<Int>>, cancelled: AtomicBoolean, greedy: Boolean): Pair<IntArray, Boolean> {
        val codes = IntArray(16)
        var result: OrtSession.Result? = null
        var slot0 = FloatArray(H)
        try {
            for (c in 0..15) {
                checkCancelled(cancelled)
                val emb = if (c == 0) h + textEmb.copyOfRange(5 * H, 6 * H)
                    else audioEmb.copyOfRange(((c - 1) * 1024 + codes[c - 1]) * H, ((c - 1) * 1024 + codes[c - 1] + 1) * H)
                ft(emb, 1, if (c == 0) 2L else 1L, H.toLong()).use { token ->
                    lt(if (c == 0) longArrayOf(0, 1) else longArrayOf((c + 1).toLong()), 1, if (c == 0) 2L else 1L).use { pos ->
                        val feed = mutableMapOf("token_emb" to token, "position_ids" to pos)
                        var empty: OnnxTensor? = null
                        if (result == null) {
                            empty = ft(FloatArray(0), 1, 8, 0, 96)
                            feed["past_k_0"] = empty; feed["past_v_0"] = empty
                        } else past(feed, result!!, 1)
                        val next = try { acoustic.run(feed) } finally { empty?.close() }
                        result?.close(); result = next
                    }
                }
                if (c == 0) slot0 = hidden(result!!)
                codes[c] = sample(NativeTts.logits(audioEmb, c * 1024 * H, 1024, hidden(result!!, c == 0)), histories[c], greedy)
                histories[c].addLast(codes[c]); if (histories[c].size > 64) histories[c].removeFirst()
            }
            val eos = NativeTts.logits(textEmb, 0, 419, slot0).let { logits -> logits.indices.maxBy { logits[it] } == 6 }
            return codes to eos
        } finally { result?.close() }
    }
    fun synthesize(text: String, cancelled: AtomicBoolean = AtomicBoolean(), greedy: Boolean = false,
                   onAudio: ((FloatArray) -> Unit)? = null, progress: (Int) -> Unit = {}): FloatArray {
        require(text.isNotBlank() && text.length <= 200) { "Nhập từ 1 đến 200 ký tự." }
        require(!text.contains("<|")) { "Bản test chỉ nhận văn bản thường." }
        checkCancelled(cancelled)
        val phones = phonemes(text)
        val ids = intArrayOf(16, 3) + tokens(phones) + intArrayOf(4)
        val count = ids.size + reference.size
        require(count < 1600) { "Văn bản sau chuẩn hóa quá dài; hãy chia câu." }
        val prompt = FloatArray(count * H)
        ids.forEachIndexed { i, id -> embed(id).copyInto(prompt, i * H) }
        reference.forEachIndexed { i, codes -> embed(7, codes).copyInto(prompt, (ids.size + i) * H) }
        var result: OrtSession.Result? = null
        val frames = mutableListOf<IntArray>()
        val chunks = mutableListOf<FloatArray>()
        val stream = if (onAudio != null) StreamDecoder() else null
        var decoded = 0
        fun emit() {
            if (stream == null || decoded == frames.size) return
            checkCancelled(cancelled)
            val pcm = stream.decode(frames.subList(decoded, frames.size)); decoded = frames.size
            chunks += pcm; onAudio!!(pcm)
        }
        val histories = Array(16) { ArrayDeque<Int>() }
        try {
            ft(prompt, 1, count.toLong(), H.toLong()).use { result = pre.run(mapOf("inputs_embeds" to it)) }
            var h = hidden(result!!, true)
            var finished = false
            for (t in 0 until 300) {
                val (codes, eos) = frame(h, histories, cancelled, greedy)
                frames += codes
                if (stream != null && frames.size - decoded >= 4) emit()
                if (t % 4 == 0) progress(t + 1)
                if (eos) { finished = true; break }
                ft(embed(5, codes), 1, 1, H.toLong()).use { input ->
                    lt(longArrayOf((count + t).toLong()), 1, 1).use { pos ->
                        val feed = mutableMapOf("inputs_embeds" to input, "position_ids" to pos)
                        past(feed, result!!, 12)
                        val next = dec.run(feed)
                        result!!.close(); result = next; h = hidden(next)
                    }
                }
            }
            require(finished) { "Model chưa kết thúc sau 24 giây audio. Hãy thử câu ngắn hơn." }
            emit()
        } finally { result?.close(); stream?.close() }
        checkCancelled(cancelled)
        if (stream != null) return FloatArray(chunks.sumOf { it.size }).also { all ->
            var offset = 0; chunks.forEach { it.copyInto(all, offset); offset += it.size }
        }
        val flat = frames.flatMap { it.toList() }.toIntArray()
        it(flat, 1, frames.size.toLong(), 16).use { codes ->
            it(intArrayOf(frames.size), 1).use { length ->
                codec.run(mapOf("audio_codes" to codes, "audio_code_lengths" to length)).use { out ->
                    val tensor = out[0] as OnnxTensor
                    val shape = tensor.info.shape
                    val channels = shape[1].toInt(); val samples = shape[2].toInt()
                    val all = FloatArray(channels * samples); tensor.floatBuffer.get(all)
                    val mono = FloatArray(samples) { i -> (0 until channels).sumOf { all[it * samples + i].toDouble() }.toFloat() / channels }
                    require(mono.isNotEmpty() && mono.all { it.isFinite() }) { "Audio đầu ra không hợp lệ." }
                    checkCancelled(cancelled)
                    return mono
                }
            }
        }
    }
    private inner class StreamDecoder : Closeable {
        private val mappings = mutableListOf<Pair<String, String>>()
        private val state = mutableMapOf<String, OnnxTensor>()
        private val initial = mutableListOf<OnnxTensor>()
        private var owner: OrtSession.Result? = null
        init {
            fun add(meta: JSONObject, input: String, output: String, shapeKey: String, float: Boolean = false, fill: Int = 0) {
                val arr = meta.getJSONArray(shapeKey)
                val shape = LongArray(arr.length()) { arr.getLong(it) }
                val count = shape.fold(1L) { a, b -> a * b }.toInt()
                val tensor = if (float) ft(FloatArray(count), *shape) else it(IntArray(count) { fill }, *shape)
                val name = meta.getString(input)
                mappings += name to meta.getString(output); state[name] = tensor; initial += tensor
            }
            try {
                val offsets = streamMeta.getJSONArray("transformer_offsets")
                for (i in 0 until offsets.length()) add(offsets.getJSONObject(i), "input_name", "output_name", "shape")
                val caches = streamMeta.getJSONArray("attention_caches")
                for (i in 0 until caches.length()) {
                    val c = caches.getJSONObject(i)
                    add(c, "offset_input_name", "offset_output_name", "offset_shape")
                    add(c, "cached_keys_input_name", "cached_keys_output_name", "cache_shape", true)
                    add(c, "cached_values_input_name", "cached_values_output_name", "cache_shape", true)
                    add(c, "cached_positions_input_name", "cached_positions_output_name", "positions_shape", fill = -1)
                }
            } catch (e: Throwable) { close(); throw e }
        }
        fun decode(frames: List<IntArray>): FloatArray {
            it(frames.flatMap { it.toList() }.toIntArray(), 1, frames.size.toLong(), 16).use { codes ->
                it(intArrayOf(frames.size), 1).use { length ->
                    val feed = state.toMutableMap()
                    feed["audio_codes"] = codes; feed["audio_code_lengths"] = length
                    val next = streamCodec.run(feed)
                    owner?.close(); initial.forEach { it.close() }; initial.clear(); owner = next
                    mappings.forEach { (input, output) -> state[input] = next.get(output).get() as OnnxTensor }
                    val tensor = next.get("audio").get() as OnnxTensor
                    val shape = tensor.info.shape; val channels = shape[1].toInt(); val samples = shape[2].toInt()
                    val all = FloatArray(channels * samples); tensor.floatBuffer.get(all)
                    val valid = (next.get("audio_lengths").get() as OnnxTensor).intBuffer.get(0)
                    require(valid in 1..samples)
                    return FloatArray(valid) { i -> (0 until channels).sumOf { all[it * samples + i].toDouble() }.toFloat() / channels }
                        .also { require(it.isNotEmpty() && it.all { x -> x.isFinite() }) }
                }
            }
        }
        override fun close() { owner?.close(); owner = null; initial.forEach { it.close() }; initial.clear(); state.clear() }
    }
    override fun close() { sessions.asReversed().forEach { runCatching { it.close() } }; sessions.clear(); if (g2p != 0L) { NativeTts.close(g2p); g2p = 0 } }
    private fun checkCancelled(cancelled: AtomicBoolean) { if (cancelled.get()) throw CancellationException("Đã dừng TTS") }
    companion object { private const val H = 768 }
}
