package com.xiaozhi.ai.llm.factory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import java.util.List;

/** A cached provider is unbound; each authenticated connection receives its own instance. */
public interface ConversationChatModel extends ChatModel, AutoCloseable {
    ConversationChatModel bind(String ownerId);
    void prepare(List<Message> history);
    boolean isReady();
    @Override void close();
}
