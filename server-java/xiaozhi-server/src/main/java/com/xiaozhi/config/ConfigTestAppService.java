package com.xiaozhi.config;

import com.xiaozhi.ai.probe.ConfigProbe;
import com.xiaozhi.ai.llm.factory.providers.CodexChatGptModelProvider;
import com.xiaozhi.common.model.resp.ConfigModelResp;
import java.util.List;
import org.springframework.ai.content.Media;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.common.model.resp.TestVoiceResp;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.storage.service.StorageServiceFactory;

/**
 * 配置试拨的编排：把表单值与库里保存的配置合并成一份真正要外呼的配置，交给 ConfigProbe 去 Provider 试拨。
 * <p>真正拨号、翻译报错的逻辑在 xiaozhi-ai 的 {@link ConfigProbe}，这里只管定出「用哪份配置」。
 */
@Service
public class ConfigTestAppService {

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ConfigProbe configProbe;

    @Resource
    private CodexChatGptModelProvider codexChatGptModelProvider;

    public List<ConfigModelResp> listModels(ConfigTestReq req, Integer userId) {
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null ? null : configRepository.findById(form.getConfigId()).orElse(null);
        ConfigBO config = resolveConfigUnderTest(saved, form, userId);
        if (!"llm".equals(config.getConfigType()) || !"codex-chatgpt".equals(config.getProvider())) {
            throw new IllegalArgumentException("ChatGPT gateway configuration required");
        }
        try {
            return codexChatGptModelProvider.listModels(config);
        } catch (IllegalStateException e) {
            throw new OperationFailedException("Không lấy được danh sách model ChatGPT. Kiểm tra gateway và tài khoản đăng nhập.");
        }
    }

    /**
     * 测试配置：使用表单当前值（可能未保存）直接发起一次真实调用，把结果翻译成给前端看的结论。
     * <p>配置类型以表单提交的值为准，不查库判断——配置可能正在改、还没保存，用库里旧值判类型会文不对题。
     * <p>库里保存的密钥只能配库里保存的端点使用。本人的配置允许用表单值覆盖端点，
     * 共享配置一律按库里那条原样测试，表单里的端点与密钥都不生效。
     */
    @Resource
    private TtsServiceFactory ttsServiceFactory;
    @Resource
    private ProviderTokenClient tokenClient;
    @Resource
    private StorageServiceFactory storageServiceFactory;

    public TestVoiceResp testTtsAudio(ConfigTestReq req, Integer userId) {
        if (!"tts".equals(req.getConfigType())) throw new IllegalArgumentException("TTS configuration required");
        if (req.getVoiceName() == null || req.getVoiceName().isBlank())
            throw new IllegalArgumentException("Voice ID is required");
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null ? null : configRepository.findById(form.getConfigId()).orElse(null);
        ConfigBO config = resolveConfigUnderTest(saved, form, userId);
        if (!"tts".equals(config.getConfigType())) throw new IllegalArgumentException("TTS configuration required");
        config.setConfigId(ConfigProbe.nextTransientConfigId());
        Path audio = null;
        boolean uploaded = false;
        try {
            var service = ttsServiceFactory.createApiService(config, req.getVoiceName(), 1.0, 1.0);
            audio = service.textToSpeech(req.getSampleText() == null || req.getSampleText().isBlank()
                    ? "Xin chào, đây là RobotAI." : req.getSampleText());
            if (audio == null || !Files.isRegularFile(audio) || Files.size(audio) == 0)
                throw new OperationFailedException("TTS provider did not generate audio");
            String url = storageServiceFactory.getStorageService().upload(audio, audio.toString());
            uploaded = true;
            return new TestVoiceResp(url);
        } catch (com.xiaozhi.ai.utils.OpenAiSpeechEndpoint.ProviderHttpException e) {
            throw new OperationFailedException("TTS provider HTTP " + e.status() + "; check authentication, model or quota", e);
        } catch (java.io.InterruptedIOException e) {
            throw new OperationFailedException("TTS provider timed out", e);
        } catch (Exception e) {
            throw new OperationFailedException("TTS test failed; check provider settings", e);
        } finally {
            ttsServiceFactory.removeCache(config);
            tokenClient.removeCache(config);
            if (!uploaded && audio != null) {
                try { Files.deleteIfExists(audio); } catch (java.io.IOException ignored) { /* failed temporary artifact */ }
            }
        }
    }

    public ConfigProbeResultBO test(ConfigTestReq req, Integer userId) {
        String configType = req.getConfigType();
        if (!"llm".equals(configType) && !"stt".equals(configType) && !"tts".equals(configType)) {
            return ConfigProbeResultBO.failure("暂不支持测试该类型配置");
        }
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null
                ? null
                : configRepository.findById(form.getConfigId()).orElse(null);
        ConfigBO bo = resolveConfigUnderTest(saved, form, userId);
        return "tts".equals(bo.getConfigType()) || req.getSampleText() != null
                ? configProbe.probe(bo, req.getVoiceName(), req.getSampleText()) : configProbe.probe(bo);
    }

    public ConfigProbeResultBO testSttAudio(ConfigTestReq req, Integer userId, byte[] pcm) {
        if (!"stt".equals(req.getConfigType())) return ConfigProbeResultBO.failure("STT configuration required");
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null ? null : configRepository.findById(form.getConfigId()).orElse(null);
        return configProbe.probeSttAudio(resolveConfigUnderTest(saved, form, userId), pcm);
    }

    public ConfigProbeResultBO testVisionImage(ConfigTestReq req, Integer userId, Media image) {
        if (!"llm".equals(req.getConfigType()) || !ConfigBO.ModelType.vision.getValue().equals(req.getModelType())) {
            return ConfigProbeResultBO.failure("Vui lòng chọn cấu hình mô hình thị giác (Vision).");
        }
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null ? null : configRepository.findById(form.getConfigId()).orElse(null);
        return configProbe.probeVision(resolveConfigUnderTest(saved, form, userId), req.getSampleText(), image);
    }

    /**
     * 定出本次真正拿去外呼的配置。
     * 未保存的表单整条用表单值；本人已保存的配置用表单值覆盖、缺的补库里；
     * 他人共享的配置整条用库里的，表单不参与，避免已保存的密钥被配上调用方指定的端点。
     */
    private ConfigBO resolveConfigUnderTest(AiConfig saved, ConfigBO form, Integer userId) {
        if (saved == null) {
            return form;
        }
        if (Objects.equals(saved.getUserId(), userId)) {
            return saved.mergePatch(form);
        }
        return saved.mergePatch(new ConfigBO());
    }
}
