package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.ai.llm.factory.ConversationChatModel;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class PersonaCleanupSessionTest {
    @Test void closesModelOnlyAfterSummaryFinishesAndCleanupIsIdempotent() throws Exception {
        var cleanup = new PersonaCleanup();
        var session = mock(ChatSession.class);
        var conversation = mock(Conversation.class);
        var model = mock(ConversationChatModel.class);
        var persona = Persona.builder().conversation(conversation).chatModel(model).sessionId("one").build();
        when(session.getPersona()).thenReturn(persona);
        var entered = new CountDownLatch(1);
        var persisted = new CountDownLatch(1);
        when(conversation.finish(any(Duration.class))).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        cleanup.cleanup(session);
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        verify(model, never()).close();
        persona.chat("late message after disconnect");
        verify(conversation, never()).add(any());
        verify(model, never()).stream(any(org.springframework.ai.chat.prompt.Prompt.class));
        cleanup.cleanup(session);
        persisted.countDown();
        cleanup.awaitPending();
        var order = inOrder(conversation, model);
        order.verify(conversation).finish(any(Duration.class));
        order.verify(model).close();
        order.verify(conversation).clear();
        verify(model, times(1)).close();
    }
}
