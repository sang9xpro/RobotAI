package com.xiaozhi.communication.protocol;

import com.xiaozhi.communication.server.websocket.Rai1Codec;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.*;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real hello/listen/binary/stop/abort handlers with a scripted STT, without network providers. */
class IndividualProtocolTest {
    private ProtocolTestHarness harness;
    private FakeDevice device;
    private long generation;
    @BeforeEach void connect() {
        harness=ProtocolTestHarness.create();device=harness.connect("11:22:33:44:55:66");
        device.sendText("""
            {"type":"hello","version":1,"audio_params":{"format":"opus","sample_rate":16000,"channels":1,"frame_duration":60},
             "robotai":{"contract_version":1,"conversation_context_v1":true,"audio_envelopes":["rai1"]}}
            """);
        var hello=device.transport().awaitJson("hello");generation=hello.path("robotai").path("generation").asLong();
        assertThat(generation).isPositive();assertThat(hello.path("robotai").path("mock").asBoolean()).isFalse();
    }
    @AfterEach void close() { harness.shutdown(); }
    private void start(long gen,String session) {
        device.sendText(JsonUtil.toJson(Map.of("type","listen","state","start","mode","manual",
                "session_id",session,"generation",gen,"turn_id","1")));
    }
    @Test void rejectsWrongSessionGenerationAndRawAudio() {
        start(generation,"wrong-session");assertThat(device.session().getDialogueContext().getConversationTransport().turn()).isZero();
        start(generation-1,device.sessionId());assertThat(device.session().getDialogueContext().getConversationTransport().turn()).isZero();
        start(generation,device.sessionId());assertThat(device.session().getDialogueContext().getConversationTransport().turn()).isEqualTo(1);
        device.sendRawAudio(new byte[]{1,2,3});assertThat(device.transport().isOpen()).isFalse();
        assertThat(harness.stt().streamCalls()).isZero();
    }
    @Test void finalSttRechecksWakeBeforeInvokingAnyLlm() {
        harness.stt().withFinalText("Ken là một robot");
        start(generation,device.sessionId());
        device.sendRawAudio(Rai1Codec.encode(new Rai1Codec.Frame(generation,1,0,FakeDevice.frame(ScriptedVadService.SPEECH_START))));
        AwaitHelper.until("STT stream",()->harness.stt().streamCalls()==1);
        var context=Map.of("conversation_id",UUID.randomUUID().toString(),"revision",1,"reason","wake","robot_name","Ken","accepted",true);
        device.sendText(JsonUtil.toJson(Map.of("type","listen","state","stop","session_id",device.sessionId(),
                "generation",generation,"turn_id","1","speaker",Map.of("status","unknown"),"listening_context",context)));
        var ignored=device.transport().awaitJson("turn_ignored");
        assertThat(ignored.path("reason").asText()).isEqualTo("not_addressed");
        assertThat(ignored.path("generation").asLong()).isEqualTo(generation);
        assertThat(device.transport().firstJson("stt")).isEmpty();
        assertThat(device.session().getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(harness.personaFactory(),never()).replaceIndividualConversation(any(),anyString());
    }
    @Test void abortExcludesLateTextAndAudioFromPreviousTurn() {
        start(generation,device.sessionId());
        var session=device.session();var transport=session.getDialogueContext().getConversationTransport();
        Runnable late=transport.bind(()->{ session.sendTextMessage("{\"type\":\"tts\",\"state\":\"start\"}");session.sendBinaryMessage(new byte[]{7},0); });
        device.sendText(JsonUtil.toJson(Map.of("type","abort","session_id",device.sessionId(),"generation",generation,"turn_id","1")));
        device.sendText(JsonUtil.toJson(Map.of("type","listen","state","start","mode","manual","session_id",device.sessionId(),"generation",generation,"turn_id","2")));
        int count=device.transport().textMessages().size();late.run();
        assertThat(device.transport().textMessages()).hasSize(count);assertThat(device.transport().binaryFrames()).isEmpty();
    }
}
