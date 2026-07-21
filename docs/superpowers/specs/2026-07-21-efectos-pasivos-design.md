# Efectos pasivos de equipo y bienes — diseño

**Fecha:** 2026-07-21 · **Estado:** aprobado, pendiente de plan de implementación
**Relacionado:** [`2026-07-13-economia-rpg-vision.md`](2026-07-13-economia-rpg-vision.md) (spec estrella
polar del RPG) · [`2026-07-15-descanso-y-energia-design.md`](2026-07-15-descanso-y-energia-design.md)
(precedente: `Camas` dio uso a las viviendas) · ADR-010 (anti-inflación) · ADR-011 (subcomandos)

---

## Problema

El catálogo (`services/Items.java`, 109 ítems) tiene **40 ítems que se compran y no hacen nada**:

- Los **20 de categoría `EQUIPO`** (de `gorra`, 200 coins, a `moto`, 6 000). El Javadoc de `Items`
  literalmente dice *«sus efectos pasivos llegan en fases posteriores»*. Esta es esa fase.
- Los **20 de categoría `BIEN`**. De estos, `Camas` ya reclamó **10** como sitios donde dormir
  (`saco_dormir`, `colchon`, `piso`, `apartamento`, `casa`, `chalet`, `mansion`, `isla`, `castillo`,
  `rascacielos`). Los **10 restantes son vehículos** (`coche`, `furgoneta`, `moto_agua`, `camion`,
  `coche_lujo`, `helicoptero`, `avioneta`, `jet`, `yate`, `cohete`) y siguen siendo inertes: entre
  35 000 y 3 000 000 de coins por un emoji en `/inventario ver`.

Esto tiene tres consecuencias malas:

1. **La tienda miente.** El jugador ve un precio de seis cifras y asume que compra algo. Comprarse un
   `yate` de 900 000 coins y que no pase absolutamente nada se lee como un bug, no como estatus.
2. **El late-game no tiene destino.** Con `/banco`, `/bolsa`, `/mercado` y `/casino` un jugador
   veterano acumula coins sin ningún objetivo que no sea el número. Los sumideros existen (ADR-010),
   pero un sumidero sin recompensa es solo un impuesto.
3. **La progresión se aplana.** Después de comprar la mejor arma, la mejor armadura, el mejor pico y
   la mejor cama, no queda nada que mejorar. La curva se acaba antes que el juego.

**Objetivo:** que esos **30 ítems** (20 `EQUIPO` + 10 vehículos `BIEN`) den **efectos pasivos reales**
sobre lo que el jugador ya hace todos los días: currar, ganar XP, regenerar energía, minar y pelear.

---

## Alcance

### Dentro

- Catálogo nuevo `services/Pasivos.java` con los 30 ítems y sus bonos.
- Sistema de **ranuras** (equipar/quitar), con tabla propia y desbloqueo por nivel.
- `PasivoService` con el cálculo de bonos sumados y topados.
- Familia de comandos `/pasivos` (`ver`, `equipar`, `quitar`) y una línea nueva en `/perfil ver`.
- Integración en `TrabajoService`, `XpService`, `MineriaService`, `BatallaService` y `EnergiaJob`.

### Fuera (no-objetivos explícitos)

- **Las 10 camas no se tocan.** `saco_dormir`, `colchon`, `piso`, `apartamento`, `casa`, `chalet`,
  `mansion`, `isla`, `castillo` y `rascacielos` ya tienen efecto vía `Camas` y el módulo de descanso
  está **cerrado y probado en vivo**. Darles además un pasivo sería doble recompensa por la misma
  compra y obligaría a reabrir el balance del descanso. Si alguien quiere pasivos de vivienda, es
  otra spec.
- **No se modifica `Items.java`.** Ver «Por qué un catálogo paralelo».
- **No se rebalancean monstruos, jefes ni mazmorras.** Ver «Análisis de balance § combate».
- **No hay pasivos en consumibles, armas, armaduras, picos, minerales ni cofres.** Esas categorías ya
  tienen su mecánica propia (efecto inmediato, ataque/defensa, tier/durabilidad, valor de venta, loot).
- **No hay sets ni sinergias** («equipa 3 vehículos y…»). Complica la explicación al jugador a cambio
  de poco; se puede añadir después sin romper nada.
- **No hay coste de mantenimiento** (impuestos por vehículo, gasolina…). Es una idea buena pero es un
  sumidero nuevo con su propio job y su propio diseño: va al backlog, no aquí.

---

## Decisiones tomadas

| Decisión | Elegido | Por qué |
|---|---|---|
| Dónde viven los datos | **Catálogo paralelo `Pasivos.java`** | Precedente triple: `Picos`, `Camas`, `Cofres`. Ver abajo |
| Cuántos bonos por ítem | **Varios (1–3)** | Decisión explícita del usuario sobre la alternativa simple de un efecto por ítem. Un `jet` con un único +11 % de sueldo es aburrido; un `jet` que además acorta el cooldown y da XP se *siente* como un jet |
| Poseer vs. equipar | **Equipar en ranuras** | Ver «Por qué ranuras» |
| Dónde se guardan las ranuras | **Tabla nueva `pasivos_equipados`** | Precedente: `durabilidad_picos`, `mineria`, `descanso`. Ver «Modelo de datos» |
| Cuántas ranuras | **1 / 2 / 3 / 4 a niveles 0 / 10 / 25 / 50** | Mismos umbrales que `Rango` (Novato/Habitual/Veterano/Leyenda): el desbloqueo cae **a la vez** que el rol nuevo, así el subidón de nivel se nota doble y no hay que explicar dos escalas |
| Equipar consume el ítem | **No** | Precedente: `CombateService.equipar` solo referencia el id y exige poseerlo |
| Fuente de verdad del bono | **El inventario, siempre** | Ver «Por qué se recalcula contra el inventario» |
| Mismo ítem en dos ranuras | **Prohibido** | Duplicaría el bono con una sola compra: es el exploit más obvio del sistema |
| Peso del combate | **Marginal (~7–9 % del poder)** | Decisión explícita del usuario: no se rebalancea el bestiario |
| Topes | **Globales por tipo, saturantes** | Mismo mecanismo que `TrabajoService.BONO_ESTUDIOS_MAX`. Sin tope, cuatro ranuras de sueldo romperían la economía lenta (ADR-010) |
| Visibilidad de los comandos | **Pública** | Regla de visibilidad de julio: las acciones de economía son públicas; efímero solo para RGPD/moderación/staff/errores |

### Por qué un catálogo paralelo y no columnas en `Items`

`Items` es un `record` de 8 componentes con **109 entradas literales**. Meter ahí los bonos obligaría
a una de dos cosas feas:

- **Ampliar el record** con campos que son `null`/`0` en 79 de 109 filas, y que además tendrían que
  ser una lista (varios bonos por ítem) dentro de un record que hoy es plano y comparable.
- **Añadir un constructor de compatibilidad más** — ya hay uno.

Además, y esto es lo importante: **los precios de `Items` son carga estructural**. `RarezaTest` deriva
la rareza del precio y `Camas` empareja por id contra ese mismo catálogo. Cada vez que se toca `Items`
hay riesgo de romper invariantes probados en otro módulo. El proyecto ya resolvió esto tres veces con
el mismo patrón —`Picos` (tier y durabilidad), `Camas` (energía/hora y tope), `Cofres` (loot)— y en las
tres el catálogo satélite **se empareja por `itemId`** y `Items` queda intacto. Se repite el patrón.

