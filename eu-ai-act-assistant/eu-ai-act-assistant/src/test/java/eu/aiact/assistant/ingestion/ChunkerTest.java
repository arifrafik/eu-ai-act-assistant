package eu.aiact.assistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static final String URL = "https://eur-lex.europa.eu/doc";
    private static List<RegulationChunk> chunks;

    @BeforeAll
    static void chunkFixture() throws IOException {
        var units = new RegulationParser().parse(RegulationParserTest.read("fixtures/ai-act-sample.html"));
        chunks = new Chunker().chunk(units, "AI_ACT", "en", URL, 800);
    }

    @Test
    void idsAreStableAndReadable() {
        assertThat(chunks).extracting(RegulationChunk::chunkId).contains(
                "AIA-RCT-1-EN", "AIA-ART-1-2-EN", "AIA-ART-6-2-EN", "AIA-ART-50-EN", "AIA-ANX-III-EN");
        assertThat(chunks).extracting(RegulationChunk::chunkId).doesNotHaveDuplicates();
    }

    @Test
    void contentStartsWithContextHeader() {
        assertThat(get("AIA-ART-6-2-EN").content()).startsWith(
                "EU AI Act › Chapter III › Article 6 – Classification rules for high-risk AI systems › Paragraph 2\n\n");
        assertThat(get("AIA-RCT-1-EN").content()).startsWith("EU AI Act › Recital 1 (non-binding)");
    }

    @Test
    void metadataSupportsCitationsAndFiltering() {
        var meta = get("AIA-ART-6-2-EN").metadata();
        assertThat(meta).containsEntry("regulation", "AI_ACT")
                .containsEntry("type", "ARTICLE")
                .containsEntry("binding", true)
                .containsEntry("number", "6")
                .containsEntry("paragraph", 2)
                .containsEntry("lang", "en")
                .containsEntry("url", URL + "#art_6");
        assertThat(get("AIA-RCT-1-EN").metadata()).containsEntry("binding", false);
    }

    @Test
    void referencesToOwnArticlesAndAnnexesAreExtracted() {
        @SuppressWarnings("unchecked")
        var refs = (List<String>) get("AIA-ART-6-2-EN").metadata().get("refs");
        // Article 9 of Regulation (EU) 2016/679 is another act and must not be linked.
        assertThat(refs).containsExactly("ART-7", "ANNEX-III");
    }

    @Test
    void referenceListsAndRangesAreExpanded() {
        @SuppressWarnings("unchecked")
        var refs = (List<String>) get("AIA-ART-50-EN").metadata().get("refs");
        assertThat(refs).containsExactly("ART-8", "ART-15");
    }

    @Test
    void longUnitsAreSplitIntoParts() {
        var units = new RegulationParser().parse(readFixture());
        var small = new Chunker().chunk(units, "AI_ACT", "en", URL, 60); // 240 characters
        var annexParts = small.stream().filter(c -> c.chunkId().startsWith("AIA-ANX-III")).toList();
        assertThat(annexParts).hasSizeGreaterThan(1);
        assertThat(annexParts.get(0).chunkId()).isEqualTo("AIA-ANX-III-P1-EN");
        assertThat(annexParts.get(0).content()).contains("(part 1)");
        assertThat(annexParts).allSatisfy(c -> assertThat(c.metadata()).containsKey("part"));
    }

    private static RegulationChunk get(String id) {
        return chunks.stream().filter(c -> c.chunkId().equals(id)).findFirst().orElseThrow();
    }

    private static String readFixture() {
        try {
            return RegulationParserTest.read("fixtures/ai-act-sample.html");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
