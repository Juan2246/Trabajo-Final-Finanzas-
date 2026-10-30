package pe.cuentaclara.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SeguridadProperties(String encryptionKey) {}
