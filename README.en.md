<!-- Idioma / Language -->
[Español](README.md) | **[English](README.en.md)**

<h1 align="center">🤖 GymProBot</h1>

<p align="center">
  <em>The official Discord bot for <strong>GymProFit</strong>: a gamified fitness community —
  XP, challenges, economy and a live bridge to the app's API.</em>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="JDA" src="https://img.shields.io/badge/JDA-5-5865F2">
  <img alt="Build" src="https://img.shields.io/badge/build-Maven-C71A36">
  <img alt="CI" src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF">
  <img alt="License" src="https://img.shields.io/badge/license-proprietary-lightgrey">
</p>

<!-- TODO: replace with a real demo GIF once Phase 1 is deployed. -->
<p align="center"><em>🎬 (demo coming soon — Phase 1)</em></p>

---

## What it does

GymProBot turns the community server into a gamified gym and wires it to the app:

- 🏋️ **Query the app** — exercises and routines from the GymProFit catalog, in ES/EN.
- ⭐ **XP and levels** — level up by taking part; server leaderboard.
- 🪙 **Economy (ProCoins)** — earn coins through activity and challenges; shop and rewards.
- 🎯 **Challenges and competition** — weekly challenges, duels, fitness trivia and rankings.
- 📊 **Linked profile** — your real app stats and progress inside Discord.
- 🛡️ **Moderation and support** — auto-mod, warns, tickets and branded announcements.

> Fully bilingual **ES/EN** from day one, with embeds consistent with the app's design.

## Quick start (5 steps)

1. **Clone** the repo: `git clone https://github.com/ruubeenn13/gymprofit-bot.git`
2. **Configure** the environment: copy `.env.example` to `.env` and fill in your values.
3. **Build and test:** `./mvnw verify` (needs **JDK 21**).
4. **Package:** `./mvnw -DskipTests package` → `target/gymprofit-bot.jar`.
5. **Run:** export the `.env` variables and `java -jar target/gymprofit-bot.jar`.
   Check `http://localhost:8080/health` → `OK`.

> Current status: **Phase 1 (Core) in progress**. Foundation ready (DB + Flyway, EmbedFactory,
> Discord connection) and slash command infrastructure.

### Commands

