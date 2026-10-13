package com.xiaozhi.config.convert;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigReasoningEffortTest {
    private final ConfigConvert api = Mappers.getMapper(ConfigConvert.class);
    private final ConfigConverter persistence = new ConfigConverter();

    @Test
    void effortSurvivesCreatePersistenceReloadAndResponse() {
        var request = new ConfigCreateReq();
        request.setConfigType("llm"); request.setProvider("codex-chatgpt");
        request.setConfigName("codex-default"); request.setReasoningEffort("high");
        var config = AiConfig.newConfig(7, api.toBO(request));
        var saved = persistence.toDO(config);
        var restored = persistence.toDomain(saved);
        assertThat(saved.getReasoningEffort()).isEqualTo("high");
        assertThat(persistence.toBO(restored).getReasoningEffort()).isEqualTo("high");
        assertThat(api.toResp(restored).getReasoningEffort()).isEqualTo("high");
        assertThat(api.toResp(api.toBO(saved)).getReasoningEffort()).isEqualTo("high");
    }

    @Test
    void omittedUpdateAndTestEffortRetainSavedValueWhileExplicitPatchOverridesIt() {
        var config = AiConfig.newConfig(7, new ConfigBO().setReasoningEffort("high"));
        config.update(api.toBO(new ConfigUpdateReq()));
        assertThat(config.getReasoningEffort()).isEqualTo("high");
        assertThat(config.mergePatch(api.toBO(new ConfigTestReq())).getReasoningEffort()).isEqualTo("high");
        var test = new ConfigTestReq(); test.setReasoningEffort("low");
        assertThat(config.mergePatch(api.toBO(test)).getReasoningEffort()).isEqualTo("low");
        assertThat(config.getReasoningEffort()).isEqualTo("high");
        var update = new ConfigUpdateReq(); update.setReasoningEffort("none");
        config.update(api.toBO(update));
        assertThat(persistence.toDomain(persistence.toDO(config)).getReasoningEffort()).isEqualTo("none");
    }

    @Test
    void legacyConfigurationRemainsNullable() {
        var config = AiConfig.newConfig(7, new ConfigBO().setEnableThinking(true));
        assertThat(persistence.toBO(persistence.toDomain(persistence.toDO(config))).getReasoningEffort()).isNull();
        assertThat(config.mergePatch(new ConfigBO()).getEnableThinking()).isTrue();
    }
}
