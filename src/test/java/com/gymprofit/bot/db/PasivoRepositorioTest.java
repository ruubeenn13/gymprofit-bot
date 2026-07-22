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
 * ranuras (y que el intento fallido no deje rastro) y el borrado en cascada (requisito RGPD).
 * También el <b>segundo pase</b> de energía ({@code PersonajeRepositorio#regenerarEnergiaPasivos}),
 * cuya SQL generada solo se puede validar contra MySQL de verdad. Se salta si el cliente Docker no
 * es alcanzable en local; corre en CI (Linux).
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

    @Test
    void segundoPaseDeEnergiaSoloAQuienTieneElItemYNoDuerme() {
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
                var personajes = new PersonajeRepositorio(db.dataSource());
                var inventario = new InventarioRepositorio(db.dataSource());
                var pasivos = new PasivoRepositorio(db.dataSource());
                var descanso = new DescansoRepositorio(db.dataSource());

                // A: yate equipado y en el inventario → +3.
                // B: yate equipado pero VENDIDO → nada (la regla del inventario, aplicada en SQL).
                // C: yate equipado y en inventario, pero DORMIDO → nada (ya cobra al despertar).
                for (long id : new long[]{101L, 102L, 103L}) {
                    usuarios.obtenerOCrear(id);
                    personajes.obtenerOCrear(id);
                    descanso.obtenerOCrear(id);
                    pasivos.equipar(id, 1, "yate");
                }
                inventario.anadir(101L, "yate", 1);
                inventario.anadir(103L, "yate", 1);
                descanso.acostar(103L, java.time.Instant.now(), null);

                // Se les baja la energía para que el UPDATE (energia < 100) los alcance.
                personajes.gastarEnergia(101L, 50);
                personajes.gastarEnergia(102L, 50);
                personajes.gastarEnergia(103L, 50);
                int antesA = personajes.obtenerOCrear(101L).energia();
                int antesB = personajes.obtenerOCrear(102L).energia();
                int antesC = personajes.obtenerOCrear(103L).energia();

                int afectados = personajes.regenerarEnergiaPasivos(
                        com.gymprofit.bot.services.Pasivos.fuentesDe(
                                com.gymprofit.bot.services.Pasivos.Tipo.ENERGIA_REGEN),
                        5);

                assertEquals(1, afectados, "solo A cumple las tres condiciones");
                assertEquals(antesA + 3, personajes.obtenerOCrear(101L).energia(), "el yate da +3");
                assertEquals(antesB, personajes.obtenerOCrear(102L).energia(),
                        "vendió el yate: la ranura sigue ahí pero no cuenta");
                assertEquals(antesC, personajes.obtenerOCrear(103L).energia(),
                        "está dormido: cobrará al despertar, no por goteo");
            }
        }
    }
}
