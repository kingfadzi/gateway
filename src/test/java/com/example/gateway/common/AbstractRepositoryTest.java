package com.example.gateway.common;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for repository integration tests.
 *
 * <p>Uses @DataJpaTest which provides a lighter context than @SpringBootTest,
 * focused on JPA components and database operations.
 *
 * <p>Uses the 'test' profile which connects to the real PostgreSQL database.
 * All tests are transactional and will be rolled back after execution.
 *
 * <p>Usage: Extend this class for repository layer tests.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {
    // Base class for repository tests
    // Tests extending this class will:
    // 1. Load only JPA/database-related beans (lighter than full context)
    // 2. Connect to real PostgreSQL database
    // 3. Automatically rollback all changes after each test
}
