# Empresas Fase 5 — Reputación y cuota de mercado (diseño)

**Fecha:** 2026-07-27
**Fase:** último subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción + F5b impuestos +
F5c empleo + F5d préstamos + F5 eventos + F5 acciones).
**Precondición:** F5 acciones desplegada y funcionando.

## Objetivo

Introducir **competencia entre las empresas de una misma rama**: cada empresa tiene una **cuota de
mercado** (su parte del prestigio de la rama) que **escala sus ingresos por venta**. Por encima de la
cuota "justa" (1/N) vende con **prima**; por debajo, con **penalización**; neutral en la media. Crecer come
cuota a las rivales. Es el último subsistema de empresas y aterriza el **ranking por rama** que quedó
aparcado en F4.

## Modelo

- **Cuota = prestigio propio / Σ prestigio de las empresas de tu rama.** El prestigio es el que ya existe
  (`Prestigio.calcular(nivel, miembros, bote) = nivel×10.000 + miembros×1.000 + bote/1.000`).
- **Cuota relativa = cuota × N** (N = nº de empresas de la rama). **1.0 = cuota justa** (tienes tu parte
  media). >1 líder de rama; <1 rezagado.
- **Factor de venta = clamp(1 + SENSIBILIDAD × (relativa − 1), MIN, MAX)** con **SENSIBILIDAD = 0.25**,
  **MIN = 0.75, MAX = 1.25**. Casos: monopolio (N=1) → relativa 1 → factor **1.0** (sin competencia, no
  premia ni penaliza); dominar la rama → hasta **+25 %**; ser marginal → hasta **−25 %**.
- **Efecto:** en `/empresa vender`, el bruto se multiplica por el factor de cuota, **además** del
  multiplicador del clima económico (F5 eventos): `bruto = round(unidades × PRECIO_UNIDAD × ventaMult ×
  factorCuota)`. No toca impuesto de venta ni producción; solo el bruto.

## Antiinflación

Los factores están **centrados en 1.0** en la cuota justa y **acotados a ±25 %**: la prima de los líderes
sale, en agregado, de la penalización de los rezagados (redistribución aproximada, no fuente permanente).
Acotado y sin creación estructural de coins.

## Arquitectura y componentes

- **`Cuota`** (clase final pura, en `services/`): constantes `SENSIBILIDAD = 0.25`, `MIN = 0.75`,
  `MAX = 1.25`; `relativa(long prestigioPropio, long prestigioTotal, int n)` (= `prestigioPropio × n /
  prestigioTotal` en coma flotante; si `prestigioTotal ≤ 0` o `n ≤ 1` devuelve 1.0 → factor neutro);
  `factorVenta(double relativa)` (= `clamp(1 + SENSIBILIDAD × (relativa − 1), MIN, MAX)`). Testeable.

- **`EmpresaRepositorio.rankingDeRama(String rama)`**: lista `EmpresaRanking(nombre, rama, nivel, miembros,
  bote)` de las empresas de una rama (mismo LEFT JOIN + COUNT que `ranking()`, con `WHERE rama = ?` y
  ordenado por prestigio desc). Reusa el record `EmpresaRanking` existente.

- **`CuotaEmpresasService`** (en `services/`): `factorVentaDe(Empresa e)` — lee `rankingDeRama(e.rama())`,
  calcula el prestigio de cada una y el propio con `Prestigio.calcular`, y devuelve
  `Cuota.factorVenta(Cuota.relativa(prestigioPropio, prestigioTotal, n))`. También `cuotaDe(Empresa e)` →
  `double` (para el display: el % de cuota). Sin estado (todo derivado); no persiste nada.

- **Hook en `EmpresaVentaService`**: el constructor recibe además `CuotaEmpresasService`; en `vender`,
  `bruto = Math.round(aVender × PRECIO_UNIDAD × eventos.ventaMult() × cuota.factorVentaDe(emp))`.

