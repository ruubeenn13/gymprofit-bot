# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Cada fase/feature deja su entrada aquí
(alimenta el comando `/anunciar`).

## [Sin publicar]

### Añadido
- **Configuración de servidor (F1)**: entidad `ConfigServidor` + `ConfigServidorRepositorio`
  (upsert de `config_servidor`), `ConfigServidorService` (fijar canal/rol/idioma conservando el
  resto) y comando `/config` (solo staff, `MANAGE_SERVER`, guild-only) con subcomandos `ver`,
  `canal`, `rol` e `idioma`. Base para bienvenida/auto-roles y el resto de F1. Tests
  `ConfigServidorServiceTest` (Mockito) y `ConfigServidorRepositorioTest` (Testcontainers).
  Registro verificado en vivo (4 comandos); uso en vivo pendiente de smoke test manual.
- **XP y niveles (F1)**: XP por mensaje con cooldown anti-spam de 60 s (`XpMensajeListener` +
  `util/Cooldown`), `XpService` con curva de nivel documentada (`NivelCalculadora`,
  `50·n²+50·n`), anuncio de subida de nivel (embed dorado), y comandos `/nivel [usuario]` y
  `/top` (leaderboard) en `commands/gamificacion`. Nueva consulta `listarTopPorXp` en el
  repositorio. Tests unitarios (`NivelCalculadoraTest`, `CooldownTest`, `XpServiceTest` con
  Mockito) y de repositorio (Testcontainers). XP por mensaje, `/nivel` y `/top` en vivo quedan
  pendientes de smoke test manual (registro de los 3 comandos verificado en vivo).
- **Infraestructura de slash commands + `/ping` (F1)**: interfaz `Comando`, `RouterComandos`
  (registra los comandos por servidor en `onGuildReady` y enruta las interacciones, aislando
  errores) y primer comando `/ping` (`commands/general`) con descripción localizada ES/EN y
  respuesta por `EmbedFactory`. `DiscordBot.start` acepta listeners; `Main` monta el router.
  Helper `Messages.desdeTag` para resolver idioma desde el locale de Discord. Tests
  `PingComandoTest` y ampliación de `MessagesTest`. Registro verificado en vivo; invocación de
  `/ping` pendiente de smoke test manual.
- **EmbedFactory central (F1, SPEC §7)**: única vía para crear embeds. `Categoria` fija la
  paleta (naranja/dorado/verde/azul/rojo) y `Tipo` asocia cada emoji identificador (uno por
  título) con su color; footer de marca `GymProBot • GymProFit` + timestamp e i18n del footer
  (`embed.footer`). Los tipos sin color en la §7 (duelos, trivia, sugerencias, tickets) usan
  azul como color de info. Test `EmbedFactoryTest`.
- **Capa de BD y arranque de Flyway (F1)**: `db/Database` monta el pool **HikariCP** y aplica
  las migraciones Flyway al arrancar; `Main` sigue el orden health → Flyway → JDA con arranque
  degradado si falta `DB_URL` o `DISCORD_TOKEN`. Primer repositorio JDBC
  `UsuarioDiscordRepositorio` (`buscar`, `obtenerOCrear`, `guardar`) sobre `usuarios_discord`,
  con `UsuarioDiscord` (entidad) y `DatabaseException`. Test end-to-end con Testcontainers
  (`UsuarioDiscordRepositorioTest`). Verificado en vivo: arranque real contra MySQL 8 aplicando
  las 2 migraciones (esquema v2) y conectando a Discord.
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