### Por qué ranuras y no «basta con tenerlo»

La alternativa barata sería: si lo tienes en el inventario, el bono cuenta. Se descarta por tres
razones:

1. **Se acumularía sin límite.** Un jugador con los 30 ítems tendría los 9 tipos a tope el día que
   termina de comprarlos, y a partir de ahí no habría ninguna decisión que tomar nunca más.
2. **No habría estrategia.** Con ranuras, el jugador que va a minar se pone `dron` + `telescopio`, y el
   que va a currar se pone `jet` + `traje`. Eso es una decisión, y las decisiones son el juego.
3. **El desbloqueo por nivel da curva.** Con 1 ranura a nivel 0 y la cuarta a nivel 50, el sistema
   crece con el jugador en vez de resolverse de golpe.

El coste es un comando más que aprender. Se compensa con `/pasivos ver`, que enseña el estado completo
de un vistazo, y con la intro de canal (ver «Texto de la intro del canal»).

### Por qué se recalcula contra el inventario

`pasivos_equipados` guarda **una referencia**, no un derecho. En cada consulta, `PasivoService` cruza
las ranuras con `InventarioRepositorio.listar(discordId)` y **descarta las ranuras cuyo ítem ya no se
posee**.

Esto no es una micro-optimización, es lo que cierra un exploit entero. Si el bono se «cobrara» al
equipar, un jugador podría: equipar el `yate` → venderlo con `/inventario vender` → seguir cobrando
+7 % de XP para siempre. Y lo mismo con `/regalar-item` (regalarlo y que se lo devuelvan),
`/mercado publicar` (ponerlo a la venta y seguir con el bono mientras está listado) o `/trueque`.

Recalcular contra el inventario **elimina los cuatro casos a la vez** y, sobre todo, significa que
**`VentaService`, `RegaloService`, `MercadoService`, `TruequeService` y `RoboService` no se tocan**.
No hay hooks de limpieza, no hay que acordarse de llamar a nada al añadir el sexto sitio donde se
puede perder un ítem. La fila muerta se queda ahí, inofensiva, y `/pasivos ver` la marca con un aviso
para que el jugador sepa por qué su bono no cuenta.

---

## Modelo

### `services/Pasivos.java`

```java
/** Un bono concreto: qué mejora y cuánto. */
public record Bono(Tipo tipo, double magnitud) { }

/** Los efectos pasivos de un ítem. Un ítem puede llevar varios bonos. */
public record Pasivo(String itemId, List<Bono> bonos) { }

public enum Tipo {
    SUELDO, COOLDOWN_WORK, XP, ENERGIA_REGEN,
    MINERIA_CANTIDAD, MINERIA_DURABILIDAD,
    COMBATE_ATAQUE, COMBATE_DEFENSA, CRITICO
}
```

Convenio de unidades, para que no haya ambigüedad al implementar:

- Los tipos **porcentuales** (`SUELDO`, `COOLDOWN_WORK`, `XP`, `MINERIA_DURABILIDAD`, `CRITICO`)
  guardan la magnitud como **fracción** (`0.11` = 11 %). Es el mismo convenio que
  `BONO_ESTUDIOS = 0.01` y que `Encantamiento.magnitud()`.
- Los tipos **planos** (`ENERGIA_REGEN`, `MINERIA_CANTIDAD`, `COMBATE_ATAQUE`, `COMBATE_DEFENSA`)
  guardan enteros en un `double` y se redondean con `Math.round` al aplicarlos.
- `COOLDOWN_WORK` es una **reducción**: la magnitud es positiva y se resta.

API pública:

```java
public static Optional<Pasivo> porId(String itemId);
public static final List<Pasivo> CATALOGO;
public static final Map<Tipo, Double> TOPES;   // el tope global de cada tipo
```

### Los nueve tipos y sus topes

| Tipo | Qué hace | Tope global |
|---|---|---|
| `SUELDO` | % extra sobre el pago de `/trabajo currar` | **+30 %** |
| `COOLDOWN_WORK` | recorta el cooldown de 60 min de `/trabajo currar` | **−25 %** (mínimo 45 min) |
| `XP` | % extra de XP de **cualquier** fuente | **+20 %** |
| `ENERGIA_REGEN` | energía extra en cada tick de `EnergiaJob` | **+10** |
| `MINERIA_CANTIDAD` | minerales extra por `/minar` | **+3** |
| `MINERIA_DURABILIDAD` | probabilidad de **no** gastar durabilidad del pico | **40 %** |
| `COMBATE_ATAQUE` | ataque plano en batalla | **+12** |
| `COMBATE_DEFENSA` | defensa plana en batalla | **+10** |
| `CRITICO` | probabilidad de crítico (aditiva) | **+8 %** |

Los topes son **saturantes**, no un error: si sumas +35 % de sueldo, cobras +30 % y `/pasivos ver` te
enseña «+30 % (tope)». Mismo mecanismo que `Math.min(BONO_ESTUDIOS_MAX, …)` en
`TrabajoService.conBonoEstudios` y que los topes de `probCritico`/`probEsquiva` en `CombateService`.
Se topa **la suma**, nunca cada bono por separado.

**Regla de diseño que el catálogo cumple:** *ningún ítem por sí solo alcanza el tope de ningún tipo*.
Siempre hacen falta al menos dos ranuras bien elegidas. Es lo que impide que el sistema se resuelva
comprando una sola cosa cara.

### `services/PasivoService.java`

```java
/** Bonos activos del jugador: sumados sobre las ranuras con ítem en inventario, y topados. */
public Map<Pasivos.Tipo, Double> bonosDe(long discordId);

/** Atajo tipado; 0.0 si no hay bono de ese tipo. */
public double bonoDe(long discordId, Pasivos.Tipo tipo);

/** Ranuras desbloqueadas según el nivel del usuario. */
public static int ranurasDe(int nivel);   // 1 / 2 / 3 / 4

public ResultadoEquipar equipar(long discordId, String itemId, Integer ranura);
public boolean quitar(long discordId, int ranura);

/** Vista completa para /pasivos ver: ranura, ítem, si está bloqueada, si falta el ítem. */
public List<EstadoRanura> ranuras(long discordId);
```

La suma y el topado se hacen en una **función pura y estática** para poder testear el balance sin
tocar la base de datos, igual que `TrabajoService.conBonoEstudios` o `CombateService.dano`:

```java
/** Suma los bonos de una lista de pasivos y aplica los topes. Pura. */
public static Map<Pasivos.Tipo, Double> sumarYTopar(List<Pasivos.Pasivo> equipados);
```

---

## Modelo de datos — migración Flyway **V25**

La última migración aplicada es `V24__ejercicio_dia.sql`, así que el hueco libre es **V25**.

**`V25__pasivos_equipados.sql`:**

