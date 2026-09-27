package eu.aiact.assistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class IngestionServiceTest {

    private final RegulationParser parser = new RegulationParser();

    @Test
    void verifyPassesWhenCountsMatch() throws IOException {
        var result = service(3, 2, 1).verify(parser.parse(RegulationParserTest.read("fixtures/ai-act-sample.html")));
        assertThat(result.articles()).isEqualTo(3);
        assertThat(result.recitals()).isEqualTo(2);
        assertThat(result.annexes()).isEqualTo(1);
    }

    @Test
    void verifyFailsLoudlyWhenParserMissesArticles() throws IOException {
        var units = parser.parse(RegulationParserTest.read("fixtures/ai-act-sample.html"));
        assertThatThrownBy(() -> service(113, 2, 1).verify(units))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Expected 113 articles but parsed 3");
    }

    @Test
    void stableIdIsDeterministic() {
        assertThat(IngestionService.stableId("AIA-ART-6-2-EN")).isEqualTo(IngestionService.stableId("AIA-ART-6-2-EN"))
                .isNotEqualTo(IngestionService.stableId("AIA-ART-6-1-EN"));
    }

    private IngestionService service(int articles, int recitals, int annexes) {
        var props = new IngestionProperties("AI_ACT", "en", "https://example.org", null, 800, 100,
                new IngestionProperties.Expected(articles, recitals, annexes));
        return new IngestionService(props, null, parser, new Chunker(), null, null, null);
    }
}
