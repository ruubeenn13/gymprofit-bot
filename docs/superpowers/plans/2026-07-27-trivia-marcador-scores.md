# Marcador de trivia (`trivia_scores`) — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Poblar y explotar `trivia_scores` como marcador acumulado por usuario (aciertos, fallos,
partidas, mejor racha, racha viva), con `/trivia stats [@usuario]` y el ranking leyendo de esa tabla.

**Architecture:** Migración V39 (columna `racha_actual` + backfill gaps-and-islands). Repo nuevo
`TriviaScoreRepositorio` (UPSERT atómico + lecturas). `TriviaService` gana el repo y registra el marcador
tras el gate one-shot y antes de pagar; su ranking pasa a leer de `trivia_scores`. Comando `/trivia stats`.

**Tech Stack:** Java 21, JDA 5, JDBC, Flyway, MySQL 8 (window functions), JUnit 5 + Mockito +
Testcontainers.

**Fuente:** `docs/superpowers/specs/2026-07-27-trivia-marcador-scores-design.md`.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw ...`
(los tests Testcontainers se saltan sin Docker en local; corren en CI). Commits **sin** trailer
Co-Authored-By.

---

### Task 1: Migración V39 + `TriviaScoreRepositorio` + tests del repo

**Files:**
- Create: `src/main/resources/db/migration/V39__trivia_scores_racha_backfill.sql`
- Create: `src/main/java/com/gymprofit/bot/db/TriviaScoreRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/TriviaScoreRepositorioTest.java`

- [ ] **Step 1: Escribir la migración V39**

`V39__trivia_scores_racha_backfill.sql`:

```sql
-- V39: marcador acumulado de trivia (F4). Da uso a trivia_scores (creada vacía en V1).
-- Añade racha_actual (racha viva) y rellena el marcador desde las respuestas ya jugadas.

-- Racha viva en curso; se rompe (0) al fallar. Permite el UPSERT O(1) sin recalcular.
ALTER TABLE trivia_scores ADD COLUMN racha_actual INT NOT NULL DEFAULT 0
    COMMENT 'Racha de aciertos seguidos en curso; 0 al fallar'
    AFTER mejor_racha;

-- Backfill idempotente desde trivia_respuestas. aciertos/fallos/partidas por agregación directa;
-- mejor_racha por gaps-and-islands (longitud máxima de aciertos consecutivos por respondida_en).
-- racha_actual se deja en 0 (la racha histórica viva no se reconstruye; se rehace jugando).
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
    mejor_racha = VALUES(mejor_racha);
```

- [ ] **Step 2: Escribir el test del repo (falla: no existe la clase)**

`TriviaScoreRepositorioTest.java` — patrón de `TriviaRepositorioTest` (Testcontainers, `assumeTrue`
Docker, `Database.migrar()`). Necesita una fila en `usuarios_discord` por la FK; usar
`UsuarioDiscordRepositorio.obtenerOCrear(id)` o INSERT directo antes de registrar.

```java
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
```

- [ ] **Step 3: Implementar `TriviaScoreRepositorio`**

```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Marcador acumulado de trivia por usuario (tabla {@code trivia_scores}, F4). Cada respuesta nueva
 * incrementa aciertos/fallos/partidas y mantiene la racha de aciertos seguidos ({@code racha_actual}) y
 * la mejor histórica ({@code mejor_racha}) con un único UPSERT atómico. Es también la fuente del ranking
 * (más barato que recontar {@code trivia_respuestas}).
 */
public final class TriviaScoreRepositorio {

    /** Marcador de un usuario. */
    public record Marcador(int aciertos, int fallos, int partidas, int mejorRacha, int rachaActual) {}

    private final DataSource ds;

    public TriviaScoreRepositorio(DataSource ds) {
        this.ds = ds;
    }

