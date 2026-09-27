-- Extensions needed by Spring AI's PgVectorStore
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI vector store table (same shape PgVectorStore would create).
-- 1536 = text-embedding-3-small. Change together with spring.ai.vectorstore.pgvector.dimensions.
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- One row per ingested regulation + language; the hash lets re-runs skip unchanged sources.
CREATE TABLE source_document (
    id           bigserial PRIMARY KEY,
    regulation   text        NOT NULL,
    lang         text        NOT NULL,
    url          text        NOT NULL,
    content_hash text        NOT NULL,
    chunk_count  integer     NOT NULL,
    ingested_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (regulation, lang)
);

-- Keyword search side of hybrid retrieval ("Article 50", "Annex III").
-- 'simple' config: no stemming, works the same for every language.
CREATE TABLE chunk_fts (
    chunk_id   text PRIMARY KEY,
    regulation text NOT NULL,
    lang       text NOT NULL,
    content    text NOT NULL,
    tsv        tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED
);
CREATE INDEX chunk_fts_tsv_idx ON chunk_fts USING gin (tsv);
CREATE INDEX chunk_fts_reg_lang_idx ON chunk_fts (regulation, lang);
