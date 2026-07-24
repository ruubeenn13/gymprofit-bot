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

## ADR-009 — Protección de datos del bot (RGPD)

**Contexto.** El bot persiste datos de miembros (IDs de Discord, XP/economía, warns, sanciones,
tickets). Hay que minimizar el riesgo legal sin romper consultas ni paginación.

**Decisión.**
- **Minimización:** solo se guardan IDs (snowflakes, pseudónimos) + números + poco texto libre.
  Nunca nombres reales, emails ni avatares.
- **Cifrado en reposo:** ya lo da Aiven (disco AES) + TLS en tránsito (`sslMode=REQUIRED`).
- **Cifrado de campo (AES-256-GCM, `util/Cifrador`):** solo el texto libre con posible dato
  personal (`warns.motivo`, `sanciones.motivo`, `sanciones.nick_anterior`, futuros transcripts).
  Los IDs y numéricos van **en claro** para poder indexar, unir y paginar. **No se cifra todo**:
  cifrar las PK/FK rompería índices y son datos ya públicos en Discord.
- **Clave** en env var `BOT_CRYPTO_KEY` (32 bytes base64). Sin clave, el bot arranca pero el cifrado
  se deshabilita y los comandos que cifran degradan sin persistir el texto. Perder la clave impide
  descifrar lo guardado.
- **Base legal:** interés legítimo (moderar y gamificar la comunidad).
- **Derechos:** `/mis-datos` (acceso/portabilidad), `/borrar-mis-datos` (olvido, FK CASCADE),
  `/privacidad` + nota en README (transparencia).
- **Retención:** job diario que purga datos viejos por ventanas.

**Estado:** aceptada e **implementada** (cifrado `Cifrador`, esquema V4, moderación con auditoría
cifrada, comandos `/privacidad` · `/mis-datos` · `/borrar-mis-datos` y job de retención `RetencionJob`).

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

---

## ADR-006 — Test de migraciones con Testcontainers (MySQL real)

**Contexto.** Las migraciones Flyway deben probarse antes de aplicarse contra Aiven. Un motor
embebido (H2 en modo MySQL) es rápido y no necesita Docker, pero no reproduce el dialecto real:
tipos, `CHECK`, `ON UPDATE CURRENT_TIMESTAMP`, charset `utf8mb4`, etc. pueden pasar en H2 y
fallar en MySQL.

**Decisión.** Probar las migraciones con **Testcontainers** levantando `mysql:8.0` (misma
familia que Aiven). Dependencias nuevas (test scope): `org.testcontainers:junit-jupiter` y
`org.testcontainers:mysql`. `MigracionesTest` ejecuta `flyway migrate` y valida el esquema y los
conteos de seeds (§10).

**Consecuencia operativa.** El test requiere un daemon Docker alcanzable por el cliente Java.
En **CI (Linux)** Docker es nativo y el test corre siempre. En **local** puede no correr si el
cliente docker-java no habla con Docker Desktop (bug del transporte *npipe* en Windows con
engines recientes): en ese caso el test se **salta** (`assumeTrue`), nunca rompe el build, y la
migración se valida a mano contra un MySQL de Docker. Alternativa para ejecutarlo también en
local: exponer el daemon en `tcp://localhost:2375` (Docker Desktop → Settings) y fijar
`DOCKER_HOST`.

**Estado:** aceptada.

## ADR-007 — Onboarding de Discord: configuración manual (no automatizada)

**Contexto.** El onboarding de comunidad (canales predeterminados + preguntas de personalización
con roles/canales por respuesta) era lo último manual al montar el servidor. Se probó a
automatizarlo por **REST cruda** (`PUT /guilds/{id}/onboarding`, que JDA 5.6.1 no envuelve) con
`Route.custom` + `RestActionImpl`, armando el body con `DataObject` en una clase `OnboardingPlan`.

