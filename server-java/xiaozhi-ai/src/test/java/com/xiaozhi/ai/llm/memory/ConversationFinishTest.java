package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationFinishTest {
    private Conversation conversation(Summarizer summarizer, int max) {
        return Conversation.builder().ownerId("user_chat_7").userId(7).roleId(1).sessionId("scope-1")
                .summarizer(summarizer).maxMessages(max).tokenBudget(100000).keepMessages(0).build();
    }

    @Test void waitsForSummaryPersistenceAndIncludesUnansweredUser() throws Exception {
        var entered = new CountDownLatch(1);
        var persisted = new CountDownLatch(1);
        var c = conversation((previous, messages) -> {
            assertThat(messages).extracting(m -> m.getText()).containsExactly("unfinished question");
            entered.countDown();
            try { assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return "saved summary";
        }, 100);
        c.add(new UserMessage("unfinished question"));
        var finished = java.util.concurrent.CompletableFuture.supplyAsync(() -> c.finish(Duration.ofSeconds(3)));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(finished.isDone()).isFalse();
        persisted.countDown();
        assertThat(finished.get(2, TimeUnit.SECONDS)).isTrue();
        assertThat(c.rawMessages()).isEmpty();
        assertThat(c.messages()).anyMatch(m -> m.getText().contains("saved summary"));
    }

    @Test void serializesFinalSummaryAfterExistingCompaction() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var c = conversation((previous, messages) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            } else assertThat(previous).isEqualTo("summary1");
            return "summary" + call;
        }, 2);
        c.add(new UserMessage("one")); c.add(new AssistantMessage("reply"));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        c.add(new UserMessage("last question"));
        var finished = java.util.concurrent.CompletableFuture.supplyAsync(() -> c.finish(Duration.ofSeconds(3)));
        release.countDown();
        assertThat(finished.get(2, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(c.rawMessages()).isEmpty();
    }

    @Test void failureAndTimeoutRetainRecoverableMessages() {
        var failed = conversation((previous, messages) -> { throw new IllegalStateException("database down"); }, 100);
        failed.add(new UserMessage("keep me"));
        assertThat(failed.finish(Duration.ofSeconds(1))).isFalse();
        assertThat(failed.rawMessages()).hasSize(1);
        var slow = conversation((previous, messages) -> {
            try { Thread.sleep(10000); } catch (InterruptedException e) { throw new RuntimeException(e); }
            return "late";
        }, 100);
        slow.add(new UserMessage("keep me too"));
        assertThat(slow.finish(Duration.ofMillis(50))).isFalse();
        assertThat(slow.rawMessages()).hasSize(1);
    }
}
