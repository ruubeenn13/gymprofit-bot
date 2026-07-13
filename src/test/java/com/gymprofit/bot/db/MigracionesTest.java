package com.gymprofit.bot.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba que las migraciones Flyway aplican limpiamente sobre el <b>mismo motor que
 * producción</b> (MySQL, vía Testcontainers) y que los seeds obligatorios de la Fase 1
 * cumplen los mínimos de la SPEC §10 (≥50 preguntas de trivia, ≥30 frases). Usar H2 u otro
 * sustituto ocultaría diferencias de dialecto que sí fallarían en Aiven.
 *
 * <p>El contenedor se arranca <b>manualmente</b> (no con {@code @Testcontainers}) tras
 * comprobar que Docker es alcanzable: así, en entornos donde el cliente Java no puede hablar
 * con el daemon (p. ej. el transporte npipe de Docker Desktop en algunas versiones de
 * Windows), el test se <b>salta</b> en vez de romper el build. En CI (Linux) Docker es nativo
 * y el test se ejecuta de verdad.</p>
 */
class MigracionesTest {

    @Test
    void migracionesAplicanYSeedsCumplenMinimos() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();

            MigrateResult resultado = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .load()
                    .migrate();

            // V1..V9 al menos; sin errores.
            assertTrue(resultado.migrationsExecuted >= 9,
                    "Deben aplicarse al menos V1..V9");
            assertTrue(resultado.success, "La migración debe terminar con éxito");

            try (Connection con = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement st = con.createStatement()) {

                assertTrue(contar(st, "SELECT COUNT(*) FROM trivia_preguntas") >= 50,
                        "La SPEC §10 exige ≥50 preguntas de trivia");
                assertTrue(contar(st, "SELECT COUNT(*) FROM frases") >= 30,
                        "La SPEC §10 exige ≥30 frases motivadoras");
                // V3 aplicada: la tabla de eventos existe y consulta sin error.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM eventos_servidor"),
                        "eventos_servidor debe existir y arrancar vacía");
                // V4 aplicada: la tabla de sanciones existe y arranca vacía.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM sanciones"),
                        "sanciones debe existir y arrancar vacía");
                // V5 aplicada: la tabla de sorteos existe y arranca vacía.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM sorteos"),
                        "sorteos debe existir y arrancar vacía");
                // V6 aplicada: personajes y transacciones existen y arrancan vacías.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM personajes"),
                        "personajes debe existir y arrancar vacía");
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM transacciones"),
                        "transacciones debe existir y arrancar vacía");
                // V7 aplicada: personajes tiene la columna trabajo.
                assertEquals(1, contar(st, "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_name = 'personajes' AND column_name = 'trabajo'"),
                        "personajes.trabajo debe existir tras V7");
                // V8 aplicada: inventario existe y arranca vacío.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM inventario"),
                        "inventario debe existir y arrancar vacío");
                // V9 aplicada: mejoras existe y arranca vacía.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM mejoras"),
                        "mejoras debe existir y arrancar vacía");
                // warns.motivo debe ser TEXT (aloja el texto cifrado en base64).
                assertEquals(1, contar(st, "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_name = 'warns' AND column_name = 'motivo' "
                                + "AND data_type = 'text'"),
                        "warns.motivo debe ser TEXT tras V4");

                // Toda pregunta debe tener una opción correcta válida (A-D) y ambos idiomas.
                assertEquals(0, contar(st,
                        "SELECT COUNT(*) FROM trivia_preguntas WHERE correcta NOT IN ('A','B','C','D')"),
                        "Ninguna pregunta puede tener una opción correcta fuera de A-D");
                assertEquals(0, contar(st,
                        "SELECT COUNT(*) FROM trivia_preguntas WHERE pregunta_es = '' OR pregunta_en = ''"),
                        "Ninguna pregunta puede quedar sin texto en algún idioma");
            }
        }
    }

    /** Ejecuta una consulta de conteo y devuelve el entero de la primera columna. */
    private static int contar(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
