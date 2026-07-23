# Empresas · Fase 1 (Fundar y pertenecer) — diseño

**Fecha:** 2026-07-23
**Estado:** aprobado (brainstorming con el usuario; dirección delegada)
**Roadmap completo:** en el backlog ([[gymprobot-backlog]]). Esta spec cubre SOLO la **F1**.

## Contexto

Las empresas son una entidad tipo gremio **ligada a una rama**, construida SOBRE el módulo de
ascensos (ramas + carrera por jugador, ya cerrado). La visión completa (economía, acciones,
impuestos, producción, etc.) está en fases posteriores. La **F1** monta el esqueleto: existir una
empresa y pertenecer a ella. Sin economía, sin gestión de rangos, sin bonus (esas son F2/F3).

Arquitectura del repo: Controller→Service→Repository, Flyway, JPA/JDBC según el patrón existente
(mirar `CarreraRepositorio`), `EmbedFactory` para todo embed, i18n ES+EN obligatorio, una clase por
comando, un test por service nuevo.

## Alcance de la F1

**Entra:** modelo de datos, fundar, ver, disolver, ingreso por invitación (con aceptar/rechazar) y
por solicitud-con-motivo (con aprobar/rechazar), y `/trabajo dimitir`.

**NO entra (fases siguientes):** gestión de rangos internos (promover/degradar), que el jefe dispare
ascensos de tier, sacar/despedir, corte de ingresos y reparto, subir nivel y bonus, ranking, canal
privado, cooldowns avanzados y toda la F5+.

## Modelo de datos — Migración `V27__empresas.sql`

- **`empresas`**
  - `id` BIGINT AUTO_INCREMENT PK
  - `rama` VARCHAR(20) NOT NULL — nombre del enum `Ascensos.Rama`
  - `dueno_discord_id` BIGINT NOT NULL
  - `nombre` VARCHAR(64) NOT NULL
  - `nivel` INT NOT NULL DEFAULT 1
  - `bote` BIGINT NOT NULL DEFAULT 0 — moneda de la empresa (se usa en F3; se crea ya)
  - `creada` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  - UNIQUE (`rama`, `nombre`) — sin nombres duplicados dentro de una rama
- **`empresa_miembros`**
  - `empresa_id` BIGINT NOT NULL, FK → `empresas(id)` ON DELETE CASCADE
  - `discord_id` BIGINT NOT NULL
  - `rango` VARCHAR(16) NOT NULL DEFAULT 'BECARIO' — enum `RangoEmpresa`
  - `se_unio` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  - PRIMARY KEY (`empresa_id`, `discord_id`)
  - UNIQUE (`discord_id`) — **un jugador, una empresa**
- **`empresa_pendientes`** (invitaciones y solicitudes, mismo modelo con dirección)
  - `id` BIGINT AUTO_INCREMENT PK
  - `empresa_id` BIGINT NOT NULL, FK → `empresas(id)` ON DELETE CASCADE
  - `discord_id` BIGINT NOT NULL
  - `tipo` VARCHAR(12) NOT NULL — `INVITACION` (la empresa invita) | `SOLICITUD` (el jugador pide)
  - `motivo` VARCHAR(300) NULL — carta del jugador en las SOLICITUD (null en INVITACION)
  - `creada` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  - UNIQUE (`empresa_id`, `discord_id`) — una pendiente por par empresa-jugador

Estilo SQL coherente con las migraciones existentes (comentarios de cabecera, `ENGINE`/charset
como el resto). No se editan migraciones aplicadas.

## Dominio

- **`RangoEmpresa`** (enum en `services/`): `BECARIO`, `EMPLEADO`, `ENCARGADO`, `DIRECTIVO`, `DUENO`.
  En F1 solo se usan `DUENO` (fundador) y `BECARIO` (nuevos miembros); el resto existe para F2. Cada
  valor lleva su clave i18n `rango.<minuscula>`.
