# EU AI Act Assistant

Ask questions about the EU AI Act and get answers that cite the exact article, paragraph or annex.
Built with Java 21, Spring Boot 4 and Spring AI 2, on Postgres + pgvector.

> Informational tool, not legal advice.

**Status: iteration 1 — ingestion.** The official text is parsed, chunked by legal structure,
embedded and indexed for hybrid (vector + keyword) search. Cited Q&A comes next.

## How it works (iteration 1)

```
EUR-Lex HTML ──► RegulationParser ──► Chunker ──► VectorStore (pgvector)  semantic search
   (jsoup)        articles, recitals,   context header,  └► chunk_fts (Postgres)    keyword search
                  annexes, paragraphs   IDs, refs, splits
```

Design choices worth knowing:

- **Chunks follow the law's structure**: one chunk per article paragraph, recital or annex (split only
  when longer than ~800 tokens). Each starts with a header such as
  `EU AI Act › Chapter III › Article 6 – Classification rules… › Paragraph 2`.
- **Stable, citable IDs** like `AIA-ART-6-2-EN`. The model will cite these, and code will verify them.
- **Cross-references** ("Article 6(2)", "Annexes I and III") are stored per chunk; references to
  other acts ("Article 9 of Regulation (EU) 2016/679") are ignored.
- **Recitals are marked non-binding** in both the header and metadata.
- **Fails loudly**: ingestion stops if it doesn't find exactly 113 articles, 180 recitals and 13 annexes.
- **Idempotent**: an unchanged source is skipped (SHA-256 hash); a changed one replaces old chunks.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for the local database and the integration tests)
- An OpenAI API key (only needed to run ingestion; tests don't need one)

## Run the tests

```bash
mvn verify        # unit tests + Testcontainers integration tests
```

## Ingest the AI Act locally

```bash
cp .env.example .env            # put your OPENAI_API_KEY in it
set -a; source .env; set +a

# Recommended: download once, then parse from disk while developing
mkdir -p data
curl -fsSL -o data/ai-act-en.html \
  "https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=OJ:L_202401689"
export INGESTION_LOCAL_FILE=data/ai-act-en.html

# Check the parser against the real file first (free, no API calls)
mvn test -Dtest=RegulationParserTest

# Then run ingestion (Spring Boot starts Postgres from compose.yaml automatically)
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest
```

Ingestion costs a few cents with `text-embedding-3-small`.

> **First thing to check:** the parser's selectors (top of `RegulationParser`) follow EUR-Lex's
> Official Journal HTML format and are tested against a hand-made fixture. If `realEurLexFile` fails on
> the downloaded file, adjust those constants — that's the one place EUR-Lex markup is encoded.

## CI/CD

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | every push and pull request | `mvn verify` with Testcontainers; on `main`, builds the Docker image and pushes it to GitHub Container Registry (`ghcr.io/<owner>/<repo>`) |
| `eurlex-check.yml` | weekly + manual | Downloads the real AI Act and checks the parser still finds every article, recital and annex |
| Dependabot | weekly/monthly | Update PRs for Maven, GitHub Actions and Docker base images |

Deployment to a server is planned for Phase 3, once hosting is chosen.

## Project structure

```
src/main/java/eu/aiact/assistant/
  AssistantApplication.java
  ingestion/
    EurLexFetcher.java           download or read the HTML
    RegulationParser.java        HTML → ParsedUnit (all EUR-Lex selectors live here)
    Chunker.java                 ParsedUnit → RegulationChunk (header, ID, metadata, splitting)
    ReferenceExtractor.java      "Article 6(2)", "Annex III" → ART-6, ANNEX-III
    IngestionService.java        fetch → parse → verify → chunk → embed + index
    IngestionRunner.java         `--ingest` command
    ChunkFtsRepository.java      Postgres full-text index
    SourceDocumentRepository.java
src/main/resources/
  application.yml
  db/migration/V1__init.sql      vector_store, source_document, chunk_fts
src/test/…                       unit tests, Testcontainers ITs, HTML fixture
```

## Roadmap

1. **Ingestion** ← you are here
2. Cited Q&A: hybrid retrieval, citation verification, streaming UI
3. Eval suite in CI (golden question set, retrieval recall, citation precision)
4. Risk classifier agent, French and German texts
5. Dashboard, public demo, write-up

## Source

Regulation (EU) 2024/1689 (Artificial Intelligence Act), from
[EUR-Lex](https://eur-lex.europa.eu/eli/reg/2024/1689/oj). EU legal texts may be reused under the
Commission's reuse policy.
