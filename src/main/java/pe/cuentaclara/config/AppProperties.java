package pe.cuentaclara.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String databasePath,
        boolean secureCookies,
        boolean demo,
        boolean initialize,
        boolean closingEnabled,
        String adminUser,
        String adminPassword,
        String demoPassword) {}
