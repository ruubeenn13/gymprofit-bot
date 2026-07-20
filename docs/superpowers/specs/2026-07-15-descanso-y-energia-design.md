# Descanso y energía — diseño

**Fecha:** 2026-07-15 · **Estado:** aprobado, pendiente de plan de implementación
**Relacionado:** [`2026-07-13-economia-rpg-vision.md`](2026-07-13-economia-rpg-vision.md) (spec estrella
polar del RPG) · ADR-010 (anti-inflación) · ADR-011 (subcomandos)

## Problema

La energía es el **freno de ritmo** del RPG: trabajar, entrenar, pelear y minar la gastan, y hoy vuelve
sola con `EnergiaJob` (+10 cada 30 min, recarga total ~5 h). Eso tiene tres pegas:

1. **El jugador no participa.** La energía sube sin que hagas nada. En un simulador de vida, descansar
   debería ser algo que *haces*.
2. **Los bienes no sirven para nada.** `piso`, `casa`, `mansion`… se compran (25 000 a 1 500 000 coins) y
   no tienen ningún efecto. Son un sumidero de coins y nada más.
3. **El freno tiene un agujero.** Los consumibles de energía no están limitados: con coins suficientes
   encadenas 20 cafés y te saltas el límite de energía entero.

## Decisiones tomadas

| Decisión | Elegido | Por qué |
|---|---|---|
| Modelo de dormir | **Estado con despertar**, no cooldown | Idea del usuario: te acuestas y al despertar recuperas **según las horas dormidas de verdad**. La siesta sale sola (dormir poco = ganar poco), sin comando aparte |
| Ritmo global | **Job a +5/30 min** | Dormir cubre la diferencia: el total diario se parece al de hoy pero participando. Más energía/día = más acciones = más coins = inflación (ADR-010) |
| Acciones mientras duermes | **Bloqueadas** | Es el coste real de dormir: cuesta tiempo de juego. Sin esto, dormir es energía gratis |
| Al intentar actuar dormido | **Embed + 2 botones** | Elección del usuario: «Seguir durmiendo» / «Despertar». No se despierta solo: perder el descanso sin querer da rabia |
| `/vacaciones` | **Fuera** | «No puedes jugar 8 h» en un bot de Discord no lo usa nadie por gusto |
| Escalado con las stats | **La resistencia da bono al descanso** (+1 %/punto, tope +50 %) | Las stats crecen sin techo: con números fijos el descanso se quedaría plano al progresar. El tope de la cama sigue mandando, así que acelera sin romper el equilibrio |

## Mecánicas

### 1. Dormir y despertar (núcleo)

`/descansar dormir` acuesta al personaje: guarda el instante y entra en estado dormido.
`/descansar despertar` lo levanta y le da energía **proporcional al tiempo dormido**, según dónde duerma.

- **Tope de 9 h.** Más no suma: dormir de más no descansa. Evita además que alguien deje el personaje
  dormido una semana y despierte con energía absurda.
- **Redondeo por minutos**, no por horas: una siesta de 20 min da su parte proporcional. Es lo que hace
  que la siesta no necesite comando propio.

### 2. Calidad de cama (da uso a los bienes)

La cama sale del **inventario**, sin columna nueva: mismo patrón que el pico en `/minar`, que elige el
mejor que tengas. Se usa siempre la mejor cama disponible.

| Sitio | Energía/h | Tope | Cómo se consigue |
|---|---|---|---|
| Suelo | 10/h | 60 | Por defecto, sin nada |
| Saco de dormir | 15/h | 75 | Ítem nuevo, ~150 coins |
| Colchón | 20/h | 85 | Ítem nuevo, ~600 coins |
| Hotel | 25/h | 100 | `/descansar dormir sitio:hotel` — 200 coins **por noche** |
| Piso / apartamento | 25/h | 95 | Bienes ya existentes (25 000 / 40 000) |
| Casa o mejor | 30/h | 100 | Bienes ya existentes (`casa` 80 000, `chalet`, `mansion`, `castillo`, `isla`, `rascacielos`) |

