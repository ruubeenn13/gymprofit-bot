# Marcador de trivia (`trivia_scores`) — diseño

**Fecha:** 2026-07-27
**Fase:** F4 Competición (extensión de trivia jugable)
**Estado:** aprobado, listo para plan

## Objetivo

Dar uso a la tabla `trivia_scores` (creada vacía en V1, nunca escrita) como **marcador acumulado por
usuario**: aciertos, fallos, partidas, mejor racha y racha viva. Mostrarlo con `/trivia stats [@usuario]`
y usarlo como fuente del ranking (más eficiente que recontar `trivia_respuestas`).

## Contexto

- `trivia_respuestas(discord_id, pregunta_id, acierto, respondida_en, PK compuesta)` es el estado
  **one-shot**: una fila por (jugador, pregunta), impide recontestar/recobrar. Es la fuente actual de
  aciertos y ranking (vía `COUNT`).
- `trivia_scores(discord_id PK, aciertos, fallos, partidas, mejor_racha, actualizado_en)` existe desde
  V1 pero **ningún código la lee ni escribe** → está vacía. Es un marcador agregado por usuario.
- El registro de juego vive en `TriviaService.responder(actorId, preguntaId, letra, es)`: valida la
  pregunta, aplica el gate atómico `TriviaRepositorio.registrarRespuesta` (INSERT IGNORE) y solo paga
  coins+XP si registró por primera vez **y** acertó.

## Alcance (aprobado)

1. Poblar `trivia_scores` en cada respuesta nueva.
2. `/trivia stats [@usuario]` (propio o de otro miembro).
3. `/trivia ranking` pasa a leer de `trivia_scores` (misma salida, otra fuente).
4. Backfill de `trivia_scores` desde `trivia_respuestas` existentes.
5. `mejor_racha` = aciertos consecutivos sin fallar (por orden temporal), con `racha_actual` persistida.

## Esquema — migración V39

`V39__trivia_scores_racha_backfill.sql`:

1. **Columna nueva** para el contador de racha viva (permite UPSERT O(1) sin recalcular):
   ```sql
   ALTER TABLE trivia_scores ADD COLUMN racha_actual INT NOT NULL DEFAULT 0
       COMMENT 'Racha de aciertos seguidos en curso; se rompe (0) al fallar'
       AFTER mejor_racha;
   ```

