package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.runtime.Persona;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话终止时清理 Persona 相关资源（上游合成订阅、播放器），并把这段对话剩下的内容压进摘要。
 */
@Slf4j
@Component
public class PersonaCleanup {
    @org.springframework.beans.factory.annotation.Value("${conversation.summary-close-timeout-seconds:60}")
    private int summaryTimeoutSeconds = 60;
    private final java.util.Set<java.util.concurrent.CompletableFuture<Void>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @jakarta.annotation.PreDestroy
    public void awaitPending() {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(summaryTimeoutSeconds + 15).toNanos();
        for (var future : java.util.List.copyOf(pending)) {
            try { future.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS); }
            catch (Exception e) { log.warn("Shutdown summary/connection cleanup did not finish within deadline"); }
        }
    }

    /**
     * 由 {@code SessionManager.closeSession} 在会话被摘出注册表之前直接调用。
     * 不挂在 ChatSessionClosedEvent 上：WebSocket 硬断线时连接已关，事件根本发不出去；
     * 服务端主动关的路径上事件虽然发得出去，但会话已被摘除，靠 sessionId 回查只能拿到 null。
     */
    public void cleanup(ChatSession session) {
        if (session == null) {
            return;
        }
        Persona persona = session.getPersona();
        if (persona != null && !persona.beginClose()) return;
        // 先断上游再停播放器，与打断路径同序，否则清完队列后新帧还会继续入队
        if (persona != null) {
            if (persona.getPlayer() != null) {
                try { persona.markInterrupted(); }
                catch (RuntimeException e) { log.warn("Could not snapshot interrupted playback - SessionId: {}", session.getSessionId()); }
            }
            Synthesizer synthesizer = persona.getSynthesizer();
            if (synthesizer != null) {
                try {
                    synthesizer.cancel();
                } catch (Exception e) {
                    log.warn("取消语音合成失败 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
                }
            }
            if (persona.getPlayer() != null) {
                try { persona.onInterrupted(); }
                catch (RuntimeException e) { log.warn("Could not commit interrupted turn; continuing connection cleanup - SessionId: {}", session.getSessionId()); }
            }
        }
        Player player = session.getPlayer();
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                log.warn("停止播放器失败 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
            }
        }
        if (persona != null) {
            // 设备断开就是这段对话的结束：没到上限的剩余轮次在这里进摘要，否则短对话永远进不了摘要
            var done = new java.util.concurrent.CompletableFuture<Void>();
            pending.add(done);
            com.xiaozhi.common.AppVirtualThreads.factory("persona-close-").newThread(() -> {
                try {
                    boolean saved = persona.getConversation().finish(java.time.Duration.ofSeconds(summaryTimeoutSeconds));
                    if (!saved) log.warn("Final summary failed/timed out; persisted messages retained - SessionId: {}", session.getSessionId());
                } finally {
                    try {
                        if (persona.getChatModel() instanceof com.xiaozhi.ai.llm.factory.ConversationChatModel model) model.close();
                    } catch (RuntimeException e) {
                        log.warn("Model session close failed; gateway lease will release it - SessionId: {}", session.getSessionId());
                    } finally {
                        persona.getConversation().clear();
                        if (session.getPersona() == persona) session.setPersona(null);
                        done.complete(null);
                        pending.remove(done);
                    }
                }
            }).start();
        }
    }
}
