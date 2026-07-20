---
name: nuevo-comando
description: Slash commands de GymProBot — clase en commands/, registro en JDA, claves i18n ES+EN, permisos, cooldown, test y qué desplegar después (reiniciar bot / setup / setup desde_cero). Usar al crear, modificar, renombrar, agrupar en subcomandos o borrar cualquier comando.
---

# Nuevo slash command

Pasos para añadir un slash command respetando las reglas del repo
([`rules/coding-rules.md`](../../../rules/coding-rules.md)).

0. **¿Familia existente? → subcomando, no comando nuevo** (ADR-011). Discord solo cuenta los
   comandos de **nivel superior** contra su límite de 100; los subcomandos son gratis (25 por
   comando). Si encaja en `/warn`, `/silenciar`, `/canal`, `/privacidad`, `/perfil`, `/inventario`,
   `/trabajo`, `/publicar`, `/gremio`, `/banco`, `/mercado`, `/bolsa` o `/casino`, se añade ahí con
   `SubcommandData` + rama en el `switch (evento.getSubcommandName())`. Vigilar el total < 100
   (`grep -c "comandos.add(" src/main/java/com/gymprofit/bot/Main.java`).
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

## Cierre obligatorio: decir qué hay que desplegar

**Siempre** que un cambio toque comandos, terminar el mensaje con un bloque **"Para verlo en el
servidor"** indicando qué tiene que hacer el usuario. No esperar a que pregunte. La regla, según lo
que se haya tocado (verificado en `RouterComandos.onGuildReady` y `SetupComando`):

| Tocado | Acción |
|---|---|
| Comandos: alta, baja, renombrado, opciones o subcomandos | **Reiniciar el bot** (`scripts/run-local.ps1`). El registro es `guild.updateCommands()` en `onGuildReady`: **`/setup` no registra comandos**. |
| Textos de intro (`intro.*` en i18n) o canales/roles/categorías nuevos en `SetupServidorPlan` | **`/setup` normal** (tras reiniciar). Crea lo que falte y **edita** la intro fijada de los canales que ya existen. No borra nada. |
| Panel de auto-roles (`panel.roles`) | **`/publicar panel`**. `/setup` se salta esa intro a propósito. |
| Posts iniciales de foro (FAQ, guías de foro) | Solo se publican al **crear** el canal → `/setup desde_cero:true`, o borrar ese canal y `/setup` normal. |
| Solo services, tests o docs | Nada. Decirlo también. |

Casi siempre es **reiniciar el bot + `/setup` normal**; `desde_cero` solo si hay que resembrar posts
de foro (borra canales y mensajes: proponerlo, nunca darlo por hecho).
