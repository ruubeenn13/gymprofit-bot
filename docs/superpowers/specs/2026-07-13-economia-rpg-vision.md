# GymProFit RPG — Economía de vida (ficción) · Spec estrella polar

**Fecha:** 2026-07-13
**Estado:** visión aprobada; se construye por fases (cada una jugable y desplegable).
**Ámbito:** subsistema de gamificación/economía. Fase 2 del proyecto (Economía) y más allá.

> **Todo es ficción.** Las monedas (**coins**) son virtuales, **no comprables con dinero real** ni
> convertibles. El gambling es una mecánica de juego sobre moneda ficticia; nunca dinero real.

## Visión

Un **simulador de vida real completo** dentro del Discord (la comunidad GymProFit es solo el lugar
donde vive; el juego **no se limita al gym**): cada miembro tiene un **personaje** con atributos y
energía, **elige una carrera de cualquier sector** (tecnología, hostelería, sanidad, arte,
transporte, deporte…) con ascensos y requisitos, **trabaja** para ganar coins, **compra ítems** (con
efecto) en tiendas ramificadas por su trabajo/sector, **mejora su personaje**, adquiere **bienes**
(casa, coche, negocios), comercia con otros (mercado, trueques, banco) y participa en estructuras
sociales (gremios/empresas, alianzas) y minijuegos de riesgo (gambling de ficción). Se puede
**hacer de todo**, como en la vida.

## Principio rector: economía equilibrada

El mayor riesgo es la **inflación** (todos ricos → nada vale). Todo el diseño equilibra:
- **Fuentes** (entra dinero): `/daily`, `/work`, `/trivia`, ventas en el mercado, premios.
- **Sumideros** (sale dinero): tienda, mejoras, mantenimiento/impuestos, comisiones del mercado,
  pérdidas de gambling, cuotas de gremio.

Reglas invariables:
- **Nunca saldo negativo.** Toda operación de dinero es atómica y validada.
- **Ledger de transacciones** (`transacciones`): toda variación de coins se registra (anti-trampa,
  auditoría, y para depurar balance).
- **Cooldowns** en toda fuente de ingresos (anti-farmeo).
- **Energía** como freno natural: trabajar/entrenar la consume; se regenera con el tiempo/consumibles.

## Modelo de datos (se crea por fases con migraciones Flyway V6+)

- **`personajes`** (1:1 con `usuarios_discord`): `discord_id`, `fuerza`, `resistencia`, `carisma`,
  `energia` (0–100), `salud` (0–100), `trabajo_id` (nullable), `nivel_trabajo`, `banco_saldo`,
  `ultima_energia_ts`. El **monedero (coins)** sigue en `usuarios_discord`.
- **`transacciones`**: `id`, `discord_id`, `delta`, `motivo`, `creado_en`. Ledger de coins.
- **Catálogo de trabajos**: en código (enum/datos), con tier, requisitos (nivel, skill, estudios),
  salario (rango), coste de energía y **rama de tienda** asociada.
- **`items`** (catálogo, en código o tabla): tipo, rama/trabajo, precio, efecto (stat/ingreso),
  equipable.
- **`inventario`**: `discord_id`, `item_id`, `cantidad`. **`equipo`**: ítems equipados.
- **`skills`/`estudios`**: progreso por skill que desbloquea trabajos.
- **`mercado`**: listados de venta jugador→jugador (con comisión = sumidero).
- **`banco`**: saldo depositado (interés), préstamos.
- **`gremios`** + **`gremio_miembros`** + **`alianzas`**.
- **`insignias`** + **`personaje_insignias`**.

## Hoja de ruta (orden = dependencias; cada fase cierra jugable)

**F-ECO-0 · Cimientos** *(primero sí o sí)*
Tabla `personajes` + `transacciones`; monedero seguro (operaciones atómicas, sin negativos, ledger);
`/balance`, `/perfil`, `/daily` (+ racha). Base sobre la que todo se apoya.

**F-ECO-1 · Trabajo y energía**
Catálogo de trabajos (carreras de **cualquier sector**: tecnología, hostelería, sanidad, arte,
transporte, deporte, oficios…); `/trabajos` (ver), `/elegir-trabajo`, `/work`
(gana coins, gasta energía, eventos aleatorios), regeneración de energía (job), `/entrenar`
(gasta energía → sube stats). Aquí también `/trivia` y `/frase` (fuentes de coins/diversión).

**F-ECO-2 · Tienda, inventario e ítems con efecto**
`items` + `inventario` + `equipo`; `/tienda` (ramificada por trabajo), `/comprar`, `/inventario`,
`/equipar`. Los ítems suben stats o ingresos.

**F-ECO-3 · Progresión**
Ascensos de carrera (requisitos), `/estudiar` (skills), rangos automáticos por nivel (roles
Novato→Leyenda), insignias por hitos, `/rank` (tarjeta de perfil).

**F-ECO-4 · Economía entre jugadores**
`/regalar`, `/trueque` (con confirmación de ambos), `/mercado` (listar/comprar, con comisión),
`/banco` (depósito, interés, préstamos).

**F-ECO-5 · Social**
`gremios` (crear/unirse, banco del gremio, perks, cuotas), `alianzas` entre gremios, rankings de
gremios.

**F-ECO-6 · Riesgo (gambling de ficción)**
`/coinflip`, `/dado`, `/ruleta`, `/duelo @usuario` (minijuego por coins). Límites de apuesta y
cooldown para no romper la economía.

## Convenciones

- Cada fase: migración(es) Flyway, entidad→repo→service→comando, i18n ES+EN, tests de la lógica
  pura (balance, cooldowns, requisitos, resolución de duelos), `./mvnw verify` verde con evidencia.
- Servicios de dinero **transaccionales** (una conexión, `UPDATE ... WHERE saldo >= importe`) para
  garantizar atomicidad sin negativos.
- Cada nuevo comando declara su `Comando.Categoria` (para el directorio autogenerado).

## Fuera de alcance (por ahora)

- Cualquier conversión coins↔dinero real (prohibido).
- Integración con la API de la app (esto es economía interna del bot).
- Balance perfecto desde el día 1: se ajustará con datos reales de uso.
