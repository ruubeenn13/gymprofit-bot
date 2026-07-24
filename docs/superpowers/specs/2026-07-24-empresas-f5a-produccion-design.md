# Empresas Fase 5a — Producción y ventas (diseño)

**Fecha:** 2026-07-24
**Fase:** primer subsistema de F5 del roadmap de empresas (sobre F1 fundar, F2 gobernanza, F3 economía,
F4 estatus). El resto de F5 (impuestos, acciones, bolsa de empleo, préstamos, eventos) queda para specs
posteriores independientes.
**Precondición:** F4 desplegada y funcionando (regla de fases del `CLAUDE.md`).

## Objetivo

Dar a la empresa una **actividad productiva**: genera **mercancía** como subproducto del trabajo de sus
miembros y la vende para engordar el bote. Es una **fuente de coins moderada y frenada**, respetando el
principio antiinflación del RPG.

## Flujo económico

1. **Producir (pasivo, ligado al gameplay).** Cada `/trabajo currar` de un miembro que pertenece a una
   empresa suma **`unidades = base + nivel`** al almacén de mercancía de su empresa — *además* del corte
   del 10 % al bote que ya existe (F3). La mercancía es el "output" narrativo del curro. No añade grind
   nueva: reutiliza `/trabajo currar`.
2. **Almacén con tope.** Capacidad = **`nivel × FACTOR_CAPACIDAD`**. Si el almacén está lleno, la
   mercancía extra del curro **se pierde** (sumidero suave, presiona a vender y da otra razón para
   `/empresa mejorar`). El corte al bote del 10 % NO se ve afectado por el rebose: solo se pierde la
   mercancía sobrante. `/empresa info` muestra `almacén X/Y`.
3. **Vender (`/empresa vender [cantidad]`).** Convierte mercancía en coins al **bote**, menos un
   **impuesto de venta que se quema**:
   - `bruto = min(cantidad, mercancia_actual) × PRECIO_UNIDAD`
   - `impuesto = bruto × IMPUESTO_VENTA` → **quemado** (sumidero, sin contrapartida en el ledger)
   - `neto = bruto − impuesto` → al **bote** (`incrementarBote`)
   - se descuenta del almacén la cantidad vendida.
   Sin argumento `cantidad`, vende **todo** el almacén.

### Antiinflación

La única inyección de coins es al vender, y va **al bote**, nunca directo al jugador. El bote ya tiene
frenos (mejoras y ascensos **queman**; la nómina redistribuye el 20 % diario). Encima: la **energía**
limita cuánto se curra, el **tope de almacén** limita la acumulación y el **impuesto** quema en cada
venta. Fuente moderada y triplemente frenada. Los números son tunables si el balance en vivo lo pide.

## Quién vende

**Altos cargos (DUEÑO + DIRECTIVO) directamente, sin voto.** Vender es operativa rutinaria, no
gobernanza (a diferencia de sacar/despedir/ascender, que sí pasan por F2). BECARIO/EMPLEADO/ENCARGADO no
venden. La autorización reusa la comprobación de rango ya existente (rango del actor ∈ {DUENO,
DIRECTIVO}).

## Números (constantes en código, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `base` unidades por curro | 5 | nivel 1 → 6/curro; nivel 10 → 15/curro (`base + nivel`) |
| `FACTOR_CAPACIDAD` | 100 | capacidad = nivel × 100 (nivel 1 → 100; nivel 10 → 1.000) |
| `PRECIO_UNIDAD` | 50 coins | almacén lleno nivel 1 = 100 uds = 5.000 brutos |
| `IMPUESTO_VENTA` | 0,15 (15 %) | quemado en cada venta |

## Esquema y componentes

- **Migración V31:** `ALTER TABLE empresas ADD COLUMN mercancia BIGINT NOT NULL DEFAULT 0`. La capacidad
  se **deriva** de `nivel` (sin columna nueva). `Empresa` record gana `long mercancia`.
- **Función pura `Produccion`** (clase final, sin estado): `unidadesPorCurro(int nivel)`,
  `capacidad(int nivel)`, y las constantes `PRECIO_UNIDAD`/`IMPUESTO_VENTA`. Testeable aislada.
- **`EmpresaRepositorio`:**
  - `sumarMercancia(long empresaId, long cantidad)` — suma con clamp al tope en una sola sentencia:
    `UPDATE empresas SET mercancia = LEAST(mercancia + ?, nivel * 100) WHERE id = ?`. El rebose se
    descarta atómicamente en SQL (no hay que leer-modificar-escribir).
  - `gastarMercancia(long empresaId, long cantidad) -> boolean` — descuenta condicional:
    `UPDATE empresas SET mercancia = mercancia - ? WHERE id = ? AND mercancia >= ?`. Devuelve si afectó
    filas (para no vender más de lo que hay).
  - `mercancia` incluido en `SELECT_EMPRESA`/`mapearEmpresa` y en el `SELECT` de `deMiembro`.
