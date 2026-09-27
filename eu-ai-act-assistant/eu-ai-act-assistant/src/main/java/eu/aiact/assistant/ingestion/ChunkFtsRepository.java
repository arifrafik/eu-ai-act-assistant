package eu.aiact.assistant.ingestion;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Keyword (full-text) index of chunks: the keyword half of hybrid retrieval. */
@Repository
public class ChunkFtsRepository {

    private final JdbcTemplate jdbc;

    public ChunkFtsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(List<RegulationChunk> chunks, String regulation, String lang) {
        jdbc.batchUpdate("""
                INSERT INTO chunk_fts (chunk_id, regulation, lang, content) VALUES (?, ?, ?, ?)
                ON CONFLICT (chunk_id) DO UPDATE SET content = EXCLUDED.content
                """,
                chunks, 500, (ps, chunk) -> {
                    ps.setString(1, chunk.chunkId());
                    ps.setString(2, regulation);
                    ps.setString(3, lang);
                    ps.setString(4, chunk.content());
                });
    }

    public int deleteByRegulationAndLang(String regulation, String lang) {
        return jdbc.update("DELETE FROM chunk_fts WHERE regulation = ? AND lang = ?", regulation, lang);
    }

    public int count(String regulation, String lang) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM chunk_fts WHERE regulation = ? AND lang = ?", Integer.class, regulation, lang);
        return n == null ? 0 : n;
    }

    /** Chunk IDs matching all words of the query, best matches first. */
    public List<String> search(String query, String regulation, String lang, int limit) {
        return jdbc.queryForList("""
                SELECT chunk_id FROM chunk_fts, plainto_tsquery('simple', ?) q
                WHERE regulation = ? AND lang = ? AND tsv @@ q
                ORDER BY ts_rank(tsv, q) DESC, chunk_id
                LIMIT ?
                """, String.class, query, regulation, lang, limit);
    }
}
