# Ascensos de carrera — diseño

**Fecha:** 2026-07-22 · **Estado:** aprobado en conversación, pendiente de plan
**Módulo:** RPG económico (extra deferido de F-ECO-3, ver `gymprobot-rpg-nucleo`)

## Contexto y objetivo

Hoy `/trabajo elegir` permite saltar a cualquier puesto del catálogo con solo cumplir su
`requisitoNivel` de servidor: un nivel 30 pasa de no trabajar a CEO en un comando. No existe
noción de carrera ni de progresión dentro de un sector, y los estudios solo dan un +1 % al sueldo.

**Objetivo:** que el tier se gane currando. Ascender es una decisión con requisitos (antigüedad,
estudios, stats, coste) y una celebración pública. Los coins del ascenso se **queman**: sumidero
antiinflación nuevo (principio rector del RPG).

## Decisiones tomadas (usuario, no reabrir)

1. **Modelo: carrera por rama.** El tier deja de ser de libre acceso; se progresa dentro de una
   rama de carrera. Cambiar de rama = entrar por abajo en la nueva.
2. **Requisitos de ascenso: los cuatro** — antigüedad (turnos currados), estudios mínimos, stat
   mínima según la rama y coste en coins (quemados).
3. **La carrera se conserva por rama**: si dejas una rama y vuelves, reentras directo en el tier
   que habías alcanzado. La antigüedad del puesto en curso se resetea con cualquier cambio.
4. **El jugador elige el puesto** del tier siguiente al ascender (autocompletado), no es
   automático.
5. **Migración limpia**: no hay jugadores reales aún; la migración pone el trabajo elegido a NULL
   y nadie hereda nada.
6. **Ramas satélite**: los 26 sectores del catálogo se agrupan en ramas en un catálogo paralelo
   `Ascensos` (precedente: `Pasivos`, `Camas`, `Picos`, `Cofres`). **`Trabajos.java` no se toca.**

## Modelo

- Cada puesto pertenece (vía su `sector`) a una **rama** de carrera definida en `Ascensos`.
- Por usuario y rama se guarda `tier_alcanzado`.
- **Elegir**: `/trabajo elegir` solo permite puestos cuyo `tier ≤ tier alcanzado en su rama`.
  El tier de **entrada** de cada rama (el más bajo que exista en ella) es siempre elegible.
- **Ascender**: desde tu puesto actual, al **siguiente tier existente de tu rama** (si la rama no
  tiene t2, de t1 se salta a t3). Requisitos: los del **tier destino**. Algunas ramas **topan**
  por debajo de t4: su carrera acaba ahí, y `/trabajo carrera` lo dice.
- **Antigüedad**: contador `turnos_puesto` que suma 1 por cada `/trabajo currar` con éxito y se
  resetea al cambiar de puesto (elegir o ascender).
- El nivel de servidor (`requisitoNivel` del catálogo) **sigue aplicando** además de todo esto.

## Ramas (catálogo `Ascensos`, en código)

La stat es la dominante de la rama; el mapeo cubre los ~50 puestos vía su campo `sector`.
Regla de integridad (test): **todo sector del catálogo pertenece a exactamente una rama.**

| Rama | Sectores | Stat | Recorrido real |
|---|---|---|---|
| 🩺 Salud y ciencia | Sanidad, Ciencia, Deporte | resistencia | t1 socorrista → t4 cirujano/astronauta |
| 🔧 Técnica | Tecnología, Oficios, Automoción, Construcción, Agricultura | fuerza | t1 jardinero → t3 ingeniero/arquitecto |
| 🚚 Transporte | Transporte, Logística, Aviación | fuerza | t1 repartidor → t3 piloto |
| 🍳 Hostelería y comercio | Hostelería, Comercio, Pesca, Belleza | carisma | t1 camarero → t2 cocinero |
| 💼 Negocios | Negocios, Finanzas, Derecho, Educación, Atención | carisma | t1 teleoperador → t4 ceo/juez |
| 🎬 Arte y medios | Arte, Medios, Entretenimiento | carisma | t2 diseñador → t3 actor |
| 🧹 Servicios públicos | Servicios, Seguridad, Emergencias | resistencia | t1 barrendero → t2 policía/bombero |