El **tope** es la clave del diseño: en el suelo no llegas a 100 aunque duermas las 9 h. Da una
progresión clara (suelo → saco → colchón → vivienda) y hace que el hotel tenga sentido como puente
mientras ahorras: es asequible pero se paga **cada noche**, así que a la larga sale mejor comprar.

Las viviendas por encima de `casa` no dan más energía (ya está el tope en 100): siguen siendo estatus.

### 3. Fatiga

Más de **24 h sin dormir**: la regen pasiva del job baja a la mitad y el sueldo de `/trabajo currar`
cae un **20 %**. Se quita durmiendo. Empuja al ciclo diario sin castigar de más.

### 4. Saciedad

Máximo **3 consumibles al día** (día natural, Europe/Madrid, como `/daily` y el interés del banco).
Cierra el agujero de comprar energía sin límite. El cuarto consumible del día responde «estás lleno».

### 5. Dormir mal por salud baja

Con **salud < 30**, la energía por hora se reduce al **50 %**. Estás malo y descansas peor. Conecta el
descanso con la salud, que hoy solo la tocan las derrotas en combate (−20) y los consumibles.

### 6. La resistencia mejora el descanso (escalado con las stats)

Las stats del jugador **crecen sin techo** (`/entrenar`, `/mejorar`), así que un descanso de números
fijos se quedaría plano según se progresa. La **resistencia** da un bono a la energía por hora:
**+1 % por punto, con tope +50 %** — el mismo patrón que `TrabajoService.conBonoEstudios` (+1 %/punto,
tope 25 %) y que `CombateService.probCritico` (escala con la stat pero con techo).

Es realista (mejor forma física, mejor descanso) y le da a `/entrenar resistencia` un uso fuera del
combate, que hoy es el único sitio donde importa.

**El bono no rompe el tope de la cama:** con 40 de resistencia duermes un 40 % más rápido, pero en el
suelo sigues sin pasar de 60. Acelera, no revienta el equilibrio — el tope sigue siendo lo que empuja
a comprar vivienda.

### 7. Job a +5/30 min

`EnergiaJob.REGEN` pasa de 10 a 5. **Mientras duermes el job no te aplica**: el descanso ya lo cubre y
si no, se contaría dos veces.

## Modelo de datos

**Migración Flyway V23**, tabla nueva `descanso` — separada de `personajes`, siguiendo el precedente de
`mineria`: `Personaje` ya tiene 14 campos y engordarlo obliga a tocar el record, el repositorio y los
cinco constructores de los tests.

```sql
CREATE TABLE descanso (
  discord_id       BIGINT      NOT NULL PRIMARY KEY,
  dormido_desde    DATETIME    NULL,      -- NULL = despierto
  ultimo_despertar DATETIME    NULL,      -- para la fatiga (>24 h)
  consumidos_hoy   INT         NOT NULL DEFAULT 0,
  dia_consumos     DATE        NULL,      -- día natural del contador de saciedad
  CONSTRAINT fk_descanso_usuario FOREIGN KEY (discord_id)
    REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
);
```

Sin fila = despierto y sin fatiga (se crea con `obtenerOCrear`, como `MineriaRepositorio`).

**RGPD:** la tabla va a `PrivacidadService` (exportar y borrar) — es dato del usuario, aunque no sea
personal. El `ON DELETE CASCADE` cubre el borrado, pero el export debe incluirla explícitamente.

## Componentes

| Pieza | Papel |
|---|---|
| `Camas` (catálogo, código) | itemId → energía/h + tope. Incluye suelo y hotel como entradas virtuales. `mejorDe(inventario)` |
| `DescansoEstado` (record) + `DescansoRepositorio` | Acceso a la tabla: `obtenerOCrear`, `acostar`, `levantar`, contador de saciedad |
| `DescansoService` | Toda la lógica. El instante llega **como parámetro** (`Instant ahora`), igual que `TrabajoService.trabajar`: así los tests fijan el reloj sin esperar 3 h |
| `DescansoComando` | `/descansar` [dormir · despertar · estado] |
| `DescansoListener` | Botones del embed de dormido |
| `Items` | +2 ítems (`saco_dormir`, `colchon`) |

