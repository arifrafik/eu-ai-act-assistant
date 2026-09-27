package eu.aiact.assistant.ingestion;

public record IngestionResult(boolean skipped, int articles, int recitals, int annexes, int chunks) {

    static IngestionResult skippedUnchanged() {
        return new IngestionResult(true, 0, 0, 0, 0);
    }
}
