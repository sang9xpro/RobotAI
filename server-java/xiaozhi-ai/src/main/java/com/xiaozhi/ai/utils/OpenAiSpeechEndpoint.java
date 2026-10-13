package com.xiaozhi.ai.utils;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** HTTP transport for OpenAI-compatible speech APIs; never forwards credentials through redirects. */
public final class OpenAiSpeechEndpoint {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(90))
            .callTimeout(Duration.ofSeconds(90)).followRedirects(false).followSslRedirects(false).build();

    private OpenAiSpeechEndpoint() {}

    public static URI resolve(String baseUrl, String resource) {
        String base = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        URI uri = URI.create(base);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Speech API requires an HTTP(S) base URL without embedded credentials");
        }
        String allowed = System.getProperty("robotai.ai.allowed-hosts", System.getenv("ROBOTAI_AI_ALLOWED_HOSTS"));
        if (allowed != null && !allowed.isBlank() && java.util.Arrays.stream(allowed.split(","))
                .map(String::trim).noneMatch(host -> host.equalsIgnoreCase(uri.getHost())))
            throw new IllegalArgumentException("API host is not allowed by deployment policy");
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base.endsWith(resource) ? base : base + resource);
    }

    public static final class ProviderHttpException extends IOException {
        private final int status;
        public ProviderHttpException(int status) { super("Speech provider returned HTTP " + status); this.status = status; }
        public int status() { return status; }
    }
    public static byte[] post(URI endpoint, String apiKey, String contentType, byte[] body, int maxResponseBytes)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Speech request cancelled");
        var builder = new Request.Builder().url(endpoint.toString())
                .post(RequestBody.create(body, MediaType.parse(contentType)));
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.trim());
        try (var response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new ProviderHttpException(response.code());
            if (response.body() == null) throw new IOException("Speech provider returned an empty response");
            try (var input = response.body().byteStream()) {
                byte[] bytes = input.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) throw new IOException("Speech provider response exceeds limit");
                return bytes;
            }
        }
    }
}