2. **Backfill idempotente** desde `trivia_respuestas` (gaps-and-islands para `mejor_racha`):
   ```sql
   INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual)
   SELECT r.discord_id,
          SUM(r.acierto)          AS aciertos,
          SUM(1 - r.acierto)      AS fallos,
          COUNT(*)                AS partidas,
          COALESCE(mr.mejor, 0)   AS mejor_racha,
          0                       AS racha_actual
   FROM trivia_respuestas r
   LEFT JOIN (
       SELECT discord_id, MAX(len) AS mejor
       FROM (
           SELECT discord_id, COUNT(*) AS len
           FROM (
               SELECT discord_id,
                      ROW_NUMBER() OVER (PARTITION BY discord_id ORDER BY respondida_en, pregunta_id)
                    - ROW_NUMBER() OVER (PARTITION BY discord_id, acierto ORDER BY respondida_en, pregunta_id) AS grp
               FROM trivia_respuestas
               WHERE acierto = TRUE
           ) x
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
   - `acierto` es `BOOLEAN` (TINYINT): `SUM(acierto)` y `1 - acierto` funcionan.
   - Islands: agrupa aciertos consecutivos (`WHERE acierto = TRUE`) por el hueco entre el ROW_NUMBER
     global y el ROW_NUMBER por-valor; la longitud máxima de grupo = mejor racha.
   - `racha_actual = 0` en backfill: la racha histórica viva NO se reconstruye (deuda aceptada); se
     reconstruye al seguir jugando. `mejor_racha` histórica sí es exacta.
   - `ON DUPLICATE KEY` → re-ejecutable sin duplicar (aunque Flyway solo la aplica una vez).

## Repositorio nuevo — `TriviaScoreRepositorio`

`db/TriviaScoreRepositorio.java` (JDBC, patrón de los demás repos; cabecera + Javadoc).

```java
/** Marcador acumulado de un usuario. */
public record Marcador(int aciertos, int fallos, int partidas, int mejorRacha, int rachaActual) {}
```

- **`void registrar(long discordId, boolean acierto)`** — UPSERT atómico en una sentencia. El orden de
  la cláusula UPDATE es crítico: `mejor_racha` se evalúa **antes** que `racha_actual` para usar el valor
  viejo de `racha_actual`.
  ```sql
  INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual)
  VALUES (?, ?, ?, 1, ?, ?)
  ON DUPLICATE KEY UPDATE
      aciertos    = aciertos + VALUES(aciertos),
      fallos      = fallos   + VALUES(fallos),
      partidas    = partidas + 1,
      mejor_racha = GREATEST(mejor_racha, IF(?, racha_actual + 1, racha_actual)),
      racha_actual = IF(?, racha_actual + 1, 0);
  ```
  Parámetros: `discordId`, `acierto?1:0` (aciertos VALUES), `acierto?0:1` (fallos VALUES),
  `acierto?1:0` (mejor_racha inicial en INSERT), `acierto?1:0` (racha_actual inicial en INSERT),
  luego los dos booleans `acierto` de la cláusula UPDATE. **Fila nueva** (INSERT): `partidas=1`,
  `mejor_racha = acierto?1:0`, `racha_actual = acierto?1:0`. **Fila existente** (UPDATE): incrementa;
  `mejor_racha` usa `racha_actual` viejo + 1 si acierto; luego `racha_actual` se actualiza.

- **`Optional<Marcador> de(long discordId)`** — `SELECT aciertos, fallos, partidas, mejor_racha,
  racha_actual FROM trivia_scores WHERE discord_id = ?`; vacío si no hay fila.

- **`List<FilaTrivia> ranking(int limite)`** — mueve el ranking aquí:
  ```sql
  SELECT discord_id, aciertos FROM trivia_scores
  WHERE aciertos > 0 ORDER BY aciertos DESC, mejor_racha DESC, actualizado_en ASC LIMIT ?
  ```
  Reusa el record `TriviaRepositorio.FilaTrivia(long discordId, int aciertos)` (o mueve el record; el
  plan decide — mantenerlo en `TriviaRepositorio` para no romper imports).

Errores → `DatabaseException` (patrón del resto).

## Servicio — `TriviaService` (se extiende, no se crea otro)

- Constructor gana `TriviaScoreRepositorio scores` (5º parámetro).
- `responder(...)`: tras `if (!repo.registrarRespuesta(...)) return YA_RESPONDIDA;` y **antes** de pagar,
  registrar el marcador para acierto Y fallo (el gate ya garantiza que es respuesta nueva):
  ```java
  scores.registrar(actorId, acierto);
  if (!acierto) return new Resultado(Estado.FALLO, p.correcta(), 0, 0);
  // ... paga ...
  ```
  Orden: gate → `scores.registrar` → pago. Si el UPSERT del marcador falla, propaga y no paga
  (consistente con el gate ya escrito; aceptable — el gate es el candado de idempotencia del pago).
- `ranking(int limite)`: delega ahora a `scores.ranking(limite)` (antes `repo.ranking`).
- **Nuevo** `Optional<TriviaScoreRepositorio.Marcador> marcador(long actorId)` → `scores.de(actorId)`.

`TriviaRepositorio.ranking` puede quedarse (sin caller) o eliminarse; el plan lo elimina si no se usa,
para no dejar código muerto, conservando `aciertosDe`/`total` que sí se usan.

## Comando — `/trivia stats [@usuario]`

`commands/comunidad/TriviaComando.java`:
- Subcomando `stats` con opción `usuario` (`OptionType.USER`, **opcional**; default = invocador).
- Resuelve el objetivo (`getOption("usuario")` → `getAsUser`, fallback al invocador).
- `service.marcador(objetivoId)`:
  - vacío → embed aviso `trivia.stats.vacio` (propio: "aún no has jugado"; ajeno: "ese usuario no ha
    jugado"). Dos claves o una con placeholder de mención — el plan usa una con `{0}` = mención.
  - presente → embed `EmbedFactory` `Tipo.STATS` con: aciertos, fallos, **% acierto**
    (`aciertos*100/partidas`, guardando `partidas==0` → 0), partidas, **mejor racha**, racha actual.
- Título con la mención/nombre del objetivo. i18n ES+EN (todas las claves en ambos).
- El registro del subcomando `stats` en `TriviaComando.definicion()` (o donde se declaran jugar/ranking).

`/trivia ranking` no cambia de firma ni de salida; solo su fuente (vía `service.ranking`).

## i18n (claves nuevas, ES + EN)

- `trivia.stats.titulo` (con `{0}` = nombre/mención)
- `trivia.stats.cuerpo` (placeholders: aciertos, fallos, %acierto, partidas, mejor_racha, racha_actual)
- `trivia.stats.vacio` (`{0}` = mención; texto según propio/ajeno se resuelve con una sola clave
  neutra, p. ej. "{0} todavía no ha jugado a la trivia")
- `trivia.stats.opcion.usuario` (descripción de la opción)
- Etiquetas de campo si el embed usa campos separados.

## Tests

- **`TriviaScoreRepositorioTest`** (Testcontainers, se salta sin Docker en local, corre en CI):
  - `registrar` acierto en fila nueva → aciertos=1, partidas=1, mejor_racha=1, racha_actual=1.
  - `registrar` fallo tras acierto → fallos=1, racha_actual=0, mejor_racha conserva 1.
  - secuencia A,A,F,A → aciertos=3, fallos=1, partidas=4, mejor_racha=2, racha_actual=1.
  - `ranking` ordena por aciertos DESC y excluye aciertos=0.
  - **backfill**: sembrar `trivia_respuestas` con una secuencia conocida, aplicar migración (ya aplicada
    por `Database.migrar()` en el arranque del contenedor) — validar que `trivia_scores` refleja
    aciertos/fallos/partidas/mejor_racha correctos. (Si el backfill corre en la migración, sembrar
    ANTES no es posible con Flyway ya aplicado; en su lugar, test dedicado que inserta en respuestas y
    llama a una consulta equivalente, o validar el UPSERT `registrar` que es el camino en caliente. El
    plan resuelve: cubrir `registrar` a fondo + un test SQL del cálculo de islands sobre datos sembrados
    a mano vía `registrar`, no dependiendo del backfill de arranque.)
- **`TriviaServiceTest`** (actualizar): mockear `TriviaScoreRepositorio`; verificar `scores.registrar`
  se llama en ACIERTO y en FALLO, y **no** se llama en YA_RESPONDIDA ni NO_EXISTE. El pago sigue solo
  en acierto nuevo.

## Orden de implementación (para el plan)

Migración V39 → `TriviaScoreRepositorio` + record `Marcador` → tests del repo → extender `TriviaService`
+ actualizar su test → `/trivia stats` + i18n → wiring en `Main` (instanciar repo, pasarlo al service)
→ docs (ADR-029, architecture, CHANGELOG, READMEs, mapa JSON+HTML).

## Deuda aceptada

- `racha_actual` histórica no reconstruida en el backfill (arranca 0). `mejor_racha` histórica sí exacta.
- N+1 no aplica: `stats` y `ranking` son una lectura cada uno.
- El pago y el marcador no están en una transacción única; el gate one-shot sigue siendo el candado del
  pago. Un fallo del UPSERT del marcador aborta antes de pagar (no cobra de más).

## Fuera de alcance

- Logros/insignias por racha o por nº de partidas.
- Reset de marcador.
- Ranking por racha o por % de acierto (solo por aciertos, como hoy).
