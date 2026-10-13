package com.xiaozhi.robot;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RobotSystemStatusServiceTest {
    @Test void reportsRealDatabaseProbeAndUnknownCapabilitiesAndCaches() throws Exception {
        DataSource db=mock(DataSource.class);Connection connection=mock(Connection.class);
        when(db.getConnection()).thenReturn(connection);when(connection.isValid(1)).thenReturn(true);
        var service=new RobotSystemStatusService(db,"","");
        try {
            var checks=service.checks();assertThat(checks).hasSize(6);
            assertThat(checks.stream().filter(c->c.id().equals("database")).findFirst().orElseThrow().state()).isEqualTo("healthy");
            assertThat(checks.stream().filter(c->c.id().equals("motor")).findFirst().orElseThrow().latencyMs()).isNull();
            assertThat(service.checks()).isSameAs(checks);verify(db,times(1)).getConnection();
            assertThat(checks).allMatch(c->c.checkedTime()!=null);
        } finally { service.close(); }
    }
    @Test void doesNotFollowHealthRedirectsOrExposeAddress() throws Exception {
        DataSource db=mock(DataSource.class);when(db.getConnection()).thenThrow(new java.sql.SQLException("secret database url"));
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var followed=new java.util.concurrent.atomic.AtomicBoolean();
        http.createContext("/health",exchange->{exchange.getResponseHeaders().add("Location","/other");exchange.sendResponseHeaders(302,-1);exchange.close();});
        http.createContext("/other",exchange->{followed.set(true);exchange.sendResponseHeaders(200,-1);exchange.close();});http.start();
        String address="http://127.0.0.1:"+http.getAddress().getPort()+"/health";
        var service=new RobotSystemStatusService(db,address,"file:///etc/passwd");
        try { var checks=service.checks();
            assertThat(checks.get(1).state()).isEqualTo("unavailable");assertThat(checks.get(2).state()).isEqualTo("degraded");
            assertThat(checks.get(3).state()).isEqualTo("unknown");assertThat(followed.get()).isFalse();
            assertThat(checks).noneMatch(c->c.detail().contains("secret") || c.detail().contains(address));
        } finally { service.close();http.stop(0); }
    }
    @Test void boundsTheWholeHealthResponseEvenIfHeadersArriveButBodyStalls() throws Exception {
        DataSource db=mock(DataSource.class);when(db.getConnection()).thenThrow(new java.sql.SQLException("offline"));
        var release=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.setExecutor(executor);
        http.createContext("/health",exchange->{
            exchange.sendResponseHeaders(200,100); exchange.getResponseBody().flush();
            try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });http.start();
        var service=new RobotSystemStatusService(db,"http://127.0.0.1:"+http.getAddress().getPort()+"/health","");
        try {
            var worker=service.checks().get(2);
            assertThat(worker.state()).isEqualTo("unavailable");
            assertThat(worker.latencyMs()).isBetween(1500L,3500L);
        } finally { release.countDown();service.close();http.stop(0);executor.close(); }
    }
}
