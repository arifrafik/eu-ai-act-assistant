package eu.aiact.assistant.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Turns the EUR-Lex HTML of a regulation into {@link ParsedUnit}s.
 *
 * <p>All knowledge of EUR-Lex's markup lives in the constants below. If the parser test
 * against a freshly downloaded file fails, adjust these selectors first.
 */
@Component
public class RegulationParser {

    // ---- EUR-Lex markup (Official Journal HTML format) ----
    static final String RECITALS = "div[id~=^rct_\\d+$]";
    static final String ARTICLES = "div[id~=^art_\\d+$]";
    static final String ANNEXES = "div[id~=^anx_[IVXLC]+$]";
    static final String ARTICLE_PARAGRAPHS = "div[id~=^\\d{3}\\.\\d{3}$]";
    static final String ARTICLE_HEADING = "p.oj-ti-art";
    static final String ARTICLE_TITLE = "p.oj-sti-art";
    static final String ANNEX_HEADINGS = "p.oj-doc-ti";
    static final String TEXT_BLOCKS = "p.oj-normal";
    static final Pattern CHAPTER_ID = Pattern.compile("^cpt_([IVXLC]+)$");

    /** "1." or "(a)" or "(iv)" on its own: a point label to merge with the following block. */
    private static final Pattern POINT_LABEL = Pattern.compile("^(\\(?[0-9a-z]{1,4}[.)])$");
    /** Leading "1.   " on article paragraphs; the number is kept in metadata instead. */
    private static final Pattern LEADING_PARAGRAPH_NUMBER = Pattern.compile("^\\d+\\.\\s+");
    /** Leading "(12)" on recitals. */
    private static final Pattern LEADING_RECITAL_NUMBER = Pattern.compile("^\\(\\d+\\)\\s*");

    public List<ParsedUnit> parse(String html) {
        Document doc = Jsoup.parse(html);
        List<ParsedUnit> units = new ArrayList<>();
        doc.select(RECITALS).forEach(el -> units.add(parseRecital(el)));
        doc.select(ARTICLES).forEach(el -> units.addAll(parseArticle(el)));
        doc.select(ANNEXES).forEach(el -> units.add(parseAnnex(el)));
        return units;
    }

    private ParsedUnit parseRecital(Element el) {
        String number = el.id().substring("rct_".length());
        String text = LEADING_RECITAL_NUMBER.matcher(clean(el.text())).replaceFirst("");
        return new ParsedUnit(UnitType.RECITAL, number, null, null, null, el.id(), List.of(text));
    }

    private List<ParsedUnit> parseArticle(Element el) {
        String number = el.id().substring("art_".length());
        String title = textOf(el.selectFirst(ARTICLE_TITLE));
        String chapter = chapterOf(el);

        List<ParsedUnit> units = new ArrayList<>();
        for (Element para : el.select(ARTICLE_PARAGRAPHS)) {
            int paragraph = Integer.parseInt(para.id().substring(4));
            List<String> lines = blocks(para);
            if (!lines.isEmpty()) {
                lines.set(0, LEADING_PARAGRAPH_NUMBER.matcher(lines.get(0)).replaceFirst(""));
                units.add(new ParsedUnit(UnitType.ARTICLE, number, paragraph, title, chapter, el.id(), lines));
            }
        }
        if (units.isEmpty()) {
            // Articles with a single unnumbered paragraph have no paragraph divs.
            Element body = el.clone();
            body.select(ARTICLE_HEADING + ", " + ARTICLE_TITLE).remove();
            List<String> lines = blocks(body);
            if (!lines.isEmpty()) {
                units.add(new ParsedUnit(UnitType.ARTICLE, number, null, title, chapter, el.id(), lines));
            }
        }
        return units;
    }

    private ParsedUnit parseAnnex(Element el) {
        String number = el.id().substring("anx_".length());
        var headings = el.select(ANNEX_HEADINGS);
        String title = headings.size() > 1 ? clean(headings.get(1).text()) : null;
        Element body = el.clone();
        body.select(ANNEX_HEADINGS).remove();
        return new ParsedUnit(UnitType.ANNEX, number, null, title, null, el.id(), blocks(body));
    }

    /** Text blocks in reading order, with point labels ("(a)") joined to their text. */
    private List<String> blocks(Element root) {
        List<String> lines = new ArrayList<>();
        String pendingLabel = null;
        for (Element p : root.select(TEXT_BLOCKS)) {
            String text = clean(p.text());
            if (text.isEmpty()) {
                continue;
            }
            if (POINT_LABEL.matcher(text).matches()) {
                pendingLabel = pendingLabel == null ? text : pendingLabel + " " + text;
                continue;
            }
            lines.add(pendingLabel == null ? text : pendingLabel + " " + text);
            pendingLabel = null;
        }
        if (pendingLabel != null) {
            lines.add(pendingLabel);
        }
        return lines;
    }

    private String chapterOf(Element article) {
        for (Element parent : article.parents()) {
            var m = CHAPTER_ID.matcher(parent.id());
            if (m.matches()) {
                return "Chapter " + m.group(1);
            }
        }
        return null;
    }

    private static String textOf(Element el) {
        return el == null ? null : clean(el.text());
    }

    /** Normalises non-breaking and repeated spaces. */
    static String clean(String s) {
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
