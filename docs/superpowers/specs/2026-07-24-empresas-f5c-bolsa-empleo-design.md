# Empresas Fase 5c — Bolsa de empleo (diseño)

**Fecha:** 2026-07-24
**Fase:** subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción + F5b impuestos).
**Precondición:** F5b desplegada y funcionando.

## Objetivo

Un tablón público **`/empleo`** para descubrir qué empresas contratan y solicitar entrar **sin conocer
el nombre**. Reutiliza el flujo de solicitud/aprobación de F1; lo nuevo es la capa de descubrimiento
(un flag opt-in por empresa) y el poder solicitar desde el propio tablón.

## Piezas

### 1. Flag `contratando` (opt-in por empresa)

- **Migración V33:** `ALTER TABLE empresas ADD COLUMN contratando BOOLEAN NOT NULL DEFAULT FALSE`.
- `Empresa` record gana `boolean contratando`; incluido en `SELECT_EMPRESA`/`mapearEmpresa` y `deMiembro`.
- Repo: `fijarContratando(long empresaId, boolean valor)`; `contratandoDeRama(String rama)` → empresas con
  el flag activo de esa rama, ordenadas por nivel desc (y nombre) para que las más fuertes salgan arriba.

### 2. `/empleo contratar` (toggle, altos cargos)

- Un **DUEÑO o DIRECTIVO** alterna el flag de **su** empresa. Reusa la comprobación de alto cargo
  (`repo.altosCargos(empresaId)`, como `EmpresaVentaService.esAltoCargo`). No es gobernanza con voto:
  es operativa, directa.
- Responde el estado nuevo: 🟢 abierta a solicitudes / 🔴 cerrada. Errores: `SIN_EMPRESA`, `NO_AUTORIZADO`.

### 3. `/empleo` (tablón, para quien busca)

- Resuelve la **rama del invocador** por su trabajo actual (`Personaje.trabajo()` → `Ascensos.ramaDe`).
  - **Sin trabajo** → aviso "elige un trabajo primero" (sin trabajo no hay rama ni empresa a la que
    entrar).
  - **Ya en una empresa** → lista igual, pero **sin botón de solicitar** y con nota "ya perteneces a una
    empresa" (no se puede estar en dos).
- Lista las empresas **contratando de esa rama** (`contratandoDeRama`): por cada una, nombre, nivel, nº
  de miembros y bote, con un botón **Solicitar** (`customId empleo:solicitar:<empresaId>`). Público.
  Troceado en varios embeds si excede 4096 (`util/Embeds.partirEnBloques`, como `/trabajo lista`).
- Si no hay ninguna contratando en la rama: mensaje "no hay vacantes ahora mismo".

### 4. Solicitar desde el botón (`empleo:solicitar:<id>`)

- El botón abre un **modal** que pide el **motivo** (misma "carta" que `/empresa solicitar`).
- Al enviar el modal, reutiliza la lógica de solicitud por **id** (nueva variante
  `EmpresaService.solicitarPorId(long discordId, long empresaId, String motivo)` — misma validación que
  `solicitar` por nombre: misma rama, no estar ya en otra empresa, empresa existe, no duplicar pendiente;
  devuelve el mismo `ResultadoIngreso`). Crea una `SOLICITUD` pendiente.
- La **resuelve el dueño** por el **flujo de botones de pendientes de F1 ya existente** (aprobar/rechazar),
  que al aceptar da el alta y, por F4, añade al miembro al canal privado. **No se toca ese flujo**: solo
  se alimenta desde una entrada nueva.
- Los errores del modal reusan los mensajes de `ResultadoIngreso` (p. ej. `YA_EN_EMPRESA`, `OTRA_RAMA`,
  `YA_PENDIENTE`), efímeros.

### 5. `/empresa info`

- Muestra `🟢 Contratando` cuando el flag está activo (línea condicional, sin renumerar el cuerpo).

## Reutiliza (no se reinventa)

