# Empresas Fase 5 — Acciones y dividendos (diseño)

**Fecha:** 2026-07-27
**Fase:** subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción + F5b impuestos +
F5c empleo + F5d préstamos + F5 eventos económicos).
**Precondición:** F5 eventos desplegada y funcionando.

## Objetivo

Que **cualquier jugador** (miembro o no) invierta capital en una empresa comprando **participaciones**
de un pool fijo: el dinero engorda el bote y a cambio cobra **dividendos** periódicos del bote según su
parte. Da a las empresas una vía de capital externo y a los jugadores un activo con riesgo. Distinto de
la **nómina** (reparte a los miembros por rango) y de la **bolsa ficticia** (8 acciones fijas con random
walk).

## Modelo

- **Pool fijo de 100 participaciones por empresa.** Tus participaciones = tu **%** directo (sobre 100).
- **Precio por participación = max(1, prestigio / 100)**, con
  `prestigio = nivel×10.000 + miembros×1.000 + bote/1.000` (la fórmula que ya usa `/empresa ranking`,
  clase `Prestigio`). El precio **flota**: si la empresa crece (nivel, miembros, bote), la participación
  vale más; comprar barato y vender tras el crecimiento da plusvalía.
- **Comprar** (`/acciones comprar <empresa> <cantidad>`, cualquiera): valida empresa existe, `cantidad ≥ 1`,
  `cantidad ≤ libres` (`100 − vendidas`) y fondos del jugador. Ejecuta el patrón atómico: gate
  `EconomiaRepositorio.gastar(jugador, coste)` → `EmpresaRepositorio.incrementarBote(empresaId, coste)`
  (el capital entra al bote) → registra participaciones (upsert). `coste = cantidad × precio`.
  Errores: `NO_EXISTE`, `CANTIDAD_INVALIDA`, `SIN_PARTICIPACIONES_LIBRES`, `SIN_SALDO`.
- **Vender** (`/acciones vender <empresa> <cantidad>`, accionista): recompra la empresa a **precio actual**.
  Valida `cantidad ≥ 1`, `cantidad ≤ tus participaciones`, y `bote ≥ cantidad × precio`. Ejecuta:
  gate `EmpresaRepositorio.gastarDelBote(empresaId, valor)` → `EconomiaRepositorio.ingresar(jugador, valor)`
  → devuelve participaciones al pool (baja/borra el holding). La plusvalía/minusvalía sale del/al bote.
  Errores: `NO_EXISTE`, `CANTIDAD_INVALIDA`, `SIN_PARTICIPACIONES`, `EMPRESA_SIN_FONDOS`.
- **Dividendos (job semanal):** por cada empresa con participaciones vendidas y bote > 0:
  `pot = floor(FRACCION_DIVIDENDO × bote)`; cada accionista cobra `floor(pot × sus_participaciones / 100)`.
  El total pagado (`Σ floor`) se descuenta del bote con un único gate `gastarDelBote`; la fracción **no
  vendida** (`novendidas/100`) nunca sale del bote (la empresa la retiene). Sin intervención del dueño.
- **Quiebra:** al `disolver` una empresa (F5b: 3 impagos, o disolución manual) se **borran todas sus
  participaciones**: los inversores **pierden el capital** (comprar es riesgo real; no hay reembolso).

## Antiinflación

Todo es **redistribución** del/al bote: comprar mueve coins jugador→bote, vender y dividendo mueven
bote→jugador. **Cero creación de coins.** `FRACCION_DIVIDENDO` se mantiene modesta (5 %) porque el bote
ya tiene sumideros y repartos (nómina 20 %/día, impuesto semanal, cuota de préstamo): los dividendos son
un reparto más, no una fuente. La quiebra que funde el capital es un sumidero neto ocasional.

## Esquema y componentes

- **Migración V36:** tabla de holdings.
  ```sql
  CREATE TABLE empresa_acciones (
      empresa_id  BIGINT NOT NULL,
      discord_id  BIGINT NOT NULL,
      cantidad    INT    NOT NULL,               -- participaciones (1..100), >0 (fila borrada al llegar a 0)
      PRIMARY KEY (empresa_id, discord_id),
      CONSTRAINT fk_acc_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id) ON DELETE CASCADE,
      CONSTRAINT fk_acc_usuario FOREIGN KEY (discord_id) REFERENCES usuarios_discord(discord_id) ON DELETE CASCADE
  );
  ```
  FK a `empresas` con **ON DELETE CASCADE** → `disolver` (que borra la fila de `empresas`) funde las
  participaciones sin código extra. FK RGPD a `usuarios_discord`. No se añade columna a `empresas`:
  `vendidas` se calcula con `SUM(cantidad)`.

- **`Accion`** (clase final pura, en `services/`): constantes `POOL = 100`, `FRACCION_DIVIDENDO = 0.05`;
  `precioParticipacion(long prestigio)` (= `max(1, prestigio / POOL)`); `dividendoDe(long pot, int
  participaciones)` (= `floor(pot × participaciones / POOL)`). Testeable sin BD.

- **`EmpresaAccionRepositorio`** (JDBC, en `db/`): `participacionesDe(empresaId, discordId)` (int),
  `vendidasDe(empresaId)` (SUM), `accionistas(empresaId)` (lista `Accionista(discordId, cantidad)` para el
  reparto), `carteraDe(discordId)` (lista `PosicionAccion(empresaId, cantidad)`), `fijar(empresaId,
  discordId, cantidad)` (upsert; borra si `cantidad ≤ 0`).

