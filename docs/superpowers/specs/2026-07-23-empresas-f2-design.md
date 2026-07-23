# Empresas · Fase 2 (Jerarquía y gestión) — diseño

**Fecha:** 2026-07-23
**Estado:** aprobado (brainstorming con el usuario)
**Depende de:** F1 (fundar y pertenecer, ya en `main`). **Roadmap:** [[gymprobot-backlog]].

## Contexto

La F1 dejó empresas con miembros y un rango (`RangoEmpresa`: BECARIO→EMPLEADO→ENCARGADO→DIRECTIVO→DUENO),
pero el rango no se puede cambiar y no hay forma de echar a nadie. La F2 añade la **gestión de la
plantilla** con una capa de **gobernanza**: el dueño actúa directo; un Directivo no manda solo, sino
que **propone** y los **altos cargos** (Dueño + Directivos) lo votan.

## Alcance de la F2

**Entra:** cambiar el rango de un miembro (promover/degradar), echar en dos modos (**sacar** = sale
de la empresa pero conserva su trabajo; **despedir** = además va al paro), y el sistema de
**propuestas + votación** para las acciones de los Directivos.

**NO entra:** disparar el ascenso de tier de un miembro (queda para **F3**, porque va acoplado a la
economía: la empresa lo patrocina desde el bote), traspaso de propiedad, y toda la economía
(reparto/bote/nivel/bonus) y F5+.

## Autoridad (regla de rango)

Orden de rango (menor→mayor): `BECARIO(0) < EMPLEADO(1) < ENCARGADO(2) < DIRECTIVO(3) < DUENO(4)`.

- Un actor solo puede gestionar a miembros de rango **estrictamente inferior** al suyo. Nunca a un
  igual o superior.
- **Promover** a un miembro: como mucho a un rango **estrictamente inferior** al del actor (el Dueño
  puede subir hasta DIRECTIVO; un Directivo puede proponer subir hasta ENCARGADO). Nunca se crea otro
  DUENO (el traspaso de propiedad es fase posterior).
- **Degradar / sacar / despedir**: solo objetivos de rango estrictamente inferior.
- **DUENO**: ejecuta las acciones **directamente**.
- **DIRECTIVO**: no ejecuta; **crea una propuesta** (ver gobernanza).
- ENCARGADO / EMPLEADO / BECARIO: sin poder de gestión.

## Gobernanza (propuestas y votación)

- **Altos cargos** = miembros con rango DUENO o DIRECTIVO. Son los que votan.
- Un Directivo `proponer(tipo, objetivo, [rangoNuevo])`: se valida su autoridad y la regla de rango
  (sobre el objetivo y, en CAMBIAR_RANGO, sobre el rango destino) **en el momento de proponer**. La
  propuesta nace con el voto **Sí** del proponente y una caducidad (`expira = ahora + 48 h`).
- **Votar** (`votar(propuestaId, votante, sí/no)`): solo altos cargos; un voto por votante
  (UNIQUE). Tras cada voto se **recomputa**: sea `N` el nº actual de altos cargos.
  - **Aprobada** si los Sí son **mayoría estricta** de `N` (`si > N/2`), o hay empate (`si == N/2`)
    **y el Dueño votó Sí** (el voto del Dueño desempata).
  - **Rechazada** si los No alcanzan la mayoría simétricamente, o si **caduca** sin aprobarse.
  - Al aprobarse se **ejecuta la acción** y se cierra la propuesta; al rechazarse/caducar, se cierra
    sin efecto. (Cerrar = borrar la propuesta y sus votos; el `ON DELETE CASCADE` ayuda.)
- La regla de rango se **revalida al ejecutar** (el objetivo pudo cambiar de rango entre proponer y
  aprobar): si ya no es válida, la propuesta se descarta con aviso.
- Un objetivo no puede tener **dos propuestas activas del mismo tipo** a la vez (UNIQUE
  `empresa_id + tipo + objetivo`), para no acumular votaciones duplicadas.

## Modelo de datos — Migración `V28__empresa_gobernanza.sql`

