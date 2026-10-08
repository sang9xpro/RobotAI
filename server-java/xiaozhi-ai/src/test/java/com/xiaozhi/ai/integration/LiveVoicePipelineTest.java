package com.xiaozhi.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.ai.stt.providers.PhoWhisperSttService;
import com.xiaozhi.ai.tts.providers.EdgeTtsService;
import com.xiaozhi.ai.llm.factory.providers.CodexChatGptModelProvider;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Real providers, with a WAV input and file output. Does not impersonate Android E2E. */
@EnabledIfEnvironmentVariable(named = "KEN_VOICE_LIVE", matches = "1")
class LiveVoicePipelineTest {
    @Test void audioToTranscriptToStreamingLlmToRealTts() throws Exception {
        Path root = Path.of(System.getProperty("robotai.root")).toAbsolutePath();
        Path out = root.resolve(".local/full-voice-test");
        Files.createDirectories(out);
        var mapper = new ObjectMapper();
        var report = new LinkedHashMap<String, Object>();
        report.put("scope", "real Java providers; WAV input/file output; no Android/WebSocket/playback");
        long start = System.nanoTime();
        String stage = "stt";
        try {
            byte[] wav = Files.readAllBytes(root.resolve(".local/phowhisper-smoke.wav"));
            assertEquals("data", new String(wav, 36, 4, java.nio.charset.StandardCharsets.US_ASCII));
            var stt = new PhoWhisperSttService("http://127.0.0.1:8788/inference");
            assertTrue(stt.isReady());
            var recognized = stt.stream(Flux.just(Arrays.copyOfRange(wav, 44, wav.length)));
            assertNull(recognized.failureReason());
            assertFalse(recognized.text().isBlank());
            report.put("stt_text", recognized.text());
            report.put("stt_ms", elapsed(start));
            stage = "llm";
            var gateway = mapper.readTree(Files.readString(root.resolve(".local/llm-gateway.json")));
            var provider = new CodexChatGptModelProvider();
            ReflectionTestUtils.setField(provider, "toolCallingManager", ToolCallingManager.builder().build());
            ReflectionTestUtils.setField(provider, "observationRegistry", ObservationRegistry.NOOP);
            var config = new ConfigBO().setProvider("codex-chatgpt").setApiUrl("http://127.0.0.1:8787/v1")
                    .setApiKey(gateway.path("token").asText()).setConfigName("codex-default").setEnableThinking(false);
            var model = provider.createChatModel(config, new RoleBO());
            long llmStart = System.nanoTime();
            var first = new AtomicLong(-1);
            var text = new StringBuilder();
            model.stream(new Prompt("Bạn là Ken. Trả lời bằng một câu tiếng Việt ngắn, tối đa 20 từ, cho lời nói sau: " + recognized.text()))
                .doOnNext(response -> {
                    if (response.getResult() != null) {
                        String chunk = response.getResult().getOutput().getText();
                        if (chunk != null && !chunk.isEmpty()) {
                            first.compareAndSet(-1, elapsed(llmStart));
                            text.append(chunk);
                        }
                    }
                }).blockLast(Duration.ofSeconds(100));
            assertFalse(text.toString().isBlank());
            report.put("llm_text", text.toString());
            report.put("llm_first_token_ms", first.get());
            report.put("llm_ms", elapsed(llmStart));
            stage = "tts";
            String voice = System.getProperty("voice.test.voice", "zh-CN-XiaoyiNeural");
            report.put("tts_provider", "edge");
            report.put("tts_voice", voice);
            long ttsStart = System.nanoTime();
            var tts = new EdgeTtsService(voice, 1.0, 1.0, out.toString());
            Path audio = tts.textToSpeech(text.toString());
            assertTrue(Files.size(audio) > 100);
            Files.copy(audio, out.resolve("reply.mp3"), StandardCopyOption.REPLACE_EXISTING);
            report.put("tts_ms", elapsed(ttsStart));
            report.put("tts_bytes", Files.size(audio));
            report.put("result", "passed");
        } catch (Throwable error) {
            report.put("result", "failed");
            report.put("failed_stage", stage);
            report.put("error_type", error.getClass().getSimpleName());
            throw error;
        } finally {
            report.put("total_ms", elapsed(start));
            Files.writeString(out.resolve("result.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
    private static long elapsed(long since) { return (System.nanoTime() - since) / 1_000_000; }
}
