# Checklist de review — GymProBot

Antes de aprobar/mergear un PR (la plantilla de PR enlaza aquí):

## Build y tests
- [ ] `./mvnw verify` **en verde** (build + tests) con **evidencia real** en el PR.
- [ ] Tests para todo lo nuevo: cada service (JUnit 5 + Mockito), cliente API con
      `MockWebServer`.

## Seguridad
- [ ] **Sin secretos** en el código, en los tests ni en logs.
- [ ] Secrets solo por env vars (`BotConfig`).

## Idiomas
- [ ] Todo texto visible viene de i18n (nada hardcodeado).
- [ ] Claves nuevas presentes en **`messages_es` y `messages_en`** y **sincronizadas**
      (mismo conjunto de claves).

## Comando
- [ ] Permisos correctos: staff con `setDefaultPermissions`.
- [ ] **Cooldown presente** si escribe en BD o llama a la API.
- [ ] Input validado (rangos, cantidades…).
- [ ] Localización del comando (`setNameLocalization` / `setDescriptionLocalization`).

## Embeds
- [ ] Creados **solo** por `EmbedFactory`.
- [ ] Color = categoría, un solo emoji en el título, footer + timestamp, paginación si no
      cabe (SPEC §7).

## Base de datos
- [ ] Cambios de esquema como **migración Flyway nueva** (no se edita una aplicada).
- [ ] El bot no accede a la BD de la app (solo vía API REST).

## Documentación (viva, en el MISMO commit/PR)
- [ ] README de la carpeta tocada actualizado, con **ES y EN sincronizados**.
- [ ] `docs/architecture.md` y/o `docs/decisions.md` actualizados si cambia estructura/decisión.
- [ ] Tablas de comandos del `README.md` / `README.en.md` actualizadas.
- [ ] `CHANGELOG.md` con su entrada.
- [ ] Dependencias nuevas justificadas en `docs/decisions.md`.
- [ ] Ningún hash de commit inventado (los hashes se obtienen del repo real tras commitear).
