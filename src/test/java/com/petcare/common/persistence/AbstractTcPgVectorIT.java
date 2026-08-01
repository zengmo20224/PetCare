package com.petcare.common.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for Testcontainers PgVector integration tests.
 * <p>
 * Starts a fixed {@code pgvector/pgvector:pg16} container once per test JVM,
 * creates the {@code vector} extension, and binds its connection info to
 * {@code petcare.ai.pgvector.*} properties via {@link DynamicPropertySource}.
 * <p>
 * <b>Does NOT override the primary MySQL datasource</b> — the main business DB
 * stays on whatever the active profile provides (H2 for pure-pgvector tests,
 * or MySQL when combined with {@link AbstractTcMySqlIT}). This binds only the
 * secondary vector-store properties so {@code ai/rag/} code can reach PgVector.
 * <p>
 * Subclasses inherit {@code @SpringBootTest}, {@code @ActiveProfiles("tc-pgvector")},
 * and the container lifecycle — they only need to declare their own test methods.
 * <p>
 * Design ref: {@code docs/09-ai-agent-design.md} §10.4; mirrors
 * {@link AbstractTcMySqlIT} conventions (single JVM-shared container + static init).
 */
@SpringBootTest
@ActiveProfiles("tc-pgvector")
public abstract class AbstractTcPgVectorIT {

    /**
     * PgVector image with the vector extension pre-installed.
     * Pinned to pg16 for reproducibility; agent-verified pgvector 0.8.6
     * (contains both hnsw and ivfflat access methods).
     * <p>
     * Uses {@code asCompatibleSubstituteFor("postgres")} so the standard
     * {@link PostgreSQLContainer} accepts the pgvector-flavored image.
     */
    private static final PostgreSQLContainer<?> PGVECTOR = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("petcare_ai")
            .withUsername("petcare")
            .withPassword("tc-pgvector-test");

    static {
        PGVECTOR.start();
        // Create vector extension after start (schema-pgvector.sql is mounted by docker-compose
        // for the real deployment, but Testcontainers does not auto-mount it).
        try {
            PGVECTOR.execInContainer("psql", "-U", "petcare", "-d", "petcare_ai", "-c",
                    "CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create vector extension in PgVector container", e);
        }
    }

    @DynamicPropertySource
    static void registerPgVectorProperties(DynamicPropertyRegistry registry) {
        // Bind to the V2 pgvector config prefix (see application.yml petcare.ai.pgvector.*)
        // DO NOT bind spring.datasource.* — that stays on the primary MySQL/H2 datasource.
        registry.add("petcare.ai.pgvector.host", PGVECTOR::getHost);
        registry.add("petcare.ai.pgvector.port", PGVECTOR::getFirstMappedPort);
        registry.add("petcare.ai.pgvector.database", PGVECTOR::getDatabaseName);
        registry.add("petcare.ai.pgvector.username", PGVECTOR::getUsername);
        registry.add("petcare.ai.pgvector.password", PGVECTOR::getPassword);
        // Enable agent+rag for this profile so rag code paths are exercised
        registry.add("petcare.ai.agent-enabled", () -> "true");
        registry.add("petcare.ai.rag-enabled", () -> "true");
    }
}
