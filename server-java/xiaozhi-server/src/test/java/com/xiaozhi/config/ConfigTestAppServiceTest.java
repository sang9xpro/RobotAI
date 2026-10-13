package com.xiaozhi.config;

import com.xiaozhi.ai.probe.ConfigProbe;
import com.xiaozhi.ai.llm.factory.providers.CodexChatGptModelProvider;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住配置试拨的合并编排：身份来自调用方、密钥回填只对当前用户本人的配置生效，
 * 他人共享配置一律按库里那条测试；配置类型由表单提交值直接判定、不查库，
 * 因为配置可能正在改、还没保存，库里旧值判类型会文不对题。
 */
@ExtendWith(MockitoExtension.class)
class ConfigTestAppServiceTest {

    private static final int SAVED_CONFIG_ID = 7;
    private static final int CURRENT_USER_ID = 9;
    private static final int OTHER_USER_ID = 10;
    private static final int ADMIN_USER_ID = 1;

    @Mock
    private ConfigConvert configConvert;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigProbe configProbe;

    @Mock
    private CodexChatGptModelProvider codexChatGptModelProvider;

    @InjectMocks
    private ConfigTestAppService configTestAppService;

    @Test
    void callerUserIdIsCarriedIntoTheConfigUsedForTesting() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(ConfigProbeResultBO.success("ok"));

        configTestAppService.test(request("stt"), CURRENT_USER_ID);

