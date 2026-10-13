package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.communication.server.websocket.Rai1Codec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConversationTransportTest {
    @Test void rejectsStaleDuplicateAndReorderedFrames() {
        var transport=new ConversationTransport();transport.enable();long gen=transport.generation();
        assertThat(transport.begin(gen,"1")).isTrue();
        assertThat(transport.accept(new Rai1Codec.Frame(gen-1,1,0,new byte[]{1}))).isFalse();
        assertThat(transport.accept(new Rai1Codec.Frame(gen,1,0,new byte[]{1}))).isTrue();
        assertThatThrownBy(()->transport.accept(new Rai1Codec.Frame(gen,1,0,new byte[]{1}))).isInstanceOf(IllegalArgumentException.class);
        assertThat(transport.begin(gen,"2")).isFalse();
        Runnable old=transport.bind(()->assertThat(transport.encode(new byte[]{2})).isNull());
        transport.cancel();assertThat(transport.begin(gen,"2")).isTrue();old.run();
        assertThat(transport.accept(new Rai1Codec.Frame(gen,1,1,new byte[]{1}))).isFalse();
    }
    @Test void historyRequiresActuallyPresentedCompleteSentences() {
        var transport=new ConversationTransport();transport.enable();transport.begin(transport.generation(),"1");
        transport.sentence("Câu một");transport.encode(new byte[]{1});transport.encode(new byte[]{1});
        transport.acknowledge(0);assertThat(transport.heardText()).isEmpty();
        transport.sentence("Câu hai");transport.encode(new byte[]{1});
        transport.acknowledge(1);assertThat(transport.heardText()).isEqualTo("Câu một");
        transport.acknowledge(2);assertThat(transport.heardText()).isEqualTo("Câu một");
        assertThat(transport.confirmCompleted()).isFalse();
        transport.finishOutput();assertThat(transport.heardText()).isEqualTo("Câu một Câu hai");
        assertThat(transport.confirmCompleted()).isTrue();assertThat(transport.confirmCompleted()).isFalse();
    }
    @Test void rai1MatchesAndroidAndDoesNotFallbackToRawAudio() {
        var original=new Rai1Codec.Frame(0xffffffffL,42,7,new byte[]{1,2,3});
        var bytes=Rai1Codec.encode(original);
        var decoded=Rai1Codec.decode(bytes);
        assertThat(decoded.generation()).isEqualTo(original.generation());assertThat(decoded.payload()).containsExactly(1,2,3);
        assertThatThrownBy(()->Rai1Codec.decode(new byte[]{1,2,3})).isInstanceOf(IllegalArgumentException.class);
        bytes[23]=4;assertThatThrownBy(()->Rai1Codec.decode(bytes)).isInstanceOf(IllegalArgumentException.class);
    }
}
