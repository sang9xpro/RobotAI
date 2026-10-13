package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ConversationChatModel;
import com.xiaozhi.ai.utils.OpenAiSpeechEndpoint;
import com.xiaozhi.common.AppVirtualThreads;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Gateway sessions are isolated from the globally cached Spring AI model. */
final class CodexConversationChatModel implements ConversationChatModel {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .executor(AppVirtualThreads.newPerTaskExecutor("codex-session-http-")).build();
    private static final reactor.core.scheduler.Scheduler IO = Schedulers.fromExecutorService(
            AppVirtualThreads.newPerTaskExecutor("codex-session-io-"));
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "codex-session-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final ChatModel delegate;
    private final String endpoint, token, model, effort, owner, id;
    private volatile boolean ready, closed;
    private ScheduledFuture<?> heartbeat;

    CodexConversationChatModel(ChatModel delegate, ConfigBO config) {
        this(delegate, OpenAiSpeechEndpoint.resolve(config.getApiUrl(), "").toString(), config.getApiKey(),
                config.getConfigName(), CodexChatGptModelProvider.reasoningEffort(config), null, null);
    }

    private CodexConversationChatModel(ChatModel delegate, String endpoint, String token, String model,
                                       String effort, String owner, String id) {
        this.delegate = delegate; this.endpoint = endpoint.replaceAll("/+$", ""); this.token = token;
        this.model = model; this.effort = effort; this.owner = owner; this.id = id;
    }

    @Override public ConversationChatModel bind(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Authenticated owner required");
        return new CodexConversationChatModel(delegate, endpoint, token, model, effort, ownerId, UUID.randomUUID().toString());
    }

    @Override public synchronized void prepare(List<Message> history) {
        if (id == null || ready) return;
        if (closed) throw new IllegalStateException("Conversation connection closed");
        var messages = history.stream().map(m -> Map.of("role", m.getMessageType().getValue(), "content", m.getText())).toList();
        int status = request("POST", "/sessions", JsonUtil.toJson(Map.of("session_id", id, "owner_id", owner,
                "model", model, "reasoning_effort", effort, "messages", messages)), Duration.ofSeconds(90));
        if (status != 200) throw new IllegalStateException("Model session preparation failed (HTTP " + status + ")");
        ready = true;
        if (heartbeat == null) {
            // The timer only dispatches; a slow user's heartbeat cannot block all other users.
            heartbeat = HEARTBEATS.scheduleWithFixedDelay(() -> IO.schedule(this::touch), 30, 30, TimeUnit.SECONDS);
        }
    }

    private void touch() {
        if (closed || !ready) return;
        try {
            if (request("POST", "/sessions/" + id + "/heartbeat", "{}", Duration.ofSeconds(10)) != 200) ready = false;
        } catch (RuntimeException ignored) {
            ready = false; // Next turn reopens from the backend's authoritative history.
        }
    }

    private int request(String method, String path, String body, Duration timeout) {
        var request = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(timeout)
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model session request interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Model gateway unavailable", e);
        }
    }

    private Prompt scoped(Prompt prompt) {
        // Keep runtime robot tools out of this provider; only the private connection header is added.
        if (id == null) return new Prompt(prompt.getInstructions());
        var seed = prompt.getInstructions();
        // The current question must remain unsent until the actual turn; warmup
        // seeds prior history only, including after a gateway restart/expired lease.
        if (!seed.isEmpty() && seed.getLast().getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
            seed = seed.subList(0, seed.size() - 1);
        }
        prepare(seed);
        return new Prompt(prompt.getInstructions(), OpenAiChatOptions.builder()
                .httpHeaders(Map.of("X-Ken-Session-Id", id)).build());
    }

    @Override public ChatResponse call(Prompt prompt) {
        try { return delegate.call(scoped(prompt)); }
        catch (RuntimeException e) { if (id != null) ready = false; throw e; }
    }
    @Override public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> delegate.stream(scoped(prompt))).doOnError(error -> { if (id != null) ready = false; }).subscribeOn(IO);
    }
    @Override public ChatOptions getDefaultOptions() { return delegate.getDefaultOptions(); }
    @Override public boolean isReady() { return id == null || (ready && !closed); }

    @Override public synchronized void close() {
        if (id == null || closed) return;
        closed = true;
        ready = false;
        if (heartbeat != null) heartbeat.cancel(false);
        request("DELETE", "/sessions/" + id, null, Duration.ofSeconds(10));
    }
}
