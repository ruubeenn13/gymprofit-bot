package com.gymprofit.bot.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link EmpresaAccionRepositorio} contra MySQL real (Testcontainers): el upsert de
 * {@code fijar} suma bien al pool ({@code vendidasDe}), borra la fila al llegar a 0, y la FK a
 * {@code empresas} con ON DELETE CASCADE arrastra las participaciones al disolver la empresa. Siembra
 * {@code usuarios_discord} y una {@code empresas} antes de insertar filas con FK a esos ids (RGPD/F5).
 * Se salta si el cliente Docker no es alcanzable en local; corre en CI (Linux).
 */
class EmpresaAccionRepositorioTest {

    @Test
    @DisplayName("sin filas, participaciones y vendidas son 0")
    void vacio() {
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
                var empresas = new EmpresaRepositorio(db.dataSource());
                var acciones = new EmpresaAccionRepositorio(db.dataSource());

                long dueno = 1201L;
                long userId = 1202L;
                usuarios.obtenerOCrear(dueno);
                usuarios.obtenerOCrear(userId);
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Accionista");

                assertEquals(0, acciones.participacionesDe(empresaId, userId));
                assertEquals(0, acciones.vendidasDe(empresaId));
            }
        }
    }

    @Test
    @DisplayName("fijar upsert + vendidasDe suma el pool")
    void fijarYSuma() {
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
                var empresas = new EmpresaRepositorio(db.dataSource());
                var acciones = new EmpresaAccionRepositorio(db.dataSource());

                long dueno = 1301L;
                long userId = 1302L;
                long otherId = 1303L;
                usuarios.obtenerOCrear(dueno);
                usuarios.obtenerOCrear(userId);
                usuarios.obtenerOCrear(otherId);
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Bursátil");

                acciones.fijar(empresaId, userId, 30);
                acciones.fijar(empresaId, otherId, 20);
                assertEquals(30, acciones.participacionesDe(empresaId, userId));
                assertEquals(50, acciones.vendidasDe(empresaId));

                // fijar de nuevo sobre el mismo par (empresa, jugador) es upsert: sustituye, no suma.
                acciones.fijar(empresaId, userId, 40);
                assertEquals(60, acciones.vendidasDe(empresaId));
            }
        }
    }

    @Test
    @DisplayName("fijar a 0 borra la fila")
    void borra() {
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
                var empresas = new EmpresaRepositorio(db.dataSource());
                var acciones = new EmpresaAccionRepositorio(db.dataSource());

                long dueno = 1401L;
                long userId = 1402L;
                usuarios.obtenerOCrear(dueno);
                usuarios.obtenerOCrear(userId);
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Volátil");

                acciones.fijar(empresaId, userId, 10);
                acciones.fijar(empresaId, userId, 0);
                assertEquals(0, acciones.participacionesDe(empresaId, userId));
            }
        }
    }

    @Test
    @DisplayName("borrar la empresa (CASCADE) borra las participaciones")
    void cascade() {
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
                var empresas = new EmpresaRepositorio(db.dataSource());
                var acciones = new EmpresaAccionRepositorio(db.dataSource());

                long dueno = 1501L;
                long userId = 1502L;
                usuarios.obtenerOCrear(dueno);
                usuarios.obtenerOCrear(userId);
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Efímero");

                acciones.fijar(empresaId, userId, 10);
                // disolver la empresa dispara el ON DELETE CASCADE de fk_acc_empresa.
                empresas.disolver(empresaId);
                assertEquals(0, acciones.vendidasDe(empresaId));
            }
        }
    }
}
