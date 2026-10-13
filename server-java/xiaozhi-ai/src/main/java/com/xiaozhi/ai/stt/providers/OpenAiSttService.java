package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.utils.OpenAiSpeechEndpoint;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.JsonUtil;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** Segment transcription. Buffers bounded PCM S16LE mono 16 kHz; does not advertise partial results. */
public final class OpenAiSttService implements SttService {
    private static final int MAX_PCM_BYTES = 16000 * 2 * 30;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiSttService(ConfigBO config) {
        endpoint = OpenAiSpeechEndpoint.resolve(config.getApiUrl(), "/audio/transcriptions");
        apiKey = config.getApiKey();
        model = config.getConfigName();
        if (model == null || model.isBlank() || model.contains("\r") || model.contains("\n"))
            throw new IllegalArgumentException("Transcription model is required");
    }

    @Override public String getProviderName() { return "openai"; }

    @Override public SttResult stream(Flux<byte[]> audioSink) {
        try {
            ByteArrayOutputStream pcm = audioSink.reduceWith(ByteArrayOutputStream::new, (buffer, frame) -> {
                if (frame == null || frame.length % 2 != 0 || buffer.size() + frame.length > MAX_PCM_BYTES)
                    throw new IllegalArgumentException("Invalid or oversized PCM segment");
                buffer.writeBytes(frame);
                return buffer;
            }).block(Duration.ofSeconds(45));
            if (pcm == null || pcm.size() == 0) return SttResult.textOnly("");
            String boundary = "speech-" + UUID.randomUUID();
            var body = new ByteArrayOutputStream();
            field(body, boundary, "model", model);
            field(body, boundary, "language", "vi");
            field(body, boundary, "response_format", "json");
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(wav(pcm.toByteArray()));
            body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] response = OpenAiSpeechEndpoint.post(endpoint, apiKey, "multipart/form-data; boundary=" + boundary,
                    body.toByteArray(), 1024 * 1024);
            var json = JsonUtil.OBJECT_MAPPER.readTree(response);
            if (json == null || !json.path("text").isTextual() || json.has("error"))
                return SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);
            return SttResult.textOnly(json.path("text").asText().trim());
        } catch (OpenAiSpeechEndpoint.ProviderHttpException e) {
            return SttResult.failure(switch (e.status()) {
                case 401, 403 -> "unauthorized";
                case 429 -> "rate_limited";
                case 404 -> "model_unavailable";
                default -> SttResult.FAILURE_UPSTREAM_ERROR;
            });
        } catch (InterruptedIOException e) {
            return SttResult.failure(Thread.currentThread().isInterrupted() ? "cancelled" : SttResult.FAILURE_TIMEOUT);
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
        }
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
