package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.utils.JsonUtil;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

/** Segment-based Vietnamese ASR. Input is PCM S16LE mono 16 kHz, not Opus.
 * No partial callbacks: whisper.cpp decodes only after the segment endpoint.
 */
public final class PhoWhisperSttService implements SttService {
    private static final int MAX_PCM_BYTES = 16000 * 2 * 30;
    private static final Semaphore CAPACITY = new Semaphore(1);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;

    public PhoWhisperSttService(String apiUrl) {
        endpoint = URI.create(apiUrl);
        if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme())) {
            throw new IllegalArgumentException("PhoWhisper requires an HTTP(S) inference URL");
        }
    }

    public boolean isReady() {
        try {
            var request = HttpRequest.newBuilder(endpoint.resolve("health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && "ok".equals(
                    JsonUtil.OBJECT_MAPPER.readTree(response.body()).path("status").asText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) { return false; }
    }

    @Override public String getProviderName() { return "phowhisper"; }

    @Override public SttResult stream(Flux<byte[]> audioSink) {
        if (!CAPACITY.tryAcquire()) return SttResult.failure("busy");
        try {
            // Bounded collection cancels upstream on oversized, failed or timed-out audio.
            ByteArrayOutputStream pcm = audioSink.reduceWith(ByteArrayOutputStream::new, (buffer, frame) -> {
                if (frame.length % 2 != 0 || buffer.size() + frame.length > MAX_PCM_BYTES) {
                    throw new IllegalArgumentException("Invalid or oversized PCM segment");
                }
                buffer.writeBytes(frame);
                return buffer;
            }).block(Duration.ofSeconds(45));
            if (Thread.currentThread().isInterrupted()) return SttResult.failure("cancelled");
            if (pcm == null || pcm.size() == 0) return SttResult.textOnly("");
            String boundary = "pho-" + UUID.randomUUID();
            var body = new ByteArrayOutputStream();
            field(body, boundary, "language", "vi");
            field(body, boundary, "translate", "false");
            field(body, boundary, "response_format", "json");
            field(body, boundary, "temperature", "0.0");
            field(body, boundary, "temperature_inc", "0.0");
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(wav(pcm.toByteArray()));
            body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);
            var json = JsonUtil.OBJECT_MAPPER.readTree(response.body());
            if (!json.path("text").isTextual() || json.has("error")) {
                return SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);
            }
            return SttResult.textOnly(json.get("text").asText().trim());
        } catch (HttpTimeoutException e) {
            return SttResult.failure(SttResult.FAILURE_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SttResult.failure("cancelled");
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return SttResult.failure("cancelled");
            }
            return SttResult.failure(e.getCause() instanceof TimeoutException ? SttResult.FAILURE_TIMEOUT
                    : e instanceof IllegalArgumentException ? SttResult.FAILURE_LOCAL_ERROR : SttResult.FAILURE_UPSTREAM_ERROR);
        } finally { CAPACITY.release(); }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] wav(byte[] pcm) {
        return ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm).array();
    }
}
