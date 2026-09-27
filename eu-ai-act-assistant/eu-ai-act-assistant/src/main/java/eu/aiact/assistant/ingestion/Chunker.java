package eu.aiact.assistant.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns parsed units into chunks: one per article paragraph, recital or annex, split only when
 * longer than the token budget. Each chunk starts with a context header so it reads on its own.
 */
@Component
public class Chunker {

    /** Rough estimate that is good enough for sizing: ~4 characters per token in English. */
    static final int CHARS_PER_TOKEN = 4;

    private static final Map<String, String> REGULATION_NAMES = Map.of("AI_ACT", "EU AI Act", "GDPR", "GDPR");
    private static final Map<String, String> ID_PREFIXES = Map.of("AI_ACT", "AIA", "GDPR", "GDPR");

    public List<RegulationChunk> chunk(List<ParsedUnit> units, String regulation, String lang,
                                       String sourceUrl, int maxTokens) {
        List<RegulationChunk> chunks = new ArrayList<>();
        for (ParsedUnit unit : units) {
            List<String> parts = split(unit.lines(), maxTokens * CHARS_PER_TOKEN);
            for (int i = 0; i < parts.size(); i++) {
                Integer part = parts.size() > 1 ? i + 1 : null;
                chunks.add(toChunk(unit, parts.get(i), part, regulation, lang, sourceUrl));
            }
        }
        return chunks;
    }

    private RegulationChunk toChunk(ParsedUnit unit, String body, Integer part, String regulation,
                                    String lang, String sourceUrl) {
        String chunkId = chunkId(unit, part, regulation, lang);
        String content = header(unit, part, regulation) + "\n\n" + body;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunk_id", chunkId);
        metadata.put("regulation", regulation);
        metadata.put("type", unit.type().name());
        metadata.put("binding", unit.type().binding());
        metadata.put("number", unit.number());
        putIfPresent(metadata, "paragraph", unit.paragraph());
        putIfPresent(metadata, "part", part);
        putIfPresent(metadata, "title", unit.title());
        putIfPresent(metadata, "chapter", unit.chapter());
        metadata.put("lang", lang);
        metadata.put("url", sourceUrl + "#" + unit.anchor());
        metadata.put("refs", ReferenceExtractor.extract(body, selfRef(unit)));
        return new RegulationChunk(chunkId, content, metadata);
    }

    /** e.g. AIA-ART-6-2-EN, AIA-RCT-12-EN, AIA-ANX-III-P2-EN. */
    static String chunkId(ParsedUnit unit, Integer part, String regulation, String lang) {
        StringBuilder id = new StringBuilder(ID_PREFIXES.getOrDefault(regulation, regulation));
        id.append(switch (unit.type()) {
            case ARTICLE -> "-ART-";
            case RECITAL -> "-RCT-";
            case ANNEX -> "-ANX-";
        }).append(unit.number());
        if (unit.paragraph() != null) {
            id.append('-').append(unit.paragraph());
        }
        if (part != null) {
            id.append("-P").append(part);
        }
        return id.append('-').append(lang.toUpperCase(Locale.ROOT)).toString();
    }

    /** e.g. "EU AI Act › Chapter III › Article 6 – Classification rules... › Paragraph 2". */
    static String header(ParsedUnit unit, Integer part, String regulation) {
        List<String> path = new ArrayList<>();
        path.add(REGULATION_NAMES.getOrDefault(regulation, regulation));
        switch (unit.type()) {
            case ARTICLE -> {
                if (unit.chapter() != null) {
                    path.add(unit.chapter());
                }
                path.add(withTitle("Article " + unit.number(), unit.title()));
                if (unit.paragraph() != null) {
                    path.add("Paragraph " + unit.paragraph());
                }
            }
            case RECITAL -> path.add("Recital " + unit.number() + " (non-binding)");
            case ANNEX -> path.add(withTitle("Annex " + unit.number(), unit.title()));
        }
        String header = String.join(" › ", path);
        return part == null ? header : header + " (part " + part + ")";
    }

    /** Groups lines into parts of at most maxChars; a single over-long line is split at sentences. */
    static List<String> split(List<String> lines, int maxChars) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            for (String piece : line.length() > maxChars ? sentences(line, maxChars) : List.of(line)) {
                if (current.length() > 0 && current.length() + 1 + piece.length() > maxChars) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static List<String> sentences(String line, int maxChars) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : line.split("(?<=[.;:])\\s+")) {
            if (current.length() > 0 && current.length() + 1 + sentence.length() > maxChars) {
                out.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static String selfRef(ParsedUnit unit) {
        return switch (unit.type()) {
            case ARTICLE -> "ART-" + unit.number();
            case ANNEX -> "ANNEX-" + unit.number();
            case RECITAL -> null;
        };
    }

    private static String withTitle(String label, String title) {
        return title == null ? label : label + " – " + title;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
