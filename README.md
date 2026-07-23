<!-- Idioma / Language -->
**[Español](README.md)** | [English](README.en.md)

<h1 align="center">🤖 GymProBot</h1>

<p align="center">
  <em>El bot oficial de Discord de <strong>GymProFit</strong>: comunidad fitness gamificada —
  XP, retos, economía y conexión en vivo con la API de la app.</em>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="JDA" src="https://img.shields.io/badge/JDA-5-5865F2">
  <img alt="Build" src="https://img.shields.io/badge/build-Maven-C71A36">
  <img alt="CI" src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF">
  <img alt="Licencia" src="https://img.shields.io/badge/licencia-propietaria-lightgrey">
</p>

<!-- TODO: sustituir por un GIF real de demo cuando la Fase 1 esté desplegada. -->
<p align="center"><em>🎬 (demo próximamente — Fase 1)</em></p>

---

## ¿Qué hace?

GymProBot convierte el servidor de la comunidad en un gimnasio gamificado y lo conecta con la app:

- 🏋️ **Consulta la app** — ejercicios y rutinas del catálogo de GymProFit, en ES/EN.
- ⭐ **XP y niveles** — sube de nivel participando; leaderboard del servidor.
- 🪙 **Economía (ProCoins)** — gana monedas por actividad y retos; tienda y recompensas.
- 🎯 **Retos y competición** — retos semanales, duelos, trivia fitness y rankings.
- 📊 **Perfil vinculado** — tus stats y progreso reales de la app dentro de Discord.
- 🛡️ **Moderación y soporte** — auto-mod, warns, tickets y anuncios de marca.

> Todo bilingüe **ES/EN** desde el día 1 y con embeds coherentes con el diseño de la app.

## Inicio rápido (5 pasos)

1. **Clona** el repo: `git clone https://github.com/ruubeenn13/gymprofit-bot.git`
2. **Configura** el entorno: copia `.env.example` a `.env` y rellena tus valores.
3. **Compila y testea:** `./mvnw verify` (necesita **JDK 21**).
4. **Empaqueta:** `./mvnw -DskipTests package` → `target/gymprofit-bot.jar`.
5. **Arranca:** exporta las variables del `.env` y `java -jar target/gymprofit-bot.jar`.
   Comprueba `http://localhost:8080/health` → `OK`.

> Estado actual: **Fase 1 (Núcleo) en curso**. Base lista (BD + Flyway, EmbedFactory, conexión
> a Discord) e infraestructura de slash commands.

### Comandos

| Comando | Qué hace |
|---|---|
| `/ping` | Comprueba que el bot responde y muestra su latencia |
| `/nivel [usuario]` | Muestra tu nivel, XP y progreso (o los de otro usuario) |
| `/top` | Ranking de XP del servidor |
| `/config` | Configuración del servidor: canales, roles de objetivo e idioma (solo staff) |
| `/setup [desde_cero]` | Monta la estructura del servidor (roles, canales, permisos) y autoconfigura; `desde_cero` borra todo antes (solo admin) |
| `/limpiar <cantidad>` | Borra los últimos N mensajes del canal (solo staff) |
| `/reto <texto>` | Fija el reto de la semana (contador `🎯 Reto` de las estadísticas, solo staff) |
| `/evento <nombre> <fecha>` | Fija el próximo evento con cuenta atrás (contador `⏳ Evento`, solo staff) |
| `/frase` | Suelta una frase motivadora al azar del banco bilingüe (cooldown de 30 s) |
| `/ejercicios [grupo] [dificultad] [buscar]` | Explora el catálogo de ejercicios de la app: lista paginada con flechas y ficha completa de cada ejercicio |
| `/ejercicio-dia` | El ejercicio elegido para hoy, con su frase motivadora (el bot lo publica solo cada día a las 8:00 en el canal configurado) |

Además: **XP por mensaje** (con cooldown anti-spam de 60 s) que sube de nivel y lo anuncia, y
**bienvenida + auto-roles** (embed al entrar un miembro con menú de objetivo que asigna el rol).

### RPG / Economía (ficción)

Simulador de vida de ficción sobre la comunidad. **Coins virtuales**, no comprables con dinero real
ni convertibles. Por bloques:

- **Personaje y progresión:** `/perfil` (ver · balance · insignias) · `/rank` · `/daily` ·
  `/entrenar` · `/estudiar` · rangos automáticos por nivel.
- **Descanso:** `/descansar` (dormir · despertar · estado). La energía se recupera **durmiendo**, y
  cuanto mejor sea tu cama (saco, colchón, piso, casa… o un hotel) más alto llegas.
- **Trabajo y tienda:** `/trabajo` (lista · elegir · currar · ascender · carrera) · `/tienda` · `/comprar` ·
  `/inventario` (ver · usar · vender) · `/mejoras` · `/mejorar` ·
  `/pasivos` (ver · equipar · quitar).
- **Combate:** `/mundos` · `/monstruos` · `/pelear` (por turnos, botones) · `/mazmorra` ·
  `/misiones` · `/equipar` · `/encantar`.
- **Minería y herrería:** `/minar` · `/reparar` · `/recetas` · `/craftear`.
- **Cofres:** `/cofres` · `/abrir`.
- **Entre jugadores:** `/regalar` · `/trueque` · `/robar` · `/mercado` (subcomandos).
- **Banca y azar:** `/banco` · `/bolsa` · `/casino` (subcomandos; todo ficción).
- **Social:** `/gremio` (grupos con canal privado, subcomandos).