> El recorrido exacto por tier lo dicta el catálogo `Trabajos` en tiempo de ejecución; la tabla es
> orientativa. Ramas sin t1 (Arte y medios) entran por su tier más bajo. Ramas que topan en t2/t3
> son cortas a propósito: el catálogo podrá crecer por rama más adelante sin tocar este diseño.

## Requisitos por tier destino (catálogo `Ascensos`)

| Salto a | Turnos en el puesto | Estudios | Stat de la rama | Coins (se queman) |
|---|---|---|---|---|
| t2 | 10 | 5 | 10 | 500 |
| t3 | 25 | 15 | 25 | 5 000 |
| t4 | 50 | 30 | 40 | 50 000 |

Los importes siguen la escala económica lenta (daily 25-70, work 30→1000). El cobro es atómico
contra el monedero (`UPDATE ... WHERE saldo >= importe`) y va al ledger `transacciones` como gasto
sin contrapartida (quemado).

## Datos (migración V26)

- Tabla **`carreras`**: `discord_id BIGINT`, `rama VARCHAR`, `tier_alcanzado TINYINT`,
  PK (`discord_id`, `rama`), FK a `usuarios_discord` **ON DELETE CASCADE** (RGPD cubierto, como
  `pasivos_equipados`). Sin fila = tier de entrada de la rama.
- Columna **`turnos_puesto INT NOT NULL DEFAULT 0`** en la tabla que hoy guarda el trabajo
  elegido y su cooldown (verificar nombre real en V7).
- La migración pone el **trabajo elegido a NULL** para todos (borrón y cuenta nueva acordado).

## Comandos (0 top-level nuevos; `/trabajo` pasa de 3 a 5 subcomandos)

- **`/trabajo ascender puesto:<opción>`** — autocompletado con los puestos del siguiente tier
  existente de tu rama (mismo patrón `ComandoAutocompletable` que `/pasivos`). Errores efímeros
  específicos: sin trabajo actual, ya en el tope de la rama, y uno por requisito incumplido con
  cuánto falta. Éxito: **embed público de celebración** (personalidad casual) + actualización del
  rol `💼 <Trabajo>` (mecanismo existente de elegir).
- **`/trabajo carrera`** — tu rama, tier alcanzado, puesto actual, turnos en el puesto y una
  checklist visual de los requisitos del siguiente salto (✅/❌ con progreso). Pública.
- **`/trabajo lista`** — los puestos de tier superior al alcanzado de su rama salen con 🔒 y el
  motivo corto. **`/trabajo elegir`** los rechaza con error efímero que apunta a `ascender`.
- Convenciones: i18n ES+EN, EmbedFactory, BD tras `deferReply`, acciones económicas públicas.

## Implementación

- **`Ascensos`** (satélite en `services/`): ramas, mapeo sector→rama, stat por rama, requisitos
  por tier, helpers (`ramaDe(sector)`, `siguienteTier(rama, tierActual)`, `requisitosPara(tier)`).
- **`CarreraRepositorio`** (`db/`): `tierAlcanzado(discordId, rama)`, `ascender(...)` (upsert),
  más el contador de turnos junto al estado de trabajo existente.
- **Lógica en `TrabajoService`** (dueño actual de elegir/currar): gate de `elegir`, incremento del
  contador en `currar`, `ascender` con validación de los 4 requisitos + cobro atómico. Solo se
  crea un service nuevo si `TrabajoService` crece de forma desproporcionada.
- **Tests**: unitarios de `Ascensos` (integridad del mapeo: todo sector cubierto exactamente una
  vez; requisitos monótonos por tier) y de `TrabajoService.ascender` (cada requisito que falla,
  el cobro, el tope de rama, el salto de tier hueco); Testcontainers para V26 + repositorio.

## No-objetivos

- Rango interno por puesto (junior/senior) — descartado al elegir modelo.
- Decaimiento de carrera al cambiar de rama — descartado.
- Ascensos automáticos — descartado; siempre acción del jugador.
- Reequilibrar sueldos del catálogo: no hace falta, los sueldos ya escalan por tier.

## Despliegue

Reiniciar el bot **y `/setup`** si se toca la intro de `💰・economía` (añadir mención del ascenso
en la sección de trabajo es deseable y entra en la última tarea del plan). Los subcomandos nuevos
requieren reinicio (registro `updateCommands` en `onGuildReady`).
