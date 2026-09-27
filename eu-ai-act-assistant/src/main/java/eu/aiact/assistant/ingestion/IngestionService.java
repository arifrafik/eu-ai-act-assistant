package eu.aiact.assistant.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * Fetch → parse → verify → chunk → embed + index. Safe to re-run: an unchanged source is skipped,
 * a changed one replaces the previous chunks. The source hash is written last, so a run that fails
 * halfway is simply redone next time.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionProperties props;
    private final EurLexFetcher fetcher;
    private final RegulationParser parser;
    private final Chunker chunker;
    private final VectorStore vectorStore;
    private final ChunkFtsRepository ftsRepository;
    private final SourceDocumentRepository sourceRepository;

    public IngestionService(IngestionProperties props, EurLexFetcher fetcher, RegulationParser parser,
                            Chunker chunker, VectorStore vectorStore, ChunkFtsRepository ftsRepository,
                            SourceDocumentRepository sourceRepository) {
        this.props = props;
        this.fetcher = fetcher;
        this.parser = parser;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.ftsRepository = ftsRepository;
        this.sourceRepository = sourceRepository;
    }

    public IngestionResult ingest() {
        String regulation = props.regulation();
        String lang = props.lang();

        String html = fetcher.fetch(props);
        String hash = sha256(html);
        if (sourceRepository.findHash(regulation, lang).filter(hash::equals).isPresent()) {
            log.info("{} ({}) unchanged since last ingestion, skipping", regulation, lang);
            return IngestionResult.skippedUnchanged();
        }

        List<ParsedUnit> units = parser.parse(html);
        IngestionResult counts = verify(units);

        List<RegulationChunk> chunks = chunker.chunk(units, regulation, lang, props.sourceUrl(), props.maxChunkTokens());
        log.info("Parsed {} articles, {} recitals, {} annexes into {} chunks",
                counts.articles(), counts.recitals(), counts.annexes(), chunks.size());

        replaceExisting(regulation, lang);
        embed(chunks);
        ftsRepository.saveAll(chunks, regulation, lang);
        sourceRepository.upsert(regulation, lang, props.sourceUrl(), hash, chunks.size());

        log.info("Ingestion of {} ({}) complete", regulation, lang);
        return new IngestionResult(false, counts.articles(), counts.recitals(), counts.annexes(), chunks.size());
    }

    /** Fails loudly if the parser found fewer units than expected, e.g. after an EUR-Lex markup change. */
    IngestionResult verify(List<ParsedUnit> units) {
        int articles = distinct(units, UnitType.ARTICLE);
        int recitals = distinct(units, UnitType.RECITAL);
        int annexes = distinct(units, UnitType.ANNEX);
        var expected = props.expected();
        check("articles", articles, expected.articles());
        check("recitals", recitals, expected.recitals());
        check("annexes", annexes, expected.annexes());
        units.stream().filter(u -> u.text().isBlank()).findFirst().ifPresent(u -> {
            throw new IngestionException("Empty text in " + u.type() + " " + u.number());
        });
        return new IngestionResult(false, articles, recitals, annexes, 0);
    }

    private void replaceExisting(String regulation, String lang) {
        var b = new FilterExpressionBuilder();
        vectorStore.delete(b.and(b.eq("regulation", regulation), b.eq("lang", lang)).build());
        int removed = ftsRepository.deleteByRegulationAndLang(regulation, lang);
        if (removed > 0) {
            log.info("Removed {} previous chunks", removed);
        }
    }

    private void embed(List<RegulationChunk> chunks) {
        int batch = Math.max(1, props.embedBatchSize());
        for (int from = 0; from < chunks.size(); from += batch) {
            List<Document> docs = chunks.subList(from, Math.min(from + batch, chunks.size())).stream()
                    .map(c -> new Document(stableId(c.chunkId()), c.content(), c.metadata()))
                    .toList();
            vectorStore.add(docs);
            log.info("Embedded {}/{} chunks", Math.min(from + batch, chunks.size()), chunks.size());
        }
    }

    /** Same chunk ID → same UUID, so re-ingesting overwrites instead of duplicating. */
    static String stableId(String chunkId) {
        return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static int distinct(List<ParsedUnit> units, UnitType type) {
        return (int) units.stream().filter(u -> u.type() == type).map(ParsedUnit::number).distinct().count();
    }

    private static void check(String what, int found, int expected) {
        if (expected > 0 && found != expected) {
            throw new IngestionException("Expected " + expected + " " + what + " but parsed " + found
                    + ". Check the selectors in RegulationParser against the source HTML.");
        }
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
