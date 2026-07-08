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

> Estado actual: **andamiaje** (Fase 0). Los comandos llegan en la Fase 1.

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
| `PORT` | Puerto del health server (Render lo inyecta; por defecto 8080) |
| `TZ` | `Europe/Madrid` (los jobs no confían en la TZ del sistema) |

Plantilla en [`.env.example`](.env.example). En local se exportan a mano; en Render se configuran
en el dashboard (ver [`render.yaml`](render.yaml)). **Nunca se commitean secretos.**

### Comandos de desarrollo

- `./mvnw verify` — build + tests (gate de CI).
- `./mvnw -DskipTests package` — genera el fat-jar ejecutable.
- Reglas del repo: [`CLAUDE.md`](CLAUDE.md), [`rules/`](rules/). Fuente de verdad:
  [`GYMPROBOT_SPEC.md`](GYMPROBOT_SPEC.md).

### Despliegue

Docker multi-stage ([`Dockerfile`](Dockerfile)) sobre Render (blueprint [`render.yaml`](render.yaml)).
Health check en `/health`. **Hosting:** el bot no puede correr free en el mismo workspace que la
API (SPEC §14 / ADR-004); ver [`docs/decisions.md`](docs/decisions.md).

### Privacidad (GDPR)

El bot guarda en su BD el estado de comunidad (XP, coins, rachas, warns, etc.) asociado a tu
`discord_id`. `/borrar-mis-datos` (Fase 1+) elimina todas tus filas y revoca la vinculación con la
app si existe. Detalle de qué se guarda y para qué, en esta sección conforme avancen las fases.

</details>

---

<sub>© 2026 Rubén Juan Candela. Todos los derechos reservados. Ver [`LICENSE`](LICENSE).</sub>
