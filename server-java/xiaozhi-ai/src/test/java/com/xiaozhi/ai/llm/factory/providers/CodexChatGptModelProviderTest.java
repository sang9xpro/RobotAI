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
    void explicitEffortOverridesLegacySwitchAndInvalidValuesAreRejected() {
        assertEquals("low", CodexChatGptModelProvider.reasoningEffort(new ConfigBO()));
        assertEquals("medium", CodexChatGptModelProvider.reasoningEffort(new ConfigBO().setEnableThinking(true)));
        assertEquals("none", CodexChatGptModelProvider.reasoningEffort(new ConfigBO()
                .setEnableThinking(true).setReasoningEffort("none")));
        assertThrows(IllegalArgumentException.class, () -> CodexChatGptModelProvider.reasoningEffort(
                new ConfigBO().setReasoningEffort("instant")));
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
            var model = provider().createChatModel(config("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", token)
                    .setReasoningEffort("high"), new RoleBO());
            var responses = model.stream(new Prompt("Xin chào")).collectList().block(Duration.ofSeconds(10));
            String text = responses.stream().filter(r -> r.getResult() != null)
                    .map(r -> r.getResult().getOutput().getText()).filter(t -> t != null).reduce("", String::concat);
            assertEquals("Chào Ken!", text);
            assertEquals("Bearer " + token, authorization.get());
            var json = new ObjectMapper().readTree(request.get());
            assertTrue(json.get("stream").asBoolean());
            assertEquals("high", json.get("reasoning_effort").asText());
            assertFalse(json.hasNonNull("tools"));
        } finally {
            server.stop(0);
        }
    }
    @Test
    void accountCatalogUsesPrivateAuthenticationAndReturnsOnlyModelMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var authorization = new AtomicReference<String>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data = """
                    {"data":[{"id":"available-model","display_name":"Available model","is_default":true,
                    "input_modalities":["text","image"], "reasoning_efforts":["low","high"],
                    "default_reasoning_effort":"low"},{"id":"legacy-model"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            String token = "test-private-token-000000000000";
            var models = provider().listModels(config("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", token));
            assertEquals("Bearer " + token, authorization.get());
            assertEquals(2, models.size());
            assertEquals("available-model", models.getFirst().id());
            assertTrue(models.getFirst().defaultModel());
            assertEquals(java.util.List.of("text", "image"), models.getFirst().inputModalities());
            assertEquals(java.util.List.of("low", "high"), models.getFirst().reasoningEfforts());
            assertEquals("low", models.getFirst().defaultReasoningEffort());
            assertTrue(models.getLast().reasoningEfforts().isEmpty());
            assertEquals("legacy-model", models.getLast().label());
            assertFalse(new ObjectMapper().writeValueAsString(models).contains(token));
            assertThrows(IllegalArgumentException.class,
                    () -> provider().listModels(config("https://example.com/v1", token)));
        } finally { server.stop(0); }
    }

    @Test
    void catalogNeverFollowsRedirectsOrExposesUpstreamBodies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.com/models");
            byte[] data = "private-upstream-secret".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(302, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            var error = assertThrows(IllegalStateException.class, () -> provider().listModels(config(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-private-token-000000000000")));
            assertEquals("Model catalog unavailable (HTTP 302)", error.getMessage());
        } finally { server.stop(0); }
    }
    @Test
    void boundUsersPrepareIndependentlyAndSendTheirSessionHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var prepared = new java.util.concurrent.ConcurrentHashMap<String, String>();
        var used = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var closed = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var efforts = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/v1/sessions", exchange -> {
            if (exchange.getRequestMethod().equals("DELETE")) {
                closed.add(exchange.getRequestURI().getPath().substring("/v1/sessions/".length()));
                exchange.sendResponseHeaders(204, -1);
            } else {
                var json = new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes());
                efforts.add(json.get("reasoning_effort").asText());
                prepared.put(json.get("session_id").asText(), json.get("owner_id").asText());
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.createContext("/v1/chat/completions", exchange -> {
            used.add(exchange.getRequestHeaders().getFirst("X-Ken-Session-Id"));
            efforts.add(new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes()).get("reasoning_effort").asText());
            String data = "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Chào\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n";
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var shared = (com.xiaozhi.ai.llm.factory.ConversationChatModel) provider().createChatModel(
                    config("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-private-token-000000000000")
                            .setReasoningEffort("xhigh"), new RoleBO());
            try (var a = shared.bind("user:A"); var b = shared.bind("user:B")) {
                a.prepare(java.util.List.of(new org.springframework.ai.chat.messages.SystemMessage("Ken")));
                b.prepare(java.util.List.of(new org.springframework.ai.chat.messages.SystemMessage("Ken")));
                a.prepare(java.util.List.of()); // Idempotent, no additional warmup.
                assertTrue(a.isReady()); assertTrue(b.isReady());
                a.stream(new Prompt("hi A")).collectList().block(Duration.ofSeconds(5));
                b.stream(new Prompt("hi B")).collectList().block(Duration.ofSeconds(5));
                assertEquals(2, prepared.size());
                assertEquals(java.util.Set.of("user:A", "user:B"), java.util.Set.copyOf(prepared.values()));
                assertEquals(2, java.util.Set.copyOf(used).size());
                assertTrue(prepared.keySet().containsAll(used));
                assertEquals(java.util.List.of("xhigh", "xhigh", "xhigh", "xhigh"), efforts);
            }
            assertEquals(prepared.keySet(), java.util.Set.copyOf(closed));
        } finally { server.stop(0); }
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
