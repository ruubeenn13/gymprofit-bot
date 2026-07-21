package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link PasivoRepositorio} contra MySQL real (Testcontainers): equipar,
 * reemplazar el contenido de una ranura, quitar, el {@code UNIQUE} que impide el mismo ítem en dos
 * ranuras (y que el intento fallido no deje rastro) y el borrado en cascada (requisito RGPD). Se
 * salta si el cliente Docker no es alcanzable en local; corre en CI (Linux).
 */
class PasivoRepositorioTest {

    @Test
    void equiparReemplazarQuitarYCascada() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();

            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                UsuarioDiscordRepositorio usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                PasivoRepositorio repo = new PasivoRepositorio(db.dataSource());

                // La FK exige que el usuario exista antes.
                usuarios.obtenerOCrear(77L);

                // Arranca sin nada equipado.
                assertEquals(Map.of(), repo.equipados(77L));

                // Equipar dos ranuras.
                repo.equipar(77L, 1, "jet");
                repo.equipar(77L, 2, "dron");
                assertEquals(Map.of(1, "jet", 2, "dron"), repo.equipados(77L));
                assertEquals(1, repo.ranuraDe(77L, "jet").orElseThrow());
                assertTrue(repo.ranuraDe(77L, "yate").isEmpty());

                // Reemplazo en la misma ranura: no duplica filas.
                repo.equipar(77L, 1, "yate");
                assertEquals(Map.of(1, "yate", 2, "dron"), repo.equipados(77L));

                // El mismo ítem en dos ranuras lo rechaza el esquema, no el service.
                assertThrows(PasivoRepositorio.ItemYaEquipadoException.class,
                        () -> repo.equipar(77L, 3, "dron"));
                // Y el intento fallido no deja la ranura 3 a medias ni toca la 2 (rollback).
                assertEquals(Map.of(1, "yate", 2, "dron"), repo.equipados(77L));

                // Reequipar el mismo ítem en su propia ranura es idempotente, no un choque.
                repo.equipar(77L, 2, "dron");
                assertEquals(Map.of(1, "yate", 2, "dron"), repo.equipados(77L));

                // Quitar: la primera vez borra, la segunda ya no encuentra nada.
                assertTrue(repo.quitar(77L, 2));
                assertFalse(repo.quitar(77L, 2));
                assertEquals(Map.of(1, "yate"), repo.equipados(77L));

                // RGPD: borrar el usuario se lleva sus ranuras por ON DELETE CASCADE.
                usuarios.borrar(77L);
                assertEquals(Map.of(), repo.equipados(77L));
            }
        }
    }
}
