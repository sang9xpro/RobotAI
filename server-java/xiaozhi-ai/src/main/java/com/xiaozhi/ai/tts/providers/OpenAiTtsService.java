package com.xiaozhi.ai.tts.providers;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.ai.utils.OpenAiSpeechEndpoint;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** OpenAI-compatible /audio/speech. Pitch is unavailable in this protocol; options retain a neutral pitch. */
public final class OpenAiTtsService implements TtsService {
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Path outputPath;
    private final XiaozhiTtsOptions options;

    public OpenAiTtsService(ConfigBO config, String voiceName, Double pitch, Double speed, String outputPath) {
        endpoint = OpenAiSpeechEndpoint.resolve(config.getApiUrl(), "/audio/speech");
        apiKey = config.getApiKey();
        model = config.getConfigName();
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Speech model is required");
        double rate = speed == null ? 1.0 : speed;
        if (!Double.isFinite(rate) || rate < 0.25 || rate > 4.0) throw new IllegalArgumentException("Invalid speech speed");
        options = XiaozhiTtsOptions.builder().voiceName(voiceName == null || voiceName.isBlank() ? "alloy" : voiceName)
                .pitch(1.0).speed(rate).build();
        this.outputPath = Path.of(outputPath);
    }

    @Override public String getProviderName() { return "openai"; }
    @Override public XiaozhiTtsOptions getOptions() { return options; }
    @Override public String audioFormat() { return "mp3"; }

    @Override public Path textToSpeech(String text) throws Exception {
        if (text == null || text.isBlank() || text.length() > 4096)
            throw new IllegalArgumentException("Speech text must contain 1 to 4096 characters");
        var params = Map.of("model", model, "input", text, "voice", getVoiceName(),
                "response_format", "mp3", "speed", getSpeed());
        byte[] audio = OpenAiSpeechEndpoint.post(endpoint, apiKey, "application/json",
                JsonUtil.toJson(params).getBytes(StandardCharsets.UTF_8), 16 * 1024 * 1024);
        if (audio.length < 2 || !(audio[0] == 'I' && audio[1] == 'D' && audio.length > 2 && audio[2] == '3')
                && !(Byte.toUnsignedInt(audio[0]) == 0xff && (Byte.toUnsignedInt(audio[1]) & 0xe0) == 0xe0))
            throw new IOException("Speech provider did not return MP3 audio");
        Files.createDirectories(outputPath);
        Path output = outputPath.resolve(getAudioFileName());
        Files.write(output, audio);
        return output;
    }
}