```sql
-- ----------------------------------------------------------------------------
-- Efectos pasivos — ranuras de equipo y bienes.
--
-- Los 20 ítems de EQUIPO y los 10 vehículos de BIEN pasan a dar bonos pasivos (sueldo, XP, energía,
-- minería y combate). El catálogo de bonos vive en código (services/Pasivos), igual que Picos, Camas
-- y Cofres: aquí solo se guarda QUÉ tiene equipado cada jugador en cada ranura.
--
-- Tabla aparte de `personajes` (mismo patrón que `mineria`, `descanso` y `durabilidad_picos`): el
-- record Personaje ya tiene 14 componentes y cada columna nueva obliga a tocar el record, el
-- repositorio y todos los constructores de los tests. Además esto es 1..N por jugador, no 1..1.
--
-- La fila es solo una REFERENCIA, no un derecho: PasivoService cruza siempre contra el inventario y
-- descarta las ranuras cuyo ítem ya no se posee. Por eso vender/regalar/publicar en el mercado no
-- necesita ningún hook de limpieza. El borrado RGPD lo cubre el ON DELETE CASCADE.
-- ----------------------------------------------------------------------------
CREATE TABLE pasivos_equipados (
    discord_id  BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    ranura      TINYINT     NOT NULL COMMENT 'Ranura 1..4 (se desbloquean a nivel 0/10/25/50)',
    item_id     VARCHAR(40) NOT NULL COMMENT 'Id del ítem (catálogo services/Items; bonos en services/Pasivos)',
    equipado_en TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuándo se equipó (solo informativo)',
    PRIMARY KEY (discord_id, ranura),
    CONSTRAINT uq_pasivos_item UNIQUE (discord_id, item_id),
    CONSTRAINT fk_pasivos_equipados_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Ítems con efecto pasivo equipados por jugador, por ranura.';
```

Dos detalles deliberados:

- **PK `(discord_id, ranura)`**: equipar es un `INSERT … ON DUPLICATE KEY UPDATE`, así que reemplazar
  el contenido de una ranura es una sola sentencia atómica.
- **`UNIQUE (discord_id, item_id)`**: la regla «un ítem no puede estar en dos ranuras» queda garantizada
  **en el esquema**, no solo en el service. El service la comprueba antes para poder devolver un error
  bonito, pero si algún día hay una carrera entre dos comandos simultáneos, la base de datos gana.
- **No se guarda el nivel de desbloqueo.** Se deriva del nivel actual con `ranurasDe(nivel)`. Si un
  jugador tenía 4 ranuras y su nivel bajara (hoy no puede pasar, pero por si acaso), las ranuras
  sobrantes simplemente dejan de contar sin borrar nada: recuperar el nivel las devuelve intactas.

**`PasivoRepositorio`** (JDBC plano, como el resto de `db/`):

```java
Map<Integer, String> equipados(long discordId);   // ranura -> itemId
void equipar(long discordId, int ranura, String itemId);
boolean quitar(long discordId, int ranura);
Optional<Integer> ranuraDe(long discordId, String itemId);
List<PasivoDe> conTipo(Pasivos.Tipo tipo);        // para el segundo pase de EnergiaJob
```

---

## Catálogo — los 30 ítems

Reglas que cumple el catálogo, y que hay que mantener si algún día se toca:

1. **Las magnitudes escalan con el precio.** La economía del RPG es lenta a propósito (ADR-010): un
   ítem de 200 coins da un bono simbólico, uno de 3 000 000 da el mejor del juego.
2. **Ningún ítem alcanza por sí solo el tope de su tipo.** Siempre hacen falta ≥ 2 ranuras.
3. **Los bonos son temáticos**, no aleatorios. La justificación de cada uno está en la tabla y va
   también a i18n (`pasivo.<id>.desc`), para que `/pasivos ver` se explique solo.
4. **Cuantos más bonos, más caro.** 1 bono en lo barato, 2 en la franja media, 3 solo en los vehículos
   de lujo — que son piezas de vanidad pero además genuinamente buenas.

### Equipo (20)

| # | Ítem | Emoji | Precio | Bonos | Por qué |
|---|---|---|---|---|---|
| 1 | `gorra` | 🧢 | 200 | XP +2 % | Te la pones y ya vas al gym. Bono de entrada, casi simbólico |
| 2 | `gafas` | 🕶️ | 300 | Crítico +1 % | Ves venir el golpe antes que el otro |
| 3 | `mochila` | 🎒 | 400 | Minería cantidad +1 | Cargas más piedra por viaje |
| 4 | `uniforme` | 🦺 | 500 | Sueldo +4 %, Defensa +1 | Vas de uniforme: te pagan mejor y el chaleco algo protege |
| 5 | `auriculares` | 🎧 | 500 | XP +3 % | Con música entrenas más y mejor |
| 6 | `movil` | 📱 | 600 | Sueldo +3 %, XP +2 % | Te organizas: turnos y rutinas siempre a mano |
| 7 | `zapatillas` | 👟 | 600 | Energía +2, Defensa +2 | Buen calzado, menos castigo en las piernas |
| 8 | `mancuernas` | 🏋️ | 700 | Ataque +3, Energía +1 | Hierro en casa: pegas más fuerte y aguantas más |
| 9 | `herramientas` | 🧰 | 800 | Sueldo +5 %, Durabilidad 5 % | Con tu propio juego rindes más y rompes menos |
| 10 | `consola` | 🎮 | 800 | XP +4 % | Reflejos, competitividad y horas de práctica |
| 11 | `patinete` | 🛴 | 800 | Cooldown −4 % | Llegas antes al curro |
| 12 | `bici` | 🚲 | 1 200 | Cooldown −6 %, Energía +1 | Vas antes y encima haces cardio |
| 13 | `guitarra` | 🎸 | 1 500 | XP +5 % | Aprender un instrumento entrena la cabeza |
| 14 | `portatil` | 💻 | 1 500 | Sueldo +6 %, XP +4 % | La herramienta que sirve para todo |
| 15 | `camara` | 📷 | 1 800 | Minería cantidad +1, XP +3 % | Documentas la veta y aprendes del terreno |
| 16 | `reloj` | ⌚ | 2 000 | Sueldo +4 %, XP +5 % | Puntual y midiendo cada sesión |
| 17 | `telescopio` | 🔭 | 2 200 | Minería cantidad +1, Durabilidad 8 % | Localizas el filón antes de picar a ciegas |
| 18 | `traje` | 🤵 | 2 500 | Sueldo +8 %, Crítico +1 % | Traje bueno: mejor sueldo y más presencia |
| 19 | `dron` | 🚁 | 4 000 | Minería cantidad +2, Durabilidad 10 % | Explora la mina por ti y te dice dónde picar |
| 20 | `moto` | 🏍️ | 6 000 | Cooldown −8 %, Sueldo +3 % | Te mueves rápido y llegas a más sitios |

### Bienes — vehículos (10)

| # | Ítem | Emoji | Precio | Bonos | Por qué |
|---|---|---|---|---|---|
| 21 | `coche` | 🚗 | 35 000 | Cooldown −9 %, Sueldo +4 % | El primer vehículo de verdad: cambia tu día entero |
| 22 | `furgoneta` | 🚐 | 45 000 | Cooldown −7 %, Minería cantidad +1 | Cabe todo: herramienta, material y lo que saques |
| 23 | `moto_agua` | 🚤 | 60 000 | Energía +3, XP +3 % | Ocio de verdad: desconectas y vuelves entero |
| 24 | `camion` | 🚚 | 90 000 | Minería cantidad +2, Sueldo +6 %, Durabilidad 8 % | Trabajo pesado: mueves tonelaje y cobras por ello |
| 25 | `coche_lujo` | 🏎️ | 200 000 | Sueldo +7 %, Crítico +2 %, Defensa +2 | Deportivo blindado: impone y protege |
| 26 | `helicoptero` | 🚁 | 350 000 | Cooldown −10 %, Durabilidad 12 %, Ataque +3 | Apoyo aéreo: llegas a la veta y a la pelea desde arriba |
| 27 | `avioneta` | 🛩️ | 400 000 | Sueldo +9 %, XP +6 %, Energía +2 | Vuelas a donde haga falta y aprendes por el camino |
| 28 | `jet` | 🛫 | 700 000 | Sueldo +11 %, Cooldown −11 %, XP +6 % | Jet privado: el mejor paquete de trabajo del juego |
| 29 | `yate` | 🛥️ | 900 000 | Energía +4, XP +7 %, Sueldo +7 % | Descanso de lujo: vuelves nuevo y con contactos |
| 30 | `cohete` | 🚀 | 3 000 000 | Ataque +5, Defensa +4, Crítico +3 % | El tope absoluto del catálogo. La única pieza sci-fi: es la que rompe el techo en combate |

