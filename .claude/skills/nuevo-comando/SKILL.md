---
name: nuevo-comando
description: Scaffold de un slash command de GymProBot — clase en commands/, registro en JDA, claves i18n ES+EN, permisos, cooldown y test. Usar al crear cualquier comando nuevo.
---

# Nuevo slash command

Pasos para añadir un slash command respetando las reglas del repo
([`rules/coding-rules.md`](../../../rules/coding-rules.md)).

1. **Clase en `commands/`** (subpaquete por categoría, p. ej. `commands/fitness/`).
   Una clase por comando. La lógica de negocio va en un **service** (`services/`), no en la
   clase del comando.
2. **Registro** de la definición en JDA con:
   - `setNameLocalization` / `setDescriptionLocalization` para **ES y EN** (SPEC §8).
   - `setDefaultPermissions(...)` si es un comando de **staff**.
3. **Textos por i18n:** añadir las claves en `messages_es.properties` **y**
   `messages_en.properties` (mismo conjunto). Nada hardcodeado. Obtenerlas con
   `Messages.get(locale, clave, args...)`.
4. **Resiliencia:** si escribe en BD o llama a la API → `deferReply()`, **cooldown** y (API)
   reintento con backoff; mensaje amable si la API no responde.
5. **Respuesta con `EmbedFactory`** (nunca `new EmbedBuilder()` suelto). Color = categoría.
6. **Test:** JUnit 5 + Mockito para el service; si toca la API, `MockWebServer`.
7. **Docs en el mismo commit:** tabla de comandos de `README.md`/`README.en.md`,
   `CHANGELOG.md` y, si cambia la carpeta, su README.

Verificación: `./mvnw verify` en verde antes de dar por hecho el comando. Lo que requiera
Discord en vivo se marca como "pendiente de smoke test manual" si no se puede ejecutar.