- **`empresa_propuestas`**
  - `id` BIGINT AUTO_INCREMENT PK
  - `empresa_id` BIGINT NOT NULL, FK → `empresas(id)` ON DELETE CASCADE
  - `tipo` VARCHAR(16) NOT NULL — `CAMBIAR_RANGO` | `SACAR` | `DESPEDIR`
  - `objetivo_discord_id` BIGINT NOT NULL, FK → `usuarios_discord(discord_id)` ON DELETE CASCADE
  - `rango_nuevo` VARCHAR(16) NULL — solo en `CAMBIAR_RANGO`
  - `proponente_discord_id` BIGINT NOT NULL, FK → `usuarios_discord(discord_id)` ON DELETE CASCADE
  - `creada` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `expira` TIMESTAMP NOT NULL
  - UNIQUE (`empresa_id`, `tipo`, `objetivo_discord_id`)
- **`empresa_votos`**
  - `propuesta_id` BIGINT NOT NULL, FK → `empresa_propuestas(id)` ON DELETE CASCADE
  - `votante_discord_id` BIGINT NOT NULL, FK → `usuarios_discord(discord_id)` ON DELETE CASCADE
  - `voto` BOOLEAN NOT NULL — true = Sí
  - PRIMARY KEY (`propuesta_id`, `votante_discord_id`)

Charset/engine como el resto (utf8mb4). FKs RGPD a `usuarios_discord` en todas las columnas de
`discord_id`, coherente con V27.

## Dominio

- Enum `TipoPropuesta { CAMBIAR_RANGO, SACAR, DESPEDIR }` (en `services/`).
- Records `Propuesta(id, empresaId, tipo, objetivoId, rangoNuevo, proponenteId, creada, expira)` y
  `Voto(propuestaId, votanteId, si)` en `db/`.
- `RangoEmpresa` gana un helper de orden si no lo tiene (usar `ordinal()` basta, el enum ya está en
  orden ascendente de autoridad).

## Repositorio — `EmpresaPropuestaRepositorio`

(JDBC, estilo `EmpresaRepositorio`.) Métodos: `long crear(Propuesta)`, `Optional<Propuesta>
porId(long)`, `List<Propuesta> activasDe(long empresaId)`, `void votar(long propuestaId, long
votanteId, boolean si)` (UPSERT: un voto por votante), `List<Voto> votos(long propuestaId)`, `void
cerrar(long propuestaId)` (borra propuesta; CASCADE borra votos). También en `EmpresaRepositorio`
(si no existen ya): `void actualizarRango(long empresaId, long discordId, RangoEmpresa)`, `void
quitarMiembro(long empresaId, long discordId)`, `List<MiembroEmpresa> altosCargos(long empresaId)`
(rango DUENO o DIRECTIVO).

## Servicio — `EmpresaGestionService`

Nuevo service (mantiene `EmpresaService` de F1 enfocado). Dependencias: `EmpresaRepositorio`,
`EmpresaPropuestaRepositorio`, `PersonajeRepositorio` (para el paro del despido).

- `gestionar(actorId, tipo, objetivoId, rangoNuevo)`: resuelve la autoridad del actor.
  - Si actor es **DUENO** y la regla de rango es válida → **ejecuta directo** y devuelve
    `ResultadoGestion.EJECUTADA`.
  - Si actor es **DIRECTIVO** y la regla es válida → crea propuesta (voto Sí del proponente) y
    devuelve `PROPUESTA_CREADA`.
  - Otro rango / regla inválida / objetivo no es de la empresa → el error correspondiente
    (`NO_AUTORIZADO`, `RANGO_INVALIDO`, `NO_ES_MIEMBRO`, `YA_HAY_PROPUESTA`).
- `votar(propuestaId, votanteId, si)`: valida alto cargo y no caducada; registra el voto; recomputa
  y, si procede, **ejecuta** o **cierra**. Devuelve `ResultadoVoto { REGISTRADO, APROBADA_EJECUTADA,
  RECHAZADA, CADUCADA, NO_AUTORIZADO, NO_EXISTE }`.