- **`EmpresaVentaService`** (o método en `EmpresaService`) — `vender(actorId, cantidadOpt)`: valida que
  el actor pertenece a una empresa y es alto cargo → calcula bruto/impuesto/neto sobre
  `min(cantidad, mercancia)` → `gastarMercancia` (gate) → `incrementarBote(neto)` → el impuesto se quema
  (no se registra ingreso a nadie). Devuelve un resultado con estado (OK/NO_AUTORIZADO/SIN_MERCANCIA/…) y
  las cifras para el embed. **El descuento de mercancía es el gate atómico** (igual patrón que
  fundar/ascender): nunca se abona al bote una venta que no pudo descontar mercancía.
- **`TrabajoService.currar`:** tras aplicar el pago y el corte al bote (F3), si el miembro pertenece a una
  empresa, `sumarMercancia(empresaId, unidadesPorCurro(nivel))`. Ya recibe `EmpresaRepositorio` (F3). El
  rebose lo maneja el `LEAST` del repo; `currar` no necesita saber la capacidad.
- **`/empresa vender [cantidad]`** en `EmpresaComando` — nuevo subcomando, opción entera opcional
  `cantidad` (sin ella, vende todo). Público. Embed con lo vendido, impuesto quemado y neto al bote.
- **`/empresa info`** — añade la línea `almacén X/Y` (mercancía actual / capacidad por nivel).

## i18n (ES + EN, obligatorio en ambos)

- `comando.empresa.vender.desc` + opción `cantidad`.
- `empresa.venta.ok` (con bruto/impuesto/neto/restante), `empresa.venta.sin_mercancia`,
  `empresa.venta.no_autorizado`, `empresa.venta.sin_empresa`.
- `empresa.info.almacen` (línea `X/Y`) para el embed de `/empresa info`.

## Tests

- **`Produccion`** (pura): `unidadesPorCurro`, `capacidad`, cálculo de impuesto/neto (orden y redondeo).
- **`EmpresaRepositorio`** (Testcontainers): `sumarMercancia` respeta el tope (`LEAST`); `gastarMercancia`
  condicional (no baja de 0, devuelve false si no hay bastante).
- **`EmpresaVentaService`** (Mockito): OK quema el impuesto y abona el neto al bote; `SIN_MERCANCIA` no
  toca el bote; `NO_AUTORIZADO` para no-altos-cargos; venta parcial vs total (sin `cantidad`).
- **`TrabajoService.currar`**: un miembro de empresa suma mercancía; uno sin empresa no; el rebose no
  rompe (se apoya en el clamp del repo, se puede testear el repo por separado).
- Baseline actual: 530 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código que la afecta)

- **ADR-020** — producción y ventas de empresas (comprobar que el último ADR es 019).
- `docs/architecture.md`: viñeta F5a en el bloque de empresas; rango de migraciones a V31.
- `CHANGELOG.md`: entrada de F5a bajo `## [Sin publicar]` / `### Añadido`.
- `README.md` / `README.en.md`: `/empresa` suma `vender`.

## Despliegue

Al cerrar F5a: **reiniciar bot** (aplica V31 + subcomando `vender` + la producción en `currar`). **No**
requiere `/setup`. La producción cambia el efecto de `/trabajo currar` para los miembros de una empresa,
así que conviene reiniciar aunque F1–F4 ya estén desplegadas. Smoke test: currar como miembro y ver
subir el almacén en `/empresa info`; `/empresa vender` (parcial y total) viendo bote↑ e impuesto quemado;
rebose al llenar el almacén.

## Fuera de alcance (F5a)

- Impuestos al Estado / quiebra, insumos y materiales, precio de mercado fluctuante por rama, venta a
  otros jugadores o empresas (B2B). Son subsistemas posteriores de F5.

## Orden de implementación (subagent-driven con review de la lógica de dinero)

- **T1**: V31 + `Empresa.mercancia` + repo (`sumarMercancia` con clamp, `gastarMercancia` condicional) +
  `Produccion` (pura) + tests.
- **T2**: producción en `TrabajoService.currar` + tests.
- **T3**: `EmpresaVentaService.vender` + `/empresa vender` + `/empresa info` con almacén + i18n. **Lleva
  review** (dinero: quema del impuesto, abono al bote, gate atómico).
- **T4**: docs (ADR-020, architecture, CHANGELOG, READMEs) + `clean verify` final.
