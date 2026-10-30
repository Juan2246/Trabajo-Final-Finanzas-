package pe.cuentaclara.comun;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class RelojNegocio {
    private final Clock clock;

    public RelojNegocio(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock).withNano(0);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    public long segundosEpoch() {
        return clock.instant().getEpochSecond();
    }
}
