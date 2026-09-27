package eu.aiact.assistant.ingestion;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SourceDocumentRepository {

    private final JdbcClient jdbc;

    public SourceDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findHash(String regulation, String lang) {
        return jdbc.sql("SELECT content_hash FROM source_document WHERE regulation = ? AND lang = ?")
                .params(regulation, lang)
                .query(String.class)
                .optional();
    }

    public void upsert(String regulation, String lang, String url, String hash, int chunkCount) {
        jdbc.sql("""
                INSERT INTO source_document (regulation, lang, url, content_hash, chunk_count)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (regulation, lang) DO UPDATE
                SET url = EXCLUDED.url, content_hash = EXCLUDED.content_hash,
                    chunk_count = EXCLUDED.chunk_count, ingested_at = now()
                """)
                .params(regulation, lang, url, hash, chunkCount)
                .update();
    }
}
