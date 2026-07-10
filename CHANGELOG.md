# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Cada fase/feature deja su entrada aquí
(alimenta el comando `/anunciar`).

## [Sin publicar]

### Añadido
- **Eventos del servidor (`/reto` y `/evento`)**: dos contadores más en SERVER STATS —`🎯 Reto`
  (reto de la semana) y `⏳ Evento` (próximo evento con cuenta atrás «en 3d 4h»)— alimentados por
  la nueva tabla `eventos_servidor` (migración **V3**) vía `EventoServidorRepositorio` y
  `EventoService`. Comandos solo-staff `/reto <texto>` y `/evento <nombre> <fecha>` (formato
  `2026-07-20 18:30`, hora peninsular; la confirmación usa timestamps dinámicos de Discord). El job
  de estadísticas refresca ambos contadores. Tests `EventoServiceTest` (parseo de fecha) y
  ampliación de `EstadisticasServiceTest` (cuenta atrás). Ejecución en vivo pendiente de smoke test.
- **Tipos de canal coherentes en `/setup` (foros, media, anuncios)**: `TipoCanalDiscord` amplía a
  `FORO`, `MEDIA` y `ANUNCIOS` y `/setup` los crea con su rama propia:
  - **Foros** (publicaciones título+imagen+descripción con etiquetas): `💡 sugerencias`
    (En estudio/Aprobada/Rechazada/Implementada), `📚 rutinas` (Push/Pull/Pierna/Full-body/Cardio/
    Movilidad), `🍎 nutrición` (Receta/Plan/Duda/Suplementación), `❓ dudas` (Técnica/Material/
    Lesión/Resuelto).
  - **Media** (galería): `📈 progresos` y `📸 fotos` (separados).
  - **Anuncios** (News, seguibles y solo-staff): `📣 anuncios` y `📲 novedades-app`.
  - Cada canal lleva su descripción (topic) con las instrucciones de uso; los foros, sus etiquetas.
    Tests `SetupServidorPlanTest` (topics de todos los canales de mensajes, etiquetas solo en foros).
  - **Para aplicar los tipos nuevos a un server ya montado hay que reejecutar con
    `/setup desde_cero:true`**: reutilizar por nombre no cambia el tipo de un canal existente.
    Creación en vivo pendiente de smoke test manual.
- **Estadísticas en vivo + AutoMod + más canales en `/setup`**:
  - Categoría **`📊 SERVER STATS`** (arriba del todo, de solo lectura) con 4 contadores en canales
    de voz bloqueados: **XP repartido**, **Nº1** (líder de XP), **Boosts** y **En voz**. Los
    mantiene al día el nuevo `EstadisticasService` (job cada 6 min; renombra solo si el valor
    cambió, para no gastar rate limit; localiza los canales por prefijo de nombre → sin persistir
    IDs). XP repartido y Nº1 salen de la BD (`sumaXp` / `listarTopPorXp`); boosts y gente en voz, de
    la caché estándar. **No requiere intents privilegiados adicionales.**
  - **AutoMod por código**: `/setup` crea (idempotente) 3 reglas con alerta a `📋・moderación`:
    anti-menciones masivas (>8), anti-spam y lenguaje inapropiado (presets PROFANITY/SLURS/
    SEXUAL_CONTENT, que cubren español). Se omite sin romper si falta `MANAGE_SERVER`.
  - **Descripciones (topic)** en todos los canales de texto; se aplican al crear y al reejecutar.
  - Nuevos canales: **`🎤 Escenario`** (Stage, mano levantada) + rol **Ponente**, y lobby
    **`➕ Crear sala`** (base del futuro Join-To-Create) en sustitución de la `Privada` fija.
  - Idempotencia reforzada: los canales de stats se casan por prefijo, así reejecutar `/setup` no
    los duplica tras el renombrado. Tests `EstadisticasServiceTest`, ampliación de
    `SetupServidorPlanTest` (topics) y `DiscordBotTest` (3 intents). Ejecución en vivo (creación de
    canales/reglas y renombrado) **pendiente de smoke test manual**.

### Cambiado
- **Servidor pulido (visual)**: `/setup` publica mensajes fijados con **contenido rico**
  (reglas largas y estructuradas por secciones, guías con divisores), **thumbnail** del bot en los
  embeds, **categorías decoradas** (`▬▬ 📢 INFORMACIÓN ▬▬`) y un **panel de navegación con
  botones-enlace** en `🚀・empieza-aquí` que salta a los canales clave. `/setup` corre en hilo
  propio, es resiliente por ítem y tolera la expiración de la interacción.
- **Pase visual (2)**: `/top` con medallas 🥇🥈🥉 en el podio; el embed de subida de nivel
  añade thumbnail del avatar. `/ping` y las confirmaciones de `/config` se dejan como están
  (línea corta, sin muro).
- **Pase visual de embeds**: footer con el avatar del bot en todos los embeds
  (`EmbedFactory.configurarIconoFooter`); `/config ver` pasa de 11 campos sueltos a una
  descripción agrupada (Canales / Roles) con indicadores ✅/⚪, emoji por línea y thumbnail;
  `/nivel` añade barra de progreso y thumbnail del avatar. Nueva utilidad `util/Barras`
  (barra de progreso reutilizable) con test.

### Añadido
- **Panel de auto-roles + contenido del servidor (F1)**: `/setup` publica y fija un panel en
  `🎭・roles` con menús (objetivo + notificaciones) gestionados por `PanelRolesListener` (además
  del onboarding). Mensajes fijados de ayuda en los canales clave (empieza-aquí, reglas,
  cómo-funciona, faq, redes, soporte, general, presentaciones, comandos-bot, fitness) y aviso de
  «próximamente» en los de fases futuras. Permisos por rol (Silenciado no habla en ningún canal),
  categorías decoradas. `/setup` corre fuera del hilo del gateway, comprueba permisos antes de
  tocar nada y ya no borra roles del propio bot ni se autobloquea en categorías ocultas.
- **Administración del servidor (F1)**: `/setup` (solo admin) monta roles, categorías y canales
  (F1–F4) con permisos según `SetupServidorPlan` (blueprint testeable), purga los mensajes
  recientes existentes y autorrellena `config_servidor`; opción `desde_cero` que borra todos los
  canales y roles borrables antes de montar (irreversible). `/limpiar <cantidad>` purga los
  últimos N mensajes del canal (solo staff), vía `LimpiezaService`. Tests `SetupServidorPlanTest`,
  `LimpiezaServiceTest`, `LimpiarComandoTest`. Ejecución en vivo pendiente de smoke test manual.
- **Bienvenida + auto-roles (F1)**: `BienvenidaListener` publica un embed de bienvenida (con
  thumbnail del avatar) en el canal configurado al entrar un miembro, con un menú de selección de
  objetivo (Fuerza/Cardio/Pérdida de peso/General); al elegir, asigna el rol configurado en
  `/config`. Helper `ConfigServidorService.rolDe` (con test). Pendiente de smoke test manual
  (requiere canal y roles configurados en el servidor).
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
