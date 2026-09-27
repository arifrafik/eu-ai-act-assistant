package eu.aiact.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the whole app against a real pgvector database. No real API key is needed:
 * nothing calls the model during startup.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@Import(TestcontainersConfig.class)
class AssistantApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextStartsAndFlywayCreatesSchema() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_name IN ('vector_store', 'source_document', 'chunk_fts')
                """, Integer.class);
        assertThat(tables).isEqualTo(3);
    }
}
