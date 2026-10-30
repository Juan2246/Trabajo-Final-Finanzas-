package pe.cuentaclara.resumen;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.cuentaclara.acceso.UsuarioSalidaDTO;
import pe.cuentaclara.auditoria.MovimientoSalidaDTO;
import pe.cuentaclara.catalogo.ProductoSalidaDTO;
import pe.cuentaclara.clientes.ClienteSalidaDTO;
import pe.cuentaclara.creditos.CuotaSalidaDTO;
import pe.cuentaclara.creditos.VentaSalidaDTO;
import pe.cuentaclara.pagos.PagoSalidaDTO;
import pe.cuentaclara.tiendas.TiendaSalidaDTO;

import java.time.LocalDate;
import java.util.List;

public record ResumenSalidaDTO(
        @JsonProperty("user") UsuarioSalidaDTO usuario,
        @JsonProperty("store") TiendaSalidaDTO tienda,
        @JsonProperty("stores") List<TiendaSalidaDTO> tiendas,
        @JsonProperty("customers") List<ClienteSalidaDTO> clientes,
        @JsonProperty("products") List<ProductoSalidaDTO> productos,
        @JsonProperty("sales") List<VentaSalidaDTO> ventas,
        @JsonProperty("dues") List<CuotaSalidaDTO> cuotas,
        @JsonProperty("payments") List<PagoSalidaDTO> pagos,
        @JsonProperty("audit") List<MovimientoSalidaDTO> auditoria,
        @JsonProperty("today") LocalDate hoy) {}