        assertThat(probedConfig().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    // 共享配置照常测得通，用的是库里那条的密钥与端点
    @Test
    void sharedConfigIsTestedWithItsOwnSavedCredentials() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig());
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(ADMIN_USER_ID)));
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(ConfigProbeResultBO.success("ok"));

        configTestAppService.test(request("stt"), CURRENT_USER_ID);

        ConfigBO used = probedConfig();
        assertThat(used.getApiKey()).isEqualTo("saved-key");
        assertThat(used.getApiUrl()).isEqualTo("https://saved.example.com");
        // 整条取库里那条，连归属与元数据都是库里的，表单一个字段都不参与
        assertThat(used.getUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(used.getConfigName()).isEqualTo("语音配置");
        assertThat(used.getState()).isEqualTo(AiConfig.STATE_ENABLED);
        assertThat(used.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_NO);
    }

    // 已保存的密钥只能配已保存的端点，表单里的端点对他人配置不生效
    @Test
    void formEndpointCannotBePairedWithAnotherUsersSavedSecret() {
        ConfigBO form = sttConfig().setApiKey("attacker-key").setApiUrl("https://attacker.example.com");
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(OTHER_USER_ID)));
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(ConfigProbeResultBO.success("ok"));

        configTestAppService.test(request("stt"), CURRENT_USER_ID);

        ConfigBO used = probedConfig();
        assertThat(used.getApiUrl()).isEqualTo("https://saved.example.com");
        assertThat(used.getApiKey()).isEqualTo("saved-key");
    }

    // 本人配置：表单字段优先（含 configType），表单留空的字段（如没重填的 apiKey）回填保存值
    @Test
    void ownConfigPrefersFormFieldsAndFillsBlankApiKeyFromSaved() {
        ConfigBO form = sttConfig().setApiKey(null).setConfigType("llm").setApiUrl("https://form.example.com");
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(CURRENT_USER_ID)));
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(ConfigProbeResultBO.success("ok"));

        configTestAppService.test(request("llm"), CURRENT_USER_ID);

        ConfigBO used = probedConfig();
        assertThat(used.getConfigType()).isEqualTo("llm");
        assertThat(used.getApiUrl()).isEqualTo("https://form.example.com");
        assertThat(used.getApiKey()).isEqualTo("saved-key");
    }

    @Test
    void configIdNullSkipsRepositoryLookup() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(ConfigProbeResultBO.success("ok"));

        configTestAppService.test(request("stt"), CURRENT_USER_ID);

        verify(configRepository, never()).findById(any());
    }

    // 试拨以前端提交内容为准，包括配置类型：即使库里保存的是 llm，表单类型不支持就直接失败、不查库
    @Test
    void unsupportedFormConfigTypeFailsWithoutQueryingRepositoryEvenIfSavedTypeIsLlm() {
        ConfigProbeResultBO response = configTestAppService.test(request("oss"), CURRENT_USER_ID);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("暂不支持测试该类型配置");
        verifyNoInteractions(configConvert, configRepository, configProbe);
    }

    @Test
    void probeResultIsReturnedAsIs() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        ConfigProbeResultBO expected = ConfigProbeResultBO.failure("接口返回未授权（401），请检查密钥与账号权限");
        when(configProbe.probe(any(ConfigBO.class))).thenReturn(expected);

        ConfigProbeResultBO response = configTestAppService.test(request("stt"), CURRENT_USER_ID);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void visionTestUsesEnteredQuestionAndImageWithOwnSavedCredentials() {
        ConfigTestReq req = request("llm");
        req.setModelType("vision");
        req.setSampleText("Hãy đọc chữ trong ảnh.");
        ConfigBO form = sttConfig().setConfigType("llm").setModelType("vision").setApiUrl("https://form.example.com");
        when(configConvert.toBO(req)).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(CURRENT_USER_ID)));
        Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[]{1, 2}).build();
        ConfigProbeResultBO expected = ConfigProbeResultBO.success("Nội dung ảnh");
        when(configProbe.probeVision(any(), eq(req.getSampleText()), eq(image))).thenReturn(expected);

        assertThat(configTestAppService.testVisionImage(req, CURRENT_USER_ID, image)).isSameAs(expected);

        ArgumentCaptor<ConfigBO> captor = ArgumentCaptor.captor();
        verify(configProbe).probeVision(captor.capture(), eq("Hãy đọc chữ trong ảnh."), eq(image));
        assertThat(captor.getValue().getModelType()).isEqualTo("vision");
        assertThat(captor.getValue().getApiKey()).isEqualTo("saved-key");
        assertThat(captor.getValue().getApiUrl()).isEqualTo("https://form.example.com");
        assertThat(captor.getValue().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    void nonVisionAttachmentRequestIsRejectedBeforeResolvingCredentials() {
        ConfigTestReq req = request("llm");
        req.setModelType("chat");
        assertThat(configTestAppService.testVisionImage(req, CURRENT_USER_ID, null).success()).isFalse();
        verifyNoInteractions(configConvert, configRepository, configProbe);
    }

    private ConfigBO probedConfig() {
        ArgumentCaptor<ConfigBO> captor = ArgumentCaptor.captor();
        verify(configProbe).probe(captor.capture());
        return captor.getValue();
    }

    @Test
    void catalogFillsAnUnchangedTokenFromTheOwnedConfiguration() {
        ConfigBO form = sttConfig().setProvider("codex-chatgpt").setConfigType("llm")
                .setApiUrl("http://localhost:8787/v1").setApiKey(null);
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(CURRENT_USER_ID)));
        when(codexChatGptModelProvider.listModels(any())).thenReturn(java.util.List.of());

        configTestAppService.listModels(request("llm"), CURRENT_USER_ID);

        ArgumentCaptor<ConfigBO> used = ArgumentCaptor.captor();
        verify(codexChatGptModelProvider).listModels(used.capture());
        assertThat(used.getValue().getApiKey()).isEqualTo("saved-key");
        assertThat(used.getValue().getApiUrl()).isEqualTo("http://localhost:8787/v1");
        verifyNoInteractions(configProbe);
    }

    @Test
    void catalogCannotPairAnotherUsersTokenWithAFormEndpoint() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig()
                .setProvider("codex-chatgpt").setConfigType("llm").setApiUrl("http://localhost:9999/v1"));
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(OTHER_USER_ID)));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> configTestAppService.listModels(request("llm"), CURRENT_USER_ID));
        verifyNoInteractions(codexChatGptModelProvider);
    }

    private static ConfigTestReq request(String configType) {
        ConfigTestReq req = new ConfigTestReq();
        req.setConfigType(configType);
        req.setConfigName("测试配置");
        req.setProvider("aliyun-nls");
        return req;
    }

    private static AiConfig savedSttConfig(int userId) {
        return AiConfig.reconstitute(SAVED_CONFIG_ID, userId, "stt", "aliyun-nls",
                "语音配置", null, null,
                null, "saved-key", null,
                null, null, "https://saved.example.com",
                null, null, AiConfig.STATE_ENABLED, false,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0));
    }

    private static ConfigBO sttConfig() {
        return new ConfigBO()
                .setConfigId(SAVED_CONFIG_ID)
                .setConfigType("stt")
                .setProvider("aliyun-nls");
    }
}