`DescansoService.energiaGanada(minutos, cama, salud, energiaActual, resistencia)` es **puro** y estático: el corazón testeable
sin BD ni JDA.

## Bloqueo de acciones

`TrabajoService`, `BatallaService` y `MineriaService` comprueban al entrar si el jugador está dormido y
devuelven un estado nuevo `DORMIDO`. Los comandos que lo reciben responden con el embed de dormido.

**Embed «😴 Estás dormido»** con dos botones:

- **Seguir durmiendo** → cierra el mensaje. Sigue dormido, no pierde nada.
- **Despertar** → ejecuta el mismo despertar que `/descansar despertar`, muestra la energía ganada y le
  dice que ya puede actuar. **No reintenta la acción original** (no se guarda la intención pendiente):
  el jugador repite el comando. Simple y previsible.

`customId`: `descanso:seguir:<ownerId>` y `descanso:despertar:<ownerId>`, con guard de dueño, como
`CombateListener` y `TruequeListener`.

## Comando

`/descansar` con subcomandos (ADR-011: familias en un solo comando; 55 → **56** de nivel superior):

| Subcomando | Qué hace |
|---|---|
| `dormir [sitio]` | Te acuesta. `sitio: propio` (defecto, usa tu mejor cama) o `hotel` (200 coins). Se llama «propio» y no «casa» para no confundirlo con el bien `casa` |
| `despertar` | Te levanta y te da la energía del descanso |
| `estado` | Cuánto llevas dormido, tu cama actual y si tienes fatiga |

**Los tres públicos.** Nada de esto es sensible y se juega a la vista de todos, como el resto de la
economía. Efímeros solo los errores («ya estás dormido», «no estabas durmiendo») y el embed de
bloqueo, que es ruido.

## Tests

`DescansoServiceTest` (JUnit 5 + Mockito, reloj fijo):

- `energiaGanada` es proporcional a los minutos dormidos.
- Tope de 9 h: dormir 12 h da lo mismo que 9 h.
- Tope por cama: en el suelo no se pasa de 60 aunque duerma las 9 h.
- Salud < 30 → la mitad de energía.
- Dormir en hotel cobra 200 y falla con `SIN_SALDO` si no llega.
- Despertar sin estar dormido → `NO_DORMIDO`.
- Fatiga: >24 h sin dormir la marca; dormir la quita.
- Saciedad: el cuarto consumible del día se rechaza; cambia el día → se reinicia.
- Integridad del catálogo `Camas`: todos los itemId existen en `Items`.

`ItemServiceTest` amplía: usar un consumible respeta la saciedad.

## Fuera de alcance

- **`/vacaciones`** — descartado.
- **Auto-despertar por alarma** (`/dormir horas:2`) — necesitaría un job por jugador. Si se pide luego,
  se valora.
- **Reintentar la acción original** tras despertar desde el botón.
- Efectos pasivos del resto de bienes (coche, yate…) — sigue en el backlog aparte.

## Orden de implementación

1. **V23** + `DescansoEstado` + `DescansoRepositorio` + `Camas` + los 2 ítems.
2. `DescansoService` (dormir, despertar, hotel, salud baja) + tests. Núcleo puro primero.
3. `/descansar` + `DescansoListener` + i18n ES/EN.
4. Bloqueo `DORMIDO` en trabajo, combate y minería + embed con botones.
5. Fatiga (toca `TrabajoService` y `EnergiaJob`) + job a +5.
6. Saciedad (toca `ItemService`).
7. `PrivacidadService`: exportar la tabla nueva.
8. Intros de `/setup` (`intro.economia`, `intro.simulador`), README ES/EN, CHANGELOG, ADR si procede.

Cada paso deja `./mvnw verify` en verde.
