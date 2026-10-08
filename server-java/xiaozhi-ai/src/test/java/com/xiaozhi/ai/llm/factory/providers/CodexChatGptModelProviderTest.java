package com.xiaozhi.ai.llm.factory.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CodexChatGptModelProviderTest {
    private CodexChatGptModelProvider provider() {
        var provider = new CodexChatGptModelProvider();
        ReflectionTestUtils.setField(provider, "toolCallingManager", ToolCallingManager.builder().build());
        ReflectionTestUtils.setField(provider, "observationRegistry", ObservationRegistry.NOOP);
        return provider;
    }
    private ConfigBO config(String url, String token) {
        return new ConfigBO().setProvider("codex-chatgpt").setApiUrl(url).setApiKey(token)
                .setConfigName("codex-default").setEnableThinking(false);
    }
    @Test
    void privateEndpointAndUnsupportedEmbedding() {
        var provider = provider();
        assertTrue(provider.supports("codex-chatgpt"));
        assertFalse(provider.supports("openai"));
        for (String endpoint : new String[]{"https://example.com/v1", "http://127.0.0.1.evil/v1", "http://user@localhost/v1"}) {
            assertThrows(IllegalArgumentException.class, () -> provider.createChatModel(config(endpoint, "long-private-token-0000000000"), new RoleBO()));
        }
        assertThrows(UnsupportedOperationException.class, () -> provider.createEmbeddingModel(new ConfigBO()));
    }
    @Test
    void springAiStreamingContract() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String data = "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Chào Ken!\"},\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            String token = "test-private-token-000000000000";
            var model = provider().createChatModel(config("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", token), new RoleBO());
            var responses = model.stream(new Prompt("Xin chào")).collectList().block(Duration.ofSeconds(10));
            String text = responses.stream().filter(r -> r.getResult() != null)
                    .map(r -> r.getResult().getOutput().getText()).filter(t -> t != null).reduce("", String::concat);
            assertEquals("Chào Ken!", text);
            assertEquals("Bearer " + token, authorization.get());
            var json = new ObjectMapper().readTree(request.get());
            assertTrue(json.get("stream").asBoolean());
            assertEquals("low", json.get("reasoning_effort").asText());
            assertFalse(json.hasNonNull("tools"));
        } finally {
            server.stop(0);
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "KEN_LLM_LIVE", matches = "1")
    void liveGatewayThroughJavaAdapter() throws Exception {
        var local = Path.of("..", ".local", "llm-gateway.json");
        if (!Files.exists(local)) local = Path.of("..", "..", ".local", "llm-gateway.json");
        var json = new ObjectMapper().readTree(Files.readString(local));
        var model = provider().createChatModel(config("http://127.0.0.1:8787/v1", json.get("token").asText()), new RoleBO());
        var result = model.stream(new Prompt("Chào Ken. Trả lời một câu ngắn bằng tiếng Việt."))
                .collectList().block(Duration.ofSeconds(90));
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getResult() != null
                && r.getResult().getOutput().getText() != null && !r.getResult().getOutput().getText().isBlank()));
    }
}
