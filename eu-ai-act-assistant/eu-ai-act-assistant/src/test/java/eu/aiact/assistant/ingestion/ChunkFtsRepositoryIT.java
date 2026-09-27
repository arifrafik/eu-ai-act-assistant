package eu.aiact.assistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import eu.aiact.assistant.TestcontainersConfig;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({TestcontainersConfig.class, ChunkFtsRepository.class, SourceDocumentRepository.class})
class ChunkFtsRepositoryIT {

    @Autowired
    ChunkFtsRepository fts;

    @Autowired
    SourceDocumentRepository sources;

    @Test
    void keywordSearchFindsExactArticleReferences() {
        fts.saveAll(List.of(
                chunk("AIA-ART-50-EN", "EU AI Act › Article 50 – Transparency obligations\n\nProviders shall ensure users are informed."),
                chunk("AIA-ART-6-2-EN", "EU AI Act › Article 6 › Paragraph 2\n\nAI systems referred to in Annex III shall be considered high-risk."),
                chunk("AIA-RCT-1-EN", "EU AI Act › Recital 1 (non-binding)\n\nThe purpose of this Regulation.")),
                "AI_ACT", "en");

        assertThat(fts.count("AI_ACT", "en")).isEqualTo(3);
        assertThat(fts.search("Annex III", "AI_ACT", "en", 5)).containsExactly("AIA-ART-6-2-EN");
        assertThat(fts.search("transparency obligations", "AI_ACT", "en", 5)).containsExactly("AIA-ART-50-EN");
        assertThat(fts.search("Annex III", "AI_ACT", "fr", 5)).isEmpty();
    }

    @Test
    void saveIsIdempotentAndDeleteIsScoped() {
        var c = chunk("AIA-ART-1-1-EN", "first version");
        fts.saveAll(List.of(c), "AI_ACT", "en");
        fts.saveAll(List.of(chunk("AIA-ART-1-1-EN", "second version")), "AI_ACT", "en");
        assertThat(fts.count("AI_ACT", "en")).isEqualTo(1);
        assertThat(fts.search("second", "AI_ACT", "en", 5)).containsExactly("AIA-ART-1-1-EN");

        assertThat(fts.deleteByRegulationAndLang("GDPR", "en")).isZero();
        assertThat(fts.deleteByRegulationAndLang("AI_ACT", "en")).isEqualTo(1);
    }

    @Test
    void sourceHashIsUpserted() {
        assertThat(sources.findHash("AI_ACT", "en")).isEmpty();
        sources.upsert("AI_ACT", "en", "https://example.org", "abc", 10);
        sources.upsert("AI_ACT", "en", "https://example.org", "def", 12);
        assertThat(sources.findHash("AI_ACT", "en")).contains("def");
    }

    private static RegulationChunk chunk(String id, String content) {
        return new RegulationChunk(id, content, Map.of());
    }
}
