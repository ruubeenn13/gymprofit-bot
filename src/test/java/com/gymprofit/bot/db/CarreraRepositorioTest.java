package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link CarreraRepositorio} contra MySQL real (Testcontainers): sin fila el
 * tier es 0, el {@code GREATEST} del upsert impide que un tier alcanzado baje, las ramas son
 * independientes entre sí y el borrado del usuario arrastra sus carreras por {@code ON DELETE
 * CASCADE} (requisito RGPD). Se salta si el cliente Docker no es alcanzable en local; corre en CI
 * (Linux).
 */
class CarreraRepositorioTest {

    @Test
    void tierAlcanzadoNuncaBajaYElBorradoArrastra() {
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
                var carreras = new CarreraRepositorio(db.dataSource());

                usuarios.obtenerOCrear(201L);
                assertEquals(0, carreras.tierAlcanzado(201L, "SALUD"), "sin fila = 0");

                carreras.fijarTier(201L, "SALUD", 3);
                assertEquals(3, carreras.tierAlcanzado(201L, "SALUD"));
                // GREATEST: reponer un tier menor no degrada la carrera.
                carreras.fijarTier(201L, "SALUD", 2);
                assertEquals(3, carreras.tierAlcanzado(201L, "SALUD"),
                        "el tier alcanzado nunca baja");
                // Ramas independientes.
                assertEquals(0, carreras.tierAlcanzado(201L, "ARTE"));

                // RGPD: borrar el usuario arrastra sus carreras (FK CASCADE).
                usuarios.borrar(201L);
                assertEquals(0, carreras.tierAlcanzado(201L, "SALUD"));
            }
        }
    }
}
