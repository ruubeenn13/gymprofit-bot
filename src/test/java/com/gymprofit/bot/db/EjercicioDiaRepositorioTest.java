package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba {@link EjercicioDiaRepositorio} contra MySQL real (V24): inserción idempotente por
 * fecha (PK), lectura por fecha, ronda actual e ids usados por ronda. Se salta sin Docker.
 */
class EjercicioDiaRepositorioTest {

    @Test
    void insercionIdempotenteYConsultasDeRonda() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                EjercicioDiaRepositorio repo = new EjercicioDiaRepositorio(db.dataSource());
                LocalDate hoy = LocalDate.of(2026, 7, 20);

                assertEquals(1, repo.rondaActual()); // sin filas: primera ronda

                assertTrue(repo.insertar(new EjercicioDia(hoy, 7, 1)));
                assertFalse(repo.insertar(new EjercicioDia(hoy, 99, 1))); // PK: no pisa
                assertEquals(7, repo.buscarPorFecha(hoy).orElseThrow().ejercicioId());

                repo.insertar(new EjercicioDia(hoy.plusDays(1), 8, 1));
                repo.insertar(new EjercicioDia(hoy.plusDays(2), 7, 2)); // ya en ronda 2
                assertEquals(Set.of(7, 8), repo.idsDeRonda(1));
                assertEquals(Set.of(7), repo.idsDeRonda(2));
                assertEquals(2, repo.rondaActual());
            }
        }
    }
}
