package pe.cuentaclara.inicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.*;
import pe.cuentaclara.catalogo.*;
import pe.cuentaclara.clientes.*;
import pe.cuentaclara.comun.RelojNegocio;
import pe.cuentaclara.config.AppProperties;
import pe.cuentaclara.creditos.*;
import pe.cuentaclara.tiendas.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InicializadorDatos implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InicializadorDatos.class);
    private final AppProperties properties;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarios;
    private final TiendaService tiendas;
    private final ClienteService clientes;
    private final ProductoService productos;
    private final CreditoService creditos;
    private final RelojNegocio reloj;

    public InicializadorDatos(
            AppProperties properties,
            UsuarioRepository usuarioRepository,
            UsuarioService usuarios,
            TiendaService tiendas,
            ClienteService clientes,
            ProductoService productos,
            CreditoService creditos,
            RelojNegocio reloj) {
        this.properties = properties;
        this.usuarioRepository = usuarioRepository;
        this.usuarios = usuarios;
        this.tiendas = tiendas;
        this.clientes = clientes;
        this.productos = productos;
        this.creditos = creditos;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.initialize() || usuarioRepository.count() > 0) return;
        String generated = "Demo" + TokensAleatorios.token(14) + "7";
        String adminPassword =
                properties.adminPassword().isBlank() ? generated : properties.adminPassword();
        Usuario admin =
                usuarios.crear(
                        properties.adminUser(),
                        "Administrador",
                        adminPassword,
                        "platform",
                        null,
                        null);
        if (properties.adminPassword().isBlank())
            log.warn(
                    "Clave inicial de {}: {}. Guárdala; solo se muestra al crear la base.",
                    properties.adminUser(),
                    adminPassword);
        if (!properties.demo()) return;
        String password =
                properties.demoPassword().isBlank() ? generated : properties.demoPassword();
        UsuarioActual usuarioActual =
                new UsuarioActual(
                        admin.getId(),
                        admin.getUsername(),
                        admin.getNombre(),
                        "platform",
                        null,
                        null,
                        "",
                        "");
        Long tiendaId =
                tiendas.crear(
                        usuarioActual,
                        new TiendaEntradaDTO(
                                "Bodega La Esquina",
                                "Bodega de barrio",
                                "Av. Los Olivos 245, Lima",
                                "Juan Torres",
                                "tienda",
                                password,
                                true));
        Long clienteId =
                clientes.crear(
                        usuarioActual,
                        tiendaId,
                        new ClienteEntradaDTO(
                                "María Flores",
                                "00000001",
                                "maria@example.com",
                                "999000111",
                                "PEN",
                                new BigDecimal("2500"),
                                new BigDecimal("24"),
                                "effective",
                                360,
                                12,
                                new BigDecimal("36"),
                                "effective",
                                360,
                                12,
                                12,
                                20,
                                23,
                                26,
                                true,
                                "cliente",
                                password,
                                true));
        Long arroz =
                productos.crear(
                        usuarioActual,
                        tiendaId,
                        new ProductoEntradaDTO(
                                "Arroz extra",
                                "",
                                "",
                                "",
                                "kg",
                                "",
                                new BigDecimal("4.50"),
                                new BigDecimal("4.80"),
                                "PEN",
                                true,
                                true));
        Long aceite =
                productos.crear(
                        usuarioActual,
                        tiendaId,
                        new ProductoEntradaDTO(
                                "Aceite vegetal",
                                "",
                                "",
                                "",
                                "litro",
                                "",
                                new BigDecimal("9.00"),
                                new BigDecimal("9.50"),
                                "PEN",
                                true,
                                true));
        productos.crear(
                usuarioActual,
                tiendaId,
                new ProductoEntradaDTO(
                        "Leche evaporada",
                        "",
                        "",
                        "",
                        "lata",
                        "",
                        new BigDecimal("4.00"),
                        new BigDecimal("4.30"),
                        "PEN",
                        true,
                        true));
        productos.crear(
                usuarioActual,
                tiendaId,
                new ProductoEntradaDTO(
                        "Servicio de reparto",
                        "",
                        "",
                        "",
                        "servicio",
                        "",
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        "PEN",
                        true,
                        true));
        creditos.crear(
                usuarioActual,
                tiendaId,
                new VentaEntradaDTO(
                        clienteId,
                        reloj.ahora().minusDays(70).withHour(10).withMinute(0),
                        "end",
                        1,
                        List.of(
                                new VentaEntradaDTO.LineaVentaDTO(arroz, BigDecimal.TEN),
                                new VentaEntradaDTO.LineaVentaDTO(aceite, new BigDecimal("2"))),
                        null));
        creditos.crear(
                usuarioActual,
                tiendaId,
                new VentaEntradaDTO(
                        clienteId,
                        reloj.ahora().minusDays(10),
                        "installments",
                        3,
                        List.of(new VentaEntradaDTO.LineaVentaDTO(arroz, new BigDecimal("30"))),
                        null));
        if (properties.demoPassword().isBlank())
            log.warn(
                    "Datos ficticios creados. Usuarios tienda y cliente; clave inicial: {}",
                    password);
    }
}
