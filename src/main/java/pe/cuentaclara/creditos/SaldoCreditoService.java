package pe.cuentaclara.creditos;

import org.springframework.stereotype.Service;

import pe.cuentaclara.finanzas.MotorFinancieroService;

import java.math.BigDecimal;

@Service
public class SaldoCreditoService {
    private final CuotaRepository cuotas;

    public SaldoCreditoService(CuotaRepository cuotas) {
        this.cuotas = cuotas;
    }

    public BigDecimal capitalPendiente(Long clienteId) {
        return MotorFinancieroService.redondearImporte(
                cuotas.findAllByVentaClienteIdAndPagoIsNull(clienteId).stream()
                        .map(CuotaEntity::getCapital)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