    /**
     * Suma una respuesta al marcador del usuario en una sola sentencia atómica. En el UPDATE,
     * {@code mejor_racha} se evalúa ANTES que {@code racha_actual} para leer la racha vieja: si es
     * acierto, la racha nueva es la vieja+1 y la mejor es el máximo; si es fallo, la racha viva vuelve a 0.
     */
    public void registrar(long discordId, boolean acierto) {
        int ac = acierto ? 1 : 0;
        int fa = acierto ? 0 : 1;
        String sql =
                "INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual) "
              + "VALUES (?, ?, ?, 1, ?, ?) "
              + "ON DUPLICATE KEY UPDATE "
              + "  aciertos = aciertos + VALUES(aciertos), "
              + "  fallos   = fallos   + VALUES(fallos), "
              + "  partidas = partidas + 1, "
              + "  mejor_racha = GREATEST(mejor_racha, IF(?, racha_actual + 1, racha_actual)), "
              + "  racha_actual = IF(?, racha_actual + 1, 0)";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, ac);       // aciertos (VALUES)
            ps.setInt(3, fa);       // fallos (VALUES)
            ps.setInt(4, ac);       // mejor_racha inicial (INSERT)
            ps.setInt(5, ac);       // racha_actual inicial (INSERT)
            ps.setBoolean(6, acierto); // IF de mejor_racha (UPDATE)
            ps.setBoolean(7, acierto); // IF de racha_actual (UPDATE)
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DatabaseException("Error registrando el marcador de trivia", e);
        }
    }

    /** Marcador del usuario; vacío si no ha jugado. */
    public Optional<Marcador> de(long discordId) {
        String sql = "SELECT aciertos, fallos, partidas, mejor_racha, racha_actual "
                   + "FROM trivia_scores WHERE discord_id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Marcador(
                        rs.getInt("aciertos"), rs.getInt("fallos"), rs.getInt("partidas"),
                        rs.getInt("mejor_racha"), rs.getInt("racha_actual")));
            }
        } catch (Exception e) {
            throw new DatabaseException("Error leyendo el marcador de trivia", e);
        }
    }

    /** Top de jugadores por aciertos (desempate: mejor racha, luego el que llegó antes). */
    public List<TriviaRepositorio.FilaTrivia> ranking(int limite) {
        String sql = "SELECT discord_id, aciertos FROM trivia_scores WHERE aciertos > 0 "
                   + "ORDER BY aciertos DESC, mejor_racha DESC, actualizado_en ASC LIMIT ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<TriviaRepositorio.FilaTrivia> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new TriviaRepositorio.FilaTrivia(rs.getLong("discord_id"), rs.getInt("aciertos")));
                }
                return res;
            }
        } catch (Exception e) {
            throw new DatabaseException("Error leyendo el ranking de trivia", e);
        }
    }
}
```

- [ ] **Step 4: Compilar y correr los tests del repo**

Run: `sh ./mvnw -B -q -Dtest=TriviaScoreRepositorioTest test`
Expected: sin Docker → los tests se saltan (BUILD SUCCESS); con Docker → 5 tests verdes.
(Al menos debe **compilar**; verificar con `sh ./mvnw -B -q -DskipTests package` si no hay Docker.)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V39__trivia_scores_racha_backfill.sql \
        src/main/java/com/gymprofit/bot/db/TriviaScoreRepositorio.java \
        src/test/java/com/gymprofit/bot/db/TriviaScoreRepositorioTest.java
git commit -m "feat(trivia): V39 marcador (trivia_scores) + racha + backfill y repo"
```

---

### Task 2: Extender `TriviaService` + actualizar su test + wiring en `Main`

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/TriviaService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/TriviaServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java:505-508`

- [ ] **Step 1: Actualizar `TriviaServiceTest` (falla: falta el 5º parámetro y el registro)**

Añadir un mock `TriviaScoreRepositorio scores` y pasarlo al constructor. Añadir aserciones:
- en ACIERTO y en FALLO nuevos → `verify(scores).registrar(actorId, acierto)`.
- en YA_RESPONDIDA (gate devuelve false) y en NO_EXISTE → `verify(scores, never()).registrar(anyLong(), anyBoolean())`.
El pago sigue solo en acierto nuevo (aserciones existentes intactas).

Ejemplo de los añadidos (integrar en el test existente, respetando su estilo):

```java
@Mock TriviaScoreRepositorio scores;
// ctor: new TriviaService(repo, economia, xp, usuarios, scores);

