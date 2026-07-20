# Reglas de código — GymProBot

Reglas de obligado cumplimiento. La review las verifica ([`review-checklist.md`](review-checklist.md)).

1. **Java 21.** `maven.compiler.release=21`. El bot sigue a la API (Java 21), no a la app
   Android (Java 11).
2. **Dominio en español.** Nombres de clases, métodos, variables y columnas de BD en español,
   coherente con la app y la API.
3. **i18n para todo texto visible.** Cualquier cadena que vea el usuario sale de
   `messages_es.properties` / `messages_en.properties` vía `Messages.get(...)`. **Prohibido
   hardcodear strings.** Toda clave se añade en **ES y EN** a la vez.
4. **Embeds solo por `EmbedFactory`.** Prohibido `new EmbedBuilder()` suelto en comandos o
   listeners. La factoría aplica color por categoría, un emoji por título, footer
   `GymProBot • GymProFit` + timestamp y paginación (SPEC §7).
5. **Servicios testeables.** La lógica de negocio vive en `services/` sin estado estático
   oculto, con BD y cliente API inyectados. Cada service nuevo llega con su test (JUnit 5 +
   Mockito). El cliente API se testea con OkHttp `MockWebServer`.
6. **Logging con SLF4J** (nunca `System.out`). **Nunca loggear** tokens ni credenciales.
7. **Secrets solo por env vars**, leídos en `config/BotConfig`. En local vía `.env` (ignorado).
8. **Migraciones Flyway** para cualquier cambio de esquema (`V2__`, `V3__`…). No se edita una
   migración aplicada ni se toca la BD a mano.
9. **Cooldown** en todo comando que escriba en BD o llame a la API (anti-abuso). **Validar el
   input** siempre (rangos de calculadoras, cantidades de tienda…).
10. **Permisos mínimos.** Comandos de staff restringidos con `setDefaultPermissions`.
11. **Resiliencia ante la API:** `deferReply()` + reintento con backoff; ante 429 respetar
    `Retry-After`; mensaje amable si la API no responde.
12. **Una clase por slash command** en `commands/` (subpaquetes por categoría). Las familias van
    agrupadas en **subcomandos** (ADR-011): Discord solo cuenta los comandos de nivel superior
    contra su límite de 100.
13. **Respuesta pública por defecto** (`deferReply(false)`, sin `setEphemeral(true)`). Es una
    comunidad: la gracia es que se vea lo que hace la gente. Va **efímero solo** lo sensible: datos
    personales (`/privacidad`), moderación, comandos de staff y sus confirmaciones, y los errores,
    cooldowns y validaciones (ruido). Ante la duda, **público**: la excepción se justifica.
14. **Documentar todo el código.** En **cada archivo**, un comentario de cabecera al
    principio explicando qué es el archivo y su papel (en clases Java, Javadoc de clase; en
    paquetes, `package-info.java`). Además, **Javadoc** en métodos públicos no triviales y
    **comentarios inline** que expliquen el *porqué* de los bloques de lógica (no narrar lo
    obvio). El mismo criterio aplica a SQL de migraciones, YAML de CI y `pom.xml`.
15. **Sin dependencias nuevas** sin justificarlas en [`../docs/decisions.md`](../docs/decisions.md).
