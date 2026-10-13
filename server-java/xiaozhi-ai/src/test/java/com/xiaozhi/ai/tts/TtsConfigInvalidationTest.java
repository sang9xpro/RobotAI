package com.xiaozhi.ai.tts;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.config.RuntimePathConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TtsConfigInvalidationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    @Test void removesPreviousAndCurrentProviderForSameIdOnly() {
        var factory=new TtsServiceFactory();
        @SuppressWarnings("unchecked") var cache=(Map<String,TtsService>)ReflectionTestUtils.getField(factory,"serviceCache");
        cache.put("old:7:v:1:1",mock(TtsService.class));cache.put("openai:7:v:1:1",mock(TtsService.class));cache.put("other:8:v:1:1",mock(TtsService.class));
        factory.onAiConfigChanged(new com.xiaozhi.event.AiConfigChangedEvent(this,"tts",7));
        assertThat(cache.keySet()).containsExactly("other:8:v:1:1");
    }
    @Test void unknownProviderCannotSilentlyBecomeEdge() {
        var factory=new TtsServiceFactory();ReflectionTestUtils.setField(factory,"runtimePathConfig",new RuntimePathConfig());
        String previous=com.xiaozhi.utils.AudioUtils.AUDIO_PATH;
        com.xiaozhi.utils.AudioUtils.AUDIO_PATH=root.toString();
        try { assertThatThrownBy(()->factory.createApiService(new ConfigBO().setProvider("misspelled"),"alloy",1.0,1.0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
        } finally { com.xiaozhi.utils.AudioUtils.AUDIO_PATH=previous; }
    }
}
