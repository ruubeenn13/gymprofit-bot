# Empresas Fase 5b — Impuestos y quiebra (diseño)

**Fecha:** 2026-07-24
**Fase:** subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción).
**Precondición:** F5a desplegada y funcionando (regla de fases del `CLAUDE.md`).

## Objetivo

Dar a la empresa un **coste recurrente de existir**: una cuota semanal escalada por nivel que se quema
del bote (sumidero antiinflación). Si la empresa no puede pagar, entra en **morosidad**; tras 3 semanas
de impago consecutivo **quiebra** (disolución forzosa, conservando los miembros su trabajo).

## Flujo

1. **Cobro semanal** (`ImpuestoEmpresasJob`, **lunes 02:00 Europe/Madrid** — *antes* de la nómina diaria
   de las 03:00, para que la empresa cumpla sus obligaciones antes de repartir a los miembros). Por cada
   empresa:
   - `cuota = nivel × FACTOR_CUOTA` (**2.500**; nivel 1 → 2.500/sem, nivel 10 → 25.000/sem).
   - **Si `bote ≥ cuota`:** `gastarDelBote(cuota)` → **quemado** (sumidero, sin contrapartida en el
     ledger); `impagos = 0`. La empresa está al día.
   - **Si `bote < cuota`:** no se cobra parcial; `impagos += 1`; queda **morosa**. Si `impagos ≥
     MOROSIDAD_MAX` (**3**) → **quiebra**: `repo.disolver(empresaId)` (borra la empresa; la cascada de FK
     saca a los miembros pero **conserva su trabajo** —`disolver` no toca `personajes.trabajo`—, así que
     nadie va al paro).
2. **Morosidad visible:** `/empresa info` muestra `⚠️ Morosa: impagos X/3` cuando `impagos > 0`. Al pagar
   una cuota, vuelve a 0 (la morosidad es de impagos **consecutivos**).

### Antiinflación

La cuota **quema** coins del bote de forma recurrente → sumidero fuerte que contrarresta las fuentes
(corte del curro de F3, ventas de F5a, nómina). Escala con el nivel: cuanto más grande la empresa, más
caro mantenerla, lo que frena inflar el nivel sin actividad real que alimente el bote.

## Avisos

Best-effort, al **canal privado de la empresa** (F4, si existe) y archivado en **`#bot-logs`**
(`ConfigServidorService.canalBotLogs()`, como el informe de `/setup`):
- **Morosidad:** aviso de impago (impagos X/3, cuánto falta para la cuota) — serio, es dinero/gobernanza.
- **Quiebra:** aviso de cierre de la empresa.
- El pago al día **no** genera aviso (evitar spam semanal). Sin canal ni `bot-logs`, solo se aplica el
  efecto (nunca una excepción rompe el job: mismo best-effort que `NominaEmpresasJob`).

## Esquema y componentes

- **Migración V32:** `ALTER TABLE empresas ADD COLUMN impagos INT NOT NULL DEFAULT 0`.
- **`Empresa` record** gana `int impagos`; incluido en `SELECT_EMPRESA`/`mapearEmpresa` y en `deMiembro`.
- **Función pura `Impuesto`** (clase final, sin estado): `cuota(int nivel)` (= `nivel × FACTOR_CUOTA`),
  constante `MOROSIDAD_MAX = 3`. Testeable aislada.
- **`EmpresaRepositorio`:** `impagos` en las lecturas; `fijarImpagos(long empresaId, int n)` (un único
  setter cubre tanto reset a 0 como el nuevo valor tras un impago). `gastarDelBote` (condicional) y
  `disolver` ya existen. Necesita además **listar TODAS las empresas** para el job (no solo las que tienen
  bote: una morosa con bote 0 también acumula impago) → `todas()` (o reutilizar un listado existente si lo
  hay; si no, añadir `List<Empresa> todas()`).