@Test
void acierto_registraMarcadorYPaga() {
    // ... arrange pregunta, correcta = 'A', gate registrarRespuesta -> true ...
    servicio.responder(ACTOR, 1L, 'A', true);
    verify(scores).registrar(ACTOR, true);
    verify(economia).ingresar(eq(ACTOR), anyLong(), eq("trivia"));
}

@Test
void fallo_registraMarcadorSinPagar() {
    servicio.responder(ACTOR, 1L, 'B', true); // correcta 'A'
    verify(scores).registrar(ACTOR, false);
    verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
}

@Test
void yaRespondida_noRegistraMarcador() {
    // gate registrarRespuesta -> false
    servicio.responder(ACTOR, 1L, 'A', true);
    verify(scores, never()).registrar(anyLong(), anyBoolean());
}
```

- [ ] **Step 2: Verificar que el test falla al compilar**

Run: `sh ./mvnw -B -q -Dtest=TriviaServiceTest test`
Expected: error de compilación (constructor con 4 args) o fallo de verificación.

- [ ] **Step 3: Extender `TriviaService`**

- Añadir campo `private final TriviaScoreRepositorio scores;` y 5º parámetro en el constructor.
- En `responder`, tras el gate y antes del pago:

```java
if (!repo.registrarRespuesta(actorId, preguntaId, acierto)) {
    return new Resultado(Estado.YA_RESPONDIDA, p.correcta(), 0, 0);
}
scores.registrar(actorId, acierto);            // marcador: cuenta acierto o fallo (respuesta nueva)
if (!acierto) return new Resultado(Estado.FALLO, p.correcta(), 0, 0);
```

- Cambiar `ranking` para leer del marcador:

```java
public java.util.List<TriviaRepositorio.FilaTrivia> ranking(int limite) { return scores.ranking(limite); }
```

- Añadir:

```java
/** Marcador acumulado del usuario (para /trivia stats); vacío si no ha jugado. */
public Optional<TriviaScoreRepositorio.Marcador> marcador(long actorId) { return scores.de(actorId); }
```

Actualizar el Javadoc de clase para mencionar el marcador. `aciertosDe`/`total` se conservan.

- [ ] **Step 4: Wiring en `Main`**

`Main.java:505-508`:

```java
// Trivia (F4): respuesta one-shot que paga coins+XP; marcador acumulado en trivia_scores.
TriviaScoreRepositorio triviaScoreRepo = new TriviaScoreRepositorio(db.dataSource());
TriviaService triviaService = new TriviaService(
        new TriviaRepositorio(db.dataSource()), economiaRepo, xpService, usuarios, triviaScoreRepo);
comandos.add(new TriviaComando(triviaService));
listeners.add(new TriviaListener(triviaService));
```

Añadir el import `import com.gymprofit.bot.db.TriviaScoreRepositorio;` (junto a los demás `db.*`).

- [ ] **Step 5: Correr los tests del service + compilar todo**

Run: `sh ./mvnw -B -q -Dtest=TriviaServiceTest test` → verde.
Run: `sh ./mvnw -B -q -DskipTests package` → BUILD SUCCESS (compila con el wiring nuevo).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/TriviaService.java \
        src/test/java/com/gymprofit/bot/services/TriviaServiceTest.java \
        src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(trivia): TriviaService registra el marcador y el ranking lee de trivia_scores"
```

---

