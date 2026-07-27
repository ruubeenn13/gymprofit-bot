package com.gymprofit.bot.db;

import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica el marcador acumulado de trivia (V39) contra MySQL real: el UPSERT atómico de
 * {@link TriviaScoreRepositorio#registrar} (aciertos/fallos/partidas/mejor_racha/racha_actual), el
 * ranking por aciertos y el algoritmo gaps-and-islands del backfill de la propia migración V39
 * (con aciertos NO consecutivos, para detectar si el WHERE se cuela dentro de la ventana). Se salta
 * sin Docker; corre en CI.
 */
class TriviaScoreRepositorioTest {

    private static MySQLContainer<?> mysql;
    private static Database db;
    private static TriviaScoreRepositorio repo;
    private static UsuarioDiscordRepositorio usuarios;

    @BeforeAll
    static void arranque() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable; el test corre en CI");
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0")).withDatabaseName("gymprofit_bot");
        mysql.start();
        db = new Database(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        db.migrar();
        repo = new TriviaScoreRepositorio(db.dataSource());
        usuarios = new UsuarioDiscordRepositorio(db.dataSource());
    }

    @AfterAll
    static void cierre() {
        if (db != null) db.close();
        if (mysql != null) mysql.stop();
    }

    @Test
    @DisplayName("acierto en fila nueva: aciertos=1, partidas=1, racha=1, mejor=1")
    void aciertoNuevo() {
        long id = 1001L;
        usuarios.obtenerOCrear(id);
        repo.registrar(id, true);
        TriviaScoreRepositorio.Marcador m = repo.de(id).orElseThrow();
        assertEquals(1, m.aciertos());
        assertEquals(0, m.fallos());
        assertEquals(1, m.partidas());
        assertEquals(1, m.mejorRacha());
        assertEquals(1, m.rachaActual());
    }

    @Test
    @DisplayName("secuencia A,A,F,A: aciertos=3 fallos=1 partidas=4 mejor=2 racha=1")
    void secuencia() {
        long id = 1002L;
        usuarios.obtenerOCrear(id);
        repo.registrar(id, true);
        repo.registrar(id, true);
        repo.registrar(id, false);
        repo.registrar(id, true);
        TriviaScoreRepositorio.Marcador m = repo.de(id).orElseThrow();
        assertEquals(3, m.aciertos());
        assertEquals(1, m.fallos());
        assertEquals(4, m.partidas());
        assertEquals(2, m.mejorRacha());
        assertEquals(1, m.rachaActual());
    }

    @Test
    @DisplayName("fallo tras acierto rompe la racha viva pero conserva la mejor")
    void falloRompeRacha() {
        long id = 1003L;
        usuarios.obtenerOCrear(id);
        repo.registrar(id, true);
        repo.registrar(id, false);
        TriviaScoreRepositorio.Marcador m = repo.de(id).orElseThrow();
        assertEquals(0, m.rachaActual());
        assertEquals(1, m.mejorRacha());
    }

    @Test
    @DisplayName("de() vacío si el usuario no ha jugado")
    void sinMarcador() {
        assertTrue(repo.de(9999L).isEmpty());
    }

    @Test
    @DisplayName("ranking ordena por aciertos DESC y excluye aciertos=0")
    void ranking() {
        long a = 2001L, b = 2002L, c = 2003L;
        usuarios.obtenerOCrear(a); usuarios.obtenerOCrear(b); usuarios.obtenerOCrear(c);
        repo.registrar(a, true); repo.registrar(a, true);   // 2 aciertos
        repo.registrar(b, true);                            // 1 acierto
        repo.registrar(c, false);                           // 0 aciertos -> excluido
        List<TriviaRepositorio.FilaTrivia> top = repo.ranking(10);
        assertTrue(top.stream().noneMatch(f -> f.discordId() == c));
        // a antes que b
        int ia = indiceDe(top, a), ib = indiceDe(top, b);
        assertTrue(ia >= 0 && ib >= 0 && ia < ib);
    }

    private static int indiceDe(List<TriviaRepositorio.FilaTrivia> l, long id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).discordId() == id) return i;
        return -1;
    }

    @Test
    @DisplayName("backfill: A,F,A,F,A -> mejor racha real 1 (no el total de aciertos)")
    void backfillIslasNoConsecutivas() throws Exception {
        long id = 3001L;
        usuarios.obtenerOCrear(id);
        // pregunta_id creciente fija el orden real de la secuencia (respondida_en puede empatar).
        insertarRespuesta(id, 1L, true);
        insertarRespuesta(id, 2L, false);
        insertarRespuesta(id, 3L, true);
        insertarRespuesta(id, 4L, false);
        insertarRespuesta(id, 5L, true);
        ejecutarBackfill();
        TriviaScoreRepositorio.Marcador m = repo.de(id).orElseThrow();
        assertEquals(3, m.aciertos());
        assertEquals(2, m.fallos());
        assertEquals(5, m.partidas());
        assertEquals(1, m.mejorRacha());
    }

    @Test
    @DisplayName("backfill: A,A,F,A -> mejor racha real 2 (isla de dos aciertos seguidos)")
    void backfillIslaDeDos() throws Exception {
        long id = 3002L;
        usuarios.obtenerOCrear(id);
        insertarRespuesta(id, 6L, true);
        insertarRespuesta(id, 7L, true);
        insertarRespuesta(id, 8L, false);
        insertarRespuesta(id, 9L, true);
        ejecutarBackfill();
        TriviaScoreRepositorio.Marcador m = repo.de(id).orElseThrow();
        assertEquals(2, m.mejorRacha());
    }

    /** Inserta directamente en {@code trivia_respuestas} (bypasa el gate one-shot: solo para sembrar el backfill). */
    private static void insertarRespuesta(long discordId, long preguntaId, boolean acierto) throws Exception {
        try (Connection con = db.dataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO trivia_respuestas (discord_id, pregunta_id, acierto) VALUES (?, ?, ?)")) {
            ps.setLong(1, discordId);
            ps.setLong(2, preguntaId);
            ps.setBoolean(3, acierto);
            ps.executeUpdate();
        }
    }

    /**
     * Re-ejecuta inline el backfill de {@code V39__trivia_scores_racha_backfill.sql} (idéntico, tras el
     * fix del gaps-and-islands: el {@code WHERE acierto = TRUE} fuera de la subconsulta con las
     * {@code ROW_NUMBER()}). Idempotente por el {@code ON DUPLICATE KEY UPDATE}.
     */
    private static void ejecutarBackfill() throws Exception {
        try (Connection con = db.dataSource().getConnection();
             Statement st = con.createStatement()) {
            st.execute("""
                    INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual)
                    SELECT r.discord_id,
                           SUM(r.acierto)        AS aciertos,
                           SUM(1 - r.acierto)    AS fallos,
                           COUNT(*)              AS partidas,
                           COALESCE(mr.mejor, 0) AS mejor_racha,
                           0                     AS racha_actual
                    FROM trivia_respuestas r
                    LEFT JOIN (
                        SELECT discord_id, MAX(len) AS mejor
                        FROM (
                            SELECT discord_id, COUNT(*) AS len
                            FROM (
                                SELECT discord_id, acierto,
                                       ROW_NUMBER() OVER (PARTITION BY discord_id ORDER BY respondida_en, pregunta_id)
                                     - ROW_NUMBER() OVER (PARTITION BY discord_id, acierto ORDER BY respondida_en, pregunta_id) AS grp
                                FROM trivia_respuestas
                            ) x
                            WHERE acierto = TRUE
                            GROUP BY discord_id, grp
                        ) y
                        GROUP BY discord_id
                    ) mr ON mr.discord_id = r.discord_id
                    GROUP BY r.discord_id
                    ON DUPLICATE KEY UPDATE
                        aciertos    = VALUES(aciertos),
                        fallos      = VALUES(fallos),
                        partidas    = VALUES(partidas),
                        mejor_racha = VALUES(mejor_racha)
                    """);
        }
    }
}