| Command | What it does |
|---|---|
| `/ping` | Checks that the bot is responsive and shows its latency |
| `/nivel [user]` | Shows your level, XP and progress (or another user's) |
| `/top` | Server XP leaderboard |
| `/config` | Server configuration: channels, goal roles and language (staff only) |
| `/setup [desde_cero]` | Set up the server structure (roles, channels, permissions, description) and auto-config, with a **report of that run's changes** (added/updated/removed) in the reply and in `bot-logs`; `desde_cero` wipes everything first (admin only) |
| `/limpiar <amount>` | Delete the latest N messages in the channel (staff only) |
| `/frase` | Drops a random motivational quote from the bilingual bank (30 s cooldown) |
| `/ejercicios [group] [difficulty] [search]` | Browse the app's exercise catalog: paginated list with arrows and a full card per exercise |
| `/ejercicio-dia` | Today's chosen exercise, with its motivational quote |

Also: **message XP** (with a 60 s anti-spam cooldown) that levels users up and announces it, and
**welcome + auto-roles** (embed on member join with a goal menu that assigns the role).

### RPG / Economy (fiction)

A fictional life sim on top of the community. **Virtual coins**, not purchasable with real money nor
convertible. By block:

- **Character & progression:** `/perfil` (ver · balance · insignias) · `/rank` · `/daily` ·
  `/entrenar` · `/estudiar` · automatic level ranks.
- **Rest:** `/descansar` (dormir · despertar · estado). Energy comes back by **sleeping**, and the
  better your bed (sleeping bag, mattress, flat, house… or a hotel) the higher you can go.
- **Work & shop:** `/trabajo` (lista · elegir · currar · ascender · carrera · dimitir) ·
  `/empresa` (fundar · info · disolver · invitar · solicitar · pendientes · rango · sacar · despedir · propuestas · mejorar · ascender · ranking · vender) · `/tienda` · `/comprar` ·
  `/inventario` (ver · usar · vender) · `/mejoras` · `/mejorar` ·
  `/pasivos` (ver · equipar · quitar).
- **Combat:** `/mundos` · `/monstruos` · `/pelear` (turn-based, buttons) · `/mazmorra` ·
  `/misiones` · `/equipar` · `/encantar`.
- **Mining & smithing:** `/minar` · `/reparar` · `/recetas` · `/craftear`.
- **Chests:** `/cofres` · `/abrir`.
- **Between players:** `/regalar` · `/trueque` · `/robar` · `/mercado` (subcommands).
- **Banking & gambling:** `/banco` · `/bolsa` · `/casino` (subcommands; all fiction).
- **Social:** `/gremio` (groups with a private channel, subcommands).

Design details in [`docs/superpowers/specs/2026-07-13-economia-rpg-vision.md`](docs/superpowers/specs/2026-07-13-economia-rpg-vision.md).

---

<details>
<summary><strong>🔧 Technical documentation (full setup, architecture, env vars)</strong></summary>

### Stack

- **Java 21** + **JDA 5** (Discord), built with **Maven** (`mvnw` wrapper).
- **Retrofit2 + OkHttp3 + Gson** to the GymProFit API (same client as the Android app).
- **Bot DB:** Aiven MySQL (database `gymprofit_bot`), schema via **Flyway**, JDBC + HikariCP.
- **i18n** ES/EN with `ResourceBundle`. **Logs** SLF4J + Logback (console only).
- **No Spring:** health server via the JDK's `com.sun.net.httpserver`; fat-jar via `maven-shade-plugin`.

### Architecture

```
Discord Gateway ⇄ GymProBot (JDA, Render)
                     ├── Bot DB (Aiven MySQL · gymprofit_bot): XP, coins, streaks, warns, shop…
                     └── GymProFit API (Render, /api): exercises, routines, achievements, stats, linking
```

The bot **never** touches the app's DB: all app data goes through the REST API. Full detail in
[`docs/architecture.md`](docs/architecture.md) and decisions in [`docs/decisions.md`](docs/decisions.md).

### Package structure (`com.gymprofit.bot`)

`config/` · `commands/` · `events/` · `services/` · `api/` · `db/` · `embeds/` · `i18n/` · `jobs/`
(each with its own README and `package-info.java`).

### Environment variables

| Variable | Use |
|---|---|
| `DISCORD_TOKEN` | Discord bot token |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Bot DB (Aiven MySQL `gymprofit_bot`) |
| `GYMPROFIT_API_URL` | GymProFit API base (e.g. `https://gymprofit-api.onrender.com/api`) |
| `BOT_SERVICE_USER` / `BOT_SERVICE_PASSWORD` | Bot service account in the app |
| `PORT` | Health server port (Render injects it; defaults to 8080) |
| `TZ` | `Europe/Madrid` (jobs don't trust the system TZ) |

Template in [`.env.example`](.env.example). Exported by hand locally; configured in the Render
dashboard for deployment (see [`render.yaml`](render.yaml)). **Secrets are never committed.**

### Development commands

- `./mvnw verify` — build + tests (CI gate).
- `./mvnw -DskipTests package` — build the executable fat-jar.
- Repo rules: [`CLAUDE.md`](CLAUDE.md), [`rules/`](rules/). Source of truth:
  [`GYMPROBOT_SPEC.md`](GYMPROBOT_SPEC.md).

### Deployment

Multi-stage Docker ([`Dockerfile`](Dockerfile)) on Render (blueprint [`render.yaml`](render.yaml)).
Health check at `/health`. **Hosting:** the bot can't run free in the same workspace as the API
(SPEC §14 / ADR-004); see [`docs/decisions.md`](docs/decisions.md).

### Privacy (GDPR)

The bot stores community state (XP, coins, streaks, warns, etc.) in its DB, keyed by your
`discord_id`. `/privacidad borrar` deletes all your rows and revokes the app link if
present. What is stored and why is detailed in this section as the phases progress.

</details>

---

<sub>© 2026 Rubén Juan Candela. All rights reserved. See [`LICENSE`](LICENSE).</sub>
