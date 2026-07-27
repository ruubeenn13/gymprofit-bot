package com.gymprofit.bot.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sanidad del banco de preguntas de trivia (F4) contra MySQL real (Testcontainers), tras aplicar todas las
 * migraciones (V2 siembra 50, V38 amplía a ~200 en 6 categorías). Recorre {@code trivia_preguntas} y
 * comprueba que cada fila está bien formada: 4 opciones no vacías en ES y EN, {@code correcta} válida
 * (A-D), categoría entre las 6 permitidas y dificultad válida. Así una semilla rota (opción vacía, letra
 * inválida) rompe el build en vez de colarse en producción. Se salta si Docker no es alcanzable en local;
 * corre en CI (Linux).
 */
class TriviaBancoTest {

    private static final Set<String> CATEGORIAS = Set.of(
            "FITNESS", "NUTRICION", "ENTRENAMIENTO", "ANATOMIA", "SUPLEMENTACION", "CULTURA_FITNESS");
    private static final Set<String> DIFICULTADES = Set.of("FACIL", "MEDIA", "DIFICIL");
    private static final Set<String> LETRAS = Set.of("A", "B", "C", "D");

    @Test
    @DisplayName("todas las preguntas están bien formadas y hay al menos ~180")
    void bancoSano() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                String[] opciones = {
                        "opcion_a_es", "opcion_a_en", "opcion_b_es", "opcion_b_en",
                        "opcion_c_es", "opcion_c_en", "opcion_d_es", "opcion_d_en"};
                int total = 0;
                try (Connection con = db.dataSource().getConnection();
                     Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM trivia_preguntas")) {
                    while (rs.next()) {
                        total++;
                        long id = rs.getLong("id");
                        assertTrue(CATEGORIAS.contains(rs.getString("categoria")),
                                "categoria inválida en pregunta " + id + ": " + rs.getString("categoria"));
                        assertTrue(DIFICULTADES.contains(rs.getString("dificultad")),
                                "dificultad inválida en pregunta " + id);
                        assertTrue(LETRAS.contains(rs.getString("correcta")),
                                "correcta inválida en pregunta " + id);
                        assertFalse(vacio(rs.getString("pregunta_es")), "pregunta_es vacía en " + id);
                        assertFalse(vacio(rs.getString("pregunta_en")), "pregunta_en vacía en " + id);
                        for (String col : opciones) {
                            assertFalse(vacio(rs.getString(col)), col + " vacía en pregunta " + id);
                        }
                    }
                }
                assertTrue(total >= 180, "esperaba al menos ~180 preguntas tras V38, había " + total);
            }
        } catch (Exception e) {
            fail("Error verificando el banco de trivia: " + e.getMessage(), e);
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
