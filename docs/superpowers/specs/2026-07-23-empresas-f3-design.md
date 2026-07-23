# Empresas · Fase 3 (Economía) — diseño

**Fecha:** 2026-07-23
**Estado:** aprobado (brainstorming con el usuario)
**Depende de:** F1 (fundar/pertenecer) y F2 (jerarquía/gobernanza), ambas en `main`. **Roadmap:** [[gymprobot-backlog]].

## Contexto

F1 y F2 dejaron empresas con miembros, rangos y gobernanza, pero el **bote** y el **nivel** (columnas
ya creadas en `empresas` desde V27) están inertes. La F3 los enciende: un corte del curro alimenta el
bote, el nivel da bonus de ingresos a todos, una nómina reparte el bote por rango, y el bote patrocina
los ascensos de tier de los miembros. La economía del bote/nivel **no** necesita esquema (las columnas
ya existen); solo hace falta una **migración mínima V29** para que las propuestas de ascenso
patrocinado puedan guardar el puesto destino (ver §Modelo de datos).

## Alcance de la F3

**Entra:** corte del curro al bote + bonus de nivel al ingreso (en `/trabajo currar`), `/empresa
mejorar` (subir nivel gastando del bote), job de **nómina** (reparto diario por rango), y `/empresa
ascender` (disparo de ascenso de tier de un miembro **pagado por el bote**, vía la gobernanza de F2).

**NO entra:** producción/ventas, impuestos, acciones, reputación, bolsa de empleo, préstamos, eventos
(F5+); ni traspaso de propiedad.

## Parámetros de balance (constantes, tuneables)

- `CORTE_EMPRESA = 0.10` — 10 % del salario bruto de cada curro va al bote.
- `BONUS_POR_NIVEL = 0.02` — +2 % de ingreso al currar por nivel de empresa.
- `NIVEL_MAX = 10` — tope del bonus (+20 %).
- `COSTE_NIVEL(n) = 50_000 * n` — coste de subir del nivel `n` al `n+1` (se gasta del bote, es sumidero).
- `FRACCION_NOMINA = 0.20` — cada nómina reparte el 20 % del bote entre los miembros; el resto queda.
- Peso de reparto por rango = `rango.ordinal() + 1` (BECARIO 1 … DUENO 5).
- Nómina: job diario a las **03:00 Europe/Madrid**.

## Modelo de datos — Migración `V29__empresa_propuesta_dato.sql`

Solo un `ALTER TABLE`: añadir a `empresa_propuestas` una columna genérica
`dato VARCHAR(32) NULL COMMENT 'Carga extra de la propuesta: puesto destino en ASCENSO, null en el resto'`.
La usa el `TipoPropuesta.ASCENSO` para guardar el id del puesto destino (los ids de puesto pueden pasar
de los 16 chars de `rango_nuevo`, por eso una columna propia y no reutilizar aquella). El record
`Propuesta` gana el campo `String dato`, y `EmpresaPropuestaRepositorio.crear` un parámetro `dato`
(null en CAMBIAR_RANGO/SACAR/DESPEDIR). Bote y nivel **no** tocan esquema.

## Corte y bonus en `/trabajo currar`

`TrabajoService.currar` (o donde se calcule el salario) integra la empresa del jugador:

1. `bruto = salarioBase * (1 + BONUS_POR_NIVEL * nivelEmpresa)` (si el jugador está en empresa; si no,
   `bruto = salarioBase`). `nivelEmpresa` se lee de la empresa del jugador (0 efecto si no tiene).
2. `corte = floor(bruto * CORTE_EMPRESA)` (solo si está en empresa) → se suma al bote de su empresa.
3. El jugador cobra `bruto - corte`.

Integración: `TrabajoService` recibe `EmpresaRepositorio` (o un `EmpresaEconomiaService`) por
constructor para consultar la empresa del jugador (`deMiembro`) y sumar al bote
(`incrementarBote`). Un jugador sin empresa: comportamiento idéntico al actual (sin corte ni bonus).
El movimiento de coins sigue siendo atómico vía `EconomiaRepositorio`.

## Nivel — `/empresa mejorar`

Solo el **Dueño**. Sube el nivel gastando `COSTE_NIVEL(nivelActual)` **del bote** (atómico:
`gastarDelBote(empresaId, coste)` con `UPDATE ... WHERE bote >= coste`, nunca negativo). Si el bote no
llega → aviso. Tope `NIVEL_MAX`. Embed público con el nuevo nivel y el bonus resultante.

## Nómina — job diario

`NominaEmpresasJob` (a las 03:00 Europe/Madrid, patrón de `EjercicioDiaJob`): por cada empresa con
`bote > 0` y ≥1 miembro:

1. `pool = floor(bote * FRACCION_NOMINA)`.
2. Reparte `pool` entre los miembros ponderado por `peso = rango.ordinal()+1`: cada miembro recibe
   `floor(pool * peso / sumaPesos)` (el redondeo a la baja deja calderilla en el bote, aceptable).