### Fuentes por tipo (resumen)

| Tipo | Ítems que lo dan (mejor → peor) |
|---|---|
| `SUELDO` | jet 11 · avioneta 9 · traje 8 · coche_lujo 7 · yate 7 · portatil 6 · camion 6 · herramientas 5 · uniforme 4 · reloj 4 · coche 4 · movil 3 · moto 3 |
| `COOLDOWN_WORK` | jet 11 · helicoptero 10 · coche 9 · moto 8 · furgoneta 7 · bici 6 · patinete 4 |
| `XP` | yate 7 · jet 6 · avioneta 6 · reloj 5 · guitarra 5 · consola 4 · portatil 4 · auriculares 3 · camara 3 · moto_agua 3 · gorra 2 · movil 2 |
| `ENERGIA_REGEN` | yate 4 · moto_agua 3 · zapatillas 2 · avioneta 2 · bici 1 · mancuernas 1 |
| `MINERIA_CANTIDAD` | dron 2 · camion 2 · mochila 1 · camara 1 · telescopio 1 · furgoneta 1 |
| `MINERIA_DURABILIDAD` | helicoptero 12 · dron 10 · telescopio 8 · camion 8 · herramientas 5 |
| `COMBATE_ATAQUE` | cohete 5 · mancuernas 3 · helicoptero 3 |
| `COMBATE_DEFENSA` | cohete 4 · zapatillas 2 · coche_lujo 2 · uniforme 1 |
| `CRITICO` | cohete 3 · coche_lujo 2 · gafas 1 · traje 1 |

---

## Análisis de balance

### El mejor build de 4 ranuras, tipo por tipo

Se calcula, para cada tipo, la suma de los **4 mejores** ítems que lo dan (es el máximo teórico: exige
gastar las 4 ranuras en un solo tipo, renunciando a todo lo demás).

| Tipo | Mejores 4 | Suma bruta | Tope | ¿Satura? |
|---|---|---|---|---|
| `SUELDO` | jet + avioneta + traje + coche_lujo | 11+9+8+7 = **+35 %** | +30 % | Sí → +30 % |
| `COOLDOWN_WORK` | jet + helicoptero + coche + moto | 11+10+9+8 = **−38 %** | −25 % | Sí → −25 % |
| `XP` | yate + jet + avioneta + reloj | 7+6+6+5 = **+24 %** | +20 % | Sí → +20 % |
| `ENERGIA_REGEN` | yate + moto_agua + zapatillas + avioneta | 4+3+2+2 = **+11** | +10 | Sí → +10 |
| `MINERIA_CANTIDAD` | dron + camion + mochila + camara | 2+2+1+1 = **+6** | +3 | Sí → +3 |
| `MINERIA_DURABILIDAD` | helicoptero + dron + telescopio + camion | 12+10+8+8 = **38 %** | 40 % | **No** — el tope no llega a tocarse |
| `COMBATE_ATAQUE` | cohete + mancuernas + helicoptero (solo hay 3) | 5+3+3 = **+11** | +12 | **No** |
| `COMBATE_DEFENSA` | cohete + zapatillas + coche_lujo + uniforme | 4+2+2+1 = **+9** | +10 | **No** |
| `CRITICO` | cohete + coche_lujo + gafas + traje | 3+2+1+1 = **+7 %** | +8 % | **No** |

Todos los máximos están **en el tope o por debajo**. Ningún tipo se pasa después del topado, por
definición, y —lo importante— **ningún ítem suelto llega al tope de nada**: el que más se acerca es
`jet` con 11 de 30 en sueldo (37 % del tope). El sistema siempre exige combinar.

Que cuatro tipos **no lleguen** a su tope es deliberado: deja margen para añadir ítems o subir
magnitudes en el futuro sin tener que retocar los topes (y sin que un jugador note una bajada, que es
lo que peor sienta).

### Los máximos son incompatibles entre sí

Los topes de arriba son máximos **aislados**. Con solo 4 ranuras, maximizar un tipo significa
renunciar a los demás. Un build realista de nivel 50 «trabajador puro» sería:

> `jet` + `avioneta` + `traje` + `coche_lujo` → Sueldo **+30 % (tope)**, Cooldown −11 %, XP +12 %,
> Energía +2, Crítico +2 %

y un «minero puro»:

> `helicoptero` + `dron` + `camion` + `telescopio` → Minería +3 (tope), Durabilidad **38 %**,
> Sueldo +6 %, Cooldown −10 %, Ataque +3

Esa tensión —no puedes tenerlo todo— es exactamente el punto del sistema de ranuras.

### El combate se queda marginal (a propósito)

Números reales del repo:

- Arma más fuerte: `espada_legendaria`, **75 de ataque** (80 000 coins).
- Armadura más fuerte: `armadura_divina`, **55 de defensa** (120 000 coins).
- `CombateService.poderCombate(p)` = `fuerza + resistencia + ataqueArma + defensaArmadura + armaNivel·2`.

Un jugador de late-game con, digamos, 60 de fuerza, 60 de resistencia, el arma y la armadura tope y
el arma a nivel 5 tiene:

> `poderCombate = 60 + 60 + 75 + 55 + 10 = 260`

El build de 4 ranuras **más orientado a combate posible** (`cohete` + `helicoptero` + `mancuernas` +
`coche_lujo`) aporta **+11 de ataque, +4 de defensa y +5 % de crítico**, o sea **+15 de poder sobre 260
= +5,8 %**. Aun asumiendo los topes teóricos (+12 / +10 = +22), sería **+8,5 %**.

Por debajo del 10 % en el peor caso: **no hace falta rebalancear ni un solo monstruo, jefe u oleada de
mazmorra**. Esa era la condición de diseño y el catálogo la cumple con margen.

Sobre el crítico: `probCritico` es `0.05 + 0.008·fuerza` con tope **0.50**. Un +5 % aditivo sobre una
probabilidad que ya puede llegar al 50 % es ruido. El bono se suma **antes** del `Math.min` del tope
existente, así que un jugador ya saturado al 50 % no gana nada — y eso está bien: el crítico de
pasivos es para quien aún no ha entrenado fuerza, no para el que ya está a tope.

**El argumento del early-game.** Alguien podría objetar: «+11 de ataque sobre un novato con
`poderCombate = 20` es un +55 %». Cierto, pero **imposible**: los tres únicos ítems con
`COMBATE_ATAQUE` son `cohete` (3 000 000), `helicoptero` (350 000) y `mancuernas` (700). Solo el
último está al alcance de un novato, y sus +3 de ataque están entre `daga` (7, 300 coins) y
`espada_corta` (10, 600 coins): es aproximadamente **un escalón de arma al precio de un escalón de
arma**. Coherente, no roto. Todo lo demás en combate está detrás de una barrera de seis o siete cifras
que solo cruza un jugador que ya lo tiene todo.

