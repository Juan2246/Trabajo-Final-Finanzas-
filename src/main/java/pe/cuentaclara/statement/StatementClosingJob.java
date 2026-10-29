package pe.cuentaclara.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.cuentaclara.config.AppProperties;

@Component
public class StatementClosingJob {
    private static final Logger log = LoggerFactory.getLogger(StatementClosingJob.class);
    private final StatementService statements;
    private final AppProperties properties;

    public StatementClosingJob(StatementService statements, AppProperties properties) {
        this.statements = statements;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void close() {
        if (!properties.closingEnabled()) return;
        try {
            int created = statements.closeAll();
            if (created > 0) log.info("Se generaron {} cierres de cuenta", created);
        } catch (Exception error) {
            log.error("No se pudieron generar los cierres pendientes", error);
        }
    }
}
