package pe.cuentaclara.privacidad;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.cuentaclara.catalogo.ImagenProductoService;

@Component
@ConditionalOnProperty(name = "app.retention-enabled", havingValue = "true", matchIfMissing = true)
public class ConservacionDatosJob {
    private final PrivacidadService privacidad;
    private final ImagenProductoService imagenes;

    public ConservacionDatosJob(PrivacidadService privacidad, ImagenProductoService imagenes) {
        this.privacidad = privacidad;
        this.imagenes = imagenes;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 3600000)
    public void procesar() {
        privacidad.procesarProgramados();
        imagenes.limpiarHuerfanas();
    }
}