### Task 3: `/trivia stats [@usuario]` + i18n

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/comunidad/TriviaComando.java`
- Modify: `src/main/resources/messages_es.properties`
- Modify: `src/main/resources/messages_en.properties`

- [ ] **Step 1: Añadir las claves i18n (ES y EN)**

En `messages_es.properties` (junto a las demás `trivia.*` y `comando.trivia.*`):

```properties
comando.trivia.stats.desc=Muestra tu marcador de trivia (o el de otro miembro)
comando.trivia.opcion.usuario=Miembro cuyo marcador quieres ver
trivia.stats.titulo=🧠 Marcador de trivia — {0}
trivia.stats.cuerpo=✅ Aciertos: **{0}**\n❌ Fallos: **{1}**\n🎯 Acierto: **{2}%**\n🎮 Partidas: **{3}**\n🔥 Mejor racha: **{4}**\n⚡ Racha actual: **{5}**
trivia.stats.vacio={0} todavía no ha jugado a la trivia.
```

En `messages_en.properties` (mismas claves):

```properties
comando.trivia.stats.desc=Show your trivia scoreboard (or another member's)
comando.trivia.opcion.usuario=Member whose scoreboard you want to see
trivia.stats.titulo=🧠 Trivia scoreboard — {0}
trivia.stats.cuerpo=✅ Correct: **{0}**\n❌ Wrong: **{1}**\n🎯 Accuracy: **{2}%**\n🎮 Games: **{3}**\n🔥 Best streak: **{4}**\n⚡ Current streak: **{5}**
trivia.stats.vacio={0} hasn't played trivia yet.
```

- [ ] **Step 2: Registrar el subcomando `stats` en `definicion()`**

En el `.addSubcommands(...)` añadir, con la opción usuario opcional:

```java
sub("stats", "comando.trivia.stats.desc").addOptions(
        new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.trivia.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trivia.opcion.usuario")))
```

- [ ] **Step 3: Enrutar y pintar `stats`**

En el `switch (sub)` añadir `case "stats" -> stats(evento, locale);` y el método:

```java
/**
 * Pinta el marcador acumulado (aciertos, fallos, % de acierto, partidas y rachas) del invocador o del
 * {@code usuario} indicado. Público: es una tarjeta de logros, no revela ninguna respuesta. Si el objetivo
 * no ha jugado, avisa sin fila. El % se calcula con guarda de partidas=0.
 */
private void stats(SlashCommandInteractionEvent evento, Locale locale) {
    net.dv8tion.jda.api.entities.User objetivo = evento.getOption("usuario",
            evento.getUser(), OptionMapping::getAsUser);
    Optional<TriviaScoreRepositorio.Marcador> mOpt = trivia.marcador(objetivo.getIdLong());
    if (mOpt.isEmpty()) {
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.TRIVIA, locale,
                Messages.get(locale, "trivia.stats.vacio", objetivo.getAsMention()))).queue();
        return;
    }
    TriviaScoreRepositorio.Marcador m = mOpt.get();
    int pct = m.partidas() == 0 ? 0 : m.aciertos() * 100 / m.partidas();
    String cuerpo = Messages.get(locale, "trivia.stats.cuerpo",
            m.aciertos(), m.fallos(), pct, m.partidas(), m.mejorRacha(), m.rachaActual());
    evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
            Messages.get(locale, "trivia.stats.titulo", objetivo.getEffectiveName()), cuerpo).build()).queue();
}
```

Añadir el import `import com.gymprofit.bot.db.TriviaScoreRepositorio;`. Nota: `evento.getOption(nombre,
default, mapper)` devuelve el default si la opción falta → resuelve el objetivo en una línea. `stats` es
**público** (no efímero): es un marcador, no revela preguntas.

- [ ] **Step 4: Actualizar el Javadoc de clase**

En la cabecera de `TriviaComando`, cambiar «subcomandos (jugar, ranking)» por «subcomandos (jugar,
ranking, stats)» y añadir una frase sobre `stats` (marcador acumulado, público).

- [ ] **Step 5: Verificar que compila y que las claves existen en ambos idiomas**

Run: `sh ./mvnw -B -q -DskipTests package` → BUILD SUCCESS.
Comprobar manualmente que cada clave nueva está en `messages_es.properties` **y** `messages_en.properties`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/comunidad/TriviaComando.java \
        src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(trivia): /trivia stats [usuario] con el marcador acumulado"
```

