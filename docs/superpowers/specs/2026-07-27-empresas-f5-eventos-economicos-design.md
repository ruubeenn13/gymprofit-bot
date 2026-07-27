# Empresas Fase 5 — Eventos económicos (diseño)

**Fecha:** 2026-07-27
**Fase:** subsistema de F5 del roadmap de empresas (sobre F1–F4 + F5a producción + F5b impuestos +
F5c empleo + F5d préstamos).
**Precondición:** F5d desplegada y funcionando.

## Objetivo

Un **clima económico global**: un evento a la vez, de duración fija, que modifica varias palancas de la
economía a la vez (ventas y producción de empresas, impuesto semanal, faucet del curro, bolsa) y se
anuncia en `💰・economía`. Da vida y variabilidad a la economía sin romper el principio antiinflación:
el catálogo está balanceado (50/50 buenos/malos, valor esperado ≈ 0; lo que crea coins se compensa con
sumideros).

Reusa el motor de eventos que la bolsa ya tiene por dentro (`BolsaService.tick`, crash/boom silencioso):
lo hace explícito, global y anunciado.

## Flujo

1. **Inicio de un evento.** Dos vías:
   - **Automática** (`EventoEconomicoJob`, ~1h): si **no hay evento activo** y el dado `< PROB_EVENTO`,
     el service elige uno **aleatorio** del catálogo y lo fija (`inicio = ahora`, `fin = ahora +
     DURACION`). Se anuncia el inicio en `💰・economía`.
   - **Manual** (`/economia lanzar [evento]`, staff con «Gestionar servidor»): fuerza uno (elegido por
     autocompletado, o aleatorio si se omite). Sobrescribe el activo si lo hubiera. Se anuncia igual.

2. **Mientras dura.** Cada consumidor de la economía pregunta al service por **su** multiplicador
   (default `1.0` / neutro si no hay evento o el evento no toca esa palanca) y lo aplica en un único
   punto. No hay estado duplicado: la única fuente de verdad es la fila del evento activo.

3. **Caducidad.** El `EventoEconomicoJob` detecta que `ahora >= fin`, limpia la fila (vuelve al estado
   neutro) y anuncia el fin en `💰・economía`. Entre eventos hay estado neutro (no siempre hay clima).

4. **Consulta** (`/economia ver`, público): muestra el evento activo (nombre, efecto, tiempo restante) o
   "sin eventos" si no hay ninguno.

## Antiinflación

Regla de diseño del proyecto. El catálogo se mantiene **simétrico**: por cada evento que crea coins
(boom de ventas, ayudas públicas que bajan el impuesto) hay uno que los destruye o reduce el faucet
(recesión, subida de impuestos). Probabilidad buenos/malos 50/50 y uno activo a la vez → el valor
esperado del clima sobre el tiempo es ≈ 0 (ligero sesgo a sumidero es aceptable, nunca a fuente). Los
eventos son **temporales y acotados**: nunca una fuente permanente de dinero.

## Arquitectura y componentes

Unidades aisladas, cada una con un propósito y probables por separado:

- **`EventoEconomico`** (catálogo puro, `enum`, en `services/`): cada entrada declara `id` estable,
  emoji, tono (bueno/malo) y **multiplicadores por palanca**: `ventaMult`, `produccionMult`,
  `impuestoMult`, `curroMult` (todos `double`, neutro `1.0`) y `bolsaSesgo` (enum `NINGUNO`/`ALCISTA`/
  `BAJISTA`). Método estático `aleatorio(double azar)`. Sin dependencias; testeable sola.

- **`EventoActivo`** (record en `db/`): `EventoEconomico tipo`, `Instant inicio`, `Instant fin`. Método
  `caducado(Instant ahora)`.

- **`EventoEconomicoRepositorio`** (en `db/`): tabla de **fila única**. `activo(): Optional<EventoActivo>`,
  `fijar(EventoEconomico, Instant inicio, Instant fin)` (upsert de la fila única), `limpiar()`.

