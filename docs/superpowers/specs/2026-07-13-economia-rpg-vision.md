# GymProFit RPG — Economía de vida (ficción) · Spec estrella polar

**Fecha:** 2026-07-13
**Estado:** visión aprobada; se construye por fases (cada una jugable y desplegable).
**Ámbito:** subsistema de gamificación/economía. Fase 2 del proyecto (Economía) y más allá.

> **Estado de implementación (2026-07-14).** Hechas y en `main`: **F-ECO-0..6** (cimientos, trabajo/
> energía, tienda/inventario, mejoras, progresión con rangos/`/rank`/estudios/insignias, economía
> entre jugadores —regalar/mercado/banco/trueque—, gremios, casino) + **combate COMBAT-1..6**
> (armas/armaduras, mundos/bestiario, batalla por turnos, críticos/esquivas/rareza, habilidades,
> encantar, minería, herrería, misiones, mazmorras) + **extras** (cofres, bolsa ficticia, `/robar`).
> Migraciones Flyway hasta **V22**. Comandos de economía agrupados en subcomandos (ADR-011).
> **Pendiente:** alianzas entre gremios (F-ECO-5b), ascensos de carrera, efectos pasivos de
> equipo/bienes, roles cosméticos comprables, directorio de comandos autogenerado, COMBAT-7 (pulido).

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

**Progresión lenta (grind) — decisión del usuario.** Crecer tiene que **costar**; no se avanza
rápido. Recompensas modestas, cooldowns amplios, energía limitada por día, subidas de stats/nivel
pequeñas, mejoras y ascensos **caros** y con requisitos exigentes. Es un "slow burn": el valor está
en el largo plazo, no en farmear en una tarde. Todas las constantes de balance nacen conservadoras
y se ajustan al alza solo si hace falta.

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

## Presentación (embeds bonitos — decisión del usuario)

Todo se muestra **lo más bonito posible** dentro de lo que permite Discord:
- **Árbol de mejora visual** (`/mejoras`): las mejoras forman un **árbol con ramas** (no lista
  plana); se renderiza con arte ASCII/emoji dentro del embed (líneas `├─ └─`, iconos de rama,
  candados 🔒 en lo bloqueado, ✅ en lo comprado, precio en cada nodo) para que el usuario vea de un
  vistazo por dónde puede tirar.
- **Inventario** (`/inventario`): agrupado por categoría, con emoji, cantidad y efecto de cada ítem;
  paginado si es largo.
- **Perfil / tienda / rankings**: barras de progreso (util `Barras`), emojis temáticos, campos
  ordenados, miniatura del avatar. Consistencia visual con `EmbedFactory`.

Implicación de diseño: el sistema de **mejoras es un árbol** (nodos con prerrequisitos y coste
creciente por rama), no compras sueltas.

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