---

### Task 4: `./mvnw verify` completo + docs + mapa

**Files:**
- Modify: `docs/decisions.md` (ADR-029)
- Modify: `docs/architecture.md`
- Modify: `CHANGELOG.md`
- Modify: `README.md`, `README.en.md`
- Modify: `docs/architecture-map.json`, `docs/architecture-map.html`

- [ ] **Step 1: Build completo en verde**

Run: `sh ./mvnw -B clean verify`
Expected: BUILD SUCCESS; `Tests run:` sube respecto a 657 (nuevos tests del repo + service). Pegar la
línea `Tests run:` en el reporte.

- [ ] **Step 2: ADR-029 en `docs/decisions.md`**

Confirmar que el último ADR es 028. Añadir `## ADR-029 — marcador de trivia (trivia_scores)` en el mismo
estilo: **Contexto** (trivia_scores vacía desde V1; ranking recontaba trivia_respuestas), **Decisión**
(poblarla por UPSERT atómico tras el gate; `racha_actual` persistida; ranking lee de scores; `/trivia
stats`; backfill gaps-and-islands, racha viva histórica no reconstruida), **Consecuencias** (V39; repo
`TriviaScoreRepositorio`; fuera: logros por racha, reset, ranking por %/racha).

- [ ] **Step 3: `docs/architecture.md`**

Añadir `stats` a `/trivia` en la lista de comandos; mencionar `TriviaScoreRepositorio` en la capa `db/`;
actualizar el rango de migraciones a incluir **V39** y añadir «marcador de trivia» a los temas.

- [ ] **Step 4: `CHANGELOG.md`**

En `[Sin publicar] / ### Añadido`: `/trivia stats` + marcador acumulado (aciertos, fallos, %, partidas,
rachas), ranking desde `trivia_scores`, migración `V39` (columna `racha_actual` + backfill).

- [ ] **Step 5: READMEs**

`README.md` y `README.en.md`: en la línea de `/trivia`, sumar `stats` (jugar · ranking · stats).

- [ ] **Step 6: Mapa de arquitectura (estilo terminal, solo datos)**

- `docs/architecture-map.json`: en `bot`, la categoría de comandos con `/trivia` → añadir `stats`;
  `persistence.flyway` V1-V38 → **V1-V39** (los dos sitios). JSON válido.
- `docs/architecture-map.html`: en la fila `/trivia` sumar `stats`; Flyway V1–V38 → V1–V39. Mantener
  EXACTAMENTE el estilo terminal (clases `tag`/`k`/`n`/`dsc`), sin tocar el footer (sigue sin «generado por»).

- [ ] **Step 7: Commit de docs**

```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md \
        docs/architecture-map.json docs/architecture-map.html
git commit -m "docs(trivia): marcador (trivia_scores) — ADR-029, architecture, changelog, READMEs y mapa"
```

---

## Notas de despliegue (para el controlador, tras la review final)

- **Reiniciar el bot**: aplica V39 (columna + backfill) y re-registra `/trivia` (nuevo subcomando `stats`).
- **No** requiere `/setup` (el canal 🧠・trivia ya existe; no cambian intros).
- Smoke: jugar varias, `/trivia stats` propio y `/trivia stats @otro`, comprobar aciertos/fallos/%/mejor
  racha/racha actual; fallar y ver la racha actual a 0 conservando la mejor; `/trivia ranking` ordena por
  aciertos. Verificar que el backfill dejó tu marcador previo (los 2 aciertos + 1 fallo ya jugados).
- Números: no hay constantes de balance nuevas.