**Decisión.** **Revertida:** se configura **a mano** en el editor de Discord. El editor impone
límites que hacen el resultado por API poco fiable/mantenible: máximo de preguntas en el flujo de
unión (~4), y en modo avanzado exige cubrir casi todos los canales públicos en preguntas/canales
predeterminados. La estrategia práctica (modo habitual, preguntas esenciales en el flujo y el resto
como "posteriores") se hace mejor desde la UI. Se eliminó `OnboardingPlan` y la llamada
`configurarOnboarding`; el **diseño** de las preguntas se conserva en el spec del 2026-07-13 como
guía para configurarlo a mano.

**Consecuencia.** Sigue siendo un **paso manual** tras `/setup`. El texto del onboarding es un set
único (Discord no lo localiza por usuario), por eso los títulos/descripciones se escriben bilingües
ES+EN.

**Estado:** revertida (onboarding manual).

## ADR-008 — Matriz de permisos por rol declarativa en el plan de setup

**Contexto.** El modelo de permisos de `/setup` era grueso: cada canal se reducía a
`@everyone` / Staff / Silenciado; roles funcionales (Coach, Nutricionista, Ponente…) no influían.

**Decisión.** `CanalPlan` gana una lista de `PermisoRol(rolNombre, allow, deny)` y builders
fluidos (`.permite(rol, Permission…)`, `.niega(…)`, `.conSoloLectura()`). `/setup` los aplica al
crear **y** al reutilizar el canal (`aplicarPermisos`), resolviendo el rol por nombre desde el mapa
de roles; los inexistentes se omiten con aviso. Los canales de anuncios pasan a solo-lectura (bug:
antes `@everyone` podía escribir en ellos).

**Estado:** aceptada.

## ADR-010 — RPG económico: catálogos en código, azar inyectable y anti-inflación

**Contexto.** La Fase 2 añade un RPG/economía enorme (combate, minería, herrería, cofres, misiones,
mazmorras, mercado, banco, trueque, gremios, casino, bolsa). Hay que decidir dónde viven los datos,
cómo se testea el azar y cómo no romper la economía ficticia.

**Decisión.**
- **Catálogos de contenido en código** (records + `List` estáticas: `Items`, `Monstruos`, `Mundos`,
  `Recetas`, `Cofres`, `Acciones`…), no en BD. La BD guarda solo el **estado del jugador**. Editar
  contenido = editar código + test; sin migraciones para cada ítem nuevo.
- **Azar inyectable** (`BatallaService.Aleatorio`, `@FunctionalInterface` `double next()`): los
  services de combate/cofres/casino/bolsa/minería lo reciben; en producción es
  `ThreadLocalRandom`, en tests una secuencia fija → balance determinista y testeable.
- **Anti-inflación por diseño:** cada fuente de coins tiene un **sumidero** que la compensa
  (comisión de mercado 5 %, comisión de banco/préstamo, ventaja de la casa en el casino con EV<1,
  comisión de bolsa, coste de encantar/reparar/fundar gremio, durabilidad de picos). Invariante
  probado donde aplica (p. ej. `CofreService`: valor esperado < precio).
- **Monedero atómico** con ledger (`EconomiaRepositorio`, `transacciones`), nunca saldo negativo.

**Estado:** aceptada e implementada (F-ECO-0..6 + combate + extras).

## ADR-011 — Comandos agrupados en subcomandos (límite de 100)

**Contexto.** Discord limita a **100** los slash commands por servidor, pero **solo cuenta los de
nivel superior**: un comando admite hasta 25 subcomandos sin gastar cupo extra. El RPG llegó a 94
comandos sueltos; cada feature nueva acercaba al muro (si se rebasa, el registro falla y no carga
nada). Con los módulos que faltan (consultas a la API, calculadoras, `/frase`, `/trivia`,
vinculación, directorio…) el margen no daba.

**Decisión.** Agrupar las familias en **un comando con subcomandos** (`SubcommandData` + dispatch
por `evento.getSubcommandName()`). Se aplicó en dos tandas:

1. Economía: `/gremio`, `/banco`, `/mercado`, `/bolsa`, `/casino` — 94 → 76.
2. Resto de familias con ahorro ≥ 2 huecos — 76 → **55** (margen 45):
   `/warn` (poner·lista·quitar·limpiar), `/silenciar` (mute·unmute·timeout·untimeout),
   `/canal` (lock·unlock·lockdown·unlockdown·slowmode), `/privacidad` (info·exportar·borrar),
   `/perfil` (ver·balance·insignias), `/inventario` (ver·usar·vender),
   `/trabajo` (lista·elegir·currar), `/publicar` (anuncio·redes·panel·sorteo).

