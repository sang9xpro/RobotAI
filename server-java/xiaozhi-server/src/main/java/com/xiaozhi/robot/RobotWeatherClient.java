package com.xiaozhi.robot;
import com.xiaozhi.common.model.resp.RobotWeatherResp;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.Semaphore;
@Component
public class RobotWeatherClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Semaphore requests = new Semaphore(4);
    public RobotWeatherResp current(double latitude, double longitude) throws IOException, InterruptedException {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180)
            throw new IllegalArgumentException("Invalid coordinates");
        if (!requests.tryAcquire()) throw new IllegalStateException("Weather requests busy");
        try {
            var uri=URI.create("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&timezone=auto");
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(), limitedBody());
                if (response.statusCode()!=200) throw new IOException("Weather provider unavailable");
                byte[] data=response.body();
                var root=JsonUtil.OBJECT_MAPPER.readTree(data);var c=root.path("current");
                if (!c.path("temperature_2m").isNumber() || !c.path("weather_code").isInt() || !c.path("time").isTextual())
                    throw new IOException("Weather data incomplete");
                return new RobotWeatherResp(c.get("temperature_2m").asDouble(),c.get("weather_code").asInt(),c.get("time").asText(),root.path("timezone").asText(),"Open-Meteo");
        } finally { requests.release(); }
    }
    private static HttpResponse.BodyHandler<byte[]> limitedBody() {
        return info -> new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
            private java.util.concurrent.Flow.Subscription subscription;
            private long received;
            public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            public void onSubscribe(java.util.concurrent.Flow.Subscription value) { subscription=value;delegate.onSubscribe(value); }
            public void onNext(java.util.List<java.nio.ByteBuffer> chunks) {
                received+=chunks.stream().mapToLong(java.nio.ByteBuffer::remaining).sum();
                if (received>65536) { subscription.cancel();delegate.onError(new IOException("Weather payload too large")); }
                else delegate.onNext(chunks);
            }
            public void onError(Throwable error) { delegate.onError(error); }
            public void onComplete() { delegate.onComplete(); }
        };
    }
}
