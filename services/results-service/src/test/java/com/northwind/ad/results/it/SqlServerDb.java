package com.northwind.ad.results.it;

import java.io.File;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One SQL Server 2022 container per test JVM, built with the real db/apply.sh
 * (schema, procs, triggers, seed, security). The service connects as ad_results, like in production, so the
 * MBA-3823 write denial (db/security/040_single_writer.sql) applies; fixtures write as sa.
 */
public final class SqlServerDb {

    static final String SA_PASSWORD = "Str0ng!Passw0rd";

    private static MSSQLServerContainer<?> container;

    private SqlServerDb() {
    }

    public static synchronized MSSQLServerContainer<?> get() {
        if (container == null) {
            File db = new File("../../db").getAbsoluteFile();
            if (!new File(db, "apply.sh").isFile()) {
                throw new IllegalStateException("db/apply.sh not found from " + new File(".").getAbsolutePath());
            }
            MSSQLServerContainer<?> c = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense()
                    .withPassword(SA_PASSWORD)
                    .withCopyFileToContainer(MountableFile.forHostPath(db.getPath()), "/ad-db/");
            c.start();
            try {
                ExecResult r = c.execInContainer("bash", "-c",
                        "AD_SQL_SERVER=localhost AD_SA_PASSWORD='" + SA_PASSWORD + "' bash /ad-db/apply.sh");
                if (r.getExitCode() != 0) {
                    throw new IllegalStateException("db/apply.sh failed: " + r.getStdout() + r.getStderr());
                }
            } catch (Exception e) {
                c.stop();
                throw new IllegalStateException(e);
            }
            container = c;
        }
        return container;
    }

    public static String jdbcUrl() {
        MSSQLServerContainer<?> c = get();
        return "jdbc:sqlserver://" + c.getHost() + ":" + c.getMappedPort(MSSQLServerContainer.MS_SQL_SERVER_PORT)
                + ";databaseName=AssessmentDelivery";
    }

    private static JdbcTemplate sa;

    /** sysadmin connection for fixtures (results-service's own login may not write dbo.Booking). */
    public static synchronized JdbcTemplate saJdbc() {
        if (sa == null) {
            DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl(), "sa", SA_PASSWORD);
            ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            sa = new JdbcTemplate(ds);
        }
        return sa;
    }

    /** Points the Spring datasource at the container as ad_results and attendance at the scheduling stub. */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            EnvironmentTestUtils.addEnvironment(ctx,
                    "spring.datasource.url=" + jdbcUrl(),
                    "spring.datasource.username=ad_results",
                    "spring.datasource.password=Resu1ts!Dev2026",
                    "results.scheduling.base-url=" + SchedulingStub.get().baseUrl());
        }
    }
}
