package com.xiaozhi.ai.stt.providers;

import com.sun.net.httpserver.HttpServer;
import com.xiaozhi.ai.stt.SttResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PhoWhisperSttServiceTest {
    @Test void sendsVietnameseWavAndParsesFinal() throws Exception {
        var body = new AtomicReference<byte[]>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inference", exchange -> {
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = "{\"text\":\" Xin chào Ken. \"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var service = new PhoWhisperSttService("http://127.0.0.1:" + server.getAddress().getPort() + "/inference");
            var partial = new AtomicReference<String>();
            var result = service.stream(Flux.just(new byte[]{1, 2}, new byte[]{3, 4}), partial::set);
            assertNull(result.failureReason());
            assertEquals("Xin chào Ken.", result.text());
            assertNull(partial.get());
            var sent = new String(body.get(), StandardCharsets.ISO_8859_1);
            assertTrue(sent.contains("name=\"language\"\r\n\r\nvi"));
            assertTrue(sent.contains("name=\"translate\"\r\n\r\nfalse"));
            int riff = sent.indexOf("RIFF");
            assertTrue(riff > 0);
            var wav = java.nio.ByteBuffer.wrap(body.get(), riff, 48).slice().order(java.nio.ByteOrder.LITTLE_ENDIAN);
            assertEquals(16000, wav.getInt(24));
            assertEquals(4, wav.getInt(40));
            assertArrayEquals(new byte[]{1, 2, 3, 4}, java.util.Arrays.copyOfRange(body.get(), riff + 44, riff + 48));
        } finally { server.stop(0); }
    }
    @Test void rejectsInvalidAudioAndReleasesCapacity() {
        var service = new PhoWhisperSttService("http://127.0.0.1:1/inference");
        assertEquals(SttResult.FAILURE_LOCAL_ERROR, service.stream(Flux.just(new byte[1])).failureReason());
        assertEquals(SttResult.FAILURE_LOCAL_ERROR, service.stream(Flux.just(new byte[960002])).failureReason());
        assertEquals("", service.stream(Flux.empty()).text());
        assertEquals(SttResult.FAILURE_UPSTREAM_ERROR, service.stream(Flux.just(new byte[2])).failureReason());
    }
    @Test void rejectsConcurrentRequestAndRecoversAfterEndpoint() throws Exception {
        var service = new PhoWhisperSttService("http://127.0.0.1:1/inference");
        var sink = reactor.core.publisher.Sinks.many().unicast().<byte[]>onBackpressureBuffer();
        var subscribed = new java.util.concurrent.CountDownLatch(1);
        var result = new AtomicReference<SttResult>();
        var thread = Thread.startVirtualThread(() -> result.set(service.stream(
                sink.asFlux().doOnSubscribe(subscription -> subscribed.countDown()))));
        try {
            assertTrue(subscribed.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("busy", service.stream(Flux.empty()).failureReason());
        } finally {
            sink.tryEmitComplete();
            thread.join(3000);
        }
        assertFalse(thread.isAlive());
        assertNull(result.get().failureReason());
        assertNull(service.stream(Flux.empty()).failureReason());
    }

    @Test void malformedResponseIsFailureAndReadinessChecksWorker() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inference", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":\"inference failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/health", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var service = new PhoWhisperSttService("http://127.0.0.1:" + server.getAddress().getPort() + "/inference");
        try {
            assertTrue(service.isReady());
            assertEquals(SttResult.FAILURE_UPSTREAM_ERROR, service.stream(Flux.just(new byte[2])).failureReason());
        } finally { server.stop(0); }
        assertFalse(service.isReady());
    }

    @Test void liveWorkerWhenRequested() throws Exception {
        String path = System.getProperty("phowhisper.test.wav");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null);
        byte[] file = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
        assertEquals("data", new String(file, 36, 4, StandardCharsets.US_ASCII));
        var result = new PhoWhisperSttService("http://127.0.0.1:8788/inference").stream(
                Flux.just(java.util.Arrays.copyOfRange(file, 44, file.length)));
        assertNull(result.failureReason());
        assertFalse(result.text().isBlank());
        System.out.println("PhoWhisper Java live transcript: " + result.text());
    }
}
