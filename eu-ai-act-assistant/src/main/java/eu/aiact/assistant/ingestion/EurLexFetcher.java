package eu.aiact.assistant.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Loads the regulation HTML from a local file (if configured) or from EUR-Lex. */
@Component
public class EurLexFetcher {

    private static final Logger log = LoggerFactory.getLogger(EurLexFetcher.class);

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "eu-ai-act-assistant/0.1")
            .build();

    public String fetch(IngestionProperties props) {
        if (props.hasLocalFile()) {
            Path path = Path.of(props.localFile());
            log.info("Reading regulation from local file {}", path.toAbsolutePath());
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IngestionException("Cannot read " + path, e);
            }
        }
        log.info("Downloading regulation from {}", props.sourceUrl());
        String html = restClient.get().uri(props.sourceUrl()).retrieve().body(String.class);
        if (html == null || html.isBlank()) {
            throw new IngestionException("Empty response from " + props.sourceUrl());
        }
        return html;
    }
}
