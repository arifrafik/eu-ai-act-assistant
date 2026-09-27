package eu.aiact.assistant.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs ingestion when the app is started with {@code --ingest}, then exits.
 * Example: {@code mvn spring-boot:run -Dspring-boot.run.arguments=--ingest}
 */
@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final IngestionService service;
    private final ApplicationContext context;

    public IngestionRunner(IngestionService service, ApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ingest")) {
            return;
        }
        IngestionResult result = service.ingest();
        log.info("Result: {}", result);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
