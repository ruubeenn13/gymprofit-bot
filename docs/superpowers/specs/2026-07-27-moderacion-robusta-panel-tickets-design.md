# Moderación robusta + panel de tickets (F1) — diseño

**Fecha:** 2026-07-27
**Fase:** endurecimiento de F1 (núcleo del servidor), tras cerrar el módulo de empresas.
**Precondición:** el stack de moderación (F1) y el sistema de tickets están desplegados y funcionando.

## Objetivo

Cerrar los huecos de moderación y tickets detectados en la auditoría:
1. Que las infracciones del **AutoMod nativo** de Discord cuenten en el sistema de warns/escalado del bot.
2. Un **anti-flood/anti-invites propio** en Java (control en tiempo real que AutoMod no da).
3. Exponer **`/warn quitar`** (revocación individual, ya está en el service) y **avisar por DM** al sancionado.
4. Dejar de **perder motivos en silencio** cuando falta `BOT_CRYPTO_KEY`.
5. Que **`/setup`** publique el **panel de tickets** (botón "Abrir ticket") sin depender de `/publicar panel`.

## Núcleo compartido (refactor) — la vía de sanción reusable

Hoy la aplicación del escalado (timeout/ban vía JDA) + el log + (nuevo) el DM viven dentro de `WarnComando`,
que necesita una `SlashCommandInteraction`. Se **extrae** a un aplicador reusable, **`AplicadorSanciones`**
(en `events/` o `services/moderacion/`, JDA-aware), con un método:

`aplicar(Guild guild, Member objetivo, long moderadorId, String motivo, boolean automatica) → Resultado`

que hace, en orden:
1. `ModeracionService.avisar(guildId, objetivoId, moderadorId, motivo)` → `ResultadoAviso(warnId,
   warnsActivos, AccionEscalado)`.
2. Aplica la `AccionEscalado` devuelta sobre Discord: `NINGUNA` nada; `TIMEOUT_1H`/`TIMEOUT_24H`
   `objetivo.timeoutFor(...)`; `BAN` `guild.ban(...)`. (Respeta jerarquía con `ModHelper` como ya se hace.)
3. Registra la sanción de escalado en el historial (`ModeracionService.registrar(...)`) igual que hoy.
4. **Log** en `bot-logs` (`ModHelper.registrarEnLogs`).
5. **DM best-effort** al usuario: "has recibido un aviso/timeout/ban en {server} por {motivo}"
   (`objetivo.getUser().openPrivateChannel()...queue(...)`, se traga el fallo si tiene los DMs cerrados).

`WarnComando` pasa a **delegar** en el aplicador (moderador = el staff que invoca; `automatica=false`). Los
listeners automáticos lo usan con `moderador = el propio bot` (`automatica=true`). Así el escalado, el log y
el DM son idénticos vengan de donde vengan.

### Endurecer el cifrado (parte del refactor)

