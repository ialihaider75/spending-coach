package com.intellermatrix.spending_coach;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public abstract class IntegrationTestSupport {

    private static final String POSTGRES_DB = "spending_coach";
    private static final String POSTGRES_USER = "app";
    private static final String POSTGRES_PASSWORD = "app";
    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16")
        .withExposedPorts(POSTGRES_PORT)
        .withEnv("POSTGRES_DB", POSTGRES_DB)
        .withEnv("POSTGRES_USER", POSTGRES_USER)
        .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(REDIS_PORT);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(POSTGRES_PORT), POSTGRES_DB));
        registry.add("spring.datasource.username", () -> POSTGRES_USER);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
