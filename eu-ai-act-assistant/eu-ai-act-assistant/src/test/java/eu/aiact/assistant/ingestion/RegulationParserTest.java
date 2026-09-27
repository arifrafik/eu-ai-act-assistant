package eu.aiact.assistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class RegulationParserTest {

    private static List<ParsedUnit> units;

    @BeforeAll
    static void parseFixture() throws IOException {
        units = new RegulationParser().parse(read("fixtures/ai-act-sample.html"));
    }

    @Test
    void findsAllUnitTypes() {
        assertThat(count(UnitType.RECITAL)).isEqualTo(2);
        assertThat(units.stream().filter(u -> u.type() == UnitType.ARTICLE).map(ParsedUnit::number).distinct())
                .containsExactly("1", "6", "50");
        assertThat(count(UnitType.ANNEX)).isEqualTo(1);
    }

    @Test
    void recitalNumberIsStrippedFromText() {
        ParsedUnit recital = find(UnitType.RECITAL, "2", null);
        assertThat(recital.text()).startsWith("This Regulation should be applied")
                .doesNotContain("\u00A0");
    }

    @Test
    void articleParagraphsCarryTitleChapterAndNumber() {
        ParsedUnit p2 = find(UnitType.ARTICLE, "6", 2);
        assertThat(p2.title()).isEqualTo("Classification rules for high-risk AI systems");
        assertThat(p2.chapter()).isEqualTo("Chapter III");
        assertThat(p2.anchor()).isEqualTo("art_6");
        assertThat(p2.text()).startsWith("In addition to the high-risk AI systems");
    }

    @Test
    void pointLabelsAreJoinedToTheirText() {
        ParsedUnit p2 = find(UnitType.ARTICLE, "1", 2);
        assertThat(p2.lines()).containsExactly(
                "This Regulation lays down:",
                "(a) harmonised rules for the placing on the market of AI systems;",
                "(b) prohibitions of certain AI practices, as set out in Article 5.");
    }

    @Test
    void unnumberedArticleBecomesOneUnitWithoutParagraph() {
        ParsedUnit art50 = find(UnitType.ARTICLE, "50", null);
        assertThat(art50.text()).startsWith("Providers shall ensure").doesNotContain("Article 50");
    }

    @Test
    void annexHasTitleAndPoints() {
        ParsedUnit annex = find(UnitType.ANNEX, "III", null);
        assertThat(annex.title()).isEqualTo("High-risk AI systems referred to in Article 6(2)");
        assertThat(annex.lines()).contains("1. Biometrics, in so far as their use is permitted under relevant Union or national law:");
        assertThat(annex.text()).doesNotContain("ANNEX III");
    }

    /**
     * Runs only when you have downloaded the real regulation to data/ai-act-en.html.
     * Confirms the selectors still match EUR-Lex's current markup.
     */
    @Test
    @EnabledIf("realFileExists")
    void realEurLexFile() throws IOException {
        List<ParsedUnit> real = new RegulationParser().parse(Files.readString(REAL_FILE, StandardCharsets.UTF_8));
        assertThat(real.stream().filter(u -> u.type() == UnitType.ARTICLE).map(ParsedUnit::number).distinct()).hasSize(113);
        assertThat(real.stream().filter(u -> u.type() == UnitType.RECITAL)).hasSize(180);
        assertThat(real.stream().filter(u -> u.type() == UnitType.ANNEX)).hasSize(13);
    }

    private static final Path REAL_FILE = Path.of("data/ai-act-en.html");

    static boolean realFileExists() {
        return Files.exists(REAL_FILE);
    }

    private static long count(UnitType type) {
        return units.stream().filter(u -> u.type() == type).count();
    }

    private static ParsedUnit find(UnitType type, String number, Integer paragraph) {
        return units.stream()
                .filter(u -> u.type() == type && u.number().equals(number)
                        && java.util.Objects.equals(u.paragraph(), paragraph))
                .findFirst().orElseThrow();
    }

    static String read(String resource) throws IOException {
        try (var in = RegulationParserTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
