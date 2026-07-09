package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de extremo a extremo de la capa {@code db/}: levanta MySQL real (Testcontainers),
 * aplica las migraciones vía {@link Database} y ejercita {@link UsuarioDiscordRepositorio}
 * (crear por defecto, actualizar y buscar inexistente). Igual que {@code MigracionesTest},
 * se salta si el cliente Docker no es alcanzable en local; corre de verdad en CI (Linux).
 */
class UsuarioDiscordRepositorioTest {

    @Test
    void creaActualizaYBuscaUsuarios() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();

            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                UsuarioDiscordRepositorio repo = new UsuarioDiscordRepositorio(db.dataSource());

                // Alta con valores por defecto.
                UsuarioDiscord nuevo = repo.obtenerOCrear(1001L);
                assertEquals(0, nuevo.xp());
                assertEquals(0, nuevo.nivel());
                assertEquals("es", nuevo.idioma());
                assertFalse(nuevo.optOutLogros());

                // Idempotencia: volver a crearlo no duplica ni resetea.
                repo.obtenerOCrear(1001L);

                // Actualización (upsert) de todos los campos.
                repo.guardar(new UsuarioDiscord(
                        1001L, 150, 3, 40, 5, LocalDate.of(2026, 7, 9), "en", true));
                UsuarioDiscord actualizado = repo.buscar(1001L).orElseThrow();
                assertEquals(150, actualizado.xp());
                assertEquals(3, actualizado.nivel());
                assertEquals(40, actualizado.coins());
                assertEquals(5, actualizado.racha());
                assertEquals(LocalDate.of(2026, 7, 9), actualizado.ultimaRachaFecha());
                assertEquals("en", actualizado.idioma());
                assertTrue(actualizado.optOutLogros());

                // Un usuario inexistente no aparece.
                assertTrue(repo.buscar(9999L).isEmpty());

                // Leaderboard: ordena por XP descendente.
                repo.guardar(new UsuarioDiscord(1002L, 300, 2, 0, 0, null, "es", false));
                repo.guardar(new UsuarioDiscord(1003L, 50, 0, 0, 0, null, "es", false));
                var top = repo.listarTopPorXp(10);
                assertEquals(3, top.size());
                assertEquals(1002L, top.get(0).discordId(), "El de más XP va primero");
                assertEquals(1001L, top.get(1).discordId());
                assertEquals(1003L, top.get(2).discordId());
                assertTrue(repo.listarTopPorXp(2).size() == 2, "Respeta el límite");
            }
        }
    }
}
