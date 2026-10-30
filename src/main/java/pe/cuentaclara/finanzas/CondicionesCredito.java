package pe.cuentaclara.finanzas;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.cuentaclara.clientes.Cliente;

import java.math.BigDecimal;

/** Tasas y calendario que quedan guardados con cada compra. */
public record CondicionesCredito(
        @JsonProperty("rate") BigDecimal tasa,
        @JsonProperty("rate_kind") String tipoTasa,
        @JsonProperty("rate_period") int periodoTasa,
        @JsonProperty("frequency") int frecuencia,
        @JsonProperty("late_rate") BigDecimal tasaMora,
        @JsonProperty("late_kind") String tipoTasaMora,
        @JsonProperty("late_period") int periodoTasaMora,
        @JsonProperty("late_frequency") int frecuenciaMora,
        @JsonProperty("max_months") int maximoMeses,
        @JsonProperty("cutoff_day") int diaCorte,
        @JsonProperty("cutoff_hour") int horaCorte,
        @JsonProperty("pay_day") int diaPago) {
    public static CondicionesCredito desde(Cliente cliente) {
        return new CondicionesCredito(
                cliente.getTasa(),
                cliente.getTipoTasa(),
                cliente.getPeriodoTasa(),
                cliente.getFrecuencia(),
                cliente.getTasaMora(),
                cliente.getTipoTasaMora(),
                cliente.getPeriodoTasaMora(),
                cliente.getFrecuenciaMora(),
                cliente.getMaximoMeses(),
                cliente.getDiaCorte(),
                cliente.getHoraCorte(),
                cliente.getDiaPago());
    }
}
