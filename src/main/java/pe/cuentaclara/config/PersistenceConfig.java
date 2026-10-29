package pe.cuentaclara.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

@Configuration
public class PersistenceConfig {
    @Bean
    public DataSource dataSource(AppProperties properties) throws Exception {
        Path database = Path.of(properties.databasePath()).toAbsolutePath();
        Files.createDirectories(database.getParent());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + database);
        config.setDriverClassName("org.sqlite.JDBC");
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("busy_timeout", "15000");
        config.addDataSourceProperty("journal_mode", "WAL");
        // SQLite admite un escritor. La conexión única serializa las transacciones completas.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        HikariDataSource source = new HikariDataSource(config);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(source);
        return source;
    }
}
