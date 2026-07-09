# `db/`

**Español**

Acceso a la BD del bot (Aiven MySQL, database `gymprofit_bot`): repositorios JDBC sobre HikariCP
y migraciones **Flyway** en `src/main/resources/db/migration`. Todo cambio de esquema es una
migración nueva (`V2__`, `V3__`…); nunca se edita una aplicada ni se toca la BD a mano. La tabla
`discord_links` **no** vive aquí (es dato de la app, en la BD de la API). Ver skill
`nueva-migracion`.

Migraciones actuales: `V1__esquema_inicial_f1.sql` (tablas de la Fase 1) y
`V2__seed_frases_trivia.sql` (seeds obligatorios: ≥50 preguntas de trivia, ≥30 frases, ES/EN).

_Ejemplo:_ nueva tabla `insignias` ⇒ `V3__crea_tabla_insignias.sql` + `InsigniaRepository`.

---

**English**

Bot DB access (Aiven MySQL, database `gymprofit_bot`): JDBC repositories over HikariCP and
**Flyway** migrations in `src/main/resources/db/migration`. Every schema change is a new
migration (`V2__`, `V3__`…); never edit an applied one or touch the DB by hand. The
`discord_links` table does **not** live here (it's app data, in the API's DB). See the
`nueva-migracion` skill.

Current migrations: `V1__esquema_inicial_f1.sql` (Phase 1 tables) and
`V2__seed_frases_trivia.sql` (mandatory seeds: ≥50 trivia questions, ≥30 quotes, ES/EN).

_Example:_ new `insignias` table ⇒ `V3__crea_tabla_insignias.sql` + `InsigniaRepository`.