- **`TipoPendiente`** (enum): `INVITACION`, `SOLICITUD`.
- Records/entidades: `Empresa(id, rama, duenoId, nombre, nivel, bote, creada)`,
  `MiembroEmpresa(empresaId, discordId, rango, seUnio)`, `Pendiente(id, empresaId, discordId, tipo,
  motivo, creada)`. Lombok según el patrón del repo.

## Repositorio — `EmpresaRepositorio`

Métodos (JDBC al estilo de `CarreraRepositorio`):
- `long fundar(String rama, long duenoId, String nombre)` — inserta empresa + al dueño como miembro
  `DUENO` en una transacción; devuelve el id. Falla si el nombre ya existe en la rama (UNIQUE) o el
  dueño ya está en una empresa (UNIQUE discord_id).
- `Optional<Empresa> porId(long id)` / `Optional<Empresa> porNombreYRama(String nombre, String rama)`.
- `Optional<Empresa> deMiembro(long discordId)` — la empresa del jugador, si tiene.
- `List<MiembroEmpresa> miembros(long empresaId)`.
- `List<Empresa> deRama(String rama)`.
- `void disolver(long empresaId)` — borra la empresa (CASCADE limpia miembros y pendientes).
- `void anadirMiembro(long empresaId, long discordId, RangoEmpresa rango)`.
- Pendientes: `long crearPendiente(long empresaId, long discordId, TipoPendiente tipo, String motivo)`,
  `Optional<Pendiente> pendiente(long id)`, `List<Pendiente> pendientesDe(long empresaId,
  TipoPendiente tipo)`, `List<Pendiente> invitacionesA(long discordId)`, `void borrarPendiente(long id)`.

## Servicio — `EmpresaService`

Reglas de negocio (devuelve enums de resultado como hace `TrabajoService`, p. ej.
`ResultadoFundar { OK, SIN_TRABAJO, NO_ES_TIER4, YA_EN_EMPRESA, NOMBRE_EN_USO, SIN_SALDO }`):

- **`fundar(discordId, nombre)`**: el jugador debe tener trabajo; la rama es la de su trabajo actual;
  debe ser **t4 en esa rama** (reusar `TrabajoService.tierAlcanzadoEn(discordId, rama) == 4`); no
  estar ya en una empresa; pagar el **coste de fundación** (coins **quemados**, importe en el plan;
  vía `EconomiaRepositorio.gastar`). El cobro es lo último (nunca se paga una fundación fallida,
  como en ascensos). Crea la empresa con el dueño de rango `DUENO`.
- **`info(discordId | nombre+rama)`**: datos de la empresa + lista de miembros con rango.
- **`disolver(discordId)`**: solo el `DUENO`; borra la empresa (CASCADE).
- **`invitar(duenoId, objetivoId)`**: solo el `DUENO`; el objetivo debe tener trabajo en la rama de
  la empresa y no estar en ninguna empresa; crea pendiente `INVITACION`. Enum de resultado con los
  casos de error.
- **`solicitar(discordId, nombre, rama, motivo)`**: el jugador debe tener trabajo en esa rama y no
  estar en empresa; crea pendiente `SOLICITUD` con el motivo (máx. 300 chars).
- **`resolver(pendienteId, aceptar, quienResuelveId)`**: valida que quien resuelve es la parte
  correcta (INVITACION → la resuelve el jugador invitado; SOLICITUD → la resuelve el dueño); si
  acepta y el jugador sigue sin empresa, lo añade como `BECARIO` y borra la pendiente; si rechaza,
  solo borra. Revalida que el jugador no se haya metido en otra empresa entretanto.
Todas las escrituras que puedan competir (fundar, aceptar) confían en las restricciones UNIQUE de la
BD como red de seguridad, además del chequeo previo.