### El combate no cambia a mitad de pelea

Los tres bonos de combate entran en el **snapshot** que `BatallaService.nuevaSesion` ya hace hoy (ahí
se congelan ataque, defensa, crítico, esquiva y robo de vida antes de crear la `CombateSesion`). Así:

- Los bonos valen para **toda la pelea y toda la mazmorra**, aunque el jugador equipe/desequipe en
  medio o venda el ítem entre oleadas.
- No hay una consulta a base de datos por turno.
- Se elimina un exploit obvio: equipar el `cohete` justo antes del golpe final del jefe.

### Impacto en la inflación (ADR-010)

`SUELDO` y `COOLDOWN_WORK` son las dos que más mueven la aguja. En el peor caso combinado que un
jugador puede montar con 4 ranuras —`jet` + `avioneta` + `traje` + `coche_lujo`— sale +30 % de sueldo
y −11 % de cooldown, es decir **~1,46× coins/hora** frente a un jugador sin nada. Si en cambio va a
por el cooldown (`jet` + `helicoptero` + `coche` + `moto`) obtiene −25 % (60 → 45 min, **1,33×** turnos)
con +18 % de sueldo → **~1,57×**.

Ese ~1,5× no es gratis: exige **1 155 000 coins** en ítems (jet + helicóptero + coche + moto) que salen
del circulante y **nivel 50** para tener las 4 ranuras. Es un sumidero enorme que se amortiza en cientos
de turnos de trabajo, y funciona en la dirección correcta: **la inflación se paga por adelantado**.

---

## Comandos y UX

Familia nueva de primer nivel `/pasivos` con tres subcomandos, siguiendo ADR-011 (los subcomandos no
gastan cupo; solo cuentan los comandos de primer nivel). Se pasa de **55 a 56** de los 100 que permite
Discord — margen de sobra. *(Verificar el total real en el momento de implementar: el módulo de
consultas a la API está en curso y añade familias propias.)*

Todas las respuestas son **públicas**, por la regla de visibilidad de julio: las acciones de economía
se ven, y aquí además es bueno que se vean (enseñar el `yate` es media gracia de comprarlo). Solo se
responde en efímero a los **errores** de validación.

### `/pasivos ver [usuario]`

Embed vía `EmbedFactory`, con:

1. **Las cuatro ranuras, en orden**, con su estado:
   - `1️⃣ 🛫 Jet privado` — ocupada
   - `2️⃣ ➖ Vacía` — libre
   - `3️⃣ 🔒 Se desbloquea en el nivel 25` — bloqueada por nivel
   - `4️⃣ 🔒 Se desbloquea en el nivel 50`
   - `2️⃣ ⚠️ 🛥️ Yate — ya no lo tienes, no cuenta` — equipado pero ausente del inventario
2. **Los bonos activos, ya sumados y ya topados.** Se muestra el resultado, no la aritmética:
   `+18 % sueldo`, no `+11 % +7 %`. Si un tipo está saturado se marca: `+30 % sueldo (tope)`.
3. Un pie con el siguiente desbloqueo: *«Ranura 3 en el nivel 25 (te faltan 4 niveles).»*

Con `[usuario]` muestra los de otro (lectura pública, sin datos sensibles).

### `/pasivos equipar <item> [ranura]`

- `<item>`: autocompletado sobre los 30 ítems **que el jugador posee y tienen pasivo**. Filtrar por
  inventario en el autocompletado evita la mitad de los errores antes de que ocurran.
- `[ranura]`: opcional, 1–4.
  - **Sin ranura:** usa la primera libre **desbloqueada**.
  - **Sin ranura y ninguna libre:** **no elige por el jugador**. Responde listando qué hay en cada
    ranura y pide que indique cuál reemplazar. Pisar en silencio un `cohete` de 3 000 000 de coins es
    inaceptable, aunque sea reversible.
  - **Con ranura ocupada:** reemplaza y dice explícitamente qué ha salido.
- El ítem **no se consume**. Se puede quitar y volver a poner las veces que se quiera, sin coste.

La respuesta confirma **qué bonos aporta el ítem** y **cómo quedan los totales después**, incluyendo si
algún tipo ha llegado al tope (para que nadie equipe una cuarta pieza de sueldo sin enterarse de que no
le está sumando nada).

### `/pasivos quitar <ranura>`

Vacía una ranura. Devuelve el ítem al estado «no equipado» (nunca salió del inventario) y muestra los
totales resultantes.

### `/perfil ver`

Gana una línea nueva con los bonos activos resumidos, en la misma línea editorial que ya tiene el
resto del embed:

> **✨ Pasivos** · +18 % sueldo · −11 % cooldown · +12 % XP · +2 energía

Si no hay ninguno, la línea no aparece (no se ensucia el perfil de un jugador nuevo).

---

## Puntos de integración

`PasivoService.bonosDe(discordId)` devuelve el mapa ya sumado y ya topado; **cada service lee solo el
tipo que le importa** y no sabe nada del resto. Esto mantiene el acoplamiento en una sola dirección.

### `TrabajoService` — sueldo

El bono entra en la tubería existente **junto al bono de estudios y antes de la penalización por
fatiga**. Esto es importante y está documentado en el código actual: la fatiga se aplica la última
*a propósito*, porque es un recorte del **sueldo final**, no del base, y así empuja al ciclo diario de
dormir sin anular los bonos que el jugador se ha ganado. Ese orden se respeta:

```
calcularPago(min, max, azar)
  → conBonoEstudios(base, estudios)      // tope +25 %
  → conBonoPasivos(base, bonoSueldo)     // NUEVO, tope +30 %
  → conPenalizacionFatiga(base, fatiga)  // ×0.8, sigue siendo la última
```

`conBonoPasivos` es una **función pura estática** más, testeable sin base de datos, exactamente igual
que sus dos vecinas. Los dos bonos son **multiplicativos entre sí** (`base × 1,25 × 1,30`), no aditivos:
mantiene cada tope independiente y hace que ninguno de los dos sistemas eclipse al otro.

### `TrabajoService` — cooldown

`COOLDOWN_WORK` deja de ser una constante en la comparación y pasa a ser un cálculo:

```java
/** Cooldown efectivo de currar, con el bono de pasivos aplicado. Puro. */
public static Duration cooldownEfectivo(double bono) {
    long segs = Math.round(COOLDOWN_WORK.getSeconds() * (1 - Math.min(TOPE, bono)));
    return Duration.ofSeconds(segs);
}
```

Con el tope del −25 %, el suelo es de **45 minutos**. `COOLDOWN_WORK` sigue existiendo como el valor
base de 60 min; no se toca ni se renombra.

### `XpService` — XP de cualquier fuente

El bono se aplica dentro de `ganarXp(discordId, cantidad)`, es decir **en el único punto por el que
pasa toda la XP del bot**: mensajes, `/daily`, trivia, misiones, victorias de combate, bonus de
mazmorra. No hay que tocar ninguno de los llamantes.

Detalle: `XpService` hoy solo depende de `UsuarioDiscordRepositorio`. Habrá que inyectarle
`PasivoService`, y hacerlo **opcional/nullable** o con un constructor alternativo para no reescribir
los tests existentes de XP que no tienen nada que ver con pasivos.

