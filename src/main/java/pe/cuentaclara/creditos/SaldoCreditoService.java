package pe.cuentaclara.creditos;

import org.springframework.stereotype.Service;

import pe.cuentaclara.finanzas.MotorFinanciero;

import java.math.BigDecimal;

@Service
public class SaldoCreditoService {
    private final CuotaRepository cuotas;

    public SaldoCreditoService(CuotaRepository cuotas) {
        this.cuotas = cuotas;
    }

    public BigDecimal capitalPendiente(Long clienteId) {
        return MotorFinanciero.redondearImporte(
                cuotas.findAllByVentaClienteIdAndPagoIsNull(clienteId).stream()
                        .map(Cuota::getCapital)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
