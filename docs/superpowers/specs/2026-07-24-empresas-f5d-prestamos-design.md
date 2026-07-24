# Empresas Fase 5d — Préstamos empresariales (diseño)

**Fecha:** 2026-07-24
**Fase:** subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción + F5b impuestos + F5c
empleo).
**Precondición:** F5c desplegada y funcionando.

## Objetivo

Una empresa puede pedir **un préstamo** (uno a la vez, como el banco de jugador): el bote recibe el
principal y lo devuelve con interés en **cuotas semanales** cobradas por el job de F5b. El impago de la
cuota se engancha a la **morosidad/quiebra de F5b** (mismo contador, misma consecuencia). Da liquidez a
las empresas jóvenes a cambio de una obligación recurrente.

## Flujo

1. **Conceder** (`/empresa prestamo <cantidad>`, **altos cargos** DUEÑO/DIRECTIVO): si la empresa **no
   tiene préstamo activo** (`deuda == 0`) y `1 ≤ cantidad ≤ Prestamo.limite(nivel)`:
   - `incrementarBote(cantidad)` (el principal entra al bote).
   - `deuda = Prestamo.deudaConInteres(cantidad)` (= `cantidad × (1 + INTERES)`);
     `cuota_prestamo = Prestamo.cuota(deuda)` (= `ceil(deuda / PLAZO_SEMANAS)`).
   - Errores: `SIN_EMPRESA`, `NO_AUTORIZADO`, `YA_TIENE_PRESTAMO`, `CANTIDAD_INVALIDA` (≤0),
     `LIMITE` (> límite del nivel).

2. **Cobro semanal** (dentro del `ImpuestoEmpresasJob` ya existente, lunes 02:00). La **obligación** de la
   semana es `Impuesto.cuota(nivel) + cuota_prestamo`:
   - `bote ≥ obligación` → `gastarDelBote(obligación)`; `impagos = 0`; **amortiza**:
     `deuda -= min(cuota_prestamo, deuda)`; si `deuda == 0` → `cuota_prestamo = 0` (saldado).
   - `bote < obligación` → **impago** (nada parcial): `impagos += 1`; a `MOROSIDAD_MAX` (3) → **quiebra**
     (la deuda muere con la empresa disuelta). Misma mecánica que F5b.

3. **Amortizar antes** (`/empresa pagar-prestamo [cantidad]`, altos cargos): paga del bote lo indicado
   (o, sin `cantidad`, todo lo posible hasta saldar) contra la deuda mediante el gate `gastarDelBote`.
   Baja `deuda`; si `deuda` cae por debajo de `cuota_prestamo`, ajusta `cuota_prestamo = deuda`; si llega a
   0, `cuota_prestamo = 0`. Errores: `SIN_EMPRESA`, `NO_AUTORIZADO`, `SIN_DEUDA`, `SIN_FONDOS`.

4. **`/empresa info`:** muestra `💳 Deuda: {deuda} 🪙 (cuota {cuota}/sem)` cuando `deuda > 0`.

### Cambio en F5b (obligación única)

`ImpuestoEmpresasService` pasa a evaluar la **obligación total** (`Impuesto.cuota(nivel) +
empresa.cuotaPrestamo()`) en vez de solo el impuesto; en el camino PAGA, tras `gastarDelBote(obligación)`,
**amortiza** la deuda (`fijarPrestamo` con la deuda y cuota nuevas). Un solo gate, un solo contador de
impagos, misma decisión PAGA/MOROSA/QUIEBRA. El aviso semanal de morosidad/quiebra menciona que la
obligación incluye la cuota del préstamo cuando la hay. **Este cambio re-toca la lógica de dinero de F5b →
lleva review.**

### Antiinflación

El préstamo **crea** liquidez temporal (principal al bote) pero se devuelve con **interés** (`+20 %` que
sale del bote vía el gate y no vuelve a nadie: es un sumidero neto a medio plazo). No es una fuente
permanente: uno a la vez, con límite por nivel y obligación semanal que puede quebrar la empresa.

## Esquema y componentes

- **Migración V34:** `ALTER TABLE empresas ADD COLUMN deuda BIGINT NOT NULL DEFAULT 0, ADD COLUMN
  cuota_prestamo BIGINT NOT NULL DEFAULT 0`. `Empresa` record gana `long deuda, long cuotaPrestamo`;
  incluidos en `SELECT_EMPRESA`/`mapearEmpresa`/`deMiembro`.
- **Función pura `Prestamo`** (clase final): `limite(int nivel)` (= `nivel × LIMITE_POR_NIVEL`),
  `deudaConInteres(long principal)` (= `round(principal × (1 + INTERES))`), `cuota(long deuda)` (=
  `ceil(deuda / PLAZO_SEMANAS)`), constantes `INTERES`, `PLAZO_SEMANAS`, `LIMITE_POR_NIVEL`. Testeable.
