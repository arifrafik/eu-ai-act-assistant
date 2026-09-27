package eu.aiact.assistant.ingestion;

import java.util.Map;

/**
 * A chunk ready to embed and index.
 *
 * @param chunkId  stable, human-readable ID the LLM cites, e.g. "AIA-ART-6-2-EN"
 * @param content  context header + text, so the chunk reads on its own
 * @param metadata stored with the vector (regulation, type, number, url, refs...)
 */
public record RegulationChunk(String chunkId, String content, Map<String, Object> metadata) {
}
