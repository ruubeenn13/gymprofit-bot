# `db/`

**Español**

Acceso a la BD del bot (Aiven MySQL, database `gymprofit_bot`): repositorios JDBC sobre HikariCP
y migraciones **Flyway** en `src/main/resources/db/migration`. Todo cambio de esquema es una
migración nueva (`V2__`, `V3__`…); nunca se edita una aplicada ni se toca la BD a mano. La tabla
`discord_links` **no** vive aquí (es dato de la app, en la BD de la API). Ver skill
`nueva-migracion`.

Migraciones actuales: `V1__esquema_inicial_f1.sql` (tablas de la Fase 1),
`V2__seed_frases_trivia.sql` (seeds obligatorios: ≥50 preguntas de trivia, ≥30 frases, ES/EN),
y `V3`–`V25` (módulos posteriores de F1 y fases RPG/economía).

_Ejemplo:_ nueva tabla `insignias` ⇒ `V3__crea_tabla_insignias.sql` + `InsigniaRepository`.

---

**English**

Bot DB access (Aiven MySQL, database `gymprofit_bot`): JDBC repositories over HikariCP and
**Flyway** migrations in `src/main/resources/db/migration`. Every schema change is a new
migration (`V2__`, `V3__`…); never edit an applied one or touch the DB by hand. The
`discord_links` table does **not** live here (it's app data, in the API's DB). See the
`nueva-migracion` skill.

Migrations so far: **V1–V25**. F1: `V1` (base schema), `V2` (trivia/quote seeds), `V3` (events),
`V4` (moderation), `V5` (raffles), `V23` (rest/sleep), `V24` (daily exercise history). RPG/economy 
(Phase 2): `V6` characters + `transacciones` ledger, `V7` work, `V8` inventory, `V9` upgrades, 
`V10`–`V13` combat (equipment, worlds progress, cooldown, enchantments), `V14`–`V15` mining 
(+durability), `V16` missions, `V17` studies, `V18` badges, `V19` market, `V20` bank, `V21` guilds, 
`V22` stock market, `V25` passive-effect slots (`pasivos_equipados`). Never edit an applied
migration; add a new `V26__…`.

_Example:_ new `insignias` table ⇒ `V23__crea_tabla_x.sql` + its `Repositorio`.