No se agruparon los pares de ahorro 1 (`/tienda`+`/comprar`, `/minar`+`/reparar`, `/equipar`+
`/desequipar`…): el hueco ganado no compensa alargar lo que se teclea.

**Consecuencias.** No se pierde funcionalidad, cambia la forma de invocar (`/warns @x` →
`/warn lista @x`). El router no cambia (enruta por el nombre de nivel superior). Los listeners de
botones siguen igual (sus `customId` no dependen del nombre del comando). Regla: los comandos nuevos
de una familia existente se añaden como subcomando; vigilar el total < 100.

**Estado:** aceptada e implementada.

## ADR-012 — Canales privados de gremio por permisos de miembro (sin rol)

**Contexto.** Los gremios (y futuras alianzas) necesitan un canal privado por grupo. Un rol por
gremio agotaría el cupo de roles del servidor (250) y ensucia la lista de roles.

**Decisión.** El canal del gremio se hace privado con **permission overrides por miembro** (deny
`VIEW_CHANNEL` a `@everyone`, allow a cada miembro), **sin crear rol**. Al entrar/salir se ajusta el
override del miembro; al disolver se borra el canal. Requiere que el bot tenga **Gestionar canales**;
es best-effort (si falla, el gremio sigue en BD y se registra el fallo).

**Estado:** aceptada e implementada (F-ECO-5a).

## ADR-013 — Catálogo satélite y ranuras para los efectos pasivos

**Contexto.** 30 ítems del catálogo (20 `EQUIPO` + 10 vehículos `BIEN`) se compraban y no hacían
nada, entre 200 y 3 000 000 de coins. La tienda mentía, el late-game no tenía destino y la
progresión se aplanaba.

**Decisión.**
1. Los bonos viven en un **catálogo paralelo** `services/Pasivos`, emparejado por `itemId`, y
   `Items` no se toca. Precedente triple: `Picos`, `Camas`, `Cofres`. Meterlos en `Items` obligaría
   a ampliar un record de 8 componentes con campos nulos en 79 de 109 filas, y sus precios son
   carga estructural probada por `RarezaTest` y `Camas`.
2. **Ranuras** (1/2/3/4 a niveles 0/10/25/50) en vez de «basta con tenerlo»: sin ranuras el sistema
   se resolvería el día que terminas de comprar y no habría ninguna decisión que tomar nunca más.
3. La fila de `pasivos_equipados` es una **referencia, no un derecho**: el bono se recalcula contra
   el inventario en cada consulta. Eso cierra a la vez los exploits de vender, regalar, publicar en
   el mercado y trocar, y evita hooks de limpieza en cinco services.
4. **Topes globales por tipo, saturantes.** Se topa la suma, nunca cada bono. Sin topes, cuatro
   ranuras de sueldo romperían la economía lenta (ADR-010).
5. Las **10 camas quedan fuera**: ya tienen efecto vía `Camas` y darles además un pasivo sería
   doble recompensa por la misma compra.

**Consecuencias.** Un comando más que aprender (`/pasivos`), compensado con `/pasivos ver` y la
intro del canal. El combate queda por debajo del +6 % del poder en el peor caso realista, así que
no hay que rebalancear el bestiario. Queda pendiente en el backlog el coste de mantenimiento de los
vehículos (sumidero con su propio job y su propio diseño).

**Estado:** aceptada e implementada.

## ADR-014 — ramas de carrera para los ascensos

**Estado:** aceptada e implementada.

**Contexto.** Cualquier puesto del catálogo se elegía directamente con solo nivel de servidor: un
nivel 30 pasaba de parado a CEO en un comando. 26 sectores para ~50 puestos hacían inviable una
carrera por sector (la mayoría tiene 1-2 puestos).

