# Arquitectura de GymProBot

Amplía la §4 de [`GYMPROBOT_SPEC.md`](../GYMPROBOT_SPEC.md). Documento vivo: quien cambie la
estructura de paquetes, el flujo de datos o el hosting, actualiza este archivo **en el mismo
commit** (ver [`../CLAUDE.md`](../CLAUDE.md)).

## Visión general

```
Discord Gateway  ⇄  GymProBot (JDA 5, Render)
                        │
                        ├── BD del bot (Aiven MySQL · gymprofit_bot)
                        │     XP, coins, rachas, warns, tienda, retos, trivia, config…
                        │
                        └── API GymProFit (Render, context-path /api)
                              ejercicios, rutinas, logros, stats, sesiones, vinculación
```

- El bot **nunca** toca la BD de la app: todo dato de la app pasa por la **API REST**.
- El estado del bot vive **siempre** en su BD: el free tier de Render borra memoria y disco
  en cada reinicio.
- Health server propio en `/health` (JDK `com.sun.net.httpserver`, sin dependencias): lo usan
  el health check de Render y `keep-alive.yml`.

## Paquetes (`com.gymprofit.bot`)

| Paquete | Responsabilidad |
|---|---|
| `Main` | Arranque: health server → **Flyway** (`Database`) → **JDA** (`DiscordBot`) → (F1) listeners + jobs. Arranque degradado si falta `DB_URL` o `DISCORD_TOKEN` |
| `DiscordBot` | Fábrica de la conexión JDA: intents privilegiados (`GUILD_MEMBERS`, `MESSAGE_CONTENT`), cache de miembros, presencia |
| `db/Database` | Pool HikariCP + ejecución de migraciones Flyway; expone el `DataSource` a los repos |
| `config/` | Carga de env vars y constantes (`BotConfig`) |
| `commands/` | Un archivo por slash command (subpaquetes por categoría). Familias grandes de economía agrupadas en **subcomandos** (`/gremio`, `/banco`, `/mercado`, `/bolsa`, `/casino`) para no rebasar el límite de 100 slash commands de Discord |
| `events/` | Listeners: bienvenida/auto-roles, XP por mensaje, botones, auto-mod |
| `services/` | Lógica de negocio testeable (`XpService`, `EstadisticasService` —contadores en vivo—, `EventoService` —reto/evento—, `EconomyService`…) |
| `api/` | Cliente Retrofit hacia la API GymProFit (interfaces por dominio) |
| `db/` | Repositorios JDBC (HikariCP) + migraciones Flyway en `resources/db/migration` |
| `embeds/` | `EmbedFactory` central: única vía para crear embeds (paleta §7) |
| `i18n/` | `Messages` sobre `ResourceBundle` (ES/EN) |
| `jobs/` | Tareas programadas: `EnergiaJob` (regen de energía cada 30 min), `BolsaJob` (mueve precios de la bolsa cada 12 min), `SorteoJob`, retención de datos |

## Flujo de un slash command (objetivo F1)

1. JDA recibe la interacción → clase de `commands/`.
2. El comando resuelve el **locale** (config de servidor / override de usuario) y saca el
   texto de `i18n`.
3. Si escribe en BD o llama a la API: aplica **cooldown**, hace `deferReply()` y (para la API)
   reintento corto con backoff (SPEC §14).
4. Delega en un **service** (lógica testeable); la respuesta se construye con **EmbedFactory**.

## RPG económico (Fase 2)

Simulador de vida de ficción sobre la BD del bot (nada toca la API). Patrón común:

- **Catálogos en código** (no en BD): `Items`, `Trabajos`, `Mundos`, `Monstruos`, `Mazmorras`,
  `Recetas`, `Cofres`, `Encantamiento`, `Habilidad`, `Misiones`, `Insignias`, `Acciones`, `Picos`,
  `Minerales`, `Rango`. La BD guarda solo el **estado del jugador** (personaje, inventario, progreso,
  cartera, gremios…).
- **Services testeables** con el **azar inyectable** (`BatallaService.Aleatorio`) para que combate,
  minería, cofres, casino y bolsa tengan tests deterministas.
- **Monedero atómico**: todo movimiento de coins pasa por `EconomiaRepositorio` (operaciones
  `UPDATE … WHERE saldo>=importe`, nunca negativo, con ledger `transacciones`).
- **Anti-inflación**: cada sistema equilibra fuentes y **sumideros** (comisiones de mercado/banco,
  ventaja de la casa en el casino, coste de encantar/reparar/fundar gremio, durabilidad de picos).
- **Combate por turnos** con sesiones en memoria (`CombateSesion`) y botones (`CombateListener`);
  otras interacciones con confirmación por botones: duelos (`DueloListener`), trueques
  (`TruequeListener`), y registros en memoria (`TruequeRegistro`).
- **Migraciones Flyway V6–V22**: personajes, trabajo, inventario, mejoras, combate (equipo, mundos,
  cooldown, encantamientos), minería (+durabilidad), misiones, mercado, banco, gremios, bolsa,
  estudios, insignias.

Fases del RPG: F-ECO-0 cimientos → F-ECO-6 gambling (todas hechas) + combate COMBAT-1..6 + extras
(cofres, bolsa, robar). Ver [`superpowers/specs/2026-07-13-economia-rpg-vision.md`](superpowers/specs/2026-07-13-economia-rpg-vision.md).

## Autenticación bot → API

Ver ADR en [`decisions.md`](decisions.md). Resumen: cuenta de servicio con login `/auth` +
refresh; el **access token se cachea** y solo se renueva ante 401; ante 429 se respeta
`Retry-After` con backoff. Objetivo F3: rol `BOT` / endpoints `/bot/**` acotados.

## Hosting y observabilidad

- **Hosting:** el bot NO puede correr free en el mismo workspace que la API (las 750 h/mes
  free se comparten). Opciones y decisión en [`decisions.md`](decisions.md) (SPEC §14).
- **Zona horaria:** los jobs fijan `Europe/Madrid` explícitamente; el contenedor va en UTC.
  `TZ=Europe/Madrid` también en `render.yaml`.
- **`#bot-logs`:** canal privado de staff donde el bot publica arranques, errores no
  controlados y fallos de jobs (appender propio, F1).
- **Resiliencia ante la API:** `deferReply()` + reintento con backoff; si no responde,
  mensaje amable de indisponibilidad.