- **`EventoEconomicoService`** (en `services/`, azar inyectable como bolsa):
  - `activo(): Optional<EventoActivo>` (lee del repo).
  - Getters de multiplicador con **default neutro** cuando no hay activo o el activo no toca la palanca:
    `ventaMult()`, `produccionMult()`, `impuestoMult()`, `curroMult()`, `bolsaSesgo()`.
  - `lanzar(EventoEconomico, Instant ahora)` y `lanzarAleatorio(Instant ahora)` → fija la fila.
  - `tick(Instant ahora): ResultadoTick` (lo llama el job): si hay activo caducado → `limpiar` y devuelve
    `FINALIZADO(evento)`; si no hay activo y `azar < PROB_EVENTO` → lanza aleatorio y devuelve
    `INICIADO(evento)`; en otro caso `NADA`. El job usa el resultado para anunciar.

- **`EventoEconomicoJob`** (en `jobs/`): reprograma cada `PERIODO` (~1h) con el scheduler existente; en
  cada pasada llama a `service.tick(ahora)` y, según el resultado, publica el embed de inicio/fin en
  `💰・economía` (best-effort, resuelto por nombre de canal por guild; si no está, log y seguir —patrón
  de avisos best-effort de F5b/bolsa). Wiring en `main()` bajo `jda != null`.

- **`EconomiaEventoComando`** (`/economia`, en `commands/economia/`, `implements ComandoAutocompletable`):
  - `ver` (público): embed con el evento activo y el tiempo restante, o "sin eventos".
  - `lanzar [evento]` (staff, `DefaultMemberPermissions` «Gestionar servidor»): fuerza uno; opción
    `evento` con autocompletado del catálogo (vacío = aleatorio). Confirma en la respuesta.

### Hooks en los consumidores (un único lookup cada uno, línea única)

Cada servicio recibe el `EventoEconomicoService` por constructor y aplica su multiplicador en un solo
punto. Redondeo `Math.round`/`floor` coherente con el cálculo existente.

- **Venta** — `EmpresaVentaService`: `bruto = Math.round(aVender * PRECIO_UNIDAD * ventaMult())` (antes
  del impuesto de venta, que se sigue calculando sobre el bruto ya modificado).
- **Producción** — `TrabajoService`: la mercancía sumada a la empresa `*= produccionMult()`
  (`Math.round`), en el mismo bloque best-effort de F3/F5a.
- **Impuesto** — `ImpuestoEmpresasService.evaluar`: `cuota = Math.round(Impuesto.cuota(nivel) *
  impuestoMult()) + cuotaPrestamo`. El multiplicador aplica **solo al impuesto**, no a la cuota del
  préstamo (la deuda es un contrato fijo). Sigue siendo un solo gate.
- **Jugador** — coins de `/trabajo currar`: el ingreso al jugador `*= curroMult()` antes del corte del
  10% al bote (el corte se calcula sobre el ingreso ya modificado, para no descuadrar la relación
  jugador↔empresa).
- **Bolsa** — `BolsaService.tick()`: si `bolsaSesgo()` no es `NINGUNO`, en vez del 50/50 boom/crash el
  evento sesga la probabilidad (p. ej. alcista → 80% boom). El crash/boom deja de ser silencioso solo
  en cuanto hay evento de bolsa anunciado por el job de eventos (la bolsa en sí sigue sin anunciar por
  acción).

## Esquema (migración V35)

```sql
CREATE TABLE evento_economico (
    id          TINYINT      NOT NULL DEFAULT 1,  -- fila única (siempre 1)
    tipo        VARCHAR(32)  NOT NULL,            -- EventoEconomico.name()
    inicio      TIMESTAMP    NOT NULL,
    fin         TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
```

Sin evento activo = **tabla vacía** (o fila borrada). `fijar` hace upsert de la fila `id=1`; `limpiar`
la borra. Sin FKs (no referencia a usuarios/empresas: es estado global del servidor).

## Números (constantes, tunables)

| Constante | Valor | Efecto |
|---|---|---|
| `DURACION` | 24 h | cuánto dura un evento activo |
| `PROB_EVENTO` | 0,04 | probabilidad por pasada del job de iniciar uno (job cada 1 h → ~1/día) |
| `PERIODO` job | 1 h | cadencia del `EventoEconomicoJob` |
| venta boom/recesión | ±30 % | `ventaMult` 1,30 / 0,70 |
| producción auge/crisis | ±50 % | `produccionMult` 1,50 / 0,50 |
| impuesto subida/ayudas | ×1,5 / ×0,5 | `impuestoMult` |
| bolsa alcista/bajista | 80 % boom / 80 % crash | `bolsaSesgo` sesga el 50/50 de `BolsaService.tick` |

### Catálogo inicial