- Ejecución de la acción (privada, compartida por directo y por aprobación):
  - `CAMBIAR_RANGO` → `actualizarRango(...)`.
  - `SACAR` → `quitarMiembro(...)` (conserva trabajo).
  - `DESPEDIR` → `quitarMiembro(...)` **y** paro del objetivo (`personajes.fijarTrabajo(objetivoId,
    null)`, que ya resetea `turnos_puesto`, como en `/trabajo dimitir`).
  - Revalida la regla de rango justo antes; si ya no aplica, no ejecuta (aviso).

Predicado de aprobación exacto (documentar en código): con `N` altos cargos y `s` votos Sí, `n`
votos No → aprobada si `s > N/2 || (2*s == N && duenoVotoSi)`; rechazada si `n > N/2 || (2*n == N &&
duenoVotoNo)`; en otro caso sigue abierta hasta el siguiente voto o la caducidad.

## Comandos (subcomandos nuevos en `/empresa`)

- `/empresa rango <@usuario> <rango>` — `rango` con choices `BECARIO|EMPLEADO|ENCARGADO|DIRECTIVO`
  (no DUENO). Promueve/degrada; Dueño directo, Directivo → propuesta.
- `/empresa sacar <@usuario>` — echar suave (conserva trabajo).
- `/empresa despedir <@usuario>` — echar duro (→ paro).
- `/empresa propuestas` — lista las propuestas activas de tu empresa (si eres alto cargo) con
  botones **Sí/No** por propuesta; muestra tipo, objetivo, proponente, votos actuales y caducidad.
- Botones de voto: `EmpresaBotonesListener` (ampliar el de F1) con customId
  `empresa:voto:<propuestaId>:<1|0>` → `EmpresaGestionService.votar(...)`, edita el mensaje con el
  resultado (registrado / aprobada+ejecutada / rechazada / caducada).

Visibilidad: las acciones directas del dueño y las aprobaciones, **públicas** (la plantilla se ve);
errores/avisos personales, efímeros. Todo texto i18n ES+EN; todo embed por `EmbedFactory`.

## Anti-abuso

- Regla de rango (no tocar iguales/superiores) impide que un Directivo se cargue a otro Directivo o
  al Dueño.
- UNIQUE de una propuesta activa por (empresa, tipo, objetivo) evita spam de votaciones.
- Caducidad de 48 h limpia propuestas muertas (chequeo perezoso al listar/votar; sin job dedicado en
  F2).
- Cooldown opcional de propuestas por proponente: **fuera de F2** salvo que el smoke test muestre
  abuso (YAGNI).

## Manejo de errores

Excepciones custom + patrón existente. Las carreras (dos votos que cruzan el umbral, objetivo que
cambia de rango entre proponer y ejecutar) se cubren con la **revalidación al ejecutar** y las
restricciones de BD.

## Testing

- **`EmpresaGestionServiceTest`** (JUnit 5 + Mockito): autoridad (dueño directo vs directivo propone
  vs sin poder), regla de rango (no tocar igual/superior; promover solo por debajo del actor),
  ejecución de CAMBIAR_RANGO/SACAR/DESPEDIR (despedir manda al paro: `verify(personajes).fijarTrabajo(
  obj, null)`), y el **predicado de votación** (aprobación por mayoría, desempate del Dueño, rechazo,
  caducada, revalidación al ejecutar).
- **`EmpresaPropuestaRepositorioTest`** (Testcontainers, se salta en local): crear/leer/activas,
  UPSERT de voto, cerrar con CASCADE de votos, UNIQUE de propuesta activa.
- Comandos y listener → smoke test manual.

## Despliegue

Migración `V28` (sola al arrancar) + subcomandos nuevos en `/empresa` → **reiniciar el bot**. No
requiere `/setup`.

## ADR

**ADR-017 — gobernanza de empresas** (dueño directo, directivo propone, voto por mayoría de altos
cargos con desempate del dueño; despido en dos modos; el disparo de ascenso de tier se difiere a F3
por su acople económico).

## Fuera de alcance (YAGNI en F2)

Disparar ascenso de tier (F3), traspaso de propiedad, economía completa, ranking, cooldown de
propuestas, y F5+.
