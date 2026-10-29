package pe.cuentaclara.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.auth.*;
import pe.cuentaclara.catalog.*;
import pe.cuentaclara.config.AppProperties;
import pe.cuentaclara.credit.*;
import pe.cuentaclara.customer.*;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.store.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final AppProperties properties;
    private final UserAccountRepository users;
    private final AccountService accounts;
    private final StoreService stores;
    private final CustomerService customers;
    private final ProductService products;
    private final CreditService credits;
    private final BusinessTime time;

    public DatabaseInitializer(
            AppProperties properties,
            UserAccountRepository users,
            AccountService accounts,
            StoreService stores,
            CustomerService customers,
            ProductService products,
            CreditService credits,
            BusinessTime time) {
        this.properties = properties;
        this.users = users;
        this.accounts = accounts;
        this.stores = stores;
        this.customers = customers;
        this.products = products;
        this.credits = credits;
        this.time = time;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.initialize() || users.count() > 0) return;
        String generated = "Demo" + RandomTokens.token(14) + "7";
        String adminPassword =
                properties.adminPassword().isBlank() ? generated : properties.adminPassword();
        UserAccount admin =
                accounts.create(
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
        CurrentUser actor =
                new CurrentUser(
                        admin.getId(),
                        admin.getUsername(),
                        admin.getName(),
                        "platform",
                        null,
                        null,
                        "",
                        "");
        Long storeId =
                stores.create(
                        actor,
                        new StoreRequest(
                                "Bodega La Esquina",
                                "Bodega de barrio",
                                "Av. Los Olivos 245, Lima",
                                "Juan Torres",
                                "tienda",
                                password,
                                true));
        Long customerId =
                customers.create(
                        actor,
                        storeId,
                        new CustomerRequest(
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
        Long rice =
                products.create(
                        actor,
                        storeId,
                        new ProductRequest(
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
        Long oil =
                products.create(
                        actor,
                        storeId,
                        new ProductRequest(
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
        products.create(
                actor,
                storeId,
                new ProductRequest(
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
        products.create(
                actor,
                storeId,
                new ProductRequest(
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
        credits.create(
                actor,
                storeId,
                new SaleRequest(
                        customerId,
                        time.now().minusDays(70).withHour(10).withMinute(0),
                        "end",
                        1,
                        List.of(
                                new SaleRequest.Line(rice, BigDecimal.TEN),
                                new SaleRequest.Line(oil, new BigDecimal("2"))),
                        null));
        credits.create(
                actor,
                storeId,
                new SaleRequest(
                        customerId,
                        time.now().minusDays(10),
                        "installments",
                        3,
                        List.of(new SaleRequest.Line(rice, new BigDecimal("30"))),
                        null));
        if (properties.demoPassword().isBlank())
            log.warn(
                    "Datos ficticios creados. Usuarios tienda y cliente; clave inicial: {}",
                    password);
    }
}
