package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.common.model.bo.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class IndividualConversationTest {
    private final String client=UUID.randomUUID().toString();
    private final SpeakerHint a=new SpeakerHint("recognized",UUID.randomUUID().toString(),"Sang","anh Sang","face_voice");
    private ListeningContext context(long revision,String reason,boolean active) {
        return new ListeningContext(client,revision,reason,"Ken",true,true,true,active,100,false,false,1);
    }
    @Test void rejectsAmbientAndUnverifiedActiveSpeaker() {
        var conversation=new IndividualConversation();
        assertThat(conversation.admit("Ken là robot",a,context(1,"wake",true),0).action()).isEqualTo(IndividualConversation.Action.IGNORE);
        assertThat(conversation.admit("Chào bạn",a,context(2,"face_voice",false),1).action()).isEqualTo(IndividualConversation.Action.IGNORE);
        assertThat(conversation.admit("Chào bạn",a,context(3,"face_voice",true),2).action()).isEqualTo(IndividualConversation.Action.ANSWER);
    }
    @Test void continuationAndNewOwnerHaveSeparateHistories() {
        var conversation=new IndividualConversation();
        var first=conversation.admit("Ken ơi, chào bạn",a,context(1,"wake",false),0);
        conversation.playbackCompleted(1000);
        var continuation=conversation.admit("Còn ngày mai?",a,context(2,"continuation",false),2000);
        assertThat(continuation.scopeId()).isEqualTo(first.scopeId());
        assertThat(continuation.switched()).isFalse();
        assertThat(conversation.admit("Lặp lại",a,context(2,"continuation",true),3000).action()).isEqualTo(IndividualConversation.Action.IGNORE);
        var b=new SpeakerHint("temporary",UUID.randomUUID().toString(),"Khách","bạn","session_voice");
        assertThat(conversation.admit("Còn ngày mai?",b,context(3,"continuation",true),4000).action()).isEqualTo(IndividualConversation.Action.IGNORE);
        assertThat(conversation.admit("Ken ơi, chào",b,context(3,"wake",true),4000).scopeId()).isNotEqualTo(first.scopeId());
    }
    @Test void temporaryVoiceExpiresAndLoneWakeClarifies() {
        var conversation=new IndividualConversation();
        var guest=new SpeakerHint("temporary",UUID.randomUUID().toString(),"Khách","bạn","session_voice");
        assertThat(conversation.admit("Ken ơi",guest,context(1,"wake",false),0).action()).isEqualTo(IndividualConversation.Action.CLARIFY);
        conversation.playbackCompleted(179000);
        assertThat(conversation.admit("Tiếp tục",guest,context(2,"continuation",true),180000).action()).isEqualTo(IndividualConversation.Action.IGNORE);
    }
}
