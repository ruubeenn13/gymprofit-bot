# Combate PvE — Mundos, monstruos y botín · Diseño

**Fecha:** 2026-07-13
**Estado:** decisiones tomadas; se construye por fases.
**Ámbito:** subsistema de combate del RPG económico (Fase 2, sub-bloque grande).

> Todo ficción. El combate usa los stats/energía/inventario que ya existen y alimenta la economía
> (coins, XP, loot). Nada centrado en el gym: temática de aventura/fantasía genérica.

## Decisiones del usuario

- **Estilo:** combate **por turnos con botones** (barras de HP, Atacar/Defender/Objeto/Huir).
- **Resolución:** por **poder** = fuerza + resistencia + arma/armadura equipadas, con **azar**.
- **Mundos:** varios mundos **desbloqueables**, cada uno con muchos monstruos por **dificultad**
  (fácil/normal/difícil) + un **JEFE**; matar al jefe desbloquea el siguiente mundo.
- **Recompensas:** coins + **XP** + **loot** (ítems con probabilidad de drop), escalado por
  mundo/dificultad/jefe. **Perder** resta salud + cooldown. **Pelear cuesta energía**.
- **Catálogo AMPLIO de mobs** (muchos por mundo, no 2+jefe) y **armas/armaduras** variadas.
- **Documentar todo** en embeds y en el `.md` de comandos.

## Extras del núcleo (elegidos por el usuario)

- **Habilidades + críticos/esquivas:** botón **Habilidad** con habilidades de cooldown (golpe
  crítico, curar, aturdir…); cada turno hay probabilidad de **crítico** (×2 daño) y de **esquiva**
  (según fuerza/carisma).
- **Rareza de loot:** común ⬜ · raro 🟦 · épico 🟪 · legendario 🟨, con probabilidad de drop por
  rareza y monstruo. Catálogo de loot amplio.
- **HP de combate propio:** el combate usa `HP = base + resistencia·k` (no la salud general). Salud =
  bienestar (consumibles); HP = combate.
- **Encantar/mejorar armas:** `/encantar` sube el nivel del arma (+daño), coste creciente. Además,
  **catálogo AMPLIO de encantamientos/efectos** (fuego, hielo, veneno, robo de vida, aturdir,
  crítico+, etc.) que se aplican a las armas y modifican el combate.

## Minería, herramientas y crafting (decisiones del usuario)

- **`/minar` = actividad universal** (independiente del `/work`): cualquiera con un **pico equipado**
  puede minar; cooldown + coste de energía. Se puede tener trabajo **y** minar (no ocupa el puesto).
- **Nivel de minería** (`personajes.nivel_mineria`): sube **con el uso** de `/minar`; a más nivel,
  más cantidad/mejores probabilidades. Así «todo el mundo puede minar mejor» con el tiempo, sin que
  sea un trabajo. Combina con el tier del pico.
- **Picos por tier (estilo Minecraft):** el pico equipado **desbloquea** qué minerales salen y cuánto:
  - Madera → piedra, carbón · Hierro → +cobre, hierro · Diamante → +oro, esmeralda ·
    Mithril → +rubí, obsidiana. (Catálogo amplio de picos y minerales.)
- **Durabilidad + reparar:** los picos (y armas) tienen **durabilidad** que baja con el uso;
  `/reparar` cuesta coins (sumidero) o se recompran. Requiere columna/tabla de durabilidad por ítem.
- **Minerales** (catálogo amplio, con rareza y valor): piedra, carbón, cobre, hierro, plata, oro,
  esmeralda, diamante, rubí, mithril, obsidiana… Guardados en el inventario.
- **Crafting (herrería):** `/craftear <receta>` combina minerales (según **recetas** del catálogo)
  para fabricar **armas, armaduras y encantamientos**. Loop completo: minar → forjar → combatir.
- **Vender:** `/vender <item> [cantidad]` convierte minerales/loot en coins; también se listan en el
  `/mercado` de jugadores (F-ECO-4).

**Implicación:** hay que ampliar el catálogo con **picos** (tiers + durabilidad), **minerales**
(tiers/rareza) y **recetas de crafting**. Todo en código, amplio.

## Modelo de datos (migraciones V10+)

- **Equipo del personaje:** columnas nuevas en `personajes`: `arma` (id), `armadura` (id) — lo
  equipado. El **poder de combate** = fuerza + resistencia + arma.ataque + armadura.defensa.
- **`progreso_mundos`** (`discord_id`, `mundo`, `jefe_derrotado`): qué mundos ha desbloqueado/
  completado el usuario. Un mundo se desbloquea al derrotar al jefe del anterior.
- **Cooldown de combate:** columna `ultimo_combate` en `personajes` (o reutilizar energía como
  freno principal).
- **Catálogos en código:** armas, armaduras, mundos, monstruos y sus loot tables.

