# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Cada fase/feature deja su entrada aquí
(alimenta el comando `/anunciar`).

## [Sin publicar]

### Añadido
- **Esquema inicial de la BD + seeds (F1)**: migraciones Flyway `V1__esquema_inicial_f1.sql`
  (tablas de la Fase 1: `usuarios_discord`, `config_servidor`, `warns`, `tickets`,
  `sugerencias`, `trivia_preguntas`, `trivia_scores`, `frases`) y `V2__seed_frases_trivia.sql`
  (seeds obligatorios SPEC §10: 50 preguntas de trivia y 32 frases, bilingües ES/EN). Test de
  migración con **Testcontainers** (`mysql:8.0`) que valida `flyway migrate` y los mínimos de
  seeds; se salta si el cliente Docker no es alcanzable en local (corre en CI). Ver ADR-006.
- **Conexión con Discord (F1, bootstrap JDA)**: `DiscordBot` centraliza la construcción de
  JDA con los intents privilegiados `GUILD_MEMBERS` + `MESSAGE_CONTENT`, cache de miembros,
  presencia (`bot.actividad` en ES/EN) y estado *online*. `Main` conecta al arrancar si hay
  `DISCORD_TOKEN` (si no, solo el health server) y cierra JDA + health de forma ordenada en
  `SIGTERM`. Sin comandos ni listeners todavía. Test `DiscordBotTest` (contrato de intents).
  Verificado en vivo: *Login Successful!* contra el servidor de test.
- **Andamiaje del repositorio** (sin lógica de comandos todavía):
  - Esqueleto Maven Java 21 + JDA 5: `pom.xml`, estructura de paquetes de la SPEC §4
    (con `package-info.java`), `Main` con health server en `/health`, `BotConfig`, `Messages` (i18n).
  - `messages_es/en.properties` (vacíos), `logback.xml` (solo consola), `.env.example`.
  - Tests iniciales: health endpoint (`HealthServerTest`) y sincronización ES/EN (`MessagesTest`).
  - Documentación de repo nativo de IA: `README.md` + `README.en.md`, `CLAUDE.md`, `docs/`
    (architecture, decisions/ADR), `rules/` (coding-rules, review-checklist), READMEs por carpeta,
    y skills `nuevo-comando` / `nueva-migracion` / `nuevo-embed`.
  - CI (`ci.yml`, JDK 21 Temurin + `mvn verify`), `keep-alive.yml`, `dependabot.yml` y plantillas
    de issue/PR.
  - `Dockerfile` (multi-stage JDK 21) y `render.yaml` (blueprint con secretos `sync:false` y
    `TZ=Europe/Madrid`).
  - `LICENSE` propietario (copiado de GymProFit) y `.gitignore` (Maven + IDE + `.env`/logs).