**`dimitir` va en `TrabajoService`, no en `EmpresaService`** (manipula `personaje.trabajo`):
`TrabajoService.dimitir(discordId)` pone el trabajo a `null` y resetea `turnos_puesto` (paro).
**No** toca la pertenencia a empresa (la pertenencia se valida al entrar, no de forma continua — se
documenta como decisión de F1). Devuelve OK/SIN_TRABAJO.

## Comandos

- **`/empresa`** (una clase `EmpresaComando` en `commands/economia/`, subcomandos):
  - `fundar <nombre>` — funda (público; celebración con embed).
  - `info [nombre]` — ver tu empresa o una por nombre.
  - `disolver` — con confirmación por botón (Sí/No), solo dueño.
  - `invitar <@usuario>` — crea invitación; se notifica al usuario con embed + botones
    **Aceptar/Rechazar** (listener de botones que llama a `resolver`).
  - `solicitar <nombre> [motivo]` — crea solicitud; el dueño la ve en `pendientes`.
  - `pendientes` — el dueño ve las **solicitudes** con su motivo y botones Aprobar/Rechazar; un
    jugador ve sus **invitaciones** con Aceptar/Rechazar. (Reusa el patrón de listener de botones ya
    presente en el repo; el estado va en el `customId` como en el paginador existente.)
- **`/trabajo dimitir`** — nuevo subcomando en el `TrabajoComando` existente (renunciar → paro),
  con confirmación por botón (es destructivo: pierdes antigüedad del puesto).

Visibilidad: por defecto público (economía a la vista); las confirmaciones y errores personales,
efímeros. Todo texto por i18n ES+EN; todo embed por `EmbedFactory` (tipo `ECONOMIA`).

## i18n

Claves nuevas en `messages_es.properties` y `messages_en.properties` (mismo conjunto): descripciones
de comando/subcomandos y opciones, `empresa.*` (fundada, info, disuelta, invitación, solicitud,
aceptada, rechazada, y todos los errores de los enums de resultado), `rango.becario|empleado|
encargado|directivo|dueno`, y las de `/trabajo dimitir` (`dimitir.ok`, `dimitir.sintrabajo`,
confirmación). Nombres i18n de rama reutilizan las `rama.*` de ascensos.

## Manejo de errores

Excepciones custom + patrón existente; nunca capturar y silenciar. Los conflictos de carrera (dos
aceptaciones a la vez, nombre pillado entre el chequeo y el insert) se resuelven por las UNIQUE de la
BD y se traducen a un error de usuario limpio.

## Testing

- **`EmpresaServiceTest`** (JUnit 5 + Mockito, repos mockeados): fundar (t4 OK, no-t4, sin trabajo,
  ya en empresa, sin saldo, cobro es lo último), invitar/solicitar (requisitos de rama y de
  no-pertenencia), resolver (parte correcta, aceptar añade BECARIO, rechazar solo borra, revalidación
  de no-empresa), disolver (solo dueño).
- **`TrabajoServiceTest`** (ampliar el existente): `dimitir` (OK deja al personaje en paro con
  `turnos_puesto` a 0; SIN_TRABAJO si ya estaba parado).
- Los comandos y listeners de botones son Discord-en-vivo → smoke test manual.

## Despliegue

Migración `V27` (se aplica sola al arrancar) + comando nuevo `/empresa` + subcomando nuevo en
`/trabajo` → **reiniciar el bot** (registro de comandos en `onGuildReady`). No requiere `/setup`.

## ADR

Nuevo **ADR-016 — empresas como entidad ligada a rama** (contexto: la jerarquía de trabajo pedida;
decisión: entidad propia sobre ascensos, una-empresa-por-jugador, ingreso con consentimiento por
ambas vías, F1 sin economía; consecuencias y qué queda para fases siguientes).

## Fuera de alcance (YAGNI en F1)

Rangos gestionables, ascenso de tier por el jefe, despidos, economía/reparto/bote activo, nivel y
bonus, ranking, canal privado, cooldowns, y todo F5+.