Detalle y diseño en [`docs/superpowers/specs/2026-07-13-economia-rpg-vision.md`](docs/superpowers/specs/2026-07-13-economia-rpg-vision.md).

---

<details>
<summary><strong>🔧 Documentación técnica (setup completo, arquitectura, env vars)</strong></summary>

### Stack

- **Java 21** + **JDA 5** (Discord), build con **Maven** (wrapper `mvnw`).
- **Retrofit2 + OkHttp3 + Gson** hacia la API GymProFit (mismo cliente que la app Android).
- **BD del bot:** Aiven MySQL (database `gymprofit_bot`), esquema con **Flyway**, JDBC + HikariCP.
- **i18n** ES/EN con `ResourceBundle`. **Logs** SLF4J + Logback (solo consola).
- **Sin Spring:** health server con `com.sun.net.httpserver` del JDK; fat-jar con `maven-shade-plugin`.

### Arquitectura

```
Discord Gateway ⇄ GymProBot (JDA, Render)
                     ├── BD del bot (Aiven MySQL · gymprofit_bot): XP, coins, rachas, warns, tienda…
                     └── API GymProFit (Render, /api): ejercicios, rutinas, logros, stats, vinculación
```

El bot **nunca** toca la BD de la app: todo dato de la app pasa por la API REST. Detalle completo
en [`docs/architecture.md`](docs/architecture.md) y decisiones en [`docs/decisions.md`](docs/decisions.md).

### Estructura de paquetes (`com.gymprofit.bot`)

`config/` · `commands/` · `events/` · `services/` · `api/` · `db/` · `embeds/` · `i18n/` · `jobs/`
(cada uno con su README y `package-info.java`).

### Variables de entorno

| Variable | Uso |
|---|---|
| `DISCORD_TOKEN` | Token del bot de Discord |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | BD del bot (Aiven MySQL `gymprofit_bot`) |
| `GYMPROFIT_API_URL` | Base de la API GymProFit (p. ej. `https://gymprofit-api.onrender.com/api`) |
| `BOT_SERVICE_USER` / `BOT_SERVICE_PASSWORD` | Cuenta de servicio del bot en la app |
| `BOT_CRYPTO_KEY` | Clave AES-256 (32 bytes base64) para cifrar el texto libre con dato personal |
| `PORT` | Puerto del health server (Render lo inyecta; por defecto 8080) |
| `TZ` | `Europe/Madrid` (los jobs no confían en la TZ del sistema) |

Plantilla en [`.env.example`](.env.example). En local se exportan a mano; en Render se configuran
en el dashboard (ver [`render.yaml`](render.yaml)). **Nunca se commitean secretos.**

### Comandos de desarrollo

- **Arrancar/reiniciar en local:** doble clic en `scripts\arrancar-bot.bat`, o
  `.\scripts\run-local.ps1` desde la terminal (compila, carga `.env` y ejecuta).
  Guía completa de operación (reiniciar tras cambios, 24/7 en Render): [`docs/operacion.md`](docs/operacion.md).
- `./mvnw verify` — build + tests (gate de CI).
- `./mvnw -DskipTests package` — genera el fat-jar ejecutable.
- Reglas del repo: [`CLAUDE.md`](CLAUDE.md), [`rules/`](rules/). Fuente de verdad:
  [`GYMPROBOT_SPEC.md`](GYMPROBOT_SPEC.md).

### Despliegue

Docker multi-stage ([`Dockerfile`](Dockerfile)) sobre Render (blueprint [`render.yaml`](render.yaml)).
Health check en `/health`. **Hosting:** el bot no puede correr free en el mismo workspace que la
API (SPEC §14 / ADR-004); ver [`docs/decisions.md`](docs/decisions.md).

### Privacidad (GDPR)

**Qué se guarda.** En la BD del bot (`gymprofit_bot` en Aiven MySQL, separada de la de la app) solo
tu **`discord_id`** (identificador público de Discord) y datos de comunidad: XP, nivel, monedas,
racha, idioma, tu estado de juego (personaje, inventario, descanso…) y, si te han moderado, tus
avisos/sanciones. **No** se guardan nombres reales, emails ni contraseñas.

**Cómo se protege.** Cifrado en reposo (Aiven) + TLS en tránsito (`sslMode=REQUIRED`). El **texto
libre** con posible dato personal (motivos de sanción, apodos previos) se guarda **cifrado con
AES-256-GCM** (`util/Cifrador`, clave `BOT_CRYPTO_KEY`). Los IDs y numéricos van en claro para poder
consultar y paginar. Minimización de datos y **retención automática** (job que purga avisos
revocados > 6 meses y sanciones > 12 meses).

**Tus derechos (base legal: interés legítimo).**
- `/privacidad info` — qué guardamos, para qué y cómo ejercer tus derechos.
- `/privacidad exportar` — descarga (efímera) un JSON con todo lo que el bot guarda de ti.
- `/privacidad borrar` — elimina **todas** tus filas del bot (con confirmación) y revoca la
  vinculación con la app si existe.

Detalles de diseño en [`docs/decisions.md`](docs/decisions.md) (ADR-009) y en el spec de
moderación/RGPD.

</details>

---

<sub>© 2026 Rubén Juan Candela. Todos los derechos reservados. Ver [`LICENSE`](LICENSE).</sub>