`EmpresaService.solicitar`/`resolver`, `ResultadoIngreso`/`ResultadoResolver`, los botones de pendientes
de F1, el alta en el canal privado de F4, y el troceado de embeds de `util/Embeds`. Lo único nuevo del
ingreso es la **variante por id** (el botón lleva id, no nombre) y el **modal** de motivo.

## i18n (ES + EN, obligatorio en ambos)

- `comando.empleo.desc`, `comando.empleo.contratar.desc`.
- `empleo.tablon.titulo`, `empleo.tablon.fila` (nombre, nivel, miembros, bote), `empleo.tablon.vacio`,
  `empleo.sin_trabajo`, `empleo.ya_en_empresa`.
- `empleo.contratar.abierta`, `empleo.contratar.cerrada`, `empleo.contratar.sin_empresa`,
  `empleo.contratar.no_autorizado`.
- `empleo.solicitar.boton`, `empleo.solicitar.modal.titulo`, `empleo.solicitar.modal.motivo`,
  `empleo.solicitar.enviada`, y el reuso de las claves de error de `ResultadoIngreso` ya existentes.
- `empresa.info.contratando` (línea `🟢 Contratando`).

## Tests

- **`EmpresaRepositorio`** (Testcontainers): `fijarContratando` persiste; `contratandoDeRama` filtra por
  rama y flag y ordena; `contratando` se lee en `Empresa`.
- **`EmpresaService.solicitarPorId`** (Mockito/Testcontainers según el estilo de los tests de `solicitar`):
  OK crea la SOLICITUD; `YA_EN_EMPRESA`/`OTRA_RAMA`/`EMPRESA_NO_EXISTE`/`YA_PENDIENTE` según el caso; y que
  reusa la misma validación que `solicitar` por nombre (no duplicar reglas).
- **Toggle** en el service que lo implemente: abre/cierra; no autorizado para no-altos-cargos.
- Baseline actual: 555 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-022** — bolsa de empleo de empresas (comprobar que el último ADR es 021).
- `docs/architecture.md`: viñeta F5c en el bloque de empresas; migraciones a V33.
- `CHANGELOG.md`: entrada F5c bajo `## [Sin publicar]` / `### Añadido`.
- `README.md` / `README.en.md`: añadir `/empleo` a la lista de comandos.

## Despliegue

Al cerrar F5c: **reiniciar bot** (V33 + comandos `/empleo` + listener del botón/modal). Añade slash
commands nuevos → hay que reiniciar para re-registrarlos; **no** requiere `/setup`. Smoke test: un alto
cargo abre su empresa con `/empleo contratar`; otro jugador de la misma rama ve el tablón con `/empleo`,
pulsa Solicitar, rellena el motivo, y el dueño recibe/aprueba la solicitud (flujo F1) con alta en el canal
privado; comprobar los avisos de sin-trabajo y ya-en-empresa; y `🟢 Contratando` en `/empresa info`.

## Fuera de alcance (F5c)

- Vacantes por puesto concreto, nº de plazas, requisitos por vacante, ranking de empleadores,
  notificación push de vacantes nuevas, y cualquier tope de tamaño de empresa (las empresas no lo tienen;
  el control de entrada es el flag on/off).

## Orden de implementación (subagent-driven)

- **T1**: V33 + `Empresa.contratando` + repo (`fijarContratando`, `contratandoDeRama`) +
  `EmpresaService.solicitarPorId` + tests.
- **T2**: `/empleo contratar` (toggle) + `/empleo` (tablón con botones) + `🟢 Contratando` en `/empresa
  info` + i18n.
- **T3**: listener del botón `empleo:solicitar` + modal de motivo → `solicitarPorId` (reusa el flujo de
  pendientes/resolución de F1). **Lleva review** (nuevo camino de ingreso + modal + reuso de la validación).
- **T4**: docs (ADR-022, architecture, CHANGELOG, READMEs) + `clean verify` final.
