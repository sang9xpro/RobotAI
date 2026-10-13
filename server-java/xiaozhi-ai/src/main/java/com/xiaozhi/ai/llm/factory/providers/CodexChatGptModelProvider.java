package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.ConfigModelResp;
import com.xiaozhi.ai.utils.OpenAiSpeechEndpoint;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Private text/vision gateway using the user's existing Codex ChatGPT login. */
@Component
public class CodexChatGptModelProvider extends OpenAiModelProvider {
    private static final HttpClient CATALOG_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    /** Read the logged-in account's catalog without generating a model response. */
    public List<ConfigModelResp> listModels(ConfigBO config) {
        validateEndpoint(config.getApiUrl());
        if (config.getApiKey() == null || config.getApiKey().length() < 24) {
            throw new IllegalArgumentException("A private gateway token is required");
        }
        var endpoint = OpenAiSpeechEndpoint.resolve(config.getApiUrl(), "/models");
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + config.getApiKey()).GET().build();
        try {
            var response = CATALOG_HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Model catalog unavailable (HTTP " + response.statusCode() + ")");
            }
            var data = JsonUtil.OBJECT_MAPPER.readTree(response.body()).get("data");
            if (data == null || !data.isArray()) throw new IllegalStateException("Invalid model catalog");
            var models = new ArrayList<ConfigModelResp>();
            for (var model : data) {
                String id = model.path("id").asText();
                if (id.isBlank()) continue;
                var inputs = new ArrayList<String>();
                for (var input : model.path("input_modalities")) inputs.add(input.asText());
                if (inputs.isEmpty()) inputs.add("text");
                var efforts = new ArrayList<String>();
                for (var effort : model.path("reasoning_efforts")) efforts.add(effort.asText());
                models.add(new ConfigModelResp(id, model.path("display_name").asText(id),
                        model.path("is_default").asBoolean(false), List.copyOf(inputs), List.copyOf(efforts),
                        model.path("default_reasoning_effort").asText(null)));
            }
            return List.copyOf(models);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model catalog request interrupted");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Model gateway unavailable");
        }
    }

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
        return new CodexConversationChatModel(delegate, config);
    }

    @Override
    protected void applyThinkingOptions(OpenAiChatOptions.Builder builder, ConfigBO config, String model) {
        builder.reasoningEffort(reasoningEffort(config));
    }

    static String reasoningEffort(ConfigBO config) {
        String effort = config.getReasoningEffort();
        if (effort == null || effort.isBlank()) {
            return Boolean.TRUE.equals(config.getEnableThinking()) ? "medium" : "low";
        }
        if (!Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra").contains(effort)) {
            throw new IllegalArgumentException("Mức suy luận không hợp lệ");
        }
        return effort;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ConfigBO config) {
        throw new UnsupportedOperationException("Codex ChatGPT has no embedding API; select another embedding provider");
    }
}
