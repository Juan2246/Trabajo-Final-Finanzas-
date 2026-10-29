package pe.cuentaclara.shared;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class BusinessTime {
    private final Clock clock;

    public BusinessTime(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public long epochSeconds() {
        return clock.instant().getEpochSecond();
    }
}
