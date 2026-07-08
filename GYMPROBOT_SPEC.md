# GymProBot — Especificación para Claude Code

> Documento de arranque del repositorio `gymprofit-bot`. Contiene todas las decisiones tomadas
> durante la planificación. Claude Code debe usarlo como fuente de verdad para generar la
> estructura del repo, los archivos de contexto (.md), las skills y el código por fases.

---

## 1. Contexto del producto

- **GymProFit** es una app Android (Java) de entrenamientos y nutrición, con backend propio:
  - API REST: Spring Boot 3.5 / Java 21 / Maven, desplegada en **Render** (Docker, Frankfurt), context-path `/api`, Swagger en dev.
  - BD: **Aiven MySQL** en producción (MariaDB en local), esquema gestionado con **Flyway**.
  - Push: Firebase Cloud Messaging (Admin SDK en el servidor). Crashlytics en la app.
  - Multiidioma **ES/EN** end-to-end (interfaz, push y catálogo vía `Accept-Language`).
  - Roles: `GUEST`, `USER`, `ADMIN`. JWT 30 min + refresh token rotado. Rate-limit en `/auth/**`. Ownership anti-IDOR.
- **GymProBot** es el bot oficial de Discord del servidor de la comunidad GymProFit:
  comunidad fitness gamificada + puente en vivo con la app a través de su API.
- El proyecto ya **no** es un TFG: hay libertad total para modificar la API cuando el bot lo necesite.

## 2. Repositorio

