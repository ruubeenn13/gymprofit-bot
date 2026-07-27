# F4 — Trivia jugable — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** `/trivia [categoria]` plantea una pregunta con botones A-D (un intento), acertar da coins+XP; `/trivia ranking` lista el top por aciertos; se amplía el banco a ~200 preguntas en 6 categorías.

**Architecture:** estado del juego en `trivia_respuestas` (one-shot, V37) + banco ampliado (CHECK + seeds, V38) + `TriviaRepositorio` (JDBC) + `Trivia` (recompensas puras) + `TriviaService` (responder atómico + premio) + `TriviaComando` + `TriviaListener`. Faucet acotado (premio único por pregunta) → antiinflación natural.

**Tech Stack:** Java 21 + JDA 5, JDBC + Flyway, JUnit 5 + Mockito + Testcontainers.

**Convenciones:** dominio español; i18n ES **y** EN; embeds por `EmbedFactory`; header Javadoc por archivo; migración Flyway para el esquema; un archivo por comando; azar inyectable para tests.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw -B clean verify`. Siempre `clean verify`. Testcontainers se saltan en local (solo CI).

**Firmas reales (verificadas):**
- `trivia_preguntas` (V1): `id BIGINT PK AI`, `categoria VARCHAR(30)` CHECK(`FITNESS`,`NUTRICION`), `dificultad VARCHAR(10)` CHECK(`FACIL`,`MEDIA`,`DIFICIL`) default `MEDIA`, `pregunta_es/en VARCHAR(300)`, `opcion_{a,b,c,d}_{es,en} VARCHAR(150)`, `correcta CHAR(1)` CHECK(A-D), `creado_en`. 50 semillas en V2.
- `EconomiaRepositorio.ingresar(long discordId, long cantidad, String motivo)` (void).
- `XpService.ganarXp(long discordId, int cantidad) -> XpResultado`.
- `UsuarioDiscordRepositorio.obtenerOCrear(long discordId)` (garantiza la fila; llamar antes de ingresar/ganarXp).
- `usuarios_discord(discord_id)` es la PK de usuarios (FK RGPD).
- Listeners se registran en `Main` con `listeners.add(new XListener(...))`. Comandos con `comandos.add(new XComando(...))`. `EmbedFactory.Tipo.TRIVIA` existe.

---

## Task 1: V37 `trivia_respuestas` + `TriviaPregunta` + `TriviaRepositorio`

**Files:**
- Create: `src/main/resources/db/migration/V37__trivia_respuestas.sql`
- Create: `src/main/java/com/gymprofit/bot/db/TriviaPregunta.java`
- Create: `src/main/java/com/gymprofit/bot/db/TriviaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/TriviaRepositorioTest.java`

- [ ] **Step 1: V37 migración**
```sql
-- V37: respuestas de trivia (F4). Una fila por (jugador, pregunta): guarda si acertó y cuándo.
-- Un intento por pregunta (PK compuesta): imposible re-responder ni re-cobrar. FK CASCADE (RGPD).
CREATE TABLE trivia_respuestas (
    discord_id    BIGINT    NOT NULL,
    pregunta_id   BIGINT    NOT NULL,
    acierto       BOOLEAN   NOT NULL,
    respondida_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id, pregunta_id),
    CONSTRAINT fk_trivresp_usuario FOREIGN KEY (discord_id) REFERENCES usuarios_discord(discord_id) ON DELETE CASCADE,
    CONSTRAINT fk_trivresp_pregunta FOREIGN KEY (pregunta_id) REFERENCES trivia_preguntas(id) ON DELETE CASCADE,
    KEY idx_trivresp_usuario (discord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
Confirma los nombres de columna/tabla de `usuarios_discord` y `trivia_preguntas` en V1 antes de finalizar.

- [ ] **Step 2: `TriviaPregunta.java`**
```java
package com.gymprofit.bot.db;

/**
 * Una pregunta de trivia ya resuelta al idioma pedido (F4). El repositorio elige es/en al leerla, así el
 * resto del código no arrastra los dos idiomas. {@code correcta} es la letra A-D de la opción buena.
 *
 * @param id         id de la pregunta
 * @param categoria  FITNESS | NUTRICION | ENTRENAMIENTO | ANATOMIA | SUPLEMENTACION | CULTURA_FITNESS
 * @param dificultad FACIL | MEDIA | DIFICIL (define el premio)
 * @param pregunta   enunciado en el idioma pedido
 * @param opciones   las 4 opciones en el idioma pedido, en orden A,B,C,D
 * @param correcta   letra de la opción correcta (A-D)
 */
public record TriviaPregunta(long id, String categoria, String dificultad, String pregunta,
                             java.util.List<String> opciones, char correcta) {}
```

- [ ] **Step 3: `TriviaRepositorioTest` (Testcontainers, write first).** Mirror el scaffolding de un repo test con Testcontainers (p. ej. `EmpresaAccionRepositorioTest`/`EmpresaRepositorioTest`): container MySQL + Flyway + DataSource + `assumeTrue(DockerClientFactory...)`. Necesita sembrar un `usuarios_discord` (FK) y usar las preguntas ya sembradas por V2. Casos:
```java
    @Test @DisplayName("aleatoriaNoRespondida excluye las ya respondidas")
    void aleatoriaExcluye() {
        // hay 50 preguntas sembradas. Sin respuestas: devuelve alguna.
        assertTrue(repo.aleatoriaNoRespondida(USER, java.util.Optional.empty()).isPresent());
        // responde una y comprueba que ya no puede salir esa misma en repetidas tiradas
        long id = repo.aleatoriaNoRespondida(USER, java.util.Optional.empty()).get().id();
        repo.registrarRespuesta(USER, id, true);
        for (int i = 0; i < 20; i++) {
            var p = repo.aleatoriaNoRespondida(USER, java.util.Optional.empty());
            if (p.isPresent()) assertNotEquals(id, p.get().id());
        }
    }

    @Test @DisplayName("registrarRespuesta es one-shot (segundo intento no cuenta)")
    void oneShot() {
        long id = repo.aleatoriaNoRespondida(USER, java.util.Optional.empty()).get().id();
        assertTrue(repo.registrarRespuesta(USER, id, false));
        assertFalse(repo.registrarRespuesta(USER, id, true)); // ya existe -> false, no re-registra
        assertEquals(0, repo.aciertosDe(USER)); // el primero fue fallo y no se puede sobreescribir
    }

    @Test @DisplayName("aciertosDe cuenta solo los aciertos; ranking ordena")
    void aciertosYRanking() {
        // responde varias como acierto para USER y menos para OTHER, comprueba aciertosDe y ranking
    }

    @Test @DisplayName("filtro por categoria y CHECK ampliado (tras V38) admite las 6 categorias")
    void categoria() {
        assertTrue(repo.aleatoriaNoRespondida(USER, java.util.Optional.of("FITNESS")).isPresent());
    }
```

- [ ] **Step 4: `TriviaRepositorio.java`** (JDBC, mirror `BolsaRepositorio` style). Idioma: parámetro `locale` (o dos métodos); aquí un helper `mapear(rs, es)` elige las columnas del idioma.
```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Repositorio JDBC de la trivia (F4): lee preguntas del banco ({@code trivia_preguntas}) resolviendo el
 * idioma, y registra las respuestas del jugador ({@code trivia_respuestas}) de forma one-shot (una por
 * pregunta). Deriva aciertos y ranking de esas respuestas.
 */
public final class TriviaRepositorio {

    /** Fila del ranking de trivia. */
    public record FilaTrivia(long discordId, int aciertos) {}

    private final DataSource dataSource;
    public TriviaRepositorio(DataSource dataSource) { this.dataSource = dataSource; }

    /** Una pregunta al azar que {@code discordId} aun no ha respondido (filtro de categoria opcional). */
    public Optional<TriviaPregunta> aleatoriaNoRespondida(long discordId, Optional<String> categoria, boolean es) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM trivia_preguntas p WHERE NOT EXISTS ("
          + "SELECT 1 FROM trivia_respuestas r WHERE r.pregunta_id = p.id AND r.discord_id = ?)");
        if (categoria.isPresent()) sql.append(" AND p.categoria = ?");
        sql.append(" ORDER BY RAND() LIMIT 1");
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setLong(1, discordId);
            if (categoria.isPresent()) ps.setString(2, categoria.get());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs, es)) : Optional.empty();
            }
        } catch (SQLException e) { throw new DatabaseException("Error leyendo pregunta de trivia", e); }
    }

    /** Pregunta por id (para validar la respuesta del boton). */
    public Optional<TriviaPregunta> porId(long id, boolean es) {
        String sql = "SELECT * FROM trivia_preguntas WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs, es)) : Optional.empty();
            }
        } catch (SQLException e) { throw new DatabaseException("Error leyendo la pregunta " + id, e); }
    }

    /** Registra la respuesta. Devuelve true si se registro (primera vez); false si ya existia (one-shot). */
    public boolean registrarRespuesta(long discordId, long preguntaId, boolean acierto) {
        String sql = "INSERT IGNORE INTO trivia_respuestas (discord_id, pregunta_id, acierto) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId); ps.setLong(2, preguntaId); ps.setBoolean(3, acierto);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new DatabaseException("Error registrando respuesta de trivia", e); }
    }

    /** Nº de aciertos del jugador. */
    public int aciertosDe(long discordId) {
        String sql = "SELECT COUNT(*) FROM trivia_respuestas WHERE discord_id = ? AND acierto = TRUE";
        return contar(sql, ps -> ps.setLong(1, discordId));
    }

    /** Total de preguntas del banco (filtro de categoria opcional). */
    public int total(Optional<String> categoria) {
        String sql = "SELECT COUNT(*) FROM trivia_preguntas" + (categoria.isPresent() ? " WHERE categoria = ?" : "");
        return contar(sql, ps -> { if (categoria.isPresent()) ps.setString(1, categoria.get()); });
    }

    /** Top por aciertos. */
    public List<FilaTrivia> ranking(int limite) {
        String sql = "SELECT discord_id, COUNT(*) AS aciertos FROM trivia_respuestas WHERE acierto = TRUE "
                   + "GROUP BY discord_id ORDER BY aciertos DESC LIMIT ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<FilaTrivia> res = new ArrayList<>();
                while (rs.next()) res.add(new FilaTrivia(rs.getLong("discord_id"), rs.getInt("aciertos")));
                return res;
            }
        } catch (SQLException e) { throw new DatabaseException("Error leyendo el ranking de trivia", e); }
    }

    private TriviaPregunta mapear(ResultSet rs, boolean es) throws SQLException {
        String suf = es ? "_es" : "_en";
        return new TriviaPregunta(
            rs.getLong("id"), rs.getString("categoria"), rs.getString("dificultad"),
            rs.getString("pregunta" + suf),
            List.of(rs.getString("opcion_a" + suf), rs.getString("opcion_b" + suf),
                    rs.getString("opcion_c" + suf), rs.getString("opcion_d" + suf)),
            rs.getString("correcta").charAt(0));
    }

    @FunctionalInterface private interface Bind { void apply(PreparedStatement ps) throws SQLException; }
    private int contar(String sql, Bind bind) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind.apply(ps);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new DatabaseException("Error contando en trivia", e); }
    }
}
```
(Ajusta los tests del Step 3 a la firma con `boolean es` — pásales `true`.)

- [ ] **Step 5:** `sh ./mvnw -B clean verify` → BUILD SUCCESS (repo test skip en local).
- [ ] **Step 6: Commit** `feat(trivia): V37 respuestas de trivia (one-shot) + repo`

---

## Task 2: `Trivia` (recompensas) + `TriviaService` — con review

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/Trivia.java`
- Create: `src/main/java/com/gymprofit/bot/services/TriviaService.java`
- Test: `src/test/java/com/gymprofit/bot/services/TriviaServiceTest.java`

- [ ] **Step 1: `Trivia.java` (pura, recompensa por dificultad)**
```java
package com.gymprofit.bot.services;

/**
 * Numeros puros de la trivia (F4): la recompensa por acertar segun la dificultad. Sin estado. El premio
 * total que un jugador puede obtener esta acotado (una vez por pregunta), asi que es un faucet finito.
 */
public final class Trivia {
    private Trivia() {}
    /** Recompensa de un acierto: coins + XP. */
    public record Recompensa(long coins, int xp) {}
    /** Recompensa segun la dificultad (FACIL/MEDIA/DIFICIL); desconocida -> MEDIA. */
    public static Recompensa recompensa(String dificultad) {
        return switch (dificultad == null ? "MEDIA" : dificultad) {
            case "FACIL" -> new Recompensa(40, 20);
            case "DIFICIL" -> new Recompensa(120, 50);
            default -> new Recompensa(80, 35); // MEDIA
        };
    }
}
```

- [ ] **Step 2: `TriviaServiceTest` (Mockito, write first).** Mock `TriviaRepositorio repo`, `EconomiaRepositorio economia`, `XpService xp`, `UsuarioDiscordRepositorio usuarios`. Casos:
```java
    @Test @DisplayName("acierto: registra, cobra coins y XP de la dificultad")
    void acierto() {
        var p = new TriviaPregunta(7, "FITNESS", "DIFICIL", "q", List.of("a","b","c","d"), 'C');
        when(repo.porId(7, true)).thenReturn(Optional.of(p));
        when(repo.registrarRespuesta(USER, 7, true)).thenReturn(true);
        var r = svc().responder(USER, 7, 'C', true);
        assertEquals(TriviaService.Estado.ACIERTO, r.estado());
        verify(repo).registrarRespuesta(USER, 7, true);
        verify(economia).ingresar(USER, 120L, "trivia");
        verify(xp).ganarXp(USER, 50);
    }

    @Test @DisplayName("fallo: registra como fallo, no cobra, revela la correcta")
    void fallo() {
        var p = new TriviaPregunta(7, "FITNESS", "FACIL", "q", List.of("a","b","c","d"), 'C');
        when(repo.porId(7, true)).thenReturn(Optional.of(p));
        when(repo.registrarRespuesta(USER, 7, false)).thenReturn(true);
        var r = svc().responder(USER, 7, 'A', true);
        assertEquals(TriviaService.Estado.FALLO, r.estado());
        assertEquals('C', r.correcta());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
        verify(xp, never()).ganarXp(anyLong(), anyInt());
    }

    @Test @DisplayName("ya respondida (registrarRespuesta=false): YA_RESPONDIDA, no cobra")
    void yaRespondida() {
        var p = new TriviaPregunta(7, "FITNESS", "MEDIA", "q", List.of("a","b","c","d"), 'C');
        when(repo.porId(7, true)).thenReturn(Optional.of(p));
        when(repo.registrarRespuesta(eq(USER), eq(7L), anyBoolean())).thenReturn(false);
        var r = svc().responder(USER, 7, 'C', true);
        assertEquals(TriviaService.Estado.YA_RESPONDIDA, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }
```

- [ ] **Step 3: `TriviaService.java`**
```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.*;
import java.util.Optional;

/**
 * Juego de trivia (F4): elige la siguiente pregunta no respondida y resuelve una respuesta. La respuesta
 * es one-shot y atomica: {@link TriviaRepositorio#registrarRespuesta} (INSERT IGNORE) es el gate; solo si
 * registra por primera vez y es acierto se paga el premio ({@link Trivia}) en coins + XP. Un doble clic o
 * una carrera no cobran dos veces.
 */
public final class TriviaService {

    public enum Estado { ACIERTO, FALLO, YA_RESPONDIDA, NO_EXISTE }
    /** Resultado de responder: estado + la letra correcta (para revelarla) + el premio si acerto. */
    public record Resultado(Estado estado, char correcta, long coins, int xp) {}

    private final TriviaRepositorio repo;
    private final EconomiaRepositorio economia;
    private final XpService xp;
    private final UsuarioDiscordRepositorio usuarios;

    public TriviaService(TriviaRepositorio repo, EconomiaRepositorio economia, XpService xp,
                         UsuarioDiscordRepositorio usuarios) {
        this.repo = repo; this.economia = economia; this.xp = xp; this.usuarios = usuarios;
    }

    /** Siguiente pregunta no respondida (idioma es/en); vacio si no quedan. */
    public Optional<TriviaPregunta> siguiente(long actorId, Optional<String> categoria, boolean es) {
        return repo.aleatoriaNoRespondida(actorId, categoria, es);
    }

    /** Resuelve una respuesta al boton {@code letra} para la pregunta {@code preguntaId}. */
    public Resultado responder(long actorId, long preguntaId, char letra, boolean es) {
        Optional<TriviaPregunta> pOpt = repo.porId(preguntaId, es);
        if (pOpt.isEmpty()) return new Resultado(Estado.NO_EXISTE, ' ', 0, 0);
        TriviaPregunta p = pOpt.get();
        boolean acierto = Character.toUpperCase(letra) == p.correcta();
        usuarios.obtenerOCrear(actorId);
        // Gate one-shot atomico: si ya estaba respondida, no re-registra ni cobra.
        if (!repo.registrarRespuesta(actorId, preguntaId, acierto)) {
            return new Resultado(Estado.YA_RESPONDIDA, p.correcta(), 0, 0);
        }
        if (!acierto) return new Resultado(Estado.FALLO, p.correcta(), 0, 0);
        Trivia.Recompensa rec = Trivia.recompensa(p.dificultad());
        economia.ingresar(actorId, rec.coins(), "trivia");
        xp.ganarXp(actorId, rec.xp());
        return new Resultado(Estado.ACIERTO, p.correcta(), rec.coins(), rec.xp());
    }

    public int aciertosDe(long actorId) { return repo.aciertosDe(actorId); }
    public int total() { return repo.total(Optional.empty()); }
    public java.util.List<TriviaRepositorio.FilaTrivia> ranking(int limite) { return repo.ranking(limite); }
}
```

- [ ] **Step 4:** run `TriviaServiceTest` → PASS. `clean verify` verde.
- [ ] **Step 5: Commit** `feat(trivia): TriviaService (respuesta one-shot + premio por dificultad)`
- [ ] **Step 6: REVIEW** — spec-reviewer (base = commit T1): el premio se paga UNA sola vez (gate INSERT IGNORE), fallo no cobra, ya-respondida no cobra ni re-registra, recompensa por dificultad correcta.

---

## Task 3: `TriviaComando` + `TriviaListener` + i18n + intro + wiring

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/comunidad/TriviaComando.java`
- Create: `src/main/java/com/gymprofit/bot/events/TriviaListener.java`
- Modify: `messages_es.properties`, `messages_en.properties`, `SetupServidorPlan` (intro), `Main.java`

- [ ] **Step 1: i18n (ambos idiomas)** — claves:
```properties
comando.trivia.desc=Responde una pregunta de trivia fitness y gana recompensas
comando.trivia.opcion.categoria=Categoría (opcional)
comando.trivia.ranking.desc=Ranking de aciertos de trivia
trivia.pregunta.titulo=🧠 Trivia · {0}
trivia.pregunta.cuerpo={0}\n\n**Categoría:** {1} · **Dificultad:** {2}
trivia.acierto=✅ ¡Correcto! +{0} 🪙 y +{1} XP. Aciertos: **{2}/{3}**.
trivia.fallo=❌ Incorrecto. La respuesta era **{0}**. Aciertos: **{1}/{2}**.
trivia.ya_respondida=Ya habías respondido esa pregunta.
trivia.sin_preguntas=¡Has respondido todas las preguntas disponibles! 🎉
trivia.ranking.titulo=🧠 Ranking de trivia
trivia.ranking.fila={0} <@{1}> — **{2}** aciertos
trivia.ranking.vacio=Nadie ha respondido trivia todavía.
trivia.categoria.fitness=Fitness
trivia.categoria.nutricion=Nutrición
trivia.categoria.entrenamiento=Entrenamiento
trivia.categoria.anatomia=Anatomía
trivia.categoria.suplementacion=Suplementación
trivia.categoria.cultura_fitness=Cultura fitness
```
EN natural equivalente (mismas claves, mismos placeholders). Sin comillas simples sin escapar en MessageFormat.

- [ ] **Step 2: `TriviaComando`** — mirar `EmpleoComando`/`EconomiaComando` para el patrón. `/trivia`:
  - `definicion()`: `Commands.slash("trivia", ...)` localizada; subcomando `jugar`? — mejor 2 subcomandos: **`jugar`** (opción `categoria` STRING con las 6 choices, no requerida) y **`ranking`**. (O `/trivia` a secas juega y `/trivia ranking`; con SubcommandData es más limpio: `jugar` + `ranking`.)
  - Locale con `Messages.desdeTag(evento.getUserLocale().getLocale())`.
  - `jugar`: `boolean es = locale==Messages.ES`; `service.siguiente(actor, categoriaOpt, es)`; si vacío → responder `trivia.sin_preguntas` (efímero); si no, construir el embed `EmbedFactory.Tipo.TRIVIA` con `trivia.pregunta.*` (enunciado + categoría/dificultad traducidas) y **4 botones** `Button.primary("trivia:resp:"+p.id()+":A", "A) "+opciones[0])` … D. **Responder efímero** (`setEphemeral(true)`), porque es un quiz personal.
  - `ranking`: `service.ranking(10)`; construir embed público con `trivia.ranking.fila` (medalla/posición, `<@id>`, aciertos). Vacío → `trivia.ranking.vacio`.
  - Category default PUBLICO (no override), pero `jugar` responde efímero por la mecánica.
- [ ] **Step 3: `TriviaListener`** (extends `ListenerAdapter`, `onButtonInteraction`): customId `trivia:resp:<id>:<letra>`. Parsear id+letra; `boolean es` del locale; `service.responder(userId, id, letra, es)`; `switch` sobre `Estado`: ACIERTO → editar el mensaje (efímero) con `trivia.acierto` (coins, xp, `service.aciertosDe`, `service.total`); FALLO → `trivia.fallo` (letra correcta, aciertos, total); YA_RESPONDIDA → `trivia.ya_respondida`; NO_EXISTE → aviso genérico. Deshabilitar/quitar los botones al responder (`editComponents()` vacío o marcados). Como la interacción original es efímera, solo el autor la ve, así que no hace falta comprobar autoría, pero verifica que el que pulsa es el dueño por si acaso (en efímeras lo es).
- [ ] **Step 4: intro del canal** — en `SetupServidorPlan`, el canal `🧠・trivia` usa `intro.proximamente`; cambiarlo a una intro propia `intro.trivia` (ES+EN) que explique `/trivia jugar` y `/trivia ranking`.
- [ ] **Step 5: Main wiring** — construir `TriviaRepositorio` + `TriviaService` (con `economiaRepo`, `xpService`, `usuarios` — grep los nombres reales), `comandos.add(new TriviaComando(triviaService, ...))`, `listeners.add(new TriviaListener(triviaService))`. Imports.
- [ ] **Step 6:** `clean verify` → BUILD SUCCESS.
- [ ] **Step 7: Commit** `feat(trivia): /trivia jugar|ranking + TriviaListener + i18n + intro`

---

## Task 4: V38 — ampliar el banco a ~200 preguntas (6 categorías)

**Files:** Create `src/main/resources/db/migration/V38__trivia_ampliacion.sql`; Test `src/test/java/com/gymprofit/bot/db/TriviaBancoTest.java` (sanidad).

- [ ] **Step 1: V38 — ampliar el CHECK + sembrar.**
```sql
-- V38: amplia el banco de trivia (F4) a 6 categorias y ~150 preguntas nuevas (ES+EN).
ALTER TABLE trivia_preguntas DROP CHECK chk_trivia_categoria;
ALTER TABLE trivia_preguntas ADD CONSTRAINT chk_trivia_categoria
    CHECK (categoria IN ('FITNESS','NUTRICION','ENTRENAMIENTO','ANATOMIA','SUPLEMENTACION','CULTURA_FITNESS'));
-- (opcional) ampliar el ancho si algun texto lo pide: ya son VARCHAR(300)/(150), suele bastar.

INSERT INTO trivia_preguntas
    (categoria, dificultad, pregunta_es, pregunta_en,
     opcion_a_es, opcion_a_en, opcion_b_es, opcion_b_en,
     opcion_c_es, opcion_c_en, opcion_d_es, opcion_d_en, correcta) VALUES
-- ~150 filas nuevas aqui (ver guia de contenido)
;
```
  **Guía de contenido (CRÍTICA):**
  - ~150 preguntas nuevas repartidas entre las **6 categorías** (para dejar ~200 totales con las 50 de V2, ~33 por categoría). Reparto por dificultad variado (FACIL/MEDIA/DIFICIL).
  - **Bilingüe ES+EN** en las 12 columnas de texto; las 4 opciones **plausibles** (no obvias), una sola correcta.
  - **Variar la posición** de la correcta (A/B/C/D repartidas, no siempre la misma letra).
  - Datos **verificables y correctos** (fisiología, nutrición, anatomía, entrenamiento, suplementación básica, cultura fitness). Nada de opiniones ni datos dudosos.
  - Escapar comillas simples SQL (`''`). Respetar los límites `VARCHAR(300)` enunciado / `VARCHAR(150)` opción.
  - **Sugerencia de ejecución subagent-driven:** trocear en 6 subagentes (uno por categoría, ~25 preguntas cada uno) que devuelvan sus filas `INSERT` (mismas columnas), y ensamblarlas en una sola V38. Revisar que ninguna repita enunciado de las 50 existentes.

- [ ] **Step 2: `TriviaBancoTest` (Testcontainers) — sanidad del banco.**
```java
    @Test @DisplayName("todas las preguntas tienen 4 opciones no vacias (es/en) y correcta valida")
    void bancoSano() {
        // SELECT * FROM trivia_preguntas: por cada fila, ninguna opcion_*_es/en vacia,
        // correcta IN (A,B,C,D), categoria IN (las 6), dificultad IN (FACIL,MEDIA,DIFICIL).
        // Ademas: >= ~180 preguntas en total (tras V2 + V38).
    }
```
- [ ] **Step 3:** `clean verify` → BUILD SUCCESS (test skip en local; valida en CI).
- [ ] **Step 4: Commit** `feat(trivia): V38 amplia el banco a ~200 preguntas y 6 categorias`

---

## Task 5: Documentación + mapa + verificación final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`, `docs/architecture-map.json`, `docs/architecture-map.html`.

- [ ] **Step 1: ADR-028** (tras ADR-027; confirmar). Contexto (preguntas sembradas sin usar). Decisión (trivia jugable, un intento por pregunta, premio único por dificultad → faucet acotado, ranking de aciertos, banco ampliado a ~200/6 categorías). Consecuencias (V37 respuestas + V38 banco; nuevos Trivia/TriviaService/repo/comando/listener; fuera: quiz múltiple, trivia del día, alta por comando).
- [ ] **Step 2: `docs/architecture.md`** — viñeta F4 trivia; migraciones a **V38**; `TriviaListener` en `events/`; `/trivia` en la lista de comandos.
- [ ] **Step 3: `CHANGELOG.md`** — entrada F4 trivia (`/trivia`, ~200 preguntas, ranking; migraciones V37+V38).
- [ ] **Step 4: READMEs** — `/trivia` (jugar · ranking) en la lista de comandos, ES+EN.
- [ ] **Step 5: mapa de arquitectura** — actualizar `docs/architecture-map.json` (bot: comando `/trivia`, `TriviaListener`, jobs sin cambios, migraciones V1–V38) y `docs/architecture-map.html` (mismo dato: comando + listener + migraciones). Mantener el estilo terminal existente. (Regla de mantener el mapa al día.)
- [ ] **Step 6: `clean verify` final** — BUILD SUCCESS; anotar recuento de tests.
- [ ] **Step 7: Commit** `docs(trivia): F4 trivia — ADR-028, architecture, changelog, READMEs y mapa`
- [ ] **Step 8: Push + aviso** — `git push origin main`. Avisar: **desplegar = reiniciar bot** (V37+V38 + `/trivia` + `TriviaListener`) **+ `/setup`** (intro del canal `🧠・trivia`). Smoke: ver el `## Despliegue` de la spec.

---

## Notas de implementación

- **One-shot atómico:** el gate es `registrarRespuesta` (INSERT IGNORE). El service paga SOLO si devolvió true y fue acierto. Un doble clic no cobra dos veces. No pre-consultar `yaRespondida` + insertar (carrera): confiar en el INSERT IGNORE.
- **Idioma:** el repo devuelve la pregunta ya en el idioma (`boolean es`); el comando/listener resuelven `es` del locale del invocador.
- **Efímero justificado:** `/trivia jugar` responde efímero (quiz personal; público spoilea). Documentado en la spec.
- **Antiinflación:** premio único por pregunta → faucet finito (~200 preguntas). Números en `Trivia`.
- **Seeds:** cuidar el escape de comillas SQL y variar la letra correcta. Test de sanidad obligatorio para no meter seeds rotas.
- **XP:** `xp.ganarXp(actor, n)`; `economia.ingresar(actor, coins, "trivia")`; `usuarios.obtenerOCrear` antes.
