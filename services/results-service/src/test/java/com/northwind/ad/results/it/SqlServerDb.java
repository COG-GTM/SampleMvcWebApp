package com.northwind.ad.results.it;

import java.io.File;

import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One SQL Server 2022 container per test JVM, built with the real db/apply.sh
 * (schema, procs, triggers, seed). The service connects as ad_app, like in production.
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

    /** Points the Spring datasource at the container as ad_app. */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            EnvironmentTestUtils.addEnvironment(ctx,
                    "spring.datasource.url=" + jdbcUrl(),
                    "spring.datasource.username=ad_app",
                    "spring.datasource.password=Northw1nd!Dev2011",
                    "scheduling.base-url=http://localhost:${local.server.port}" + LegacyDbSchedulingStub.BASE);
        }
    }
}