El redondeo es `Math.round` con **suelo de +1** cuando el bono es positivo y la cantidad base ≥ 1: que
un +20 % sobre 3 XP se redondee a 3 (sin ganancia) se leería como un bug.

### `MineriaService` — cantidad y durabilidad

Dos puntos:

- **Cantidad:** en `tirar(tierPico, nivel)`, `cantidad` pasa a ser
  `Math.min(CANTIDAD_MAX + bonoCantidad, 1 + nivel/25 + azar + bonoCantidad)`. Ojo: **el tope
  `CANTIDAD_MAX = 5` también sube** con el bono, si no el pasivo no haría nada para un minero de nivel
  alto que ya toca el tope, que es justo el que se lo ha comprado.
- **Durabilidad:** antes de `mineria.gastarDurabilidad(...)` se tira `azar.next() < bonoDurabilidad`;
  si sale, **no se gasta**. Se usa el `BatallaService.Aleatorio` que el service ya tiene inyectado, así
  que el test es determinista sin añadir infraestructura.

El resultado de `/minar` menciona cuándo el pico **no** se ha gastado, si no el jugador no se entera de
que su pasivo está funcionando.

### `BatallaService` — combate

Los tres bonos entran en `nuevaSesion(p, monstruo, mundoId)`, en el mismo bloque donde hoy se aplican
los encantamientos:

```java
ataque  += (int) Math.round(bono(COMBATE_ATAQUE));
defensa += (int) Math.round(bono(COMBATE_DEFENSA));
crit     = Math.min(0.9, crit + bono(CRITICO));   // mismo techo duro que el encantamiento CRITICO
```

Orden: **pasivos antes que encantamientos** para el ataque, porque el encantamiento `DANO_PCT`
multiplica y debe multiplicar sobre el ataque completo (que es la lectura intuitiva: «+X % de daño»
significa del daño que haces de verdad). Es una decisión, y hay que dejarla escrita en el código.

Como `nuevaSesion` es un snapshot, esto se resuelve **una vez por pelea o por mazmorra**, no por turno.

### `EnergiaJob` — el caso delicado

Hoy la regeneración es **un solo `UPDATE` masivo** para todos los personajes
(`PersonajeRepositorio.regenerarEnergia(int)`), con un `NOT EXISTS` que salta a los dormidos. Es una
consulta cada 30 minutos para todo el servidor, y esa eficiencia no se negocia: es lo que permite que
el job sea invisible.

El diseño **conserva ese `UPDATE` global tal cual** y añade un **segundo pase** que toca únicamente a
los jugadores que tienen equipado un pasivo de `ENERGIA_REGEN`:

```sql
-- Segundo pase: SOLO los que tienen un pasivo de energía equipado Y el ítem en el inventario.
UPDATE personajes p
  JOIN (SELECT pe.discord_id, SUM(...) AS extra
          FROM pasivos_equipados pe
          JOIN inventario i ON i.discord_id = pe.discord_id
                           AND i.item_id = pe.item_id AND i.cantidad > 0
         WHERE pe.item_id IN (:idsConEnergiaRegen)
         GROUP BY pe.discord_id) b ON b.discord_id = p.discord_id
   SET p.energia = LEAST(100, p.energia + LEAST(:tope, b.extra))
 WHERE p.energia < 100
   AND NOT EXISTS (SELECT 1 FROM descanso d
                    WHERE d.discord_id = p.discord_id AND d.dormido_desde IS NOT NULL);
```

Puntos a respetar:

- El `JOIN` contra `inventario` es lo que aplica aquí la regla de «se recalcula contra el inventario»
  sin traerse nada a memoria.
- Se repite el `NOT EXISTS` de dormidos: un jugador dormido **no** debe recibir tampoco el extra, por
  la misma razón por la que no recibe el goteo base (ya cobra al despertar y sería doble ración).
- La lista `:idsConEnergiaRegen` y las magnitudes salen del catálogo en código: la SQL se **genera**
  desde `Pasivos`, nunca se escriben ids a mano en el SQL.
- **Coste real: una consulta más cada 30 minutos.** No es un rediseño del job.

**Corrección respecto al enunciado del diseño:** `EnergiaJob.REGEN` vale hoy **5**, no 10 — se bajó a
la mitad en la spec de descanso porque el grueso de la energía se gana durmiendo. El tope de +10 del
pasivo es, por tanto, **el triple del goteo base** (5 → 15), no un +100 %. Es un bono potente y hay que
verlo como tal, pero sigue siendo mucho menos que lo que da una noche de sueño (hasta 100 de energía),
así que **no rompe el diseño del descanso**: el pasivo ayuda al que no puede conectarse a dormir, no
sustituye al ciclo.

---

## Errores a manejar

Todos con clave i18n propia en ES y EN, y respuesta **efímera** (son errores, no acciones).

| Caso | Estado | Mensaje (idea) |
|---|---|---|
| El ítem no existe en `Items` | `NO_EXISTE` | «Ese ítem no existe. Mira `/tienda`.» |
| El ítem existe pero no tiene pasivo | `SIN_PASIVO` | «`🍎 Fruta` no da ningún efecto pasivo. Solo el equipo y los vehículos.» + sugerir `/pasivos ver` |
| No lo tiene en el inventario | `NO_TIENE` | «No tienes `🛫 Jet privado`. Cómpralo con `/comprar`.» |
| Ranura fuera de rango (< 1 o > 4) | `RANURA_INVALIDA` | «Las ranuras van de la 1 a la 4.» |
| Ranura bloqueada por nivel | `RANURA_BLOQUEADA` | «La ranura 3 se desbloquea en el nivel 25. Vas por el 18.» |
| El mismo ítem ya está en otra ranura | `YA_EQUIPADO` | «Ya lo tienes en la ranura 2. Un ítem no puede ocupar dos ranuras.» |
| Sin ranura libre y sin `ranura` indicada | `SIN_HUECO` | Lista las 4 ranuras y pide elegir cuál reemplazar |
| `quitar` sobre una ranura ya vacía | `VACIA` | «La ranura 2 ya está vacía.» |

Nota sobre `YA_EQUIPADO`: el service comprueba y responde bonito, pero la garantía dura es el
`UNIQUE (discord_id, item_id)` del esquema. Si la restricción salta igualmente (carrera entre dos
comandos simultáneos), se captura y se devuelve el mismo `YA_EQUIPADO` en vez de un error genérico.

---

## Tests

Estilo del repo: JUnit 5 + Mockito, azar inyectable, funciones puras `public static` que reciben el
`Random`/`Aleatorio`, y barridos de invariantes sobre el catálogo al estilo de `CamasTest` y
`RarezaTest`.

### `PasivosTest` — integridad del catálogo (barridos, sin mocks)

1. **Los 30 ids existen en `Items`** (`Items.porId(id).isPresent()` para todos).
2. **Todos son `EQUIPO` o vehículo `BIEN`**: la categoría es `EQUIPO`, o es `BIEN` **y no está en
   `Camas.CATALOGO`**. Este test es el que impide que alguien le cuelgue un pasivo a una cama por
   error.
3. **Cobertura total**: los 20 `EQUIPO` tienen entrada, y los 10 `BIEN` que no son camas también.
   Es decir, `Pasivos.CATALOGO.size() == 30` **y** el conjunto coincide exactamente. Si mañana se añade
   un `EQUIPO` nuevo a `Items` sin pasivo, este test falla — que es justo lo que se quiere.
