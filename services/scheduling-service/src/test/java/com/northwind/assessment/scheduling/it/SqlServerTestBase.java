package com.northwind.assessment.scheduling.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One SQL Server 2022 container per test JVM. The legacy AssessmentDelivery database is built exactly
 * like {@code db/apply.sh} (schema, procs, triggers, deterministic seed) plus the read-only importer login,
 * then scheduling-service starts against its own {@code Scheduling} database with the startup import on.
 */
public abstract class SqlServerTestBase {

    static final String SA_PASSWORD = "Str0ng!Passw0rd";
    static final Path DB_DIR = Path.of("..", "..", "db").toAbsolutePath().normalize();

    static final MSSQLServerContainer<?> SQL = startWithLegacyDatabase();

    /** A started SQL Server 2022 container with AssessmentDelivery built as by db/apply.sh. */
    @SuppressWarnings("resource")
    static MSSQLServerContainer<?> startWithLegacyDatabase() {
        MSSQLServerContainer<?> sql = new MSSQLServerContainer<>(
                DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                .acceptLicense()
                .withPassword(SA_PASSWORD)
                .withCopyFileToContainer(MountableFile.forHostPath(DB_DIR), "/legacy-db");
        sql.start();
        try {
            for (String script : legacyScripts()) {
                Container.ExecResult r = sql.execInContainer("/opt/mssql-tools18/bin/sqlcmd", "-S", "localhost",
                        "-U", "sa", "-P", SA_PASSWORD, "-C", "-b", "-I", "-d", "master", "-i", "/legacy-db/" + script);
                if (r.getExitCode() != 0) {
                    throw new IllegalStateException("Legacy script " + script + " failed: " + r.getStdout() + r.getStderr());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return sql;
    }

    static List<String> legacyScripts() throws IOException {
        List<String> scripts = new ArrayList<>();
        for (String folder : List.of("schema", "procs", "triggers", "seed", "security")) {
            try (Stream<Path> files = Files.list(DB_DIR.resolve(folder))) {
                files.filter(p -> p.toString().endsWith(".sql")).map(p -> folder + "/" + p.getFileName()).sorted()
                        .forEach(scripts::add);
            }
        }
        return scripts;
    }

    static String jdbcUrl(String database) {
        return jdbcUrl(SQL, database);
    }

    static String jdbcUrl(MSSQLServerContainer<?> sql, String database) {
        return "jdbc:sqlserver://" + sql.getHost() + ":" + sql.getMappedPort(1433) + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true";
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl("Scheduling"));
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> SA_PASSWORD);
        registry.add("scheduling.legacy.url", () -> jdbcUrl("AssessmentDelivery"));
        registry.add("scheduling.import.on-startup", () -> "true");
    }
}