| Evento | Tono | Palanca |
|---|---|---|
| `BOOM_CONSUMO` 📈 | bueno | venta ×1,30 |
| `RECESION` 📉 | malo | venta ×0,70 |
| `AUGE_INDUSTRIAL` 🏭 | bueno | producción ×1,50 |
| `CRISIS_SUMINISTRO` 🦠 | malo | producción ×0,50 |
| `SUBIDA_IMPUESTOS` 🏛️ | malo | impuesto ×1,50 |
| `AYUDAS_PUBLICAS` 🎁 | bueno | impuesto ×0,50 |
| `MERCADO_ALCISTA` 🐂 | bueno | bolsa sesgo alcista |
| `MERCADO_BAJISTA` 🐻 | malo | bolsa sesgo bajista |

4 buenos / 4 malos. `curroMult` queda reservado en el catálogo (una entrada futura tipo "bonanza/paro"
puede usarlo); el hook del curro se implementa desde el principio para no re-tocar `TrabajoService`
después.

## Tests

- **`EventoEconomico`** (pura): multiplicadores por entrada; `aleatorio` reparte sobre el catálogo;
  neutralidad (entradas que no tocan una palanca devuelven 1.0 / `NINGUNO`).
- **`EventoEconomicoService`** (Mockito, azar inyectable): `tick` inicia con `azar < PROB` y no con
  `azar >=`; `tick` finaliza un evento caducado y no uno vigente; getters devuelven el multiplicador del
  activo y neutro sin activo; `lanzar`/`lanzarAleatorio` fijan la fila.
- **Hooks** (Mockito, uno por servicio): con un `EventoEconomicoService` mockeado devolviendo un
  multiplicador ≠ 1, el resultado del cálculo cambia en la proporción esperada; con neutro, igual que
  hoy (test de no-regresión).
- **`EventoEconomicoRepositorio`** (Testcontainers): `fijar`→`activo` persiste; `limpiar` vacía;
  upsert no duplica.
- Baseline actual: 589 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-024** — eventos económicos (comprobar que el último ADR es 023).
- `docs/architecture.md`: viñeta F5 eventos; migraciones a V35; nota de los 5 hooks y del
  `EventoEconomicoJob`.
- `CHANGELOG.md`: entrada F5 eventos económicos.
- `README.md` / `README.en.md`: nuevo comando `/economia` (ver · lanzar).

## Despliegue

Al cerrar: **reiniciar bot** (V35 + comando `/economia` + `EventoEconomicoJob` + los 5 hooks). Añade un
slash command nuevo → reiniciar para re-registrarlo; **no** requiere `/setup` (el canal `💰・economía` ya
lo crea setup). Smoke: `/economia lanzar` (staff) de cada tipo → ver el anuncio en `💰・economía` y
`/economia ver` con el tiempo restante; comprobar que una venta rinde ±30% con boom/recesión activo, que
el curro y la producción cambian, que el cobro semanal aplica el ×impuesto (invocar `cobrar()` a mano
como en F5b) y que el sesgo de bolsa se nota en varios ticks; dejar caducar (o bajar `DURACION`) y ver el
anuncio de fin + vuelta a la normalidad.

## Fuera de alcance (esta fase)

- Varios eventos simultáneos; eventos **por rama** (todos son globales); eventos que targetean una
  empresa concreta; encadenados/narrativos; efectos sobre precios de tienda/mercado/casino; historial
  persistente de eventos pasados; predicción o "pronóstico" del próximo.

## Orden de implementación (subagent-driven; la lógica de dinero lleva review)

- **T1**: migración **V35** + `EventoEconomico` (catálogo puro) + `EventoActivo` + repo + tests puros.
- **T2**: `EventoEconomicoService` (tick/lanzar/getters, azar inyectable) + tests. **Review** (lógica de
  activación/caducidad).
- **T3**: `EventoEconomicoJob` + `/economia ver`/`lanzar` (autocompletado, permisos) + anuncios en
  `💰・economía` + i18n ES+EN. Wiring en `main()`.
- **T4**: los 5 hooks (venta, producción, impuesto, curro, bolsa), cada uno con su test de proporción y
  de no-regresión. **Review** (re-toca la lógica de dinero de F5a/F5b + curro + bolsa).
- **T5**: docs (ADR-024, architecture, CHANGELOG, READMEs) + `clean verify` final.