## Catálogos (en código, amplios)

- **Armas** (categoría ARMA en `Items`): +ataque, precio creciente. Ej.: puño, palo, daga, espada,
  hacha, maza, lanza, arco, ballesta, katana, martillo de guerra, espada legendaria… (≈15+).
- **Armaduras** (categoría ARMADURA): +defensa. Ej.: ropa, cuero, cota de malla, placas, escudo,
  armadura élfica, armadura dracónica… (≈12+).
- **Mundos** (`Mundos`): id, nombre, nivel requerido, orden. Ej.: Bosque, Cueva, Pantano, Desierto,
  Montaña Helada, Volcán, Ciudad en Ruinas, Reino Sombrío (≈8).
- **Monstruos** (`Monstruos`, AMPLIO): id, mundo, dificultad, poder, HP, coins, XP, loot (lista de
  {item, prob}), esJefe. **Muchos por mundo** (p. ej. 6-10 normales + 1 jefe → ≈60+ en total).

## Componentes por fase

**COMBAT-1 · Armas, armaduras y equipar** *(cimiento del combate)* — ✅ **HECHO**
Extiende `Items` con ARMA/ARMADURA (ataque/defensa); columnas `personajes.arma`/`armadura`;
`/equipar <item>`, `/desequipar`, y el **poder de combate** en `/perfil`. La tienda ya los vende.
> Implementado: **V10** (`arma`/`armadura`), 16 armas + 12 armaduras en `Items`, `CombateService`
> (`equipar`/`desequipar`/`poderCombate`), `EquiparComando`/`DesequiparComando`, `/perfil` con poder
> de combate + equipo, `/tienda` con secciones Armas/Armaduras y su stat. `CombateServiceTest` (11).
> El **HP de combate** se difiere a COMBAT-3 (cuando haya pelea). Sin canales nuevos aún.

**COMBAT-2 · Mundos y monstruos**
Catálogos `Mundos` y `Monstruos` (amplios); tabla `progreso_mundos`; `/mundos` (lista con
desbloqueo), `/monstruos <mundo>` (bestiario). Sin pelea aún (solo datos + navegación).

**COMBAT-3 · Batalla por turnos**
Sesión de combate en memoria (jugador HP vs monstruo HP, turno). `/pelear <monstruo>`: comprueba
mundo desbloqueado, nivel, energía y cooldown; abre un embed con **barras de HP** y botones
**Atacar / Defender / Objeto / Huir** (listener `CombateListener`). Cada turno: daño =
f(ataque+fuerza, defensa rival, azar); el monstruo contraataca. Al ganar: coins + XP + tirada de
loot (añade ítem al inventario); si es jefe, marca `jefe_derrotado` y **desbloquea el siguiente
mundo**. Al perder: −salud + cooldown. Todo en **embeds bonitos**.

**COMBAT-4 · Habilidades, rareza y encantamientos** *(profundidad)*
Habilidades de combate + críticos/esquivas; rareza de loot (común→legendario); `/encantar` +
catálogo amplio de encantamientos/efectos aplicados a armas.

**COMBAT-5 · Minería y recursos**
`/minar` (recursos con cooldown/energía), catálogo amplio de minerales, `/vender` (recursos/loot →
coins). Base para el crafting.

**COMBAT-6 · Contenido y objetivos**
- **Misiones de caza** (`/mision`): objetivos tipo «mata 10 lobos» → recompensa; diarias/semanales.
- **Mazmorras / oleadas** (`/mazmorra`): varios monstruos seguidos, más riesgo → más botín, jefe al
  final.
- **Loot en el mercado:** el botín y los minerales se listan/venden en el `/mercado` de jugadores
  (integra con F-ECO-4), cerrando el círculo de la economía.

**COMBAT-7 · Pulido**
Rare spawns (monstruo raro con loot top), sets de armadura (bonus por conjunto), rankings de mundos/
kills, títulos, loot exclusivo de jefes.

## Balance (progresión lenta)

- El poder del monstruo escala por mundo/dificultad; el jugador necesita **mejorar stats
  (/entrenar, /mejorar) y equipo (armas/armaduras)** para avanzar. Sin farmear un mundo en una
  tarde.
- Recompensas escaladas pero contenidas; el loot raro es poco probable (sumidero de tiempo).
- Energía y cooldown limitan el ritmo.

## Documentación (requisito del usuario)

- Cada comando nuevo responde con **embed** claro.
- Todo comando entra en el **directorio autogenerado** (`docs/comandos.md` + mensajes maestros;
  ver [[gymprobot-directorio-comandos]]). Cada comando declara su `Comando.Categoria`.

## Fuera de alcance

- PvP entre jugadores (posible fase futura).
- Combate cooperativo / raids (futuro, con gremios).
