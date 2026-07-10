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
| `commands/` | Un archivo por slash command, subpaquetes por categoría |
| `events/` | Listeners: bienvenida/auto-roles, XP por mensaje, botones, auto-mod |
| `services/` | Lógica de negocio testeable (`XpService`, `EstadisticasService` —contadores en vivo—, `EventoService` —reto/evento—, `EconomyService`…) |
| `api/` | Cliente Retrofit hacia la API GymProFit (interfaces por dominio) |
| `db/` | Repositorios JDBC (HikariCP) + migraciones Flyway en `resources/db/migration` |
| `embeds/` | `EmbedFactory` central: única vía para crear embeds (paleta §7) |
| `i18n/` | `Messages` sobre `ResourceBundle` (ES/EN) |
| `jobs/` | Tareas programadas (ejercicio del día, retos, sync de logros) |

## Flujo de un slash command (objetivo F1)

1. JDA recibe la interacción → clase de `commands/`.
2. El comando resuelve el **locale** (config de servidor / override de usuario) y saca el
   texto de `i18n`.
3. Si escribe en BD o llama a la API: aplica **cooldown**, hace `deferReply()` y (para la API)
   reintento corto con backoff (SPEC §14).
4. Delega en un **service** (lógica testeable); la respuesta se construye con **EmbedFactory**.

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
