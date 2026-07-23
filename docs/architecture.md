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
| `commands/` | Un archivo por slash command (subpaquetes por categoría). Las familias van agrupadas en **subcomandos** (`/warn`, `/silenciar`, `/canal`, `/privacidad`, `/perfil`, `/inventario`, `/trabajo`, `/publicar`, `/descansar`, `/gremio`, `/banco`, `/mercado`, `/bolsa`, `/casino`) para no rebasar el límite de 100 slash commands de Discord: 59 de nivel superior (ADR-011). `commands/consultas` agrupa lo que lee de la API/contenido de la app: `/ejercicios`, `/ejercicio-dia` y `/frase`, con `ConsultaAsincrona` como **punto único** de ejecución asíncrona y manejo de errores del módulo (lo usan también los componentes del paginador): API caída → aviso amable, cualquier otro fallo → log a nivel error + aviso genérico, y consumidor de fallo en los `queue()` para las interacciones caducadas |
| `events/` | Listeners: bienvenida/auto-roles, XP por mensaje, botones, auto-mod, `EjerciciosPaginadorListener` (flechas y ficha del catálogo, con el estado codificado en el customId) |
| `services/` | Lógica de negocio testeable (`XpService`, `EstadisticasService` —contadores en vivo—, `EventoService` —reto/evento—, `EconomyService`…) |
| `api/` | Cliente Retrofit2+OkHttp3 hacia la API GymProFit: `ApiClient` (Bearer por interceptor, renovación ante 401 —contando 401 en la cadena, no cualquier `priorResponse`—, timeouts 60 s por Render free más `callTimeout` de la llamada completa, executor propio de 4 hilos daemon y `cerrar()` que los libera), `TokenManager` (refresh serializado con caída a login), interfaces por dominio (`AuthApi`, `EjerciciosApi`) y DTOs como records. Lo consume `services/EjercicioService` (caché TTL 5 min acotada y de vuelo único por consulta+idioma + reintentos con backoff acotado, 429 respeta `Retry-After`) |
| `db/` | Repositorios JDBC (HikariCP) + migraciones Flyway en `resources/db/migration` |
| `embeds/` | `EmbedFactory` central: única vía para crear embeds (paleta §7) |
| `i18n/` | `Messages` sobre `ResourceBundle` (ES/EN) |
| `jobs/` | Tareas programadas: `EnergiaJob` (goteo de energía cada 30 min, +5, y no a los dormidos), `BolsaJob` (mueve precios de la bolsa cada 12 min), `EjercicioDiaJob` (publica el ejercicio del día a las **08:00 Europe/Madrid** en el canal `EJERCICIO_DIA` de cada servidor que lo tenga configurado), `SorteoJob`, retención de datos |

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
  `Minerales`, `Rango`, `Pasivos`. La BD guarda solo el **estado del jugador** (personaje, inventario, progreso,
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
- **Reintento tras despertar**: quien bloquea una acción por sueño (currar, minar, pelear,
  mazmorra) la deja guardada en `events/ReintentoRegistro` (en memoria, una por jugador, caduca a los
  30 min) y el descanso la **relanza al despertar**. Las acciones devuelven `MessageCreateData` en
  vez de enviarlo ellas, así el mismo código sirve para la respuesta normal y para el reintento.
- **Descanso como estado**: la energía se gana **durmiendo** (`DescansoService`), no por goteo. El
  cálculo (energía ganada, bono de resistencia, fatiga) es **puro y estático**, testeable sin BD; el
  tope depende de la cama (`Camas`), que sale del inventario. Trabajo, combate y minería reciben el
  servicio por constructor para bloquear al que esté dormido.
- **Efectos pasivos con ranuras**: el equipo y los vehículos dan bonos permanentes (sueldo,
  cooldown de trabajo, XP, energía, minería y combate) al equiparlos en ranuras que se desbloquean
  a los niveles 0/10/25/50. El catálogo vive en `Pasivos` (satélite de `Items`, como `Camas`); la
  tabla `pasivos_equipados` guarda solo la **referencia**, y `PasivoService` **recalcula contra el
  inventario** en cada consulta: vender, regalar, publicar en el mercado o trocar el ítem apaga su
  bono sin necesidad de hooks en `VentaService`, `RegaloService`, `MercadoService`,
  `TruequeService` ni `RoboService`. La suma y el topado son una función pura; los topes son
  **globales por tipo y saturantes**. Los bonos de combate entran en el **snapshot** de
  `BatallaService.nuevaSesion` (valen toda la pelea, sin consultas por turno) y la energía extra es
  un **segundo `UPDATE`** en `EnergiaJob`, sin tocar el pase masivo.
- **Ascensos de carrera**: los sectores del catálogo de trabajos se agrupan en 7 ramas
  (`Ascensos`, satélite de `Trabajos`); el tier deja de ser de libre acceso y se gana currando.
  La tabla `carreras` guarda el tier alcanzado por rama (referencia con `GREATEST`: nunca baja) y
  `personajes.turnos_puesto` cuenta la antigüedad del puesto. Cada salto exige antigüedad,
  estudios, la stat dominante de la rama y un coste en coins que se **quema** (sumidero
  antiinflación). El cobro es lo último que se valida: nunca se paga un ascenso fallido.
- **Migraciones Flyway V6–V26**: personajes, trabajo, inventario, mejoras, combate (equipo, mundos,
  cooldown, encantamientos), minería (+durabilidad), misiones, mercado, banco, gremios, bolsa,
  estudios, insignias, descanso, pasivos equipados, carreras.

Fases del RPG: F-ECO-0 cimientos → F-ECO-6 gambling (todas hechas) + combate COMBAT-1..6 + extras
(cofres, bolsa, robar). Ver [`superpowers/specs/2026-07-13-economia-rpg-vision.md`](superpowers/specs/2026-07-13-economia-rpg-vision.md).

## Setup del servidor

`/setup` (y `/setup desde_cero`) monta la estructura del servidor de forma idempotente y, además,
gestiona la **descripción del servidor** (solo en servidores `VERIFIED`/`PARTNERED`: Discord no
permite fijar ese campo vía API en el resto). Cada ejecución produce un **informe de cambios**: un
colector `RegistroCambios` anota lo creado/actualizado/eliminado por nombre —el contenido reaplicado
(intros, welcome, AFK, descripción) solo cuenta si **difiere** del actual—, `InformeSetup` lo
renderiza y `util/Embeds` lo trocea en varios embeds; el informe va a la respuesta del comando y, como
registro persistente, a `#bot-logs` (ADR-015).

## Autenticación bot → API

Ver ADR en [`decisions.md`](decisions.md). Implementado en `api/`: cuenta de servicio con
login `/auth` + refresh; el **access token se cachea** (`TokenManager`) y solo se renueva ante
401 —renovación **serializada** y con caída a login si el refresh también caduca—; ante 429,
`services/EjercicioService` respeta `Retry-After` con backoff (recortado a 10 s: detrás hay una
interacción de Discord esperando) y cachea las respuestas 5 min. Un fallo de red **durante el
login** viaja como `ApiException` con causa `IOException` y se reintenta como un 5xx; unas
credenciales rechazadas fallan al primer intento.
Objetivo F3: rol `BOT` / endpoints `/bot/**` acotados.

## Hosting y observabilidad

- **Hosting:** el bot NO puede correr free en el mismo workspace que la API (las 750 h/mes
  free se comparten). Opciones y decisión en [`decisions.md`](decisions.md) (SPEC §14).
- **Zona horaria:** los jobs fijan `Europe/Madrid` explícitamente; el contenedor va en UTC.
  `TZ=Europe/Madrid` también en `render.yaml`.
- **`#bot-logs`:** canal privado de staff donde el bot publica arranques, errores no
  controlados y fallos de jobs (appender propio, F1).
- **Resiliencia ante la API:** `deferReply()` + reintento con backoff; si no responde,
  mensaje amable de indisponibilidad.