- **`EmpresaRepositorio`:** `deuda`/`cuota_prestamo` en lecturas; `fijarPrestamo(long empresaId, long
  deuda, long cuota)` (un setter para conceder/amortizar/saldar).
- **`PrestamoEmpresasService`:** `conceder(actorId, cantidad) -> ResultadoPrestamo` y `pagar(actorId,
  cantidad?) -> ResultadoPago`. Autorización de alto cargo (`repo.altosCargos`), gate `gastarDelBote`
  para el pago, `incrementarBote` para el principal. Nunca deja estado a medias (el orden es el patrón
  atómico del resto: validar → mover dinero → fijar).
- **`ImpuestoEmpresasService`:** obligación = impuesto + cuota; amortiza en PAGA (ver arriba).
- **`ImpuestoEmpresasJob`:** al aplicar, pasa a `fijarPrestamo` la deuda/cuota resultantes (el service las
  calcula); el aviso incluye la cuota del préstamo.
- **`EmpresaComando`:** subcomandos `prestamo` (opción entera `cantidad`) y `pagar-prestamo` (opción
  entera opcional `cantidad`); línea de deuda en `info`.
- **i18n ES+EN, ADR-023, docs.**

## Tests

- **`Prestamo`** (pura): `limite`, `deudaConInteres`, `cuota` (redondeos incluidos).
- **`PrestamoEmpresasService`** (Mockito): conceder OK (bote↑, deuda/cuota fijadas), `YA_TIENE_PRESTAMO`,
  `LIMITE`, `NO_AUTORIZADO`, `SIN_EMPRESA`, `CANTIDAD_INVALIDA`; pagar OK (amortiza, ajusta cuota),
  `SIN_DEUDA`, `SIN_FONDOS`, pago que salda (cuota→0).
- **`ImpuestoEmpresasService`** (actualizado): obligación incluye la cuota; PAGA amortiza (deuda baja,
  cuota se ajusta/salda); sin deuda se comporta como F5b; obligación no cubierta → impago sin amortizar.
- **`EmpresaRepositorio`** (Testcontainers): `fijarPrestamo` persiste deuda+cuota; se leen en `Empresa`.
- Baseline actual: 565 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-023** — préstamos empresariales (comprobar que el último ADR es 022).
- `docs/architecture.md`: viñeta F5d; migraciones a V34; nota de que el job de F5b cobra ahora la
  obligación (impuesto + cuota).
- `CHANGELOG.md`: entrada F5d.
- `README.md` / `README.en.md`: `/empresa` suma `prestamo · pagar-prestamo`.

## Despliegue

Al cerrar F5d: **reiniciar bot** (V34 + subcomandos `prestamo`/`pagar-prestamo` + la obligación en el job
semanal). Añade slash commands → reiniciar para re-registrarlos; **no** requiere `/setup`. Smoke: un alto
cargo pide `/empresa prestamo` (ver bote↑ y deuda en `info`); amortizar con `/empresa pagar-prestamo`;
comprobar que el cobro semanal descuenta impuesto + cuota (invocar `cobrar()` a mano como en F5b), que
saldar deja `deuda`/`cuota` a 0, y que si el bote no cubre la obligación cuenta como impago (F5b).

## Fuera de alcance (F5d)

- Varios préstamos simultáneos, refinanciación, aval/garantía, embargo de activos al quebrar, préstamos
  entre empresas, interés variable.

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `LIMITE_POR_NIVEL` | 20.000 | límite = nivel × 20.000 (nivel 1 → 20k; nivel 10 → 200k) |
| `INTERES` | 0,20 | deuda = principal × 1,20 |
| `PLAZO_SEMANAS` | 4 | cuota = ceil(deuda / 4) |

## Orden de implementación (subagent-driven; la lógica de dinero lleva review)

- **T1**: V34 + `Empresa.deuda/cuotaPrestamo` + `Prestamo` (pura) + repo (`fijarPrestamo`) + tests.
- **T2**: `PrestamoEmpresasService` (`conceder`/`pagar`) + `/empresa prestamo` + `/empresa pagar-prestamo`
  + deuda en `info` + i18n. **Review** (dinero: principal al bote, gate del pago, límite).
- **T3**: obligación única en `ImpuestoEmpresasService` (impuesto + cuota, amortiza en PAGA) + `fijarPrestamo`
  desde el job + avisos + tests actualizados. **Review** (re-toca la lógica de dinero/quiebra de F5b).
- **T4**: docs (ADR-023, architecture, CHANGELOG, READMEs) + `clean verify` final.
