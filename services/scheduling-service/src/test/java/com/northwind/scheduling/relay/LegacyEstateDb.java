package com.northwind.scheduling.relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * A real AssessmentDelivery for the MBA-3823 ITs, built from the repo's db/ scripts (schema, procs, triggers, seed,
 * security - the db/apply.ps1 order) under the name {@value #DB}, so a live local estate is never touched.
 * <p>Target: env {@code SCHEDULING_IT_SQL_URL} (e.g. {@code jdbc:sqlserver://localhost:1433;encrypt=true;
 * trustServerCertificate=true}) + {@code SCHEDULING_IT_SQL_USER}/{@code _PASSWORD} (sysadmin, default sa /
 * Str0ng!Passw0rd) - the Windows SQL Express from scripts/run-legacy.ps1; else Testcontainers when Docker runs;
 * else {@link #available()} is false and the ITs are skipped.
 */
public final class LegacyEstateDb {

    public static final String DB = "AssessmentDelivery_RelayIT";
    public static final String SCHEDULING_DB = "Scheduling_RelayIT";
    private static final String[] FOLDERS = {"schema", "procs", "triggers", "seed", "security"};

    private static String baseUrl;
    private static String user;
    private static String password;
    private static boolean applied;

    private LegacyEstateDb() {
    }

    public static synchronized boolean available() {
        if (baseUrl != null) {
            return true;
        }
        String env = System.getenv("SCHEDULING_IT_SQL_URL");
        if (env != null && !env.isBlank()) {
            baseUrl = env.endsWith(";") ? env.substring(0, env.length() - 1) : env;
            user = envOr("SCHEDULING_IT_SQL_USER", "sa");
            password = envOr("SCHEDULING_IT_SQL_PASSWORD", "Str0ng!Passw0rd");
            return true;
        }
        if (DockerClientFactory.instance().isDockerAvailable()) {
            MSSQLServerContainer<?> c = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
            c.start();
            baseUrl = c.getJdbcUrl();
            user = c.getUsername();
            password = c.getPassword();
            return true;
        }
        return false;
    }

    public static String url(String database) {
        return baseUrl + ";databaseName=" + database;
    }

    public static String adminUser() {
        return user;
    }

    public static String adminPassword() {
        return password;
    }

    public static Connection admin(String database) throws SQLException {
        return DriverManager.getConnection(url(database), user, password);
    }

    public static Connection login(String login, String pwd) throws SQLException {
        return DriverManager.getConnection(url(DB), login, pwd);
    }

    /** Drops and rebuilds {@value #DB} (and drops {@value #SCHEDULING_DB}) once per JVM. */
    public static synchronized void ensureApplied() {
        if (applied) {
            return;
        }
        try (Connection c = admin("master"); Statement st = c.createStatement()) {
            for (String db : List.of(DB, SCHEDULING_DB)) {
                st.execute("IF DB_ID('" + db + "') IS NOT NULL BEGIN ALTER DATABASE [" + db + "] SET SINGLE_USER WITH "
                        + "ROLLBACK IMMEDIATE; DROP DATABASE [" + db + "]; END");
            }
            Path dbDir = repoRoot().resolve("db");
            for (String folder : FOLDERS) {
                List<Path> files;
                try (Stream<Path> s = Files.list(dbDir.resolve(folder))) {
                    files = s.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
                }
                for (Path f : files) {
                    for (String batch : batches(Files.readString(f, StandardCharsets.UTF_8))) {
                        try {
                            st.execute(batch);
                        } catch (SQLException e) {
                            throw new IllegalStateException(f.getFileName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
            applied = true;
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("cannot build " + DB, e);
        }
    }

    /** GO-separated batches, with the database renamed (new logins default to master). */
    static List<String> batches(String script) {
        String renamed = script.replace("DEFAULT_DATABASE = AssessmentDelivery", "DEFAULT_DATABASE = master")
                .replaceAll("\\bAssessmentDelivery\\b", DB);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : renamed.split("\\r?\\n")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                add(out, cur);
            } else {
                cur.append(line).append('\n');
            }
        }
        add(out, cur);
        return out;
    }

    private static void add(List<String> out, StringBuilder cur) {
        if (!cur.toString().isBlank()) {
            out.add(cur.toString());
        }
        cur.setLength(0);
    }

    static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("db").resolve("triggers"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root with db/ not found");
        }
        return p;
    }

    private static String envOr(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v;
    }
}
