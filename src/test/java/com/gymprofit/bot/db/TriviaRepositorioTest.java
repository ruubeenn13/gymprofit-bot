package com.gymprofit.bot.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link TriviaRepositorio} contra MySQL real (Testcontainers): las 50 preguntas
 * sembradas en V2 permiten sacar preguntas al azar excluyendo las ya respondidas, {@code registrarRespuesta}
 * es one-shot (el {@code INSERT IGNORE} no sobrescribe un intento previo) y aciertos/ranking se derivan
 * solo de las respuestas correctas. Se salta si el cliente Docker no es alcanzable en local; corre en CI (Linux).
 */
class TriviaRepositorioTest {

    private static final long USER = 1601L;
    private static final long OTHER = 1602L;

    @Test
    @DisplayName("aleatoriaNoRespondida excluye las ya respondidas")
    void aleatoriaExcluye() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var repo = new TriviaRepositorio(db.dataSource());
                usuarios.obtenerOCrear(USER);

                assertTrue(repo.aleatoriaNoRespondida(USER, Optional.empty(), true).isPresent());
                long id = repo.aleatoriaNoRespondida(USER, Optional.empty(), true).get().id();
                repo.registrarRespuesta(USER, id, true);
                for (int i = 0; i < 20; i++) {
                    var p = repo.aleatoriaNoRespondida(USER, Optional.empty(), true);
                    if (p.isPresent()) assertNotEquals(id, p.get().id());
                }
            }
        }
    }

    @Test
    @DisplayName("registrarRespuesta es one-shot")
    void oneShot() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var repo = new TriviaRepositorio(db.dataSource());
                usuarios.obtenerOCrear(USER);

                long id = repo.aleatoriaNoRespondida(USER, Optional.empty(), true).get().id();
                assertTrue(repo.registrarRespuesta(USER, id, false));
                assertFalse(repo.registrarRespuesta(USER, id, true));
                assertEquals(0, repo.aciertosDe(USER));
            }
        }
    }

    @Test
    @DisplayName("aciertosDe y ranking")
    void aciertosYRanking() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var repo = new TriviaRepositorio(db.dataSource());
                usuarios.obtenerOCrear(USER);
                usuarios.obtenerOCrear(OTHER);

                long id1 = repo.aleatoriaNoRespondida(USER, Optional.empty(), true).get().id();
                repo.registrarRespuesta(USER, id1, true);
                long id2 = repo.aleatoriaNoRespondida(USER, Optional.empty(), true).get().id();
                repo.registrarRespuesta(USER, id2, true);

                assertEquals(2, repo.aciertosDe(USER));
                assertEquals(0, repo.aciertosDe(OTHER));

                var ranking = repo.ranking(10);
                assertTrue(ranking.stream().anyMatch(f -> f.discordId() == USER && f.aciertos() == 2));
            }
        }
    }

    @Test
    @DisplayName("filtro por categoria")
    void categoria() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var repo = new TriviaRepositorio(db.dataSource());
                usuarios.obtenerOCrear(USER);

                assertTrue(repo.aleatoriaNoRespondida(USER, Optional.of("FITNESS"), true).isPresent());
            }
        }
    }
}
