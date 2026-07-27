package com.gymprofit.bot.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Acceso a la BD del bot (Aiven MySQL, database {@code gymprofit_bot}): mantiene el pool de
 * conexiones {@link HikariDataSource} y ejecuta las migraciones Flyway al arrancar.
 *
 * <p>El {@link DataSource} que expone es el único que deben usar los repositorios JDBC de
 * {@code db/}. El pool se mantiene pequeño a propósito: los planes free de Aiven/Render
 * ofrecen pocas conexiones concurrentes.</p>
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final HikariDataSource dataSource;

    /**
     * Crea el pool de conexiones. No abre conexiones aún (Hikari es perezoso); la primera se
     * abre al migrar o al pedir una conexión.
     *
     * @param url      URL JDBC (nunca se loggea completa por si lleva credenciales)
     * @param user     usuario de la BD
     * @param password contraseña de la BD (nunca se loggea)
     */
    public Database(String url, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setPoolName("gymprobot-pool");
        // Pool reducido: los planes free de Aiven/Render limitan conexiones concurrentes.
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        // Config resiliente para MySQL en la nube (Aiven): sin keepalive, la conexión ociosa que guarda
        // el pool la cierra el servidor/la red por inactividad y luego se repartía muerta
        // («No operations allowed after connection closed»), y abrir una nueva SSL fría no cabía en 10 s.
        cfg.setConnectionTimeout(30_000);   // tolera una conexión SSL fría a Aiven (10 s era agresivo)
        cfg.setKeepaliveTime(60_000);       // pinga las conexiones ociosas: las mantiene vivas y descarta las muertas
        cfg.setMaxLifetime(600_000);        // recicla cada 10 min, por debajo del wait_timeout del servidor
        cfg.setValidationTimeout(5_000);
        this.dataSource = new HikariDataSource(cfg);
    }

    /**
     * Aplica las migraciones Flyway pendientes (carpeta {@code db/migration}). Idempotente:
     * si el esquema ya está al día no hace nada.
     */
    public void migrar() {
        MigrateResult resultado = Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
        log.info("Flyway: {} migración(es) aplicada(s); esquema en versión {}.",
                resultado.migrationsExecuted, resultado.targetSchemaVersion);
    }

    /** El pool de conexiones para los repositorios JDBC. */
    public DataSource dataSource() {
        return dataSource;
    }

    /** Cierra el pool de conexiones. */
    @Override
    public void close() {
        dataSource.close();
    }
}
