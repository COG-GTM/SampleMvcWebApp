package com.northwind.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.northwind.scheduling.SqlServerTestBase;

/**
 * MBA-3832 AC: readiness flips DOWN while the Scheduling DB container is stopped and back UP after it restarts;
 * liveness stays UP throughout. Own container on a fixed host port so docker stop/start keeps the JDBC URL valid.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.hikari.connection-timeout=2000", "scheduling.import.on-startup=true"})
@Import(SqlServerTestBase.FixtureLegacy.class)
@DirtiesContext
class ReadinessIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final MSSQLServerContainer<?> DB;

    static {
        MSSQLServerContainer<?> c = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            int hostPort = freePort();
            c = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense()
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                            new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(1433))));
            c.start();
        }
        DB = c;
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB.getJdbcUrl() + ";databaseName=Scheduling");
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @AfterAll
    static void stopDb() {
        if (DB != null) {
            DB.stop();
        }
    }

    @Autowired
    TestRestTemplate http;

    private ResponseEntity<Map<String, Object>> probe(String group) {
        return http.exchange("/actuator/health/" + group, HttpMethod.GET, null, MAP);
    }

    private ResponseEntity<Map<String, Object>> awaitReadiness(HttpStatus expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        ResponseEntity<Map<String, Object>> r = probe("readiness");
        while (r.getStatusCode() != expected && System.nanoTime() < deadline) {
            Thread.sleep(1000);
            r = probe("readiness");
        }
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void readinessFollowsTheSchedulingDbWhileLivenessStaysUp() throws Exception {
        assertThat(probe("readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(probe("liveness").getStatusCode()).isEqualTo(HttpStatus.OK);

        DockerClientFactory.instance().client().stopContainerCmd(DB.getContainerId()).exec();
        ResponseEntity<Map<String, Object>> down = awaitReadiness(HttpStatus.SERVICE_UNAVAILABLE, Duration.ofSeconds(60));
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(down.getBody()).containsEntry("status", "DOWN");
        Map<String, Object> components = (Map<String, Object>) down.getBody().get("components");
        assertThat((Map<String, Object>) components.get("db")).containsEntry("status", "DOWN");
        assertThat((Map<String, Object>) components.get("readinessState")).containsEntry("status", "UP");
        ResponseEntity<Map<String, Object>> live = probe("liveness");
        assertThat(live.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(live.getBody()).containsEntry("status", "UP");

        DockerClientFactory.instance().client().startContainerCmd(DB.getContainerId()).exec();
        ResponseEntity<Map<String, Object>> up = awaitReadiness(HttpStatus.OK, Duration.ofSeconds(120));
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(up.getBody()).containsEntry("status", "UP");
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
