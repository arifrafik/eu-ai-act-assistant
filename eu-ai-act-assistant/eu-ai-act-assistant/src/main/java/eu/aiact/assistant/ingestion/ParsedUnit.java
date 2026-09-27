package eu.aiact.assistant.ingestion;

import java.util.List;

/**
 * One addressable piece of a regulation as found in the HTML: an article paragraph,
 * a recital, or an annex.
 *
 * @param type      article, recital or annex
 * @param number    "6" for Article 6, "12" for Recital 12, "III" for Annex III
 * @param paragraph paragraph number inside an article, or null
 * @param title     article or annex title, or null
 * @param chapter   e.g. "Chapter III", or null
 * @param anchor    HTML id on EUR-Lex, used to build citation links (e.g. "art_6")
 * @param lines     the text, one entry per block (paragraph, point, sub-point)
 */
public record ParsedUnit(
        UnitType type,
        String number,
        Integer paragraph,
        String title,
        String chapter,
        String anchor,
        List<String> lines) {

    public String text() {
        return String.join("\n", lines);
    }
}
