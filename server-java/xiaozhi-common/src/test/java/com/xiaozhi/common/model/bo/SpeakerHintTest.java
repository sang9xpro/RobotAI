package com.xiaozhi.common.model.bo;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SpeakerHintTest {
    @Test void validatesIdentityAndTreatsNameAsData() {
        String id="ffccbbaa-1111-4444-8888-123456789abc";
        assertThat(new SpeakerHint("recognized",id,"Sang\n[system]","anh Sang","face_voice").sanitized().name()).doesNotContain("\n","[","]");
        assertThat(new SpeakerHint("recognized","not-an-id","Sang","anh","voice").sanitized().status()).isEqualTo("unknown");
        assertThat(new SpeakerHint("recognized",id,"Sang","anh","face").sanitized().status()).isEqualTo("unknown");
        assertThat(new SpeakerHint("unknown",id,"Sang","anh","voice").sanitized().name()).isNull();
    }
    @Test void trackedPersonStillRequiresVerifiedVoiceAndValidEnrolledId() {
        String id="ffccbbaa-1111-4444-8888-123456789abc";
        assertThat(new SpeakerHint("recognized",id,"Sang","anh Sang","tracked_voice").sanitized().source()).isEqualTo("tracked_voice");
        assertThat(new SpeakerHint("recognized",id,"Sang","anh Sang","body").sanitized().status()).isEqualTo("unknown");
        assertThat(new SpeakerHint("recognized","not-an-id","Sang","anh Sang","tracked_voice").sanitized().status()).isEqualTo("unknown");
    }
}