`ModeracionService.avisar` deja de tragar el fallo de cifrado: `ResultadoAviso` gana un flag
`boolean motivoGuardado` (true si el motivo se cifró y persistió; false si faltaba `BOT_CRYPTO_KEY` y se
guardó `null`). El aplicador/comando, si `!motivoGuardado`, añade un **aviso efímero** al moderador ("⚠️ el
motivo no se guardó: falta la clave de cifrado") además del `log.warn` actual. Los warns automáticos solo lo
loguean (no hay moderador humano a quien avisar).

## Funcionalidades

### 1. AutoMod nativo → warn interno

Listener del evento de ejecución de AutoMod de JDA (**confirmar el nombre exacto en JDA 5**: p. ej.
`net.dv8tion.jda.api.events.automod.AutoModExecutionEvent`). Cuando Discord AutoMod actúa sobre un usuario:
- **Anti-ráfaga:** un `Cooldown` por usuario (30 s); si el usuario ya generó un auto-warn hace <30 s, se
  ignora (solo se loguea) para no escalar a ban de golpe con una ráfaga.
- Si pasa el gate: `AplicadorSanciones.aplicar(guild, autor, botId, "AutoMod: " + regla, true)`.
- Ignora bots y a los propios miembros de staff (no auto-sancionar a moderadores).

Efecto: las infracciones nativas cuentan para el escalado y salen en `/modlogs`.

### 2. Anti-flood / anti-invites propio (listener Java)

Listener de `MessageReceivedEvent` (ignora bots, DMs y **staff**):
- **Flood:** ventana rodante por usuario; si manda **≥ FLOOD_MSGS mensajes en FLOOD_SEG segundos** → borra
  el/los mensajes recientes que pueda + auto-warn.
- **Invites de Discord:** regex sobre el contenido (`discord\.gg/`, `discord(app)?\.com/invite/`) → borra el
  mensaje + auto-warn.
- La acción es **borrar + auto-warn** vía `AplicadorSanciones.aplicar(..., "flood"/"invitación de Discord",
  true)`, con el **mismo anti-ráfaga** (30 s) para que un flood de 10 mensajes no ponga 10 warns.
- Exentos: bots, staff (roles `🧹 Staff`/`🛡️ Admin`/`👑 Fundador`, vía `ModHelper`), y las acciones son
  best-effort (borrar un mensaje ya borrado no rompe nada).
- **Enlaces externos generales quedan FUERA** (demasiados falsos positivos en una comunidad fitness que
  comparte vídeos/artículos): solo se bloquean invites de Discord.

Estado del flood en memoria (por usuario, ventana corta): un mapa acotado tipo el `Cooldown`/`ReintentoRegistro`
del proyecto; no se persiste.

### 3. `/warn quitar` + DM

- **`/warn quitar <usuario> [id]`** (o `<id>`): subcomando nuevo de `/warn` que llama a
  `ModeracionService.revocarWarn(id)` (ya existe) o, sin id, al último warn activo del usuario. Staff, con
  permiso de moderación como el resto de `/warn`. Responde con el resultado y lo loguea.
- **DM al sancionado:** ya integrado en `AplicadorSanciones` (§ núcleo). Cubre warn manual, escalado
  (timeout/ban) y los warns automáticos.

### 4. Panel de tickets en `/setup`

`/setup` (y `/setup desde_cero`) publica de forma **idempotente** el panel con el botón "Abrir ticket"
(`PublicarComando.BOTON_ABRIR`, `TicketListener` ya lo escucha) **fijado** en el canal `🎫・soporte`, dentro
de la instrumentación de cambios que ya tiene `/setup` (`RegistroCambios`): solo lo (re)publica si no hay ya
un panel del bot fijado con ese botón (evita duplicados en cada `/setup`). Reusa el embed/So botón de
`PublicarComando` (extraer el armado del panel a un punto reutilizable si hace falta, sin duplicar el
`customId`).

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `FLOOD_MSGS` | 5 | mensajes que disparan el flood |
| `FLOOD_SEG` | 7 | ventana del flood (segundos) |
| anti-ráfaga auto-warn | 30 s | máx 1 auto-warn por usuario en esta ventana |
| escalado (F1, intacto) | 3→1h, 5→24h, 7→ban | umbrales de `ModeracionService` |

## Tests

- **`AplicadorSanciones`** (Mockito, JDA mockeado): aplica la `AccionEscalado` correcta (NINGUNA/timeouts/ban),
  registra la sanción, loguea y **abre el DM** (verify sobre `openPrivateChannel`); un DM que falla no rompe la
  sanción; respeta jerarquía.
- **`ModeracionService.avisar`** (actualizado): `motivoGuardado=true` con clave; `false` sin `BOT_CRYPTO_KEY`
  (motivo `null`, sin excepción). Los tests existentes de escalado siguen verdes.
- **Anti-flood/invites** (la parte pura testeable): un **detector puro** (`DeteccionAbuso` o similar) —
  `esFlood(historialTimestamps, ahora)` y `tieneInviteDiscord(texto)` — testeado sin JDA (ventana, regex,
  fronteras). El listener (JDA) se prueba en smoke.
- **AutoMod→warn:** el anti-ráfaga (cooldown) como función pura/testeable; el listener en smoke.
- Baseline actual: 638 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-027** — moderación robusta (AutoMod→warn, anti-flood propio, DM, cifrado, panel de tickets en setup).
- `docs/architecture.md`: listeners nuevos en `events/`, el `AplicadorSanciones` compartido, y la nota de que
  `/setup` publica el panel de tickets.
- `CHANGELOG.md`: entrada del módulo.
- `README.md` / `README.en.md`: `/warn` suma `quitar`; nota del anti-flood y del auto-registro de AutoMod.

## Despliegue

Al cerrar: **reiniciar bot** (listeners nuevos + `/warn quitar` re-registra el comando) **y re-ejecutar
`/setup`** (para que publique el panel de tickets en `🎫・soporte`). El anti-flood necesita el intent
`MESSAGE_CONTENT` (ya activo, se usa para XP). El AutoMod→warn necesita que el bot tenga permiso de moderación
(timeout/ban) y que AutoMod esté creado (lo hace `/setup`). Smoke: flood de 5 msgs en <7 s → borrado + warn +
(al 3º) timeout + DM; pegar una invitación `discord.gg/...` → borrado + warn; disparar una regla de AutoMod →
aparece un warn en `/modlogs`; `/warn quitar`; sin `BOT_CRYPTO_KEY` → aviso efímero al moderador; `/setup` en
un server nuevo deja el botón de ticket fijado y funcional.

## Fuera de alcance

- Tickets: modal de asunto/categorías, claim/asignación de staff, reapertura, transcript sin tope (todo lo no
  elegido en el brainstorm).
- Moderación: bloqueo de enlaces externos generales; auto-registro de bans/kicks/timeouts hechos fuera del bot
  (manual o por otros bots); notificación de escalado por otro canal que no sea el DM + `bot-logs`.

## Orden de implementación (subagent-driven; la lógica de sanción lleva review)

- **T1**: `AplicadorSanciones` (escalado + log + DM, reusable) + `ResultadoAviso.motivoGuardado` (endurecer
  cifrado) + `WarnComando` delega en él. Tests del aplicador + del flag. **Review** (re-toca la aplicación de
  sanciones/escalado). 
- **T2**: `DeteccionAbuso` (detector puro flood/invite) + tests; listener anti-flood/invites que borra +
  auto-warn (anti-ráfaga) usando el aplicador; wiring. **Review** (borra mensajes + sanciona en vivo).
- **T3**: listener AutoMod→warn (anti-ráfaga, ignora staff/bots) + wiring. Confirmar el evento JDA.
- **T4**: `/warn quitar` (revocación individual) + i18n.
- **T5**: panel de tickets en `/setup` (idempotente, instrumentado en `RegistroCambios`).
- **T6**: docs (ADR-027, architecture, CHANGELOG, READMEs) + `clean verify` final.
