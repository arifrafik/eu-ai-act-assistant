package eu.aiact.assistant.ingestion;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds references to other articles and annexes of the same regulation, e.g.
 * "Article 6(2)", "Articles 8 to 15", "Annexes I and III", as ["ART-6", "ART-8", "ART-15", ...].
 * References to other acts ("Article 9 of Regulation (EU) 2016/679") are ignored.
 */
final class ReferenceExtractor {

    private static final Pattern ARTICLES = Pattern.compile(
            "\\bArticles?\\s+(\\d+(?:\\([^)]*\\))*(?:\\s*(?:,|and|or|to)\\s*\\d+(?:\\([^)]*\\))*)*)");
    private static final Pattern ANNEXES = Pattern.compile(
            "\\bAnnex(?:es)?\\s+([IVXLC]+(?:\\s*(?:,|and|or|to)\\s*[IVXLC]+)*)\\b");
    private static final Pattern OTHER_ACT = Pattern.compile(
            "^\\s*(?:of|to)\\s+(?:Regulation|Directive|Decision|the Treaty|the Charter)|^\\s*(?:TEU|TFEU)\\b");
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern ROMAN = Pattern.compile("[IVXLC]+");

    private ReferenceExtractor() {
    }

    static List<String> extract(String text, String selfRef) {
        Set<String> refs = new LinkedHashSet<>();
        collect(text, ARTICLES, "ART-", NUMBER, true, refs);
        collect(text, ANNEXES, "ANNEX-", ROMAN, false, refs);
        refs.remove(selfRef);
        return List.copyOf(refs);
    }

    private static void collect(String text, Pattern pattern, String prefix, Pattern item,
                                boolean stripParentheses, Set<String> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (OTHER_ACT.matcher(text.substring(m.end())).find()) {
                continue;
            }
            String list = stripParentheses ? PARENTHESES.matcher(m.group(1)).replaceAll("") : m.group(1);
            Matcher i = item.matcher(list);
            while (i.find()) {
                out.add(prefix + i.group());
            }
        }
    }
}