- **`AccionEmpresasService`** (en `services/`): `comprar(actorId, empresaId, cantidad) → ResultadoCompra`,
  `vender(actorId, empresaId, cantidad) → ResultadoVenta`, `cartera(actorId) → CarteraAcciones`
  (valorada a precio actual), y `repartirDividendos(Empresa)` (para el job). Necesita el **nº de miembros**
  para el prestigio: se obtiene con un método de conteo del `EmpresaRepositorio` (usar el existente o
  añadir `contarMiembros(empresaId)`). El precio se calcula con `Prestigio.calcular(nivel, miembros, bote)`
  y `Accion.precioParticipacion`.

- **`DividendoEmpresasJob`** (en `jobs/`, espejo de `ImpuestoEmpresasJob`): **semanal, jueves 02:00
  Europe/Madrid** (separado del lunes del impuesto/nómina para repartir la carga sobre el bote; auto-reprograma
  con `TemporalAdjusters`). Itera `empresas.todas()`, llama a `service.repartirDividendos(e)` por empresa
  (aislada en try/catch), y avisa best-effort en el **canal privado** de la empresa (F4) del reparto (solo
  si hubo pago). `repartir()` público para test/manual.

- **`AccionesComando`** (`/acciones`, familia nueva en `commands/economia/`, `implements
  ComandoAutocompletable` para la opción `empresa`): subcomandos `comprar`, `vender`, `ver` (participaciones
  vendidas + precio + tu parte de una empresa), `cartera` (tus participaciones en todas las empresas +
  valor + P/L estimado). No es subcomando de `/empresa` (ya tiene 14). Autocompletado de empresas por nombre.

- **`/empresa info`:** añade una línea `📈 Acciones: {vendidas}/100 (precio {precio} 🪙)`.

- **i18n ES+EN, ADR-025, docs.**

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `POOL` | 100 | participaciones por empresa (= %) |
| precio | prestigio / 100 (mín 1) | flota con nivel/miembros/bote |
| `FRACCION_DIVIDENDO` | 0,05 | 5 % del bote/semana al pot de dividendos |
| job dividendos | jueves 02:00 Europe/Madrid | cadencia semanal, separado del impuesto (lunes 02:00) |

## Tests

- **`Accion`** (pura): `precioParticipacion` (redondeo, suelo 1), `dividendoDe` (floor, fracción del pool).
- **`AccionEmpresasService`** (Mockito): comprar OK (bote↑, holding fijado, coste correcto), `SIN_PARTICIPACIONES_LIBRES`
  (tope del pool), `SIN_SALDO`, `NO_EXISTE`, `CANTIDAD_INVALIDA`; vender OK (bote↓, holding baja), `SIN_PARTICIPACIONES`,
  `EMPRESA_SIN_FONDOS` (bote no cubre); `repartirDividendos` (reparto proporcional, floor, la parte no vendida
  se queda, gate único; empresa sin accionistas o bote 0 no paga).
- **`EmpresaAccionRepositorio`** (Testcontainers): upsert/fijar, `vendidasDe` SUM, borrado a 0, cascade al
  borrar la empresa.
- Baseline actual: 608 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-025** — acciones y dividendos (comprobar que el último ADR es 024).
- `docs/architecture.md`: viñeta F5 acciones; migraciones a V36; nota del `DividendoEmpresasJob`.
- `CHANGELOG.md`: entrada F5 acciones/dividendos.
- `README.md` / `README.en.md`: nuevo comando `/acciones`.

## Despliegue

Al cerrar: **reiniciar bot** (V36 + `/acciones` + `DividendoEmpresasJob` + línea en `/empresa info`). Añade
un slash command → reiniciar para re-registrarlo; **no** requiere `/setup`. Smoke: `/acciones comprar` en una
empresa (ver bote↑, participaciones en `/acciones ver` y `/empresa info`); `/acciones cartera`; `/acciones
vender` (bote↓, participaciones al pool); reparto de dividendos (invocar `repartir()` a mano como en F5b) →
los accionistas cobran su parte, la no vendida se queda; comprar todo el pool y ver `SIN_PARTICIPACIONES_LIBRES`;
disolver una empresa con accionistas y ver que pierden el capital.

## Fuera de alcance (esta fase)

- Mercado secundario entre jugadores (solo se compra/vende contra el pool de la empresa).
- Precio por oferta/demanda (el precio es puramente fundamental, prestigio/100).
- Dividendo declarado por el dueño (es automático por job).
- Derechos de voto por participaciones, OPA/absorciones, dilución/ampliaciones de capital, recompra parcial
  forzada, splits.

## Orden de implementación (subagent-driven; la lógica de dinero lleva review)

- **T1**: migración **V36** + `Accion` (pura) + `EmpresaAccionRepositorio` + tests (puros + Testcontainers).
- **T2**: `AccionEmpresasService` (`comprar`/`vender`/`cartera`, patrón atómico) + `/acciones comprar · vender ·
  ver · cartera` + línea en `/empresa info` + i18n ES+EN. **Review** (dinero: capital al bote, gate de venta,
  tope del pool, precio).
- **T3**: `AccionEmpresasService.repartirDividendos` + `DividendoEmpresasJob` (semanal) + avisos + tests.
  Wiring en `main()`. **Review** (reparto proporcional, gate único, la parte no vendida se queda).
- **T4**: docs (ADR-025, architecture, CHANGELOG, READMEs) + `clean verify` final.
