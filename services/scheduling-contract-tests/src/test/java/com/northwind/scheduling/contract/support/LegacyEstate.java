package com.northwind.scheduling.contract.support;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code -Dcontract.target=legacy} (default): one SQL Server 2022 container with the legacy schema, procs and triggers (db/apply.sh --no-seed), plus the
 * real legacy booking-api and results-service jars (Java 8) pointed at it. Shared by every test class in the JVM.
 * Tests create their own fixtures, so nothing depends on wall-clock-sensitive seed rows.
 * {@code -Dcontract.target=scheduling}: the same container also gets the Scheduling database
 * (services/scheduling-service/db/bootstrap.sql), the real scheduling-service jar (Java 21) runs against it, and
 * booking-api/results-service are started with {@code --scheduling.base-url} pointing at it. {@link #sched} is then
 * the Scheduling database (ExamSession/Booking/Waitlist/Attempt); in legacy mode it is the same as {@link #db}.
 */
public final class LegacyEstate {

    public static final String SA_PASSWORD = "Str0ng!Passw0rd";
    public static final String AD_APP_PASSWORD = "Northw1nd!Dev2011";

    private static LegacyEstate instance;

    public final Path repoRoot;
    public static final boolean SCHEDULING = "scheduling".equalsIgnoreCase(System.getProperty("contract.target", "legacy"));

    public final Db db;
    /** Where ExamSession/Booking/Waitlist live: Scheduling DB for the scheduling target, AssessmentDelivery otherwise. */
    public final Db sched;
    /** scheduling-service root URL (scheduling target only, else null). */
    public final Http scheduling;
    public final Http bookingApi;
    public final Http resultsService;
    private final MSSQLServerContainer<?> container;
    private final List<Process> processes = new ArrayList<>();

    public static synchronized LegacyEstate get() {
        if (instance == null) {
            instance = new LegacyEstate();
            Runtime.getRuntime().addShutdownHook(new Thread(instance::stop));
        }
        return instance;
    }

    public static Path repoRoot() {
        Path root = Path.of(System.getProperty("legacy.repo.root", "../..")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("db/apply.sh"))) {
            throw new IllegalStateException("legacy.repo.root does not contain db/apply.sh: " + root);
        }
        return root;
    }

    private LegacyEstate() {
        repoRoot = repoRoot();
        Path bookingJar = requireJar("booking-api");
        Path resultsJar = requireJar("results-service");

        container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withPassword(SA_PASSWORD)
                .withCopyFileToContainer(MountableFile.forHostPath(repoRoot.resolve("db").toString()), "/ad-db/")
                .withCopyFileToContainer(MountableFile.forHostPath(
                        repoRoot.resolve("services/scheduling-service/db").toString()), "/sched-db/");
        container.start();
        try {
            ExecResult r = container.execInContainer("bash", "-c",
                    "AD_SQL_SERVER=localhost AD_SA_PASSWORD='" + SA_PASSWORD + "' bash /ad-db/apply.sh --no-seed");
            if (r.getExitCode() != 0) {
                throw new IllegalStateException("db/apply.sh failed: " + r.getStdout() + r.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            container.stop();
            throw new IllegalStateException(e);
        }
        String jdbcUrl = "jdbc:sqlserver://" + container.getHost() + ":"
                + container.getMappedPort(MSSQLServerContainer.MS_SQL_SERVER_PORT)
                + ";databaseName=AssessmentDelivery;encrypt=false;trustServerCertificate=true";
        db = new Db(jdbcUrl, "ad_app", AD_APP_PASSWORD);

        List<String> extra = new ArrayList<>();
        if (SCHEDULING) {
            String schedUrl = jdbcUrl.replace("databaseName=AssessmentDelivery", "databaseName=Scheduling");
            sqlAsSa("/sched-db/bootstrap.sql");
            sched = new Db(schedUrl, "ad_app", AD_APP_PASSWORD);
            String base = startScheduling(jdbcUrl, schedUrl);
            scheduling = new Http(base);
            extra.add("--scheduling.base-url=" + base + "/api/v1");
        } else {
            sched = db;
            scheduling = null;
        }
        bookingApi = new Http(start("booking-api", bookingJar, jdbcUrl, "/api/v1/sessions/0", extra));
        resultsService = new Http(start("results-service", resultsJar, jdbcUrl, "/api/v1/results/candidate/NW00000000", extra));
    }

    private void sqlAsSa(String file) {
        try {
            ExecResult r = container.execInContainer("bash", "-c", "SQLCMD=$(command -v sqlcmd || ls /opt/mssql-tools18/bin/sqlcmd "
                    + "/opt/mssql-tools/bin/sqlcmd 2>/dev/null | head -1); \"$SQLCMD\" -C -b -S localhost -U sa -P '" + SA_PASSWORD
                    + "' -i " + file);
            if (r.getExitCode() != 0) {
                throw new IllegalStateException(file + " failed: " + r.getStdout() + r.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Real scheduling-service: no initial import (fixtures own every row), no relay polling (Fixtures triggers
     * POST /reference/sync), no nightly reconciler, single-writer seat accounting (HB-01 removed, contract section 6).
     */
    private String startScheduling(String adUrl, String schedUrl) {
        Path jar = Path.of(System.getProperty("scheduling.jar",
                repoRoot.resolve("services/scheduling-service/target/scheduling-service.jar").toString()));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " missing: build services/scheduling-service first "
                    + "(services/scheduling-contract-tests/run-contract-tests.sh does this)");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return launch("scheduling-service", List.of(java, "-Duser.timezone=UTC", "-jar", jar.toString(),
                "--spring.datasource.url=" + schedUrl,
                "--spring.datasource.username=ad_app",
                "--spring.datasource.password=" + AD_APP_PASSWORD,
                "--scheduling.source.url=" + adUrl,
                "--scheduling.source.username=ad_app",
                "--scheduling.source.password=" + AD_APP_PASSWORD,
                "--scheduling.import.on-startup=never",
                "--scheduling.reference.poll-ms=0",
                "--scheduling.reconciler.cron=-",
                "--scheduling.legacy-seat-accounting=false"), "/actuator/health", 240);
    }

    private Path requireJar(String module) {
        Path jar = repoRoot.resolve("services/" + module + "/target/" + module + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " missing: build the legacy services first "
                    + "(services/scheduling-contract-tests/run-contract-tests.sh does this)");
        }
        return jar;
    }

    private String start(String name, Path jar, String jdbcUrl, String probePath, List<String> extraArgs) {
        String javaHome = System.getProperty("legacy.java.home", "/usr/lib/jvm/java-8-openjdk-amd64");
        List<String> cmd = new ArrayList<>(List.of(javaHome + "/bin/java", "-Duser.timezone=UTC", "-Xmx384m",
                "-jar", jar.toString(),
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=ad_app",
                "--spring.datasource.password=" + AD_APP_PASSWORD));
        cmd.addAll(extraArgs);
        return launch(name, cmd, probePath, 180);
    }

    private String launch(String name, List<String> command, String probePath, int timeoutSeconds) {
        int port = freePort();
        File log = repoRoot.resolve("services/scheduling-contract-tests/target/legacy-logs/" + name + ".log").toFile();
        log.getParentFile().mkdirs();
        List<String> cmd = new ArrayList<>(command);
        cmd.add("--server.port=" + port);
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(log);
        try {
            Process p = pb.start();
            processes.add(p);
            String base = "http://localhost:" + port;
            HttpClient client = HttpClient.newHttpClient();
            long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
            while (System.nanoTime() < deadline) {
                if (!p.isAlive()) {
                    throw new IllegalStateException(name + " exited early, see " + log);
                }
                try {
                    client.send(HttpRequest.newBuilder(URI.create(base + probePath)).build(),
                            HttpResponse.BodyHandlers.discarding());
                    return base;
                } catch (IOException notYet) {
                    Thread.sleep(500);
                }
            }
            throw new IllegalStateException(name + " did not start within " + timeoutSeconds + "s, see " + log);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stop() {
        processes.forEach(Process::destroy);
        container.stop();
    }
}
