package com.xiaozhi.dialogue.runtime;
import com.xiaozhi.common.model.bo.SpeakerHint;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SpeakerTurnContextTest {
    private final SpeakerHint hint=new SpeakerHint("recognized","ffccbbaa-1111-4444-8888-123456789abc","Sang","anh Sang","face_voice");
    @Test void noCrossTurnOrReplayAttribution() {
        var c=new SpeakerTurnContext(); c.begin("1");
        assertThat(c.complete("1",hint).name()).isEqualTo("Sang");
        assertThat(c.complete("1",hint).status()).isEqualTo("unknown");
        c.begin("2");assertThat(c.accepts("1")).isFalse();
        assertThat(c.complete("2",null).status()).isEqualTo("unknown");
    }
    @Test void aNewTransportCannotInheritIdentity() {
        assertThat(new SpeakerTurnContext().complete("1",hint).status()).isEqualTo("unknown");
        var c=new SpeakerTurnContext();c.begin(null);assertThat(c.complete(null,hint).status()).isEqualTo("unknown");
    }
}
