---
name: nueva-migracion
description: Crear una migración Flyway de la BD del bot (gymprofit_bot) con la numeración correcta. Usar ante cualquier cambio de esquema o seed de datos.
---

# Nueva migración Flyway

La BD del bot (`gymprofit_bot`) se versiona **solo** con Flyway. Nunca se toca a mano ni se
edita una migración ya aplicada ([`rules/coding-rules.md`](../../../rules/coding-rules.md)).

1. **Ubicación:** `src/main/resources/db/migration/`.
2. **Nombre:** `V<n>__<descripcion_en_snake_case>.sql`, con `<n>` = siguiente número libre.
   Revisar el mayor `V` existente antes de elegir (no reutilizar ni saltar números).
   - Esquema: `V2__crea_tabla_warns.sql`
   - Seeds (datos): también como migración, p. ej. `V3__seed_trivia_es_en.sql`
3. **Contenido:** SQL idempotente donde aplique; nombres de tablas/columnas **en español**
   (dominio del proyecto). Recordar los **seeds obligatorios** (SPEC §10): ≥50 preguntas de
   trivia y ≥30 frases motivadoras, en ES y EN.
4. **No** incluir aquí la tabla `discord_links`: vive en la BD de la API, no en la del bot.
5. **Verificación:** Flyway aplica las migraciones al arrancar. Comprobar que el arranque
   local (o el test de migración) pasa, y `./mvnw verify` en verde.
6. **Docs:** si el cambio de esquema afecta a la arquitectura, actualizar
   `docs/architecture.md` y el README de `db/` en el mismo commit.
