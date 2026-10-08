package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Set;

/** Private text/vision gateway using the user's existing Codex ChatGPT login. */
@Component
public class CodexChatGptModelProvider extends OpenAiModelProvider {
    @Override
    public String getProviderName() {
        return "codex-chatgpt";
    }

    static void validateEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null
                || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Codex ChatGPT gateway must use a private loopback HTTP endpoint");
        }
    }

    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        validateEndpoint(config.getApiUrl());
        if (config.getApiKey() == null || config.getApiKey().length() < 24) {
            throw new IllegalArgumentException("A private gateway token is required (not an OpenAI API key)");
        }
        ChatModel delegate = super.createChatModel(config, role);
        // Persona attaches robot tools on every request. This provider has no function calling:
        // preserve messages but remove runtime tool callbacks and tool context.
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return delegate.call(new Prompt(prompt.getInstructions()));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return delegate.stream(new Prompt(prompt.getInstructions()));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return delegate.getDefaultOptions();
            }
        };
    }

    @Override
    protected void applyThinkingOptions(OpenAiChatOptions.Builder builder, boolean thinking, String model) {
        builder.reasoningEffort(thinking ? "medium" : "low");
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ConfigBO config) {
        throw new UnsupportedOperationException("Codex ChatGPT has no embedding API; select another embedding provider");
    }
}
