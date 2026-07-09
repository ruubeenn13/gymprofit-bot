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

Also: **message XP** (with a 60 s anti-spam cooldown) that levels users up and announces it.

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
`discord_id`. `/borrar-mis-datos` (Phase 1+) deletes all your rows and revokes the app link if
present. What is stored and why is detailed in this section as the phases progress.

</details>

---

<sub>© 2026 Rubén Juan Candela. All rights reserved. See [`LICENSE`](LICENSE).</sub>
