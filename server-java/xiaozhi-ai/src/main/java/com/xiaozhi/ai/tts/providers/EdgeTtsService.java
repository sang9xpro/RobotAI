package com.xiaozhi.ai.tts.providers;

import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.concurrent.*;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EdgeTtsService implements TtsService {
    private static final String PROVIDER_NAME = "edge";

    private final XiaozhiTtsOptions options;
    private final String outputPath;
    private final long timeoutMillis;
    private static final ExecutorService REQUESTS = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore SLOTS = new Semaphore(4);

    public EdgeTtsService(String voiceName, Double pitch, Double speed, String outputPath) {
        this(voiceName, pitch, speed, outputPath, 25000);
    }

    EdgeTtsService(String voiceName, Double pitch, Double speed, String outputPath, long timeoutMillis) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public XiaozhiTtsOptions getOptions() {
        return options;
    }

    @Override
    public String audioFormat() {
        return "mp3";
    }

    @Override
    public Path textToSpeech(String text) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Future<Path> work = REQUESTS.submit(() -> {
                if (!SLOTS.tryAcquire()) throw new com.xiaozhi.ai.tts.TtsOverloadException("Edge TTS busy");
                try {
                    Path path = synthesizeOnce(text);
                    if (path == null || !Files.isRegularFile(path) || Files.size(path) < 100)
                        throw new java.io.IOException("Edge TTS produced no audio file");
                    return path;
                } finally { SLOTS.release(); }
            });
            try { return work.get(timeoutMillis, TimeUnit.MILLISECONDS); }
            catch (InterruptedException error) { work.cancel(true); throw error; }
            catch (TimeoutException error) { work.cancel(true); last = new TimeoutException("Edge TTS timed out"); }
            catch (ExecutionException error) {
                if (error.getCause() instanceof com.xiaozhi.ai.tts.TtsOverloadException overload) throw overload;
                last = new java.io.IOException("Edge TTS synthesis failed", error.getCause());
            }
        }
        throw last;
    }

    protected Path synthesizeOnce(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            throw new Exception("文本内容为空");
        }

        Voice voiceObj = TTSVoice.provides().stream()
                .filter(v -> v.getShortName().equals(getVoiceName()))
                .findFirst()
                .orElseThrow(() -> new Exception("Edge TTS 找不到语音: " + getVoiceName()));

        int ratePercent = (int) ((getSpeed() - 1.0f) * 100);
        int pitchHz = (int) ((getPitch() - 1.0f) * 50);
        String pitch = (pitchHz >= 0 ? "+" : "") + pitchHz + "Hz";
        String rate = (ratePercent >= 0 ? "+" : "") + ratePercent + "%";

        String filename = new TTS(voiceObj, text)
                .findHeadHook()
                .isRateLimited(true)
                .storage(outputPath)
                .voicePitch(pitch)
                .voiceRate(rate)
                .connectTimeout(10000)
                .trans();

        if (filename == null || filename.isEmpty()) {
            throw new Exception("Edge TTS 合成结果为空");
        }
        return Paths.get(outputPath, filename);
    }
}