4. **Sin ids duplicados.**
5. **Ningún ítem alcanza por sí solo el tope de ningún tipo** (regla de diseño 2, probada).
6. **Todas las magnitudes son > 0** y todos los tipos del enum tienen al menos una fuente (si algún
   día se añade un tipo y se olvida asignarlo, salta).
7. **Monotonía razonable con el precio**: la suma ponderada de bonos de un ítem no es menor que la de
   otro que cueste menos de la mitad. Es un test blando pero pilla las erratas de tecleo (un `0.7`
   donde iba `0.07`).

### `PasivoServiceTest` — lógica (con mocks)

8. **`ranurasDe(nivel)`**: 0→1, 9→1, 10→2, 24→2, 25→3, 49→3, 50→4, 100→4. Los bordes exactos.
9. **Filtrado por inventario**: con dos ranuras equipadas y solo un ítem en el inventario, únicamente
   cuenta uno. Es la prueba del anti-exploit.
10. **Saturación por tipo**: cuatro ítems de sueldo suman 35 % → devuelve exactamente 0,30.
11. **Se topa la suma, no cada bono**: dos ítems de 20 % con tope 30 % dan 30 %, no 40 % ni 20 %.
12. **Un tipo sin ítems equipados devuelve 0,0**, no `null` ni ausencia (para que los llamantes no
    tengan que comprobar nada).
13. **Errores**: uno por cada fila de la tabla de errores.
14. **`equipar` sin ranura** usa la primera libre desbloqueada; con todas llenas devuelve `SIN_HUECO`
    sin modificar nada.

### Añadidos a tests existentes

15. **`TrabajoServiceTest`**: `conBonoPasivos` es pura y se prueba aparte; y un test de **orden de la
    tubería** que verifica que con bono de sueldo **y** fatiga el resultado es
    `round(round(base·1,25)·1,30)·0,8` y no otro orden. Este test es el guardián de la decisión
    documentada sobre la fatiga.
16. **`TrabajoServiceTest`**: `cooldownEfectivo(0.0)` = 60 min; `cooldownEfectivo(0.25)` = 45 min;
    `cooldownEfectivo(0.90)` = 45 min (tope).
17. **`MineriaServiceTest`**: con `MINERIA_CANTIDAD` +2 salen 2 minerales más y el tope sube; con
    `MINERIA_DURABILIDAD` y un `Aleatorio` amañado que devuelve 0,0, **no** se llama a
    `gastarDurabilidad` (verificado con Mockito); con 0,99 sí se llama.
18. **`BatallaServiceTest`**: la sesión nace con el ataque/defensa/crítico ya sumados; y **la sesión no
    cambia** si se modifican los pasivos a mitad de mazmorra (prueba del snapshot).
19. **`XpServiceTest`**: `ganarXp(100)` con +20 % da 120; `ganarXp(3)` con +20 % da 4 (suelo de +1).

### `PasivoRepositorioTest` — Testcontainers

Como el resto de `db/`: MySQL real con las migraciones aplicadas por Flyway. Cubre `equipar` (incluido
el reemplazo por `ON DUPLICATE KEY`), `quitar`, `equipados`, que el `UNIQUE (discord_id, item_id)`
rechaza el duplicado, y que el `ON DELETE CASCADE` borra las filas al borrar el usuario (requisito
RGPD, mismo test que tienen `descanso` y `durabilidad_picos`).

---

## i18n

**Todo** texto visible va a `messages_es.properties` **y** `messages_en.properties`. Claves previstas:

```
# Nombre y descripción de cada uno de los 30 pasivos (60 claves por idioma)
pasivo.jet.nombre        = Jet privado
pasivo.jet.desc          = Vuelas a currar donde haga falta: +11 % de sueldo, −11 % de cooldown y +6 % de XP.
...

# Nombre de cada tipo de bono, para los resúmenes
pasivo.tipo.sueldo       = sueldo
pasivo.tipo.cooldown     = cooldown de trabajo
pasivo.tipo.xp           = XP
pasivo.tipo.energia      = energía por tick
pasivo.tipo.mineria      = minerales por minado
pasivo.tipo.durabilidad  = ahorro de durabilidad
pasivo.tipo.ataque       = ataque
pasivo.tipo.defensa      = defensa
pasivo.tipo.critico      = crítico

# Comando /pasivos: descripciones, opciones, embeds
pasivos.ver.titulo / pasivos.ver.ranura.vacia / pasivos.ver.ranura.bloqueada /
pasivos.ver.ranura.sinitem / pasivos.ver.bonos / pasivos.ver.tope /
pasivos.equipar.ok / pasivos.quitar.ok / ...

# Errores (uno por fila de la tabla de errores)
pasivos.error.noexiste / pasivos.error.sinpasivo / pasivos.error.notiene /
pasivos.error.ranurainvalida / pasivos.error.ranurabloqueada /
pasivos.error.yaequipado / pasivos.error.sinhueco / pasivos.error.vacia

# Perfil
perfil.pasivos.linea
```

La **descripción** de cada pasivo (`pasivo.<id>.desc`) no es decorativa: es lo que hace que
`/pasivos ver` y el autocompletado se expliquen solos sin que el jugador tenga que abrir la doc. Se
escribe en tono **entrenador motivador** (SPEC §6) y dice **qué hace**, con números, no solo el sabor.

El inglés no es traducción literal: *«Fly to work wherever it is: +11 % pay, −11 % cooldown, +6 % XP.»*

---

## Texto de la intro del canal

### En qué canal va, y por qué

Se revisó `SetupServidorPlan.java`. La categoría **▬▬ 🎮 VIDA ▬▬** tiene estos canales:
`📖・cómo-jugar`, `💰・economía`, `⚔️・combate`, `🗺️・mundos`, `📖・bestiario` y `📈・bolsa`.

**No existe ningún canal `🌳・mejoras`.** «🌳 Mejoras» es una **sección dentro** del texto de
`intro.economia.desc` (la que documenta `/mejoras` y `/mejorar`), no un canal. Así que la elección real
es entre `💰・economía` y `⚔️・combate`.

Va en **`💰・economía`**, por tres razones:

1. Ese canal ya documenta **todo lo que se compra y se posee** (`/tienda`, `/comprar`, `/inventario`,
   `/mejoras`, `/minar`, `/cofres`) y los pasivos son exactamente eso: darle sentido a lo que compras.
2. **Siete de los nueve tipos** de bono son de economía (sueldo, cooldown, XP, energía, minería ×2).
   Los dos de combate son marginales por diseño; ponerlo en `⚔️・combate` daría la impresión contraria
   y es justo el malentendido que hay que evitar.
3. El jugador descubre `/pasivos` en el mismo sitio donde ya está mirando qué comprarse.

**Implementación:** se añade una sección nueva **`✨ Pasivos`** al texto de `intro.economia.desc` (ES y
EN), justo después de `🌳 Mejoras` — que es su vecino conceptual: los dos son «mejoras permanentes de
personaje». Además, el `.conTopic(...)` del canal se actualiza para mencionar `/pasivos`.

Como el texto de `intro.economia.desc` es una sola clave larga y ya está cerca de ser un muro, la
sección nueva se escribe **compacta**, en el mismo formato de las demás, y **lo explicativo se lleva a
`/pasivos ver`**, que es donde el jugador tiene delante sus propias ranuras. Una intro que nadie lee
entera no documenta nada.

