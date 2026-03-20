package com.hiscope.evaluation.scenario.migration;

import com.hiscope.evaluation.support.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.bootstrap.super-admin.enabled=false"
})
@ActiveProfiles("test")
class FlywayMigrationValidationIntegrationTest extends TestcontainersConfig {

    private static final int START_VERSION = 1;
    private static final int END_VERSION = 16;
    private static final String MIGRATION_LOCATION = "classpath:db/migration/common";

    @Test
    void V1부터_V16까지_마이그레이션이_PostgreSQL에서_성공한다() {
        int lastSuccessfulTarget = 0;

        for (int targetVersion = START_VERSION; targetVersion <= END_VERSION; targetVersion++) {
            String schema = "migration_check_v" + targetVersion;
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl(), username(), password())
                    .locations(MIGRATION_LOCATION)
                    .createSchemas(true)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .baselineOnMigrate(true)
                    .cleanDisabled(false)
                    .target(MigrationVersion.fromVersion(String.valueOf(targetVersion)))
                    .load();

            try {
                flyway.clean();
                flyway.migrate();
                lastSuccessfulTarget = targetVersion;
            } catch (Exception ex) {
                MigrationInfo current = flyway.info().current();
                String lastAppliedVersion = current == null ? "none" : "V" + current.getVersion();
                fail(
                        "Flyway migration failed at target V" + targetVersion
                                + " (last successful target: V" + lastSuccessfulTarget
                                + ", last applied version in schema: " + lastAppliedVersion
                                + "). Root cause: " + rootCauseMessage(ex),
                        ex
                );
            }
        }

        Flyway fullMigrationFlyway = Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations(MIGRATION_LOCATION)
                .createSchemas(true)
                .schemas("migration_check_full")
                .defaultSchema("migration_check_full")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .target(MigrationVersion.fromVersion(String.valueOf(END_VERSION)))
                .load();

        fullMigrationFlyway.clean();
        fullMigrationFlyway.migrate();

        MigrationInfo current = fullMigrationFlyway.info().current();
        assertThat(current).as("Flyway current migration").isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo(String.valueOf(END_VERSION));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }
}