- Nombre: `gymprofit-bot` (owner `ruubeenn13`). **Separado** del monorepo de GymProFit.
- Visibilidad: **público**.
- Licencia: **copiar el archivo `LICENSE` propietario del repo GymProFit** ("Todos los derechos
  reservados", bilingüe ES/EN) en el primer commit. No usar licencias del desplegable de GitHub.
- `.gitignore`: partir del template **Maven** y extenderlo con: `.idea/`, `*.iml`, `.vscode/`,
  `.env`, `logs/`, `*.log`, `target/` (si no estuviera). Debe existir **antes** del primer commit con código.
- Descripción del repo: `🤖 Bot oficial de Discord de GymProFit: comunidad fitness gamificada — XP, retos, economía y conexión en vivo con la API de la app. Java 21 + JDA.`

## 3. Stack técnico

| Área | Elección | Motivo |
|---|---|---|
| Lenguaje | **Java 21** (`maven.compiler.release=21`) | Mismo que la API → coherencia y reutilización de conocimiento. Ojo: la app Android es Java 11; el bot sigue a la API, no a la app |
| Librería Discord | **JDA 5** (última estable) | Librería Java más madura: slash commands, botones, select menus, modals |
| Build | **Maven** (+ `mvnw`) | Igual que la API |
| HTTP hacia la API | **Retrofit2 + OkHttp3 + Gson** | Mismo cliente que usa la app Android; patrones ya conocidos |
| BD del bot | **MySQL en el mismo servidor Aiven**, database nueva `gymprofit_bot` | Coste cero extra, TLS ya resuelto, estado fuera del FS efímero de Render |
| Migraciones | **Flyway** | Mismo criterio que la API: el esquema se versiona solo |
| Acceso a datos | JDBC + HikariCP (o jOOQ si crece) | Simple para el volumen de un bot |
| i18n | `ResourceBundle` con `messages_es.properties` / `messages_en.properties` | Mismo patrón que `MessageSource` en la API |
| Tests | JUnit 5 + Mockito | Igual que la API (que tiene 230 tests) |
| Logs | SLF4J + Logback (solo consola en prod) | FS de Render es efímero |
| Hosting | **Render instancia de pago (~7 $/mes, recomendado)** o workspace Render **separado** en free con ping externo — **nunca free en el mismo workspace que la API** (ver §14) | Las 750 h free/mes se comparten por workspace: dos servicios 24/7 suspenden todo a mitad de mes |

**Intents privilegiados de Discord** (activar en el Developer Portal): `GUILD_MEMBERS`
(bienvenida y auto-roles) y `MESSAGE_CONTENT` (XP por mensaje y auto-mod).

**Zona horaria**: todos los jobs programados usan `Europe/Madrid`, pero los contenedores de Render
corren en **UTC**. Fijar la TZ explícitamente en cada `@Scheduled`/scheduler (no confiar en la del
sistema) y, además, poner `TZ=Europe/Madrid` como env var en `render.yaml` — si no, el ejercicio del
día, los retos y los recordatorios se disparan a la hora equivocada.

## 4. Arquitectura

```
Discord Gateway ⇄ GymProBot (JDA, Render)
                      │
                      ├── BD del bot (Aiven MySQL · gymprofit_bot): XP, coins, rachas, warns, tienda…
                      │
                      └── API GymProFit (Render, /api): ejercicios, rutinas, logros, stats, vinculación
```

- El bot **nunca** toca la BD de la app directamente: todo dato de la app pasa por la API REST.
- Estado del bot siempre en su BD (los reinicios del free tier de Render borran memoria y disco).
- Estructura de paquetes propuesta:

```
com.gymprofit.bot
├── Main.java              → arranque JDA + health server + Flyway
├── config/                → carga de env vars, constantes
├── commands/              → un archivo por slash command (subpaquetes por categoría)
├── events/                → listeners (bienvenida, mensajes para XP, botones)
├── services/              → lógica de negocio (XpService, EconomyService, ChallengeService…)
├── api/                   → cliente Retrofit hacia la API GymProFit (interfaces por dominio)
├── db/                    → repositorios JDBC + migraciones Flyway en resources/db/migration
├── embeds/                → EmbedFactory central (única vía para crear embeds)
├── i18n/                  → Messages helper sobre ResourceBundle
└── jobs/                  → tareas programadas (ejercicio del día, retos, sync logros)
```

### 4.1 Autenticación bot → API (documentar en `docs/decisions.md`)

- **Corto plazo (F1):** cuenta de servicio en la app (usuario dedicado al bot). Login en
  `/auth` + flujo de refresh igual que la app Android. Credenciales en env vars.
  Para los KPIs de `/admin` la cuenta necesita rol `ADMIN` — asumirlo temporalmente.
- **Rate-limit**: la API limita `/auth/**` a 15 req/60s por IP (→ 429). El bot **cachea el access
  token** y solo llama a `/auth` para el login inicial y para refrescar cuando caduca (401);
  **nunca** re-loguea en cada petición. Ante un 429, respetar `Retry-After` con backoff — un bucle
  de reintentos puede auto-banear al bot.
- **Objetivo (F3):** crear en la API un rol `BOT` con permisos acotados y/o endpoints `/bot/**`
  específicos, para no tener un ADMIN completo circulando. La API ya se puede modificar libremente.

### 4.2 Endpoints de la API que consume el bot

| Uso | Endpoint |
|---|---|
| Catálogo de ejercicios (filtros por grupo, dificultad, nombre) | `GET /api/ejercicios` |
| Rutinas predefinidas | `GET /api/rutinas` |
| Catálogo de logros y logros por usuario | `GET /api/logros…` |
| KPIs globales (6) para stats del servidor | `GET /api/admin/…` |
| Sesiones del usuario vinculado (validación de retos, /mis-stats) | `GET /api/sesiones…` |
| Mediciones (para /mi-progreso) | `GET /api/mediciones-corporales/usuario/{id}/ordenadas` |
| Vinculación Discord ↔ app (**nuevo en F3**) | `POST/GET/DELETE /api/discord/…` |

El catálogo es bilingüe: enviar header `Accept-Language` según el idioma del servidor/usuario.

## 5. Funcionalidades por fases

> Regla de oro: **cada fase termina desplegada y funcionando** antes de empezar la siguiente.
> Todo comando responde en ES o EN según configuración (ver §8).

### Fase 1 — Núcleo (MVP)

| Módulo | Detalle |
|---|---|
| Bienvenida + auto-roles | Mensaje de bienvenida con embed de marca. Select menu de objetivo (Fuerza / Cardio / Pérdida de peso / General) que asigna rol |
| XP y niveles | XP por mensaje con cooldown anti-spam (60 s). `/nivel` (propio o de otro), `/top` (leaderboard XP). Curva de nivel documentada en el código |
| Consultas a la app | `/ejercicios [grupo] [dificultad]`, `/rutinas [nivel]` — embeds paginados con botones |
| Ejercicio del día | Job diario 8:00 Europe/Madrid que publica un ejercicio del catálogo en su canal + comando `/ejercicio-dia` |
| Calculadoras | `/imc`, `/calorias`, `/macros` — **portar la lógica de `CalculadoraNutricional` de la app** (Mifflin-St Jeor + factor actividad + reparto por objetivo). `/rm` (fórmula de Epley, indicar fórmula en el footer) |
| Frase motivadora | `/frase` + inclusión en el mensaje del ejercicio del día. Banco de frases propio en ES/EN |
| Auto-mod + warns | Anti-spam (repetición/flood), filtro de insultos configurable, `/warn`, `/warns`, escalado (3 warns → timeout). Registro en BD |
| Tickets | Botón "Abrir ticket" en canal de soporte → canal privado usuario+staff, botón de cierre con transcript simple |
| Anuncios | `/anunciar` (solo staff): embed de marca para updates de la app (changelog) |
| Base | `/ayuda` con listado por categorías, `/ping` |

### Fase 2 — Economía

| Módulo | Detalle |
|---|---|
| ProCoins | Moneda virtual. Se gana por actividad (XP → coins), retos y eventos. `/balance` |
| Racha diaria | `/daily`: check-in de entreno, racha con multiplicador de recompensa. Romper racha la resetea |
| Tienda | `/tienda` + `/comprar`: roles cosméticos (color de nombre), ventajas (ej. multiplicador XP temporal). Catálogo en BD, gestionable por staff |
| Insignias | Medallas coleccionables por hitos del servidor (no confundir con los logros de la app). `/insignias` |
| Sugerencias | `/sugerencia`: publica embed en canal dedicado con votación 👍/👎 y estados (pendiente/aceptada/rechazada) gestionados por staff |

### Fase 3 — Vinculación con la app (toca la API)

| Módulo | Detalle |
|---|---|
| Vinculación | Nueva tabla `discord_links` (userId app ↔ discordId) + endpoints en la API. Flujo: `/vincular` genera código de un solo uso que el usuario introduce en la app (o flujo inverso). `/desvincular` |
| Perfil personal | `/mis-stats` (sesiones, calorías, minutos, logros de la app), `/mi-progreso` (últimas mediciones y evolución de peso/IMC) — solo con cuenta vinculada |
| Compartir logro | Botón "Compartir" en `/mis-stats`/logro → publica embed dorado en el canal de logros |
| Anuncio automático de logros | Job que detecta logros nuevos de usuarios vinculados vía API y los anuncia (respetar opt-out por usuario) |
| Stats del servidor | `/server-stats` con los 6 KPIs globales de `/admin` |
| Retos semanales | Publicación automática lunes 9:00 + ranking al cierre. **Validación mixta**: si la cuenta está vinculada se valida contra sesiones reales de la API; si no, auto-reporte con `/reto-completar`. Los vinculados puntúan con verificación (distinguirlo en el ranking) |

### Fase 4 — Competición

| Módulo | Detalle |
|---|---|
| Duelos | `/duelo @usuario <apuesta>`: reto 1vs1 con ProCoins en juego, aceptación con botón, resolución por métrica del reto activo |
| Trivia | `/trivia` fitness y nutrición: banco de preguntas propio ES/EN, rondas con botones, premios en coins |
| Rankings | Semanal / mensual / histórico de XP, coins y retos. Reset programado con anuncio de podio |
| Eventos especiales | Sistema configurable por staff (ej. "Semana de Piernas"): multiplicadores, reto temático y rol conmemorativo |

## 6. Personalidad y tono

Mezcla controlada según contexto (definir en `CLAUDE.md` y aplicar en todos los textos):

- **Por defecto**: entrenador motivador — energía, empuje, sin gritar en cada frase.
- **Casual** (bienvenidas, daily, duelos, trivia): colega de gym, humor ligero y algún meme. Nunca humor a costa del físico de nadie.
- **Serio** (tickets, moderación, warns, anuncios): directo, profesional, cero bromas.
- Tuteo en ES; tono equivalente natural en EN (no traducción literal).

## 7. Sistema visual de embeds

Coherente con el design system de la app (dark mode es el modo por defecto de la app y el mayoritario en Discord).

### Paleta por categoría

| Categoría | Color | Hex | Origen |
|---|---|---|---|
| Marca, bienvenida, ejercicio del día, anuncios | Naranja | `#FF6A00` | `gp_primary` (modo oscuro de la app) |
| Logros, rachas, insignias | Dorado | `#E8B84B` | `gp_gold` — la app lo reserva exactamente para esto |
| Economía, tienda, retos completados | Verde | `#1E8E4A` | `gp_success` |
| Stats y perfil | Azul | `#378ADD` | Nuevo (no colisiona con la app) |
| Moderación y warns | Rojo | `#C62828` | `gp_error` |

### Reglas de embed (implementar en un `EmbedFactory` central; prohibido crear embeds a mano)

1. Color del embed = color de su categoría (tabla anterior).
2. Un emoji identificador por título, **máximo uno**: 📣 anuncios · 🏋️ ejercicio · 🏆 logros · 🔥 racha · 🪙 economía · 🎯 retos · 📊 stats · ⚔️ duelos · 🧠 trivia · 🛡️ moderación · 🎫 tickets · 💡 sugerencias.
3. Footer siempre: `GymProBot • GymProFit` + timestamp.
4. Fields alineados (inline) para datos; descripción corta con el tono de §6.
5. GIF/imagen grande solo en hitos: subida de nivel, logro desbloqueado, podio de reto.
6. Nada de walls of text: si no cabe en un embed, paginar con botones.

## 8. Idiomas (ES/EN)

- Todo texto visible sale de `messages_es.properties` / `messages_en.properties`. **Prohibido hardcodear strings.**
- Idioma por servidor (config staff) con posible override por usuario (guardado en BD).
- Las consultas a la API envían `Accept-Language` para recibir el catálogo localizado.
- Los comandos slash se registran con `setNameLocalization`/`setDescriptionLocalization` de JDA.

## 9. Seguridad

- Secretos **solo** por variables de entorno: `DISCORD_TOKEN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `GYMPROFIT_API_URL`, `BOT_SERVICE_USER`, `BOT_SERVICE_PASSWORD`. En local, archivo `.env` (ignorado por git).
- Nunca loggear tokens ni credenciales. Revisar antes de cada commit (checklist §12).
- Permisos de Discord mínimos necesarios; comandos de staff restringidos con `setDefaultPermissions`.
- Cooldowns en todos los comandos que escriben en BD o llaman a la API (anti-abuso).
- Validar siempre el input de comandos (rangos de calculadoras, cantidades de la tienda, etc.).
- Privacidad (GDPR): comando `/borrar-mis-datos` que elimina todas las filas del usuario en `gymprofit_bot` (y revoca la vinculación vía API si existe), retención mínima de datos y nota de privacidad en el README (qué se guarda y para qué).

## 10. Base de datos del bot (`gymprofit_bot`)

Tablas mínimas (crear vía migraciones Flyway `V1__`, `V2__`…):

`usuarios_discord` (discord_id PK, xp, nivel, coins, racha, ultima_racha_fecha, idioma, opt_out_logros),
`insignias` + `usuario_insignias`, `tienda_items` + `compras`, `warns`, `tickets`,
`sugerencias`, `retos` + `reto_participaciones`, `duelos`, `trivia_preguntas` + `trivia_scores`,
`config_servidor` (canales, idioma, roles de objetivo).

> La tabla de vinculación `discord_links` vive en la **BD de la API** (es dato de la app), no aquí.

**Seeds obligatorios** (migraciones de datos junto al esquema): banco inicial de ≥50 preguntas de
trivia y ≥30 frases motivadoras, ambos en ES y EN — sin ellos, `/trivia` y `/frase` nacen vacíos.

## 11. Estructura del repo y archivos a generar

Claude Code debe crear, siguiendo el modelo de "repositorio nativo de IA":

```
gymprofit-bot/
├── README.md                      → DOS CAPAS: arriba divulgativo para cualquiera (banner naranja, 4-5 badges, GIF demo, features, quickstart 5 pasos); abajo lo técnico (setup completo, env vars, arquitectura) en bloques <details>
├── CLAUDE.md                      → ver contenido abajo
├── README.en.md                   → versión inglesa del README principal, mismo contenido; ambos enlazados arriba ([Español] | [English])
├── CHANGELOG.md                   → formato Keep a Changelog; cada fase/feature deja su entrada (alimenta /anunciar)
├── LICENSE                        → copiado literal del repo GymProFit
├── .gitignore                     → Maven + .idea/ *.iml .vscode/ .env logs/ *.log
├── docs/
│   ├── architecture.md            → §4 ampliada: diagrama, paquetes, flujo de datos, hosting
│   └── decisions.md               → ADRs: auth bot→API (§4.1), BD compartida en Aiven, validación mixta de retos, hosting del bot (§14: pago ~7$/mes o workspace free separado, nunca free junto a la API)
├── rules/
│   ├── coding-rules.md            → Java 21, dominio en español (coherente con la app/API), strings solo por i18n, embeds solo por EmbedFactory, servicios testeables, SLF4J, secrets solo env
│   └── review-checklist.md        → compila + tests verdes, sin secretos, textos en ES y EN, permisos del comando correctos, cooldown presente, embed usa la paleta §7, toda la documentación afectada actualizada en el mismo commit y con ES/EN sincronizados
├── .github/
│   ├── ISSUE_TEMPLATE.md          → plantilla con contexto/fase/criterios de aceptación
│   ├── pull_request_template.md   → qué cambia, fase, checklist de review enlazada
│   └── workflows/
│       ├── ci.yml                 → mvn verify en cada PR con setup-java temurin JDK 21 (mismo patrón que la API)
│       └── keep-alive.yml         → ping a /health, SOLO si el bot corre en free (workspace separado); innecesario en instancia de pago (§14)
├── .claude/skills/
│   ├── nuevo-comando/SKILL.md     → scaffold de slash command: clase en commands/, registro, claves i18n ES+EN, test
│   ├── nueva-migracion/SKILL.md   → crear migración Flyway con numeración correcta
│   └── nuevo-embed/SKILL.md       → añadir método a EmbedFactory respetando paleta y reglas §7
├── src/main/java/…                → §4
├── src/main/resources/            → messages_es/en.properties, db/migration/, logback.xml
├── src/test/java/…
├── Dockerfile                     → mismo estilo que la API (para Render)
├── render.yaml                    → blueprint del servicio del bot (env vars sync:false)
└── pom.xml
```

### READMEs por carpeta

Cada carpeta significativa lleva su propio `README.md` **corto** (5-10 líneas por idioma): propósito de la
carpeta, reglas propias y un ejemplo. Aplica a: `docs/`, `rules/`, `.claude/skills/`, y dentro de
`src/main/java/com/gymprofit/bot/` a `commands/`, `events/`, `services/`, `api/`, `db/`, `embeds/`,
`i18n/` y `jobs/`. En los paquetes Java puede complementarse con `package-info.java` (forma
idiomática). Mantenimiento obligatorio: quien cambia la estructura o el propósito de una carpeta,
actualiza su README (está en `review-checklist.md`).

**Bilingüe (ES + EN, mismo contenido)**: el README principal son dos archivos enlazados arriba
(`README.md` en español + `README.en.md` en inglés). Los READMEs de carpeta son **un solo archivo**
con la sección en español arriba y la inglesa debajo — así una desincronización se ve en el mismo
diff. La checklist exige ambos idiomas sincronizados en cada cambio.

### Contenido mínimo de `CLAUDE.md`

- Qué es el proyecto (2 líneas) y enlace a este SPEC, `docs/architecture.md` y `rules/`.
- Comandos: `./mvnw verify` (build+tests), cómo arrancar en local con `.env`.
- Convenciones: dominio en español, i18n obligatorio, EmbedFactory obligatorio, una clase por comando, tests para cada service nuevo.
- Flujo de trabajo: trabajar por fases (§5); no empezar módulos de una fase sin cerrar la anterior; migraciones Flyway para cualquier cambio de esquema. Ramas: `main` protegida, rama por feature, PR con la plantilla de `.github/` y merge solo con CI en verde.
- Commits: **siempre sin** trailer `Co-Authored-By` ni pie "Generated with Claude Code" (puede fijarse también en los settings de Claude Code con `includeCoAuthoredBy: false`).
- Documentación viva: cada commit incluye, **en el mismo commit**, la actualización de toda la documentación afectada por el cambio (README de la carpeta tocada, `docs/architecture.md`, `docs/decisions.md`, tablas de comandos del README principal…). Si un documento debe referenciar el hash de un commit, el hash se obtiene **siempre del repo real tras commitear** (`git rev-parse --short HEAD` o `git log`) y se añade en un commit de docs posterior — **prohibido inventar o escribir hashes de memoria**.
- Definición de terminado (obligatoria): nada se entrega sin verificación completa — `./mvnw verify` en verde, tests para todo lo nuevo y la **evidencia real** (salida de build/tests) mostrada en la respuesta. Si algo falla, se itera hasta que pase; prohibido cerrar con "debería funcionar". Lo que exija Discord en vivo (slash commands, botones, jobs) se prueba contra el **servidor de pruebas con el bot/token de test** (separados de producción, crearlos antes de F1); si Claude Code no puede ejecutarlo, lo marca explícitamente como "pendiente de smoke test manual" — nunca lo da por verificado sin serlo.
- Prohibiciones: nunca commitear secretos; nunca llamar a la BD de la app directamente; no añadir dependencias sin justificarlo en `docs/decisions.md`.

## 12. Testing y CI

- Unitarios de todos los services (XP, economía, rachas, retos) con JUnit 5 + Mockito.
- Tests del cliente API con OkHttp `MockWebServer`.
- CI en GitHub Actions: `setup-java` (temurin, **JDK 21**) + `mvn verify` en cada PR. Dependabot activado (mismo patrón que el repo principal).

## 13. Decisiones ya tomadas (no reabrir sin motivo)

1. Repo separado, público, licencia propietaria de GymProFit.
2. Java 21 + JDA 5 + Maven. Nombre: **GymProBot**. Moneda: **ProCoins** (renombrable solo antes de F2).
3. BD propia en el mismo Aiven; nada de estado en memoria/disco de Render.
4. Validación de retos **mixta** (API si vinculado, auto-reporte si no).
5. Sin temporizador de descanso y sin recordatorios por DM (descartados explícitamente).
6. Idiomas ES + EN desde el día 1. Personalidad mixta (§6). Sistema visual §7 aprobado.

## 14. Operación y observabilidad

- **Canal `#bot-logs`** (privado, solo staff): el bot publica ahí sus arranques, errores no
  controlados y fallos de jobs. Implementar como appender/handler que envía embeds rojos con el
  stacktrace resumido. Es la alarma de "el bot se ha caído a las 3 AM".
- **Hosting del bot — restricción crítica**: Render regala 750 h de instancia free **por workspace y mes**,
  compartidas entre todos los servicios free; dos servicios 24/7 (~1.440 h) las agotan hacia el día 16 y
  Render suspende **todos** los free del workspace, API de producción incluida. Por tanto el bot **no**
  puede desplegarse gratis junto a la API. Opciones: **(a) instancia de pago ~7 $/mes (recomendada)**,
  (b) workspace de Render separado en free — con `/health` añadido al servicio de ping externo que ya
  mantiene despierta la API —, (c) VM Always Free de Oracle Cloud. Registrar la elección en `docs/decisions.md`.
- **Resiliencia ante la API** (no cold-start: la API se mantiene despierta con un ping externo cada 5 min):
  aun así, `autoDeploy: true` reinicia la API en cada push y hay ventanas breves de indisponibilidad.
  Todo comando que llame a la API hace `deferReply()` + reintento corto con backoff; si no responde,
  mensaje amable de "la API no está disponible ahora mismo, prueba en un momento".
- **`/version`**: muestra versión del `pom.xml`, fase actual y hash corto del commit desplegado
  (inyectado en build — coherente con la regla de hashes reales de `CLAUDE.md`).
- Health: endpoint `/health` (§3) usado por Render y por el workflow `keep-alive.yml`.
