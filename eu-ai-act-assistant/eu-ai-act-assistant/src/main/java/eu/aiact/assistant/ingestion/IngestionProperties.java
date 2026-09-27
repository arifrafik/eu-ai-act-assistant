package eu.aiact.assistant.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code ingestion.*} in application.yml. */
@ConfigurationProperties("ingestion")
public record IngestionProperties(
        String regulation,
        String lang,
        String sourceUrl,
        String localFile,
        int maxChunkTokens,
        int embedBatchSize,
        Expected expected) {

    /** Minimum counts the parser must find; 0 disables a check. */
    public record Expected(int articles, int recitals, int annexes) {
    }

    public boolean hasLocalFile() {
        return localFile != null && !localFile.isBlank();
    }
}
