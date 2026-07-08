# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Cada fase/feature deja su entrada aquí
(alimenta el comando `/anunciar`).

## [Sin publicar]

### Añadido
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
