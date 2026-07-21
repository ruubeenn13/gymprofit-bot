package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link ConfigServidorRepositorio} contra MySQL real (Testcontainers):
 * alta por defecto, upsert y lectura, incluidos los campos nulos. Se salta si el cliente Docker
 * no es alcanzable en local; corre en CI (Linux).
 */
class ConfigServidorRepositorioTest {

    @Test
    void creaPorDefectoYPersisteCambios() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();

            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                ConfigServidorRepositorio repo = new ConfigServidorRepositorio(db.dataSource());

                // Alta por defecto: idioma español y todo sin fijar.
                ConfigServidor nueva = repo.obtenerOCrear(42L);
                assertEquals("es", nueva.idioma());
                assertNull(nueva.canalBienvenida());
                assertNull(nueva.rolObjetivoFuerza());

                // Upsert de varios campos y relectura.
                repo.guardar(new ConfigServidor(42L, "en", 100L, null, null, null, null, null,
                        200L, null, null, null));
                ConfigServidor leida = repo.buscar(42L).orElseThrow();
                assertEquals("en", leida.idioma());
                assertEquals(100L, leida.canalBienvenida());
                assertEquals(200L, leida.rolObjetivoFuerza());
                assertNull(leida.canalLogros());

                // listarConEjercicioDia: solo salen los servidores con el canal fijado.
                assertEquals(0, repo.listarConEjercicioDia().size());
                repo.guardar(new ConfigServidor(42L, "en", 100L, 555L, null, null, null, null,
                        200L, null, null, null));
                assertEquals(1, repo.listarConEjercicioDia().size());
                assertEquals(555L, repo.listarConEjercicioDia().get(0).canalEjercicioDia());
            }
        }
    }
}
