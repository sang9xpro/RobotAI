package com.xiaozhi.ai.stt.providers;

import com.sun.net.httpserver.HttpServer;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.tts.providers.OpenAiTtsService;
import com.xiaozhi.ai.utils.OpenAiSpeechEndpoint;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiSpeechProvidersTest {
    @TempDir Path output;

    @Test void transcriptionPostsConfiguredModelKeyAndVietnameseWav() throws Exception {
        var body = new AtomicReference<byte[]>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/transcriptions", e -> {
            body.set(e.getRequestBody().readAllBytes()); auth.set(e.getRequestHeaders().getFirst("Authorization"));
            byte[] reply = "{\"text\":\" Xin chào RobotAI \"}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, reply.length); e.getResponseBody().write(reply); e.close();
        });
        server.start();
        try {
            var service = new OpenAiSttService(config(server, "custom-transcribe"));
            var result = service.stream(Flux.just(new byte[]{1,2},new byte[]{3,4}));
            assertFalse(result.operationFailed()); assertEquals("Xin chào RobotAI", result.text());
            assertEquals("Bearer private-test-key", auth.get());
            String sent = new String(body.get(), StandardCharsets.ISO_8859_1);
            assertTrue(sent.contains("name=\"model\"\r\n\r\ncustom-transcribe"));
            assertTrue(sent.contains("name=\"language\"\r\n\r\nvi"));
            int riff = sent.indexOf("RIFF"); assertTrue(riff > 0);
            var wav = ByteBuffer.wrap(body.get(), riff, 48).slice().order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(16000, wav.getInt(24)); assertEquals(4,wav.getInt(40));
        } finally { server.stop(0); }
    }

    @Test void speechUsesConfiguredVoiceModelSpeedAndProducesMp3() throws Exception {
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/speech", e -> {
            body.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = {'I','D','3',0,0,0}; e.sendResponseHeaders(200, reply.length);
            e.getResponseBody().write(reply); e.close();
        });
        server.start();
        try {
            var service = new OpenAiTtsService(config(server,"custom-speech"),"custom-voice",1.0,1.2,output.toString());
            Path file = service.textToSpeech("Xin chào");
            assertTrue(Files.isRegularFile(file)); assertTrue(file.toString().endsWith(".mp3"));
            var json = JsonUtil.OBJECT_MAPPER.readTree(body.get());
            assertEquals("custom-speech",json.path("model").asText());
            assertEquals("custom-voice",json.path("voice").asText());
            assertEquals("Xin chào",json.path("input").asText());
            assertEquals(1.2,json.path("speed").asDouble());
            assertEquals("mp3",json.path("response_format").asText());
        } finally { server.stop(0); }
    }

    @Test void failuresNeverWriteProviderJsonAsAudioAndRejectInvalidPcm() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/audio/speech", e -> {
            e.getRequestBody().readAllBytes(); byte[] reply="{\"error\":\"private-key\"}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,reply.length); e.getResponseBody().write(reply); e.close();
        });
        server.createContext("/v1/audio/transcriptions", e -> {
            e.getRequestBody().readAllBytes(); e.sendResponseHeaders(401,-1); e.close();
        });
        server.start();
        try {
            var stt = new OpenAiSttService(config(server,"test"));
            assertEquals(SttResult.FAILURE_LOCAL_ERROR,stt.stream(Flux.just(new byte[1])).failureReason());
            assertEquals(SttResult.FAILURE_LOCAL_ERROR,stt.stream(Flux.just(new byte[960002])).failureReason());
            assertEquals("unauthorized",stt.stream(Flux.just(new byte[2])).failureReason());
            var tts = new OpenAiTtsService(config(server,"test"),"alloy",1.0,1.0,output.toString());
            var error=assertThrows(java.io.IOException.class,()->tts.textToSpeech("Xin chào"));
            assertFalse(error.getMessage().contains("private-key"));
            try(var files=Files.list(output)) { assertEquals(0,files.count()); }
        } finally { server.stop(0); }
    }

    @Test void endpointRejectsUnsupportedSchemesCredentialsAndQuery() {
        for(String url:new String[]{"file:///secret","https://user:secret@example.com/v1","https://example.com/v1?key=secret"})
            assertThrows(IllegalArgumentException.class,()->OpenAiSpeechEndpoint.resolve(url,"/audio/speech"));
        assertEquals("http://localhost:1234/v1/audio/speech",OpenAiSpeechEndpoint.resolve("http://localhost:1234/v1/","/audio/speech").toString());
    }

    @Test void timeoutIsDistinguishedFromUpstreamFailure() throws Exception {
        try (var transport = org.mockito.Mockito.mockStatic(OpenAiSpeechEndpoint.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            transport.when(() -> OpenAiSpeechEndpoint.post(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenThrow(new java.net.SocketTimeoutException("read timed out"));
            var service = new OpenAiSttService(new ConfigBO().setApiUrl("http://localhost:1234/v1").setConfigName("test"));
            assertEquals(SttResult.FAILURE_TIMEOUT,service.stream(Flux.just(new byte[2])).failureReason());
        }
    }

    private static ConfigBO config(HttpServer server,String model) {
        return new ConfigBO().setProvider("openai").setConfigName(model).setApiKey("private-test-key")
                .setApiUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
    }
}
