# CLAUDE.md — GymProBot

Bot oficial de Discord de **GymProFit**: comunidad fitness gamificada (XP, retos, economía)
y puente en vivo con la API de la app. Java 21 + JDA 5.

**Fuente de verdad:** [`GYMPROBOT_SPEC.md`](GYMPROBOT_SPEC.md). Arquitectura:
[`docs/architecture.md`](docs/architecture.md). Decisiones (ADR):
[`docs/decisions.md`](docs/decisions.md). Reglas: [`rules/`](rules/).

## Comandos

- **Build + tests:** `./mvnw verify` (Windows: `mvnw.cmd verify`). Requiere **JDK 21**.
- **Empaquetar jar:** `./mvnw -DskipTests package` → `target/gymprofit-bot.jar`.
- **Arrancar en local:** exportar las variables de `.env` (copiar de `.env.example`) y
  `java -jar target/gymprofit-bot.jar`. Con solo `PORT`, arranca el health server en
  `/health` aunque falte `DISCORD_TOKEN` (útil para andamiaje).

## Convenciones

- **Dominio en español** (coherente con la app/API): clases, métodos y BD en español.
- **i18n obligatorio:** todo texto visible sale de `messages_es.properties` /
  `messages_en.properties`. Prohibido hardcodear strings. Añadir la clave en **ambos** idiomas.
- **EmbedFactory obligatorio:** ningún embed se crea a mano; todos pasan por la factoría
  central (paleta y reglas de [`GYMPROBOT_SPEC.md`](GYMPROBOT_SPEC.md) §7).
- **Documentar todo:** cada archivo abre con un comentario de cabecera (Javadoc de clase o
  `package-info.java`) sobre qué es y su papel; Javadoc en métodos públicos no triviales y
  comentarios inline explicando el *porqué* de la lógica. Aplica también a SQL, YAML y `pom.xml`.
- **Una clase por slash command** en `commands/` (subpaquetes por categoría).
- **Un test por service nuevo** (JUnit 5 + Mockito); cliente API con OkHttp `MockWebServer`.
- **Secrets solo por env vars** (`config/BotConfig`); nunca en código ni en logs.
- Migraciones **Flyway** para cualquier cambio de esquema; nunca tocar la BD a mano.

## Flujo de trabajo

- **Por fases** (SPEC §5): F1 Núcleo → F2 Economía → F3 Vinculación → F4 Competición.
  No se empiezan módulos de una fase sin cerrar la anterior (desplegada y funcionando).
- **Ramas:** `main` protegida; una rama por feature; PR con la plantilla de `.github/`;
  merge **solo con CI en verde**.
- **Schema:** cualquier cambio de esquema es una migración Flyway nueva (`V2__`, `V3__`…);
  no se edita una migración ya aplicada.

## Commits

- **Siempre sin** trailer `Co-Authored-By` ni pie "Generated with Claude Code".
  (Se puede fijar además en los settings de Claude Code con `includeCoAuthoredBy: false`.)
- **Documentación viva:** cada commit incluye, **en el mismo commit**, la actualización de
  toda la documentación afectada por el cambio (README de la carpeta tocada,
  `docs/architecture.md`, `docs/decisions.md`, tablas de comandos del README, `CHANGELOG.md`).
- **Hashes reales:** si un documento debe referenciar el hash de un commit, se obtiene
  **del repo real tras commitear** (`git rev-parse --short HEAD`) y se añade en un commit de
  docs posterior. **Prohibido inventar o escribir hashes de memoria.**

## Definición de terminado (obligatoria)

Nada se entrega sin verificación completa:

1. `./mvnw verify` **en verde**.
2. Tests para todo lo nuevo.
3. **Evidencia real** (salida de build/tests) mostrada en la respuesta.

Si algo falla, se itera hasta que pase. Prohibido cerrar con "debería funcionar". Lo que
exija Discord en vivo (slash commands, botones, jobs) se prueba contra el **servidor de
pruebas con el bot/token de test** (separados de producción). Si no se puede ejecutar, se
marca explícitamente como **"pendiente de smoke test manual"** — nunca se da por verificado
sin serlo.

## Prohibiciones

- Nunca commitear secretos.
- Nunca llamar a la BD de la app directamente: todo dato de la app pasa por la API REST.
- No añadir dependencias sin justificarlo en [`docs/decisions.md`](docs/decisions.md).

## Personalidad y tono (SPEC §6)

- **Por defecto:** entrenador motivador (energía, sin gritar en cada frase).
- **Casual** (bienvenidas, daily, duelos, trivia): colega de gym, humor ligero. Nunca humor
  a costa del físico de nadie.
- **Serio** (tickets, moderación, warns, anuncios): directo, profesional, cero bromas.
- Tuteo en ES; tono equivalente natural en EN (no traducción literal).
