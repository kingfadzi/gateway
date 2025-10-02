package com.example.gateway.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for integration tests that require the full Spring context.
 *
 * <p>Uses the 'test' profile which connects to the real PostgreSQL database.
 * All tests are transactional and will be rolled back after execution,
 * ensuring data is not persisted between tests.
 *
 * <p>Usage: Extend this class for service and controller integration tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {
    // Base class for integration tests
    // Tests extending this class will:
    // 1. Load full Spring application context
    // 2. Connect to real PostgreSQL database
    // 3. Automatically rollback all changes after each test
}