- **Display:**
  - `/empresa ranking` gana una **opción opcional `rama`** (choices = las 7 ramas): sin ella, el top global
    de siempre (F4); con ella, el **ranking de esa rama** con el **% de cuota** de cada empresa. La lógica
    de cuota/orden vive en `EmpresaService` (nuevo `rankingDeRama(rama)` que envuelve el del repo y añade el
    % de cuota a cada fila).
  - Línea `🏪 Cuota de rama: {0} % (venta ×{1})` en `/empresa info`.

- **Sin migración** (la cuota es derivada del prestigio, no se persiste). **Sin job.**
- **i18n ES+EN, ADR-026, docs.**

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `SENSIBILIDAD` | 0,25 | pendiente del factor respecto a la cuota relativa |
| `MIN` / `MAX` | 0,75 / 1,25 | tope de penalización/prima de venta (±25 %) |
| cuota justa | 1/N | punto neutro (factor 1.0) |

## Tests

- **`Cuota`** (pura): `relativa` (cuota justa → 1.0; líder → >1; monopolio N=1 → 1.0; `prestigioTotal=0` →
  1.0 defensivo); `factorVenta` (neutro en relativa 1; clamp a MIN/MAX en extremos; prima/penalización
  intermedias).
- **`CuotaEmpresasService`** (Mockito): `factorVentaDe` con una rama de varias empresas (líder tiene
  factor >1, rezagado <1); rama de una sola empresa → 1.0; suma de prestigios correcta.
- **`EmpresaVentaService`** (actualizado): con `factorVentaDe` mockeado ≠ 1, el bruto escala en esa
  proporción (compuesto con el `ventaMult` del clima); con factor 1.0, igual que hoy (no-regresión); los
  tests existentes pasan a stubear `cuota.factorVentaDe(...)` = 1.0.
- **`EmpresaRepositorio.rankingDeRama`** (Testcontainers): filtra por rama y ordena por prestigio.
- Baseline actual: 630 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-026** — reputación y cuota de mercado (comprobar que el último ADR es 025).
- `docs/architecture.md`: viñeta F5 cuota de mercado; nota del hook de venta y de la vista por rama del
  ranking (sin migración nueva: V6–V36 se mantiene).
- `CHANGELOG.md`: entrada F5 reputación/cuota.
- `README.md` / `README.en.md`: `/empresa ranking` admite filtro por rama con cuota (no hay comando nuevo).

## Despliegue

Al cerrar: **reiniciar bot** (el hook de venta y la opción `rama` del ranking; **no** hay migración ni job).
Cambia la definición de `/empresa` (opción nueva en `ranking`) → reiniciar para re-registrar el comando;
**no** requiere `/setup`. Smoke: en una rama con varias empresas, la líder vende con prima y la pequeña con
penalización (comparar el bruto de `/empresa vender` de dos empresas de la misma rama); `/empresa ranking
rama:<X>` muestra el ranking de esa rama con el % de cuota; `🏪 Cuota de rama` aparece en `/empresa info`;
una empresa sola en su rama vende neutral (factor 1.0).

## Fuera de alcance (esta fase)

- Reputación como **stat persistente** con acumulación/decadencia (la "reputación" es aquí la cuota
  derivada del prestigio).
- Cuota por **ventas recientes** (ventana rodante) en vez de por prestigio.
- Efectos de la cuota en el **impuesto**, la **contratación**, la confianza de **inversores** o el precio
  de las **acciones**.
- Guerra de precios activa entre dueños, alianzas/carteles, absorciones.

## Orden de implementación (subagent-driven; la lógica de dinero lleva review)

- **T1**: `Cuota` (pura) + `EmpresaRepositorio.rankingDeRama` + `CuotaEmpresasService` + tests (puros +
  Testcontainers del repo).
- **T2**: hook en `EmpresaVentaService` (bruto × factor de cuota) + vista por rama de `/empresa ranking`
  (opción `rama` + % de cuota) + línea `🏪 Cuota` en `/empresa info` + i18n + wiring Main. **Review**
  (re-toca la lógica de dinero de la venta).
- **T3**: docs (ADR-026, architecture, CHANGELOG, READMEs) + `clean verify` final.