- **`ImpuestoEmpresasService`** — la **decisión pura** y testeable, separada del job (que hace I/O de
  Discord). `evaluar(Empresa) -> ResultadoImpuesto`:
  - `PAGA(cuota)` si `bote ≥ cuota`.
  - `MOROSA(nuevosImpagos, cuota, falta)` si `bote < cuota` y `nuevosImpagos < MOROSIDAD_MAX`.
  - `QUIEBRA(cuota)` si `bote < cuota` y `nuevosImpagos ≥ MOROSIDAD_MAX`.
  Un método `aplicar(...)` (o el job) ejecuta el efecto sobre el repo: `PAGA` → `gastarDelBote(cuota)` +
  `fijarImpagos(id,0)`; `MOROSA` → `fijarImpagos(id, nuevosImpagos)`; `QUIEBRA` → `disolver(id)`. El gate
  de dinero es `gastarDelBote` (condicional): si por una carrera el bote bajó entre evaluar y cobrar, el
  `gastarDelBote` devuelve false → se trata como impago (no se quema de más).
- **`ImpuestoEmpresasJob`** — espejo de `NominaEmpresasJob`: se auto-reprograma calculando el **próximo
  lunes 02:00** local; itera `todas()`, evalúa cada empresa con el service, aplica el efecto y manda los
  avisos (canal privado + `bot-logs`).
- **`/empresa info`** — línea de morosidad cuando `impagos > 0`.

## i18n (ES + EN, obligatorio en ambos)

- `empresa.impuesto.morosa` (impagos X/3, cuota, cuánto falta), `empresa.impuesto.quiebra`.
- `empresa.info.morosa` (línea `⚠️ Morosa: {0}/{1}` para el embed de `info`).

## Tests

- **`Impuesto`** (pura): `cuota(nivel)`, `MOROSIDAD_MAX`.
- **`ImpuestoEmpresasService.evaluar`** (Mockito/puro): bote ≥ cuota → PAGA; bote < cuota con impagos 0/1
  → MOROSA con impagos+1; bote < cuota con impagos 2 → QUIEBRA; y `aplicar` → `gastarDelBote`+reset,
  `fijarImpagos`, `disolver` según rama; `gastarDelBote` false → tratado como impago.
- **`EmpresaRepositorio`** (Testcontainers): `fijarImpagos` persiste; `impagos` se lee; `todas()` lista.
- Baseline actual: 544 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-021** — impuestos y quiebra de empresas (comprobar que el último ADR es 020).
- `docs/architecture.md`: viñeta F5b en el bloque de empresas; migraciones a V32; `ImpuestoEmpresasJob` en
  la fila de `jobs/`.
- `CHANGELOG.md`: entrada F5b bajo `## [Sin publicar]` / `### Añadido`.
- `README.md` / `README.en.md`: mencionar la cuota semanal si procede (no hay comando nuevo).

## Despliegue

Al cerrar F5b: **reiniciar bot** (aplica V32 + `ImpuestoEmpresasJob`). **No** requiere `/setup` ni añade
comandos. Smoke test: forzar una empresa con bote < cuota y ver el aviso de morosidad + `impagos` subir en
`/empresa info`; una con bote suficiente ve bajar el bote la cuota; simular 3 impagos → disolución con los
miembros conservando su trabajo. (Como el cobro es semanal, para el smoke conviene una constante/gatillo
manual temporal o ajustar la hora; decidir en el plan si se expone un disparo manual de prueba.)

## Fuera de alcance (F5b)

- Impuesto progresivo por tramos, condonación/rescate, deuda acumulada (aquí el impago solo suma al
  contador, no crea saldo negativo), impuestos a jugadores individuales. Subsistemas posteriores.

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `FACTOR_CUOTA` | 2.500 | cuota = nivel × 2.500 semanal (nivel 1 → 2.500; nivel 10 → 25.000) |
| `MOROSIDAD_MAX` | 3 | impagos consecutivos → quiebra |
| Cobro | lunes 02:00 Europe/Madrid | antes de la nómina (03:00) |

## Orden de implementación (subagent-driven; la lógica de dinero/quiebra lleva review)

- **T1**: V32 + `Impuesto` (pura) + `impagos` en `Empresa`/repo + `fijarImpagos`/`todas()` + tests.
- **T2**: `ImpuestoEmpresasService` (decisión `evaluar` + `aplicar`) + tests. **Lleva review** (dinero:
  quema de la cuota, gate `gastarDelBote`, y la quiebra).
- **T3**: `ImpuestoEmpresasJob` (lunes 02:00, auto-reprograma) + avisos (canal privado + `bot-logs`) +
  línea de morosidad en `/empresa info` + i18n + wiring en `Main`.
- **T4**: docs (ADR-021, architecture, CHANGELOG, READMEs) + `clean verify` final.
