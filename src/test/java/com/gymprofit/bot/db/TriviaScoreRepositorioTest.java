package com.gymprofit.bot.db;

import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica el marcador acumulado de trivia (V39) contra MySQL real: el UPSERT atómico de
 * {@link TriviaScoreRepositorio#registrar} (aciertos/fallos/partidas/mejor_racha/racha_actual) y el
 * ranking por aciertos. Se salta sin Docker; corre en CI.
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
}