**Decisión.** (1) Carrera por **rama**: 7 ramas agrupan los sectores en un catálogo satélite
`Ascensos` — `Trabajos` no se toca (precedente: `Pasivos`/`Camas`/`Picos`/`Cofres`). (2) Cuatro
requisitos por salto — antigüedad en el puesto, estudios, stat dominante de la rama y coins que se
queman — validados antes del cobro atómico. (3) La carrera **se conserva por rama** y el tier
alcanzado nunca baja (`GREATEST`): cambiar de rama es explorar, no un castigo. (4) Ramas con
huecos saltan al siguiente tier existente y las que topan por debajo de t4 acaban antes: el
catálogo puede crecer por rama sin tocar el diseño. (5) Migración V26 con borrón del trabajo
elegido: no había jugadores reales que migrar.

**Consecuencias.** El late-game gana un destino más (t4 cuesta 50 000 coins quemados) y los
estudios y las stats ganan un segundo uso. `/trabajo` pasa a 5 subcomandos. Queda fuera el rango
interno por puesto (descartado) y `/trabajo dimitir` (cambiar = elegir otro, YAGNI).

## ADR-015 — informe de cambios de /setup

**Estado:** aceptada e implementada.

**Contexto.** /setup era idempotente pero opaco: solo daba contadores, no qué cambiaba. Sin registro
no había forma de saber qué se añadió o actualizó en cada tanda.

**Decisión.** Un colector `RegistroCambios` recorre los helpers de setup y registra creado/actualizado/
eliminado por nombre; el contenido reaplicado (intros, welcome, descripción del servidor) se compara y
solo cuenta si difiere. El informe se renderiza con `InformeSetup`, se trocea con `util/Embeds` y se
envía a la respuesta y, persistente, a bot-logs. Setup pasa además a gestionar la descripción del
servidor.

**Consecuencias.** Cada /setup deja un registro consultable. Coste: instrumentación repartida por
SetupComando y alguna lectura extra a la API (`complete()`) para los diffs de contenido, que alarga
algo el montaje.

## ADR-016 — empresas como entidad ligada a rama

**Estado:** aceptada e implementada (Fase 1).

**Contexto.** Se pidió una jerarquía de trabajo «como una empresa»: dimitir y que los cargos altos
gestionen a los inferiores. El sistema de ascensos daba carrera individual por rama, pero no una
entidad colectiva.

**Decisión.** Empresa como **entidad** ligada a una rama, no una capa sobre la carrera: la funda un
**t4 de la rama** (100.000 coins quemados, cobro atómico con reembolso si el nombre colisiona), un
jugador pertenece a **una sola** empresa (UNIQUE global), y el ingreso exige **consentimiento** por
ambas vías (invitación del dueño / solicitud-con-motivo del jugador), resuelto por botones. La F1
monta solo estructura y pertenencia; rangos gestionables, ascenso por el jefe, despidos, economía
(reparto/bote/nivel), ranking y las funciones «vida real» (producción, impuestos, acciones,
reputación, bolsa de empleo, préstamos, eventos) quedan para fases posteriores.

**Consecuencias.** Migración V27 (empresas, empresa_miembros, empresa_pendientes) con FKs RGPD a
usuarios_discord. `/empresa` nuevo y `/trabajo dimitir`. La pertenencia se valida al entrar, no de
forma continua (dimitir no expulsa de la empresa en F1).

## ADR-017 — gobernanza de empresas

**Estado:** aceptada e implementada (Fase 2).

**Contexto.** La F1 daba miembros y rango, pero el rango no se podía cambiar ni echar a nadie. Se
quería «que los cargos altos gestionen a los inferiores», pero sin que un Directivo mande solo.

**Decisión.** El **Dueño** gestiona directo (cambiar rango, sacar, despedir) a cualquier rango
inferior. Un **Directivo** no ejecuta: **propone**, y los **altos cargos** (Dueño + Directivos)
votan; se aprueba por mayoría estricta del censo, con el voto del Dueño como desempate, y caduca a
las 48 h. El recuento ignora votos de quien ya no es alto cargo. Echar tiene dos modos: **sacar**
(conserva el trabajo) y **despedir** (al paro). La regla de rango (nunca tocar a un igual/superior)
acota el abuso. El disparo de ascenso de tier de un miembro se **difiere a F3** por su acople con la
economía (lo patrocina el bote).

