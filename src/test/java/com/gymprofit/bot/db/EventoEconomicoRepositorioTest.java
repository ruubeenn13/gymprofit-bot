package com.gymprofit.bot.db;

import com.gymprofit.bot.services.EventoEconomico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link EventoEconomicoRepositorio} contra MySQL real (Testcontainers): la tabla
 * {@code evento_economico} arranca vacía (sin evento, estado neutro), {@code fijar} persiste tipo y
 * ventana, un segundo {@code fijar} sobrescribe la fila única (no duplica) y {@code limpiar} vuelve a
 * dejarla vacía. Se salta si el cliente Docker no es alcanzable en local; corre en CI (Linux).
 */
class EventoEconomicoRepositorioTest {

    @Test
    @DisplayName("sin evento fijado, activo() es vacío")
    void vacioAlPrincipio() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var repo = new EventoEconomicoRepositorio(db.dataSource());

                assertTrue(repo.activo().isEmpty());
            }
        }
    }

    @Test
    @DisplayName("fijar persiste tipo y ventana; activo() lo devuelve")
    void fijarYLeer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var repo = new EventoEconomicoRepositorio(db.dataSource());

                Instant ini = Instant.parse("2026-07-27T10:00:00Z");
                Instant fin = ini.plusSeconds(86_400);
                repo.fijar(EventoEconomico.RECESION, ini, fin);
                EventoActivo a = repo.activo().orElseThrow();
                assertEquals(EventoEconomico.RECESION, a.tipo());
                assertEquals(fin, a.fin());
            }
        }
    }

    @Test
    @DisplayName("fijar de nuevo sobrescribe (fila única, no duplica)")
    void fijarUpsert() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var repo = new EventoEconomicoRepositorio(db.dataSource());

                Instant ini = Instant.parse("2026-07-27T10:00:00Z");
                repo.fijar(EventoEconomico.RECESION, ini, ini.plusSeconds(10));
                repo.fijar(EventoEconomico.BOOM_CONSUMO, ini, ini.plusSeconds(20));
                assertEquals(EventoEconomico.BOOM_CONSUMO, repo.activo().orElseThrow().tipo());
            }
        }
    }

    @Test
    @DisplayName("limpiar deja activo() vacío")
    void limpiar() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var repo = new EventoEconomicoRepositorio(db.dataSource());

                Instant ini = Instant.parse("2026-07-27T10:00:00Z");
                repo.fijar(EventoEconomico.BOOM_CONSUMO, ini, ini.plusSeconds(10));
                repo.limpiar();
                assertTrue(repo.activo().isEmpty());
            }
        }
    }
}
