package com.xiaozhi.robot;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
@Service
public class RobotSystemStatusService {
    public record Check(String id,String state,String checkedTime,Long latencyMs,String detail) {}
    private final DataSource database;
    private final String sttHealth,gatewayHealth;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ThreadPoolExecutor probe=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),
        task->{Thread t=new Thread(task,"robotai-db-health");t.setDaemon(true);return t;});
    private List<Check> cached;
    private long measuredNanos;
    public RobotSystemStatusService(DataSource database,
        @Value("${robotai.admin.stt-health-url:}") String sttHealth,
        @Value("${robotai.admin.gateway-health-url:}") String gatewayHealth) {
        this.database=database;this.sttHealth=sttHealth;this.gatewayHealth=gatewayHealth;
    }
    @PreDestroy void close() { probe.shutdownNow(); }
    public synchronized List<Check> checks() {
        if(cached!=null && DateUtils.elapsedMillis(measuredNanos)<10_000) return cached;
        cached=List.of(new Check("backend","healthy",DateUtils.instant().toString(),null,"HTTP process responding"),
            databaseCheck(),httpCheck("stt-worker",sttHealth),httpCheck("llm-gateway",gatewayHealth),
            new Check("android","unknown",DateUtils.instant().toString(),null,"No handset telemetry channel"),
            new Check("motor","unknown",DateUtils.instant().toString(),null,"Hardware telemetry is simulated"));
        measuredNanos=System.nanoTime();return cached;
    }
    private Check databaseCheck() {
        long start=System.nanoTime();Future<Boolean> future=null;
        try {
            future=probe.submit(()->{try(var connection=database.getConnection()){return connection.isValid(1);}});
            boolean healthy=future.get(2,TimeUnit.SECONDS);
            return result("database",healthy?"healthy":"unavailable",start,healthy?"Connection validated":"Connection invalid");
        } catch(InterruptedException e) { Thread.currentThread().interrupt();return result("database","unknown",start,"Check interrupted"); }
        catch(Exception e) { return result("database","unavailable",start,"Database check failed or timed out"); }
        finally { if(future!=null && !future.isDone()) future.cancel(true); }
    }
    private Check httpCheck(String id,String address) {
        if(address==null || address.isBlank()) return new Check(id,"unknown",DateUtils.instant().toString(),null,"Health URL not configured");
        long start=System.nanoTime();
        CompletableFuture<HttpResponse<Void>> pending=null;
        try {
            URI uri=URI.create(address);
            if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getFragment()!=null || uri.getQuery()!=null) return result(id,"unknown",start,"Invalid deployment health URL");
            pending=client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());
            var response=pending.get(2,TimeUnit.SECONDS);
            int status=response.statusCode();
            return result(id,status>=200&&status<300?"healthy":status>=300&&status<500?"degraded":"unavailable",start,"Health HTTP "+status);
        } catch(InterruptedException e) { Thread.currentThread().interrupt();return result(id,"unknown",start,"Check interrupted"); }
        catch(Exception e) { return result(id,"unavailable",start,"Health endpoint unreachable or timed out"); }
        finally { if(pending!=null && !pending.isDone()) pending.cancel(true); }
    }
    private Check result(String id,String state,long start,String detail) {
        return new Check(id,state,DateUtils.instant().toString(),DateUtils.elapsedMillis(start),detail);
    }
}
