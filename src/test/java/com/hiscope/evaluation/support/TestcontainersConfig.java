package com.hiscope.evaluation.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class TestcontainersConfig {

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("hiscope_test")
            .withUsername("hiscope")
            .withPassword("hiscope");

    static {
        POSTGRESQL.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    }

    protected static String jdbcUrl() {
        return POSTGRESQL.getJdbcUrl();
    }

    protected static String username() {
        return POSTGRESQL.getUsername();
    }

    protected static String password() {
        return POSTGRESQL.getPassword();
    }
}
