package pe.cuentaclara.estados;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.cuentaclara.config.AppProperties;

@Component
public class CierreEstadosJob {
    private static final Logger log = LoggerFactory.getLogger(CierreEstadosJob.class);
    private final EstadoCuentaService estados;
    private final AppProperties properties;

    public CierreEstadosJob(EstadoCuentaService estados, AppProperties properties) {
        this.estados = estados;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cerrarPendientes() {
        if (!properties.closingEnabled()) return;
        try {
            int creados = estados.cerrarPendientes();
            if (creados > 0) log.info("Se generaron {} cierres de cuenta", creados);
        } catch (Exception error) {
            log.error("No se pudieron generar los cierres pendientes", error);
        }
    }
}
