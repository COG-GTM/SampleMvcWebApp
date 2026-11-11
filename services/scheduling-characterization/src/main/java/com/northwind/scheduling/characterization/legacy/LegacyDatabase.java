package com.northwind.scheduling.characterization.legacy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The legacy AssessmentDelivery database. By default a Testcontainers SQL Server 2022 container provisioned by
 * running the repo's own {@code db/apply.sh} (schema -> procs -> triggers -> seed, exactly as
 * {@code scripts/run-legacy.sh} does) inside the container with its bundled sqlcmd.
 *
 * <p>Set {@code -Dlegacy.jdbcUrl=...} (plus {@code legacy.user}/{@code legacy.password}) to run against an
 * already provisioned database instead (e.g. the {@code mssql} container of {@code scripts/run-legacy.sh}).
 */
public final class LegacyDatabase implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyDatabase.class);
    /** Strong reference: silences mssql-jdbc's prelogin warnings while Testcontainers polls the starting server. */
    private static final java.util.logging.Logger JDBC_LOG = java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc");

    static {
        JDBC_LOG.setLevel(java.util.logging.Level.SEVERE);
    }
    public static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-latest";
    /** sa password db/apply.sh defaults to. */
    static final String SA_PASSWORD = "Str0ng!Passw0rd";
    /** Shared application login created by db/schema/000_create_database.sql (used by portal and services). */
    static final String APP_USER = "ad_app";
    static final String APP_PASSWORD = "Northw1nd!Dev2011";

    private final MSSQLServerContainer<?> container;
    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final Path repoRoot;

    private LegacyDatabase(MSSQLServerContainer<?> container, String jdbcUrl, String user, String password, Path repoRoot) {
        this.container = container;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.repoRoot = repoRoot;
    }

    public static LegacyDatabase start() {
        Path root = repoRoot();
        String external = System.getProperty("legacy.jdbcUrl");
        if (external != null && !external.isBlank()) {
            LOG.info("Using existing legacy database {}", external);
            return new LegacyDatabase(null, external, System.getProperty("legacy.user", APP_USER),
                    System.getProperty("legacy.password", APP_PASSWORD), root);
        }
        @SuppressWarnings("resource")
        MSSQLServerContainer<?> c = new MSSQLServerContainer<>(DockerImageName.parse(IMAGE))
                .acceptLicense()
                .withPassword(SA_PASSWORD)
                .withEnv("MSSQL_PID", "Developer")
                .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("db")), "/ad/db")
                .withStartupTimeout(Duration.ofMinutes(4));
        c.start();
        try {
            long t0 = System.nanoTime();
            ExecResult r = c.execInContainer("bash", "-c",
                    "AD_SQL_SERVER=localhost,1433 AD_SA_PASSWORD='" + SA_PASSWORD + "' "
                            + "SQLCMD=/opt/mssql-tools18/bin/sqlcmd bash /ad/db/apply.sh");
            if (r.getExitCode() != 0) {
                throw new IllegalStateException("db/apply.sh failed (" + r.getExitCode() + "):\n" + r.getStdout() + "\n" + r.getStderr());
            }
            LOG.info("db/apply.sh provisioned AssessmentDelivery in {} ms:\n{}", (System.nanoTime() - t0) / 1_000_000, r.getStdout());
        } catch (Exception e) {
            c.stop();
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
        String url = "jdbc:sqlserver://" + c.getHost() + ":" + c.getMappedPort(1433)
                + ";databaseName=AssessmentDelivery;encrypt=false;trustServerCertificate=true;applicationName=scheduling-characterization";
        return new LegacyDatabase(c, url, APP_USER, APP_PASSWORD, root);
    }

    /** Repo root (contains db/procs). From -Drepo.root or by walking up from the working directory. */
    public static Path repoRoot() {
        String prop = System.getProperty("repo.root");
        if (prop != null && Files.isDirectory(Paths.get(prop, "db", "procs"))) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("db").resolve("procs"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("Cannot find the repo root (db/procs); set -Drepo.root");
    }

    public Path repoRootPath() {
        return repoRoot;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
        }
    }
}
