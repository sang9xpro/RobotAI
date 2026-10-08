package com.xiaozhi.ai.tts.providers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class EdgeTtsServiceTest {
    @TempDir Path directory;
    @Test void stalledSdkIsCancelledAndRetriedBeforeAnyAudioIsReturned() throws Exception {
        Path audio = directory.resolve("speech.mp3"); Files.write(audio, new byte[200]);
        var calls = new AtomicInteger(); var cancelled = new CountDownLatch(1);
        var service = new EdgeTtsService("vi-VN-HoaiMyNeural",1d,1d,directory.toString(),100) {
            @Override protected Path synthesizeOnce(String text) throws Exception {
                if (calls.incrementAndGet()==1) {
                    try { Thread.sleep(60000); } catch (InterruptedException e) { cancelled.countDown(); throw e; }
                }
                return audio;
            }
        };
        assertEquals(audio, service.textToSpeech("hello"));
        assertTrue(cancelled.await(1, TimeUnit.SECONDS)); assertEquals(2,calls.get());
    }
    @Test void missingFileNeverCountsAsSuccessfulSynthesis() {
        var calls = new AtomicInteger();
        var service = new EdgeTtsService("vi-VN-HoaiMyNeural",1d,1d,directory.toString(),100) {
            @Override protected Path synthesizeOnce(String text) { calls.incrementAndGet(); return directory.resolve("missing.mp3"); }
        };
        assertThrows(java.io.IOException.class, () -> service.textToSpeech("hello")); assertEquals(2,calls.get());
    }
}
