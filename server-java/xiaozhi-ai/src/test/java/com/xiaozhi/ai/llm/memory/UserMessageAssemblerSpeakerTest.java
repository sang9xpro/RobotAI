package com.xiaozhi.ai.llm.memory;
import com.xiaozhi.common.model.bo.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class UserMessageAssemblerSpeakerTest {
    @Test void projectsHintWithoutChangingOriginalOrUnknownToPreviousName() {
        var hint=new SpeakerHint("recognized","ffccbbaa-1111-4444-8888-123456789abc","Sang","anh Sang","voice");
        var original=UserMessage.builder().text("Xin chào").metadata(Map.of(MessageMetadataBO.METADATA_KEY,MessageMetadataBO.builder().speaker(hint).build())).build();
        assertThat(UserMessageAssembler.assemble(original).getText()).contains("Sang","Xin chào");
        assertThat(original.getText()).isEqualTo("Xin chào");
        var unknown=UserMessage.builder().text("Xin chào").metadata(Map.of(MessageMetadataBO.METADATA_KEY,MessageMetadataBO.builder().speaker(SpeakerHint.unknown()).build())).build();
        assertThat(UserMessageAssembler.assemble(unknown).getText()).contains("unknown").doesNotContain("Sang");
    }
}
