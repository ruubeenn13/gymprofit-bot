# Decisiones de arquitectura (ADR)

Registro de decisiones técnicas de GymProBot. Toda dependencia nueva y toda decisión con
impacto estructural se documenta aquí **en el mismo commit** que la introduce
(ver [`../CLAUDE.md`](../CLAUDE.md)). Formato ligero: contexto → decisión → estado.

---

## ADR-001 — Autenticación bot → API

**Contexto.** El bot consume la API GymProFit (ejercicios, rutinas, logros, stats, sesiones).
La API usa JWT (30 min) + refresh rotado y limita `/auth/**` a 15 req/60 s por IP (429).

**Decisión.**
- **Corto plazo (F1):** cuenta de servicio (usuario dedicado al bot) en la app. Login en
  `/auth` + flujo de refresh igual que la app Android. Credenciales en env vars
  (`BOT_SERVICE_USER` / `BOT_SERVICE_PASSWORD`). Para los KPIs de `/admin` la cuenta necesita
  rol `ADMIN`; se asume temporalmente.
- El **access token se cachea**: solo se llama a `/auth` para el login inicial y para
  refrescar al caducar (401). **Nunca** se re-loguea en cada petición. Ante un 429 se respeta
  `Retry-After` con backoff (un bucle de reintentos puede auto-banear el bot).
- **Objetivo (F3):** crear en la API un rol `BOT` con permisos acotados y/o endpoints
  `/bot/**` específicos, para no tener un ADMIN completo circulando.

**Estado:** aceptada (F1 vigente; F3 pendiente).

---

## ADR-002 — BD del bot en el mismo Aiven, database separada

**Contexto.** El bot necesita estado persistente (XP, coins, rachas, warns, tienda, retos…).
El free tier de Render borra memoria y disco en cada reinicio.

**Decisión.** Usar el **mismo servidor Aiven MySQL** que la API, con una **database nueva
`gymprofit_bot`** y esquema versionado con **Flyway**. Coste cero extra, TLS ya resuelto,
estado fuera del FS efímero. El bot **no** accede a la BD de la app: la tabla de vinculación
`discord_links` vive en la BD de la API (es dato de la app).

**Estado:** aceptada.

---

## ADR-003 — Validación mixta de retos

**Contexto.** Los retos semanales (F3) deben poder validarse tengan o no cuenta de la app
vinculada.

**Decisión.** Validación **mixta**: si la cuenta está vinculada, se valida contra las
sesiones reales de la API; si no, auto-reporte con `/reto-completar`. Los vinculados puntúan
con verificación y se distinguen en el ranking.

**Estado:** aceptada (implementación en F3).

---

## ADR-004 — Hosting del bot

**Contexto (SPEC §14, restricción crítica).** Render regala 750 h de instancia free **por
workspace y mes**, compartidas entre todos los servicios free. Dos servicios 24/7 (~1.440 h)
las agotan hacia el día 16 y Render suspende **todos** los free del workspace, **API de
producción incluida**. Por tanto el bot **no** puede desplegarse gratis junto a la API.

**Opciones.**
- **(a)** Instancia de pago ~7 $/mes (**recomendada**): sin keep-alive, sin sustos.
- **(b)** Workspace de Render **separado** en free, con `/health` añadido al pinger externo
  que ya mantiene despierta la API (o `keep-alive.yml`).
- **(c)** VM Always Free de Oracle Cloud.

**Decisión.** _Pendiente de elección definitiva por el responsable del despliegue._ Hasta
entonces, `render.yaml` queda en `plan: free` documentado y `keep-alive.yml` presente pero
marcado como innecesario en pago. **Actualizar este ADR con la opción elegida antes del
primer deploy de producción.**

**Estado:** propuesta (pendiente de decisión de despliegue).

---

## ADR-005 — Stack de dependencias inicial

**Contexto.** Andamiaje del repo; hay que fijar librerías coherentes con la app/API.

**Decisión.**
- **JDA 5** para Discord (librería Java más madura: slash commands, botones, modals).
- **Retrofit2 + OkHttp3 + Gson** para la API (mismo cliente que la app Android).
- **Flyway + HikariCP + mysql-connector-j** para la BD del bot.
- **SLF4J + Logback** (solo consola: FS de Render efímero).
- **JUnit 5 + Mockito + OkHttp MockWebServer** para tests.
- **Sin Spring:** el health server usa `com.sun.net.httpserver` del JDK (cero dependencias
  extra) y el fat-jar se genera con `maven-shade-plugin`.

**Estado:** aceptada. Toda dependencia añadida después amplía este ADR con su justificación.