3. Descuenta del bote la suma efectivamente repartida (`gastarDelBote`) y abona a cada miembro
   (`economia.ingresar(miembro, parte, "nomina_empresa")`).

El job es **testeable**: el cálculo del reparto (pool → mapa miembro→parte) es una **función pura**
(`RepartoNomina.calcular(bote, miembros)`) probada sin BD; el job solo la orquesta y persiste.

## Ascenso patrocinado — `/empresa ascender <@miembro> <puesto>`

Dispara el ascenso de tier de un miembro pagándolo **del bote** (no del saldo del miembro). Vía la
gobernanza de F2: el **Dueño** lo ejecuta directo; un **Directivo** lo propone (nuevo
`TipoPropuesta.ASCENSO`, con el `puesto` destino guardado en la propuesta) y se vota.

Lógica (reusa la validación de `TrabajoService.ascender` sin duplicarla):
- El miembro debe tener trabajo en la rama de la empresa; `puesto` es un puesto del **siguiente tier**
  de su rama (autocompletado con `TrabajoService.opcionesAscenso(miembroId)`).
- Se validan los requisitos **no monetarios** del miembro (antigüedad `turnos_puesto`, estudios, stat
  de la rama) — se reutiliza la misma comprobación que `ascender`.
- El **coste en coins** del ascenso se paga del **bote** (`gastarDelBote`), no del miembro; si el bote
  no llega → aviso. El coste se **quema** igual (sumidero): sale del bote y no va a nadie.
- Se aplica el ascenso (mismo efecto que `/trabajo ascender`: nuevo puesto + tier de carrera por
  `GREATEST`).

**Refactor necesario:** extraer de `TrabajoService.ascender` la validación de requisitos y la
aplicación del ascenso a métodos reutilizables, de modo que exista una variante «pagada por un tercero»
sin duplicar reglas. Se documenta el punto en el plan.

## Comandos y jobs nuevos

- `/empresa mejorar` (dueño; sube nivel gastando del bote).
- `/empresa ascender <@miembro> <puesto>` (dueño directo / directivo propone; `puesto` con
  autocompletado del siguiente tier del miembro). Reusa el flujo de propuesta/voto de F2 con el nuevo
  `TipoPropuesta.ASCENSO`.
- `/empresa info` (F1) pasa a mostrar **bote** y **nivel** (con el bonus actual).
- `NominaEmpresasJob` (03:00 Europe/Madrid).

## Repositorio

Métodos nuevos en `EmpresaRepositorio`: `void incrementarBote(long empresaId, long cantidad)`,
`boolean gastarDelBote(long empresaId, long cantidad)` (atómico, `WHERE bote >= ?`, devuelve si pudo),
`void subirNivel(long empresaId)` (o `fijarNivel`), y `List<Empresa> conBote()` (para el job de
nómina; empresas con `bote > 0`).

## i18n

Claves nuevas ES+EN: `comando.empresa.mejorar.*`, `comando.empresa.ascender.*` (+ opción `puesto`),
`empresa.mejora.*` (ok/tope/sin_fondos), `empresa.ascenso.*` (ok/sin_fondos/requisitos/…),
`empresa.nomina.*` (si se anuncia), y ampliar `empresa.info.*` con bote/nivel/bonus.

## Manejo de errores

Gasto del bote atómico (nunca negativo). El corte en `currar` no debe romper el curro si la lectura de
empresa falla: se degrada a «sin corte» y se loguea (el curro del jugador es prioritario). Excepciones
custom + patrón existente.

## Testing

- **`RepartoNomina.calcular`** (función pura): reparto por pesos, redondeo a la baja, empresa de 1
  miembro, pesos mezclados, bote pequeño.
- **`EmpresaRepositorio`** (Testcontainers): `incrementarBote`, `gastarDelBote` (no baja de 0 y
  devuelve false si no llega), `subirNivel`, `conBote`.
- **`TrabajoService.currar`** (ampliar test): con empresa aplica corte y bonus (verify suma al bote y
  el jugador cobra bruto−corte); sin empresa, idéntico al actual.
- **Ascenso patrocinado** (service): paga del bote, valida requisitos no monetarios, no cobra si falla.
- El job y los comandos → smoke test manual.

## Despliegue

Migración mínima `V29` (columna `dato`) + comandos nuevos + job nuevo → **reiniciar el bot**. No
requiere `/setup`.

## ADR

**ADR-018 — economía de empresas** (corte del curro al bote, bonus de nivel, nómina por rango,
ascenso patrocinado por el bote; `bote`/`nivel` ya existían, solo una columna `dato` en las propuestas
para el puesto del ascenso).

## Fuera de alcance (YAGNI en F3)

Producción/ventas, impuestos/quiebra, acciones, reputación/competencia, bolsa de empleo, préstamos,
eventos (F5+); traspaso de propiedad.