**Consecuencias.** Migración V28 (empresa_propuestas, empresa_votos). `EmpresaGestionService` separado
de `EmpresaService`. La regla de rango se revalida al ejecutar (el objetivo pudo cambiar entre
proponer y aprobar).

## ADR-018 — economía de empresas

**Estado:** aceptada e implementada (Fase 3).

**Contexto.** El bote y el nivel de las empresas (columnas desde V27) estaban inertes; faltaba el
motor económico y el sentido de pertenecer.

**Decisión.** Un **10 % del curro** de cada miembro va al **bote**; el **nivel** (subido gastando del
bote, `/empresa mejorar`) da **+2 %/nivel de ingresos** a todos (tope +20 %); una **nómina diaria**
reparte el 20 % del bote por rango; y el bote **patrocina ascensos de tier** de los miembros
(`/empresa ascender`, vía la gobernanza de F2). Se refactoriza `TrabajoService.ascender` en
validación/aplicación reutilizables para el patrocinio sin duplicar reglas. Un ascenso patrocinado
que se aprueba pero no puede aplicarse (bote insuficiente o requisitos ya no cumplidos al ejecutar)
se anuncia como **aprobado pero no ejecutado**: no se toca el bote ni se miente al votante.

**Consecuencias.** Migración mínima V29 (columna `dato` en las propuestas para el puesto del ascenso);
`bote`/`nivel` ya existían. `TrabajoService.currar` depende ahora de `EmpresaRepositorio`. Nuevo
`NominaEmpresasJob` (03:00 Europe/Madrid).

## ADR-019 — estatus de empresas

**Estado:** aceptada e implementada (Fase 4).

**Contexto.** Tras la economía (F3) las empresas necesitaban un motivo para competir y un espacio propio.

**Decisión.** Un **ranking de prestigio** (`/empresa ranking`) ordena las empresas por un score puro
`nivel*10.000 + miembros*1.000 + bote/1.000` (el nivel domina; el bote pesa poco a propósito para no
premiar acaparar). Cada empresa tiene un **canal privado** por permisos de miembro (sin rol, como los
gremios), para **todas** las empresas y con **creación perezosa**: si `canal_id` es null, la primera
acción relevante (fundar, ingreso o `/empresa info` de la propia empresa) lo crea y sincroniza a los
miembros actuales. La materialización usa un `UPDATE ... WHERE canal_id IS NULL` condicional y borra el
canal recién creado si pierde la carrera, para no dejar huérfanos. Toda la lógica de canal vive en la
capa comando/listener (necesita el `Guild`); los services siguen guild-agnostic. Best-effort: sin
permisos, se registra y se sigue.

**Consecuencias.** Migración V30 (columna `canal_id` en `empresas`). Los cosméticos comprables se
difieren a una mini-fase posterior. La competición por rama (cuota de mercado) queda para F5.

## ADR-020 — producción y ventas de empresas

**Estado:** aceptada e implementada (Fase 5a).

**Contexto.** Tras F4 la empresa tenía estatus pero ninguna actividad productiva propia.

**Decisión.** Cada `/trabajo currar` de un miembro produce **mercancía** (`5 + nivel` unidades) al almacén
de su empresa —además del corte del 10 % al bote de F3—, con **tope por nivel** (`nivel × 100`): el rebose
se pierde (sumidero suave). `/empresa vender [cantidad]` (altos cargos, sin voto) convierte mercancía en
coins al **bote** menos un **impuesto del 15 % que se quema**. El descuento de mercancía es el gate
atómico (nunca se abona una venta que no pudo descontar). Números tunables en `Produccion`.

**Consecuencias.** Migración V31 (`empresas.mercancia`). La producción es una fuente de coins moderada y
triplemente frenada (energía del curro, tope de almacén, impuesto quemado). Impuestos al Estado, insumos,
precio fluctuante y venta B2B quedan para subsistemas posteriores de F5.