### Texto propuesto (ES) — sección para `intro.economia.desc`

> **✨ Pasivos**
> `/pasivos ver` · `/pasivos equipar` · `/pasivos quitar`
> Tu equipo y tus vehículos **ya no son solo decoración**: dan bonos permanentes. Equipa lo que tengas
> en tus **ranuras** y el bono se aplica solo, sin gastar el ítem.
> > **🔓 Ranuras:** empiezas con **1**. La 2ª en el **nivel 10**, la 3ª en el **25** y la 4ª en el
> > **50** — las mismas fechas que tus rangos, así que suben juntas.
> > **⚙️ Qué mejoran:** 💼 sueldo · ⏱️ cooldown de currar · ⭐ XP · ⚡ energía · ⛏️ minerales y aguante
> > del pico · ⚔️ ataque, defensa y crítico.
> > **📈 Topes:** cada tipo tiene un techo (p. ej. +30 % de sueldo). Al llegar, `/pasivos ver` te lo
> > marca y la quinta pieza de lo mismo ya no suma: mejor reparte.
> > **⚠️ Ojo:** el bono cuenta **solo si tienes el ítem**. Si lo vendes, lo regalas o lo pones en
> > `/mercado`, la ranura se queda ahí pero **deja de contar** (y `/pasivos ver` te avisa).
> Empieza barato: 🧢 `gorra` (200) o 🎒 `mochila` (400) ya suman. Termina caro: 🛫 `jet`, 🛥️ `yate`,
> 🚀 `cohete`. **Nadie lo tiene todo — elige tu build.**

### Texto propuesto (EN) — sección para `intro.economia.desc`

> **✨ Passives**
> `/pasivos ver` · `/pasivos equipar` · `/pasivos quitar`
> Your gear and vehicles are **not just for show** any more: they grant permanent bonuses. Slot what
> you own and the bonus applies on its own — the item is never consumed.
> > **🔓 Slots:** you start with **1**. The 2nd at **level 10**, the 3rd at **25**, the 4th at **50** —
> > same milestones as your ranks, so they unlock together.
> > **⚙️ What they boost:** 💼 pay · ⏱️ work cooldown · ⭐ XP · ⚡ energy · ⛏️ minerals and pick
> > durability · ⚔️ attack, defence and crits.
> > **📈 Caps:** every type has a ceiling (e.g. +30 % pay). Once you hit it, `/pasivos ver` says so and
> > a fifth piece of the same kind adds nothing — spread them out.
> > **⚠️ Heads up:** a bonus only counts **while you own the item**. Sell it, gift it or list it on
> > `/mercado` and the slot stays but **stops counting** (`/pasivos ver` flags it).
> Start cheap: 🧢 `gorra` (200) or 🎒 `mochila` (400) already help. Finish rich: 🛫 `jet`, 🛥️ `yate`,
> 🚀 `cohete`. **Nobody gets everything — pick your build.**

### Y la guía larga, ¿dónde?

Lo que no cabe en la intro (la tabla completa de los 30 ítems y sus bonos) **lo enseña el propio bot**:
`/pasivos ver` lista los bonos activos, y el autocompletado de `/pasivos equipar` muestra
`pasivo.<id>.desc` de cada ítem que el jugador posee. No hace falta un canal nuevo ni un mensaje
maestro: el sistema se documenta desde dentro, que es lo que ya hacen `/mejoras`, `/cofres` y
`/recetas`.

---

## Orden de implementación

Siguiendo el orden estándar del proyecto:

1. **Migración** `V25__pasivos_equipados.sql`.
2. **Catálogo** `services/Pasivos.java` + `PasivosTest` (los 7 barridos de integridad). El catálogo se
   valida antes de que exista nada que lo use.
3. **Repositorio** `db/PasivoRepositorio.java` + `PasivoRepositorioTest` (Testcontainers).
4. **Service** `services/PasivoService.java` + `PasivoServiceTest`.
5. **Integraciones**, una por una y con su test: `TrabajoService` (sueldo y cooldown) → `XpService` →
   `MineriaService` → `BatallaService` → `EnergiaJob`.
6. **Comandos** `commands/economia/PasivosComando.java` + registro en JDA + línea nueva en
   `/perfil ver`.
7. **i18n** ES + EN (los 30 nombres, las 30 descripciones, comandos y errores).
8. **Docs**: `docs/architecture.md`, `CHANGELOG.md`, tabla de comandos del README,
   `src/main/java/com/gymprofit/bot/services/README.md`, y ADR en `docs/decisions.md` si se decide
   dejar registrada la decisión del catálogo paralelo + ranuras.

---

## Despliegue

| Qué | ¿Hace falta? | Por qué |
|---|---|---|
| **Reiniciar el bot** | **Sí** | Registra el comando nuevo `/pasivos` en JDA y aplica la migración V25 al arrancar (Flyway) |
| **`/setup`** | **Sí** | Reescribe la intro de `💰・economía` con la sección `✨ Pasivos` y actualiza el topic del canal |
| **`/setup desde_cero`** | **No** | No hay canales, categorías ni roles nuevos. Solo cambia el texto de una intro existente |

Smoke test manual en el **servidor de pruebas** (bot y token de test, nunca producción), en este orden:

1. `/pasivos ver` sin nada equipado → 1 ranura libre, 3 con 🔒 y sus niveles.
2. `/comprar` una `gorra` → `/pasivos equipar gorra` → `/pasivos ver` muestra `+2 % XP`.
3. `/inventario vender gorra` → `/pasivos ver` marca la ranura con ⚠️ y **el bono desaparece**.
4. `/pasivos equipar` un ítem sin pasivo (p. ej. `fruta`) → error `SIN_PASIVO`.
5. Equipar el mismo ítem en dos ranuras → error `YA_EQUIPADO`.
6. Con `SUELDO` equipado: `/trabajo currar` y comprobar que el pago sube y que el cooldown baja.
7. `/perfil ver` muestra la línea de pasivos.

Hasta que esos siete pasos se ejecuten contra Discord en vivo, el módulo queda marcado como
**«pendiente de smoke test manual»**.

---

## Discrepancias encontradas contra el diseño de partida

Se dejan escritas para que quien implemente no se sorprenda:

1. **No existe el canal `🌳・mejoras`.** «🌳 Mejoras» es una sección dentro de `intro.economia.desc`.
   La intro va a **`💰・economía`** (justificado arriba).
2. **`EnergiaJob.REGEN` vale 5, no 10.** Se bajó a la mitad en la spec de descanso (2026-07-15). El
   tope de `ENERGIA_REGEN` (+10) triplica el goteo base, no lo duplica. Se mantiene el +10 porque
   sigue siendo mucho menos que una noche de sueño, pero conviene saberlo.
3. **`BatallaService.nuevaSesion` es `private`.** El snapshot existe y es el sitio correcto, pero para
   testear los bonos de combate habrá que exponer una función pura auxiliar o inyectar el
   `PasivoService` en el constructor del service (que ya recibe un `Aleatorio` inyectable, así que el
   patrón está).
4. **El total de comandos de primer nivel (55) viene de ADR-011** y el módulo de consultas a la API
   está en ejecución ahora mismo, así que puede haber subido. Hay que recontar al implementar; el
   margen frente a 100 es amplio en cualquier caso.
