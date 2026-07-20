# Descanso y energía — plan de implementación

> ## ESTADO (2026-07-15, fin de sesión)
>
> **Tasks 1-8 HECHAS.** `./mvnw verify` verde: **283 tests, 0 fallos**, 3 skipped (Testcontainers, sin
> Docker local; corren en CI). **NADA COMMITEADO**: todo en el árbol de trabajo.
>
> **Queda:** Task 9 (saciedad) · Task 10 (RGPD export) · Task 11 (docs) · **revisar las Tasks 4-8**
> (solo se revisaron formalmente la 1 y la 2) · **smoke test en vivo** (nada probado en Discord).
>
> **Al retomar:** un subagente por tarea, de uno en uno. Dile siempre: **no commitear**, **no tocar
> `pom.xml`**, **JUnit puro** (no AssertJ) y **desconfiar de este plan y verificar contra el código**.
>
> **Este plan traía 5 errores**, ya corregidos aquí pero que avisan del nivel de confianza: AssertJ
> (dependencia inexistente en el repo), `inventario()`→`listar()`, el bloqueo iba en `CombateListener`
> y no en `PelearComando`, `mod.nopuedes`→`descansar.noestuyo`, y la V23 sin la convención del repo.
>
> **Despliegue al acabar:** reiniciar el bot (registra `/descansar` y aplica V23) + `/setup` normal
> (refresca intros). No hace falta `desde_cero`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **NO COMMITEAR.** El usuario lo ha pedido explícitamente (2026-07-15): los pasos «Commit» de cada
> tarea quedan **anulados**. Deja los cambios en el árbol de trabajo y nada más. No ejecutes `git add`,
> `git commit` ni `git stash`.

**Goal:** Que la energía se recupere **durmiendo** (estado con despertar, proporcional al tiempo real dormido y a la cama que tengas) en vez de subir sola, dando de paso un uso a los bienes de vivienda.

**Architecture:** Tabla `descanso` separada de `personajes` (patrón de `mineria`), catálogo `Camas` en código, `DescansoService` con el cálculo puro y estático, `/descansar` con subcomandos (ADR-011) y un listener para los botones del embed de bloqueo. El instante se pasa **como parámetro** (`Instant ahora`), igual que `TrabajoService.trabajar`: así los tests fijan el reloj sin esperar.

**Tech Stack:** Java 21, JDA 5, Maven, Flyway (siguiente libre: **V23**), JUnit 5 + Mockito, i18n `messages_es/en.properties`, `EmbedFactory`.

**Spec:** [`../specs/2026-07-15-descanso-y-energia-design.md`](../specs/2026-07-15-descanso-y-energia-design.md)

**Build:** `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify` (JDK 21; el `java` por defecto es 8). Los 3 tests de Testcontainers se saltan en local sin Docker y corren en CI.

---

## Estructura de ficheros

| Fichero | Responsabilidad |
|---|---|
| `src/main/resources/db/migration/V23__descanso.sql` | Tabla `descanso` |
| `db/DescansoEstado.java` | Record de la fila |
| `db/DescansoRepositorio.java` | JDBC: obtenerOCrear, acostar, levantar, saciedad |
| `db/PersonajeRepositorio.java` | **Modificar**: `sumarEnergiaConTope` |
| `services/Camas.java` | Catálogo cama → energía/h + tope |
| `services/DescansoService.java` | Lógica: dormir, despertar, estado, fatiga, cálculo puro |
| `services/Items.java` | **Modificar**: +`saco_dormir`, +`colchon` |
| `services/ItemService.java` | **Modificar**: saciedad en `usar` |
| `services/TrabajoService.java` | **Modificar**: bloqueo `DORMIDO` + penalización por fatiga |
| `services/BatallaService.java` | **Modificar**: bloqueo `DORMIDO` |
| `services/MineriaService.java` | **Modificar**: bloqueo `DORMIDO` |
| `services/PrivacidadService.java` | **Modificar**: exportar `descanso` |
| `jobs/EnergiaJob.java` | **Modificar**: `REGEN` 10 → 5, saltar dormidos |
| `commands/economia/DescansoComando.java` | `/descansar` [dormir·despertar·estado] |
| `events/DescansoListener.java` | Botones del embed de bloqueo |
| `Main.java` | **Modificar**: wiring |

---

## Task 1: Migración V23 + repositorio — ✅ HECHA

**Files:**
- Create: `src/main/resources/db/migration/V23__descanso.sql`
- Create: `src/main/java/com/gymprofit/bot/db/DescansoEstado.java`
- Create: `src/main/java/com/gymprofit/bot/db/DescansoRepositorio.java`
- Modify: `src/test/java/com/gymprofit/bot/db/MigracionesTest.java` (si enumera migraciones)

- [ ] **Step 1: Crear la migración**

**Antes de escribirla, abre `V22__bolsa.sql` y copia su molde**: banner `-- ---…` con título y el
porqué, **`COMMENT '…'` inline en cada columna** (los `--` sueltos no llegan al esquema real; los
`COMMENT` sí), `PRIMARY KEY` a nivel de tabla, indentación de 4, `) COMMENT '…';` al cerrar, y **sin**
`ENGINE`/`CHARSET`/`COLLATE` (se heredan de la BD). Tipos: `TIMESTAMP` para los instantes — es lo que
usan las otras 13 migraciones para este mismo caso (`mineria.ultimo_minado` también es un `Instant`).

`src/main/resources/db/migration/V23__descanso.sql` — estructura (rellenar los COMMENT siguiendo V22):

```sql
CREATE TABLE descanso (
    discord_id       BIGINT      NOT NULL COMMENT 'Jugador (snowflake de Discord)',
    dormido_desde    TIMESTAMP   NULL     COMMENT 'Instante en que se acostó; NULL = despierto',
    ultimo_despertar TIMESTAMP   NULL     COMMENT 'Último despertar; base de la fatiga (>24 h)',
    consumidos_hoy   INT         NOT NULL DEFAULT 0 COMMENT 'Consumibles usados hoy (saciedad)',
    dia_consumos     DATE        NULL     COMMENT 'Día natural al que pertenece consumidos_hoy',
    cama_pagada      VARCHAR(16) NULL     COMMENT 'Cama pagada al acostarse (hotel); NULL = la suya',
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_descanso_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Estado de descanso: dormir es un estado y al despertar se gana energía';
```

**Ojo:** el DDL **no se ejecuta en local** (sin Docker, `MigracionesTest` se salta con `assumeTrue`):
compilar no prueba ni una línea de este SQL, y la validación real llega en CI. Repásalo a ojo contra
V22.

- [ ] **Step 2: Crear el record**

`src/main/java/com/gymprofit/bot/db/DescansoEstado.java`:

```java
package com.gymprofit.bot.db;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Estado de descanso de un jugador (tabla {@code descanso}, 1:1 con {@code usuarios_discord}).
 *
 * @param discordId       jugador (snowflake)
 * @param dormidoDesde    instante en que se acostó, o {@code null} si está despierto
 * @param ultimoDespertar último despertar, o {@code null} si nunca durmió (para la fatiga)
 * @param consumidosHoy   consumibles usados en {@code diaConsumos} (saciedad)
 * @param diaConsumos     día natural al que pertenece {@code consumidosHoy}, o {@code null}
 * @param camaPagada      cama pagada al acostarse ({@code "hotel"}), o {@code null} si duerme en la
 *                        suya. El hotel no se posee, así que no puede salir del inventario al
 *                        despertar: hay que recordarlo
 */
public record DescansoEstado(long discordId, Instant dormidoDesde, Instant ultimoDespertar,
                             int consumidosHoy, LocalDate diaConsumos, String camaPagada) {

    /** {@code true} si el jugador está dormido ahora mismo. */
    public boolean dormido() {
        return dormidoDesde != null;
    }
}
```

- [ ] **Step 3: Crear el repositorio**

`src/main/java/com/gymprofit/bot/db/DescansoRepositorio.java`:

```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Repositorio JDBC del estado de descanso ({@code descanso}). {@link #obtenerOCrear} garantiza la
 * fila (requiere que exista antes {@code usuarios_discord}, por la FK). Sin fila = despierto y sin
 * fatiga.
 */
public final class DescansoRepositorio {

    private final DataSource dataSource;

    public DescansoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Estado de descanso, creándolo con valores por defecto si no existe. */
    public DescansoEstado obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO descanso (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el descanso de " + discordId, e);
        }
        String sql = "SELECT discord_id, dormido_desde, ultimo_despertar, consumidos_hoy, "
                + "dia_consumos, cama_pagada FROM descanso WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("El descanso " + discordId
                            + " no existe tras crearlo", null);
                }
                return mapear(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando el descanso de " + discordId, e);
        }
    }

    private static DescansoEstado mapear(ResultSet rs) throws SQLException {
        Timestamp dd = rs.getTimestamp("dormido_desde");
        Timestamp ud = rs.getTimestamp("ultimo_despertar");
        Date dc = rs.getDate("dia_consumos");
        return new DescansoEstado(rs.getLong("discord_id"),
                dd == null ? null : dd.toInstant(),
                ud == null ? null : ud.toInstant(),
                rs.getInt("consumidos_hoy"),
                dc == null ? null : dc.toLocalDate(),
                rs.getString("cama_pagada"));
    }

    /**
     * Acuesta al jugador: marca {@code dormido_desde} y, si pagó, la cama.
     *
     * @param camaPagada {@code "hotel"} si pagó por dormir, o {@code null} para usar la suya
     */
    public void acostar(long discordId, Instant ahora, String camaPagada) {
        ejecutar("UPDATE descanso SET dormido_desde = ?, cama_pagada = ? WHERE discord_id = ?",
                ps -> {
                    ps.setTimestamp(1, Timestamp.from(ahora));
                    ps.setString(2, camaPagada);
                    ps.setLong(3, discordId);
                }, "acostando a " + discordId);
    }

    /**
     * Levanta al jugador: borra {@code dormido_desde}, sella el despertar (para la fatiga) y limpia
     * la cama pagada, que solo vale para esa noche.
     */
    public void levantar(long discordId, Instant ahora) {
        ejecutar("UPDATE descanso SET dormido_desde = NULL, cama_pagada = NULL, "
                + "ultimo_despertar = ? WHERE discord_id = ?", ps -> {
            ps.setTimestamp(1, Timestamp.from(ahora));
            ps.setLong(2, discordId);
        }, "levantando a " + discordId);
    }

    /**
     * Registra un consumible del día (saciedad). Si {@code dia_consumos} no es hoy, reinicia el
     * contador a 1 y fija el día; si ya es hoy, incrementa. Atómico en una sola sentencia.
     *
     * <p><b>El orden de los SET importa:</b> MySQL evalúa las asignaciones de izquierda a derecha,
     * así que {@code consumidos_hoy} se asigna primero y su {@code CASE} lee el {@code dia_consumos}
     * <b>viejo</b>. Si se intercambian las dos cláusulas, el {@code CASE} siempre daría TRUE y el
     * contador no se reiniciaría nunca al cambiar de día — y ningún test lo detectaría.
     */
    public void registrarConsumo(long discordId, LocalDate hoy) {
        ejecutar("UPDATE descanso SET "
                + "consumidos_hoy = CASE WHEN dia_consumos = ? THEN consumidos_hoy + 1 ELSE 1 END, "
                + "dia_consumos = ? WHERE discord_id = ?", ps -> {
            ps.setDate(1, Date.valueOf(hoy));
            ps.setDate(2, Date.valueOf(hoy));
            ps.setLong(3, discordId);
        }, "registrando consumo de " + discordId);
    }

    /** Plantilla de UPDATE con manejo uniforme de errores. */
    private void ejecutar(String sql, SqlBinder binder, String contexto) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error " + contexto, e);
        }
    }

    /** Enlaza los parámetros de un PreparedStatement (permite lambdas que lanzan SQLException). */
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
```

- [ ] **Step 4: Comprobar que compila**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Revisar MigracionesTest**

Run: `grep -n "V2[0-9]\|size()\|count" src/test/java/com/gymprofit/bot/db/MigracionesTest.java`
Si el test fija un número exacto de migraciones, actualizarlo. Según ADR-006 usa `>=`, así que **probablemente no haya que tocar nada**. El test se salta en local (sin Docker) pero **corre en CI**: un verify verde en local no garantiza CI verde.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V23__descanso.sql src/main/java/com/gymprofit/bot/db/DescansoEstado.java src/main/java/com/gymprofit/bot/db/DescansoRepositorio.java
git commit -m "feat(descanso): tabla descanso (V23) y su repositorio"
```

---

## Task 2: Catálogo de camas + ítems nuevos — ✅ HECHA

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/Camas.java`
- Modify: `src/main/java/com/gymprofit/bot/services/Items.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`
- Test: `src/test/java/com/gymprofit/bot/services/CamasTest.java`

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/gymprofit/bot/services/CamasTest.java`:

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests del catálogo de camas: elección de la mejor e integridad contra {@link Items}. */
class CamasTest {

    @Test
    @DisplayName("sin nada en el inventario se duerme en el suelo")
    void sinNadaDuermeEnElSuelo() {
        assertEquals(Camas.SUELO, Camas.mejorDe(Map.of()));
    }

    @Test
    @DisplayName("elige la mejor cama que se posee, no la primera")
    void eligeLaMejor() {
        Map<String, Integer> inv = Map.of("saco_dormir", 1, "casa", 1, "colchon", 1);
        assertEquals("casa", Camas.mejorDe(inv).itemId());
    }

    @Test
    @DisplayName("un ítem con cantidad 0 no cuenta")
    void cantidadCeroNoCuenta() {
        assertEquals(Camas.SUELO, Camas.mejorDe(Map.of("colchon", 0)));
    }

    @Test
    @DisplayName("todas las camas con itemId existen en el catálogo de Items")
    void integridadDelCatalogo() {
        for (Camas c : Camas.CATALOGO) {
            assertEquals(60, Items.porId(c.itemId()))
                    .as("la cama %s debe existir en Items", c.itemId())
                    .isPresent();
        }
    }

    @Test
    @DisplayName("el suelo tiene el tope más bajo y la casa el más alto")
    void progresionDeTopes() {
        assertEquals(60, Camas.SUELO.tope());
        assertEquals(100, Camas.mejorDe(Map.of("casa", 1)).tope());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=CamasTest`
Expected: FAIL — no compila, `Camas` no existe.

- [ ] **Step 3: Añadir los 2 ítems a Items**

En `src/main/java/com/gymprofit/bot/services/Items.java`, junto al resto de `Categoria.BIEN` (sobre la línea 89, antes de `piso`), añadir:

```java
            // Camas baratas: primer escalón de descanso antes de poder pagar una vivienda.
            new Items("saco_dormir", Categoria.BIEN, "🎒", 150, Efecto.NINGUNO, 0),
            new Items("colchon", Categoria.BIEN, "🛏️", 600, Efecto.NINGUNO, 0),
```

- [ ] **Step 4: Crear el catálogo Camas**

`src/main/java/com/gymprofit/bot/services/Camas.java`:

```java
package com.gymprofit.bot.services;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de sitios donde dormir. Cada cama define cuánta energía se gana por hora y su
 * <b>tope</b>: en el suelo no se llega a 100 por mucho que se duerma. Es lo que da progresión
 * (suelo → saco → colchón → vivienda) y lo que por fin da un uso a los bienes de vivienda, que
 * hasta ahora solo eran un sumidero de coins.
 *
 * <p>La cama no se equipa: sale del inventario, igual que el pico en {@code /minar}. Las viviendas
 * por encima de {@code casa} no dan más (ya está el tope en 100): siguen siendo estatus.
 *
 * @param itemId       id en {@link Items}, o {@code null} para las entradas virtuales
 * @param energiaHora  energía ganada por hora dormida
 * @param tope         energía máxima alcanzable durmiendo aquí
 */
public record Camas(String itemId, int energiaHora, int tope) {

    /** Sin nada: se duerme en el suelo. No tiene ítem. */
    public static final Camas SUELO = new Camas(null, 10, 60);

    /** Hotel: se paga por noche, no se posee. No tiene ítem. */
    public static final Camas HOTEL = new Camas(null, 25, 100);

    /** Precio por noche del hotel (sumidero de coins). */
    public static final long PRECIO_HOTEL = 200;

    /**
     * Camas que se poseen, <b>de peor a mejor</b>. El orden importa: {@link #mejorDe} se queda con
     * la última que encuentre en el inventario.
     */
    public static final List<Camas> CATALOGO = List.of(
            new Camas("saco_dormir", 15, 75),
            new Camas("colchon", 20, 85),
            new Camas("piso", 25, 95),
            new Camas("apartamento", 25, 95),
            // De casa en adelante ya se llega a 100: las viviendas caras son estatus, no ventaja.
            new Camas("casa", 30, 100),
            new Camas("chalet", 30, 100),
            new Camas("mansion", 30, 100),
            new Camas("isla", 30, 100),
            new Camas("castillo", 30, 100),
            new Camas("rascacielos", 30, 100));

    /** La mejor cama que hay en el inventario, o {@link #SUELO} si no hay ninguna. */
    public static Camas mejorDe(Map<String, Integer> inventario) {
        Camas mejor = SUELO;
        for (Camas c : CATALOGO) {
            Integer n = inventario.get(c.itemId());
            if (n != null && n > 0 && c.tope() >= mejor.tope() && c.energiaHora() >= mejor.energiaHora()) {
                mejor = c;
            }
        }
        return mejor;
    }
}
```

- [ ] **Step 5: Añadir i18n de los 2 ítems**

En `messages_es.properties`, junto al resto de `item.*`:

```properties
item.saco_dormir=Saco de dormir
item.colchon=Colchón
```

En `messages_en.properties`:

```properties
item.saco_dormir=Sleeping bag
item.colchon=Mattress
```

- [ ] **Step 6: Ejecutar el test y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=CamasTest`
Expected: PASS (5 tests)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/Camas.java src/main/java/com/gymprofit/bot/services/Items.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties src/test/java/com/gymprofit/bot/services/CamasTest.java
git commit -m "feat(descanso): catalogo de camas y items saco_dormir/colchon"
```

---

## Task 3: `sumarEnergiaConTope` (sin bajar energía) — ✅ HECHA

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java:114`

- [ ] **Step 1: Añadir el método**

Debajo de `sumarEnergia` (línea 114), añadir:

```java
    /**
     * Suma energía respetando un <b>tope</b> propio de la cama en la que se ha dormido.
     *
     * <p>Ojo con el {@code GREATEST}: sin él, dormir en el suelo (tope 60) con 80 de energía la
     * <b>bajaría</b> a 60. El resultado es {@code max(energia, min(tope, energia + cantidad))}, así
     * que dormir nunca puede restar energía: como mucho no suma.
     */
    public void sumarEnergiaConTope(long discordId, int cantidad, int tope) {
        ejecutar("UPDATE personajes SET energia = GREATEST(energia, LEAST(?, energia + ?)) "
                        + "WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, tope);
                    ps.setInt(2, cantidad);
                    ps.setLong(3, discordId);
                });
    }
```

- [ ] **Step 2: Comprobar que compila**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java
git commit -m "feat(descanso): sumarEnergiaConTope, que nunca baja la energia"
```

---

## Task 4: DescansoService — el cálculo puro — ✅ HECHA

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/DescansoService.java`
- Test: `src/test/java/com/gymprofit/bot/services/DescansoServiceTest.java`

- [ ] **Step 1: Escribir los tests del cálculo puro**

`src/test/java/com/gymprofit/bot/services/DescansoServiceTest.java`:

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests del cálculo de descanso. Todo puro: sin BD ni JDA. */
class DescansoServiceTest {

    private static final Camas CASA = new Camas("casa", 30, 100);
    private static final int SALUD_OK = 100;

    @Test
    @DisplayName("la energia es proporcional a los minutos dormidos")
    void proporcionalALosMinutos() {
        // 2 h en casa a 30/h = 60.
        assertEquals(60, DescansoService.energiaGanada(120, CASA, SALUD_OK, 0, 0));
        // 30 min = media hora = 15.
        assertEquals(15, DescansoService.energiaGanada(30, CASA, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("dormir mas de 9 h no suma mas que dormir 9 h")
    void topeDeNueveHoras() {
        int nueve = DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 0);
        int doce = DescansoService.energiaGanada(12 * 60, Camas.SUELO, SALUD_OK, 0, 0);
        assertEquals(nueve, doce);
    }

    @Test
    @DisplayName("en el suelo no se pasa del tope 60 aunque se duerman las 9 h")
    void topePorCama() {
        // 9 h x 10/h = 90 brutos, pero el suelo topa en 60 y se parte de 0.
        assertEquals(60, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("el tope cuenta la energia que ya se tenia")
    void topeCuentaLaEnergiaPrevia() {
        // Con 50 y tope 60, dormir 9 h en el suelo solo puede dar 10.
        assertEquals(10, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 50, 0));
    }

    @Test
    @DisplayName("por encima del tope de la cama no se gana nada, pero nunca se resta")
    void porEncimaDelTopeNoGanaNiResta() {
        assertEquals(0, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 80, 0));
    }

    @Test
    @DisplayName("con salud baja se descansa la mitad")
    void saludBajaDescansaLaMitad() {
        // 2 h en casa = 60 brutos; con salud 20 (<30) la mitad = 30.
        assertEquals(30, DescansoService.energiaGanada(120, CASA, 20, 0, 0));
    }

    @Test
    @DisplayName("dormir 0 minutos no da energia")
    void ceroMinutosCeroEnergia() {
        assertEquals(0, DescansoService.energiaGanada(0, CASA, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("la resistencia acelera el descanso: +1 % por punto")
    void resistenciaAceleraElDescanso() {
        // 1 h en casa = 30 brutos; con 20 de resistencia, +20 % = 36.
        assertEquals(36, DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 20));
    }

    @Test
    @DisplayName("el bono de resistencia topa en +50 %")
    void bonoDeResistenciaTopado() {
        // 1 h en casa = 30 brutos; el tope es +50 % = 45, aunque tenga 500 de resistencia.
        assertEquals(45, DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 500));
        assertEquals(DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 500), DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 50));
    }

    @Test
    @DisplayName("la resistencia acelera pero no rompe el tope de la cama")
    void resistenciaNoRompeElTopeDeCama() {
        // Con 500 de resistencia, en el suelo se sigue sin pasar de 60.
        assertEquals(60, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 500));
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=DescansoServiceTest`
Expected: FAIL — no compila, `DescansoService` no existe.

- [ ] **Step 3: Crear DescansoService con solo el cálculo puro**

`src/main/java/com/gymprofit/bot/services/DescansoService.java`:

```java
package com.gymprofit.bot.services;

import java.time.Duration;

/**
 * Lógica de {@code /descansar}. Dormir es un <b>estado</b>: al acostarse se guarda el instante y al
 * despertar se gana energía proporcional al tiempo dormido de verdad, según la cama ({@link Camas}).
 * La siesta no necesita comando propio: es dormir poco y ganar poco.
 *
 * <p>El instante se pasa como parámetro ({@code Instant ahora}), igual que en
 * {@link TrabajoService#trabajar}, para poder fijar el reloj en los tests.
 */
public final class DescansoService {

    /** Dormir más de esto no suma: dormir de más no descansa. */
    public static final int MAX_HORAS = 9;
    /** Por debajo de esta salud se descansa peor: estás malo. */
    public static final int SALUD_BAJA = 30;
    /** Multiplicador de energía cuando la salud está por debajo de {@link #SALUD_BAJA}. */
    public static final double PENAL_SALUD = 0.5;
    /** Sin dormir más de esto, aparece la fatiga. */
    public static final Duration FATIGA = Duration.ofHours(24);
    /** Bono de descanso por punto de resistencia (+1 %). */
    public static final double BONO_RESISTENCIA = 0.01;
    /** Techo del bono de resistencia (+50 %), para que no se dispare al subir stats sin límite. */
    public static final double BONO_RESISTENCIA_MAX = 0.5;

    private DescansoService() {
        // Task 5 le añade constructor con dependencias; de momento solo cálculo puro.
    }

    /**
     * Energía que se gana al despertar. <b>Puro</b>: el corazón testeable del sistema.
     *
     * <p>La <b>resistencia</b> acelera el descanso (+1 % por punto, techo +50 %): las stats crecen sin
     * límite y sin esto el descanso se quedaría plano al progresar. Aun así <b>no rompe el tope de la
     * cama</b>: por muy en forma que estés, en el suelo no pasas de 60.
     *
     * @param minutos        minutos dormidos (se recortan a {@link #MAX_HORAS})
     * @param cama           dónde se ha dormido (energía/hora y tope)
     * @param salud          salud actual (por debajo de {@link #SALUD_BAJA} se descansa a la mitad)
     * @param energiaActual  energía antes de dormir (el tope de la cama la incluye)
     * @param resistencia    resistencia del personaje (bono al descanso)
     * @return energía a sumar, nunca negativa
     */
    public static int energiaGanada(long minutos, Camas cama, int salud, int energiaActual,
                                    int resistencia) {
        long tope = Math.min(minutos, (long) MAX_HORAS * 60);
        double bruta = tope / 60.0 * cama.energiaHora();
        bruta *= 1 + bonoResistencia(resistencia);
        if (salud < SALUD_BAJA) {
            bruta *= PENAL_SALUD;
        }
        int ganada = (int) Math.round(bruta);
        // El tope es de energía TOTAL, no de ganancia: con 50 y tope 60 solo caben 10 más.
        int cabe = cama.tope() - energiaActual;
        return Math.max(0, Math.min(ganada, cabe));
    }

    /** Bono de descanso por resistencia: +1 % por punto, con techo en +50 %. <b>Puro</b>. */
    public static double bonoResistencia(int resistencia) {
        return Math.min(BONO_RESISTENCIA_MAX, Math.max(0, resistencia) * BONO_RESISTENCIA);
    }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=DescansoServiceTest`
Expected: PASS (10 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/DescansoService.java src/test/java/com/gymprofit/bot/services/DescansoServiceTest.java
git commit -m "feat(descanso): calculo de energia por descanso (puro y testeado)"
```

---

## Task 5: DescansoService — dormir, despertar, estado y fatiga — ✅ HECHA

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/DescansoService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/DescansoServiceTest.java`

- [ ] **Step 1: Escribir los tests de comportamiento**

Añadir a `DescansoServiceTest` (y los imports `java.time.Instant`, `java.util.Map`, `org.junit.jupiter.api.BeforeEach`, `org.mockito.Mockito.*`, más los repos y `assertThatCode`):

```java
    private static final long ID = 7L;
    private static final Instant AHORA = Instant.parse("2026-07-15T10:00:00Z");

    private DescansoRepositorio descansoRepo;
    private PersonajeRepositorio personajeRepo;
    private InventarioRepositorio inventarioRepo;
    private EconomiaRepositorio economiaRepo;
    private UsuarioDiscordRepositorio usuarios;
    private DescansoService servicio;

    @BeforeEach
    void setUp() {
        descansoRepo = mock(DescansoRepositorio.class);
        personajeRepo = mock(PersonajeRepositorio.class);
        inventarioRepo = mock(InventarioRepositorio.class);
        economiaRepo = mock(EconomiaRepositorio.class);
        usuarios = mock(UsuarioDiscordRepositorio.class);
        servicio = new DescansoService(descansoRepo, personajeRepo, inventarioRepo,
                economiaRepo, usuarios);
    }

    /** Estado de descanso despierto y sin consumos. */
    private static DescansoEstado despierto() {
        return new DescansoEstado(ID, null, null, 0, null, null);
    }

    /**
     * Personaje con la energía y salud dadas. <b>Resistencia 0 a propósito</b>: así el bono por
     * resistencia no ensucia las cuentas de estos tests (se prueba aparte, en los puros).
     * Orden del record: discordId, fuerza, resistencia, carisma, energia, salud, …
     */
    private static Personaje personaje(int energia, int salud) {
        return new Personaje(ID, 10, 0, 10, energia, salud, null, null, null, null, null, 0, null, 0);
    }

    @Test
    @DisplayName("dormir acuesta al jugador")
    void dormirAcuesta() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(inventarioRepo.listar(ID)).thenReturn(Map.of());

        DescansoService.ResultadoDormir r = servicio.dormir(ID, false, AHORA);

        assertEquals(DescansoService.EstadoDormir.OK, r.estado());
        assertEquals(Camas.SUELO, r.cama());
        verify(descansoRepo).acostar(ID, AHORA, null);
    }

    @Test
    @DisplayName("no se puede dormir dos veces seguidas")
    void noSePuedeDormirDosVeces() {
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, AHORA, null, 0, null, null));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, false, AHORA);

        assertEquals(DescansoService.EstadoDormir.YA_DORMIDO, r.estado());
        verify(descansoRepo, never()).acostar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("el hotel cobra y usa la cama de hotel")
    void hotelCobra() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(economiaRepo.gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString())).thenReturn(true);

        DescansoService.ResultadoDormir r = servicio.dormir(ID, true, AHORA);

        assertEquals(DescansoService.EstadoDormir.OK, r.estado());
        assertEquals(Camas.HOTEL, r.cama());
        verify(economiaRepo).gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString());
    }

    @Test
    @DisplayName("sin saldo no se duerme en el hotel")
    void hotelSinSaldo() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(economiaRepo.gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString())).thenReturn(false);

        DescansoService.ResultadoDormir r = servicio.dormir(ID, true, AHORA);

        assertEquals(DescansoService.EstadoDormir.SIN_SALDO, r.estado());
        verify(descansoRepo, never()).acostar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("despertar tras 2 h en casa da 60 de energia")
    void despertarDaEnergia() {
        // Resistencia 0 en el helper: el bono por resistencia se prueba aparte, en los tests puros.
        Instant hace2h = AHORA.minus(Duration.ofHours(2));
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, hace2h, null, 0, null, null));
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(0, 100));
        when(inventarioRepo.listar(ID)).thenReturn(Map.of("casa", 1));

        DescansoService.ResultadoDespertar r = servicio.despertar(ID, AHORA);

        assertEquals(DescansoService.EstadoDespertar.OK, r.estado());
        assertEquals(60, r.energiaGanada());
        assertEquals(120, r.minutosDormidos());
        verify(personajeRepo).sumarEnergiaConTope(ID, 60, 100);
        verify(descansoRepo).levantar(ID, AHORA);
    }

    @Test
    @DisplayName("despertar sin estar dormido no hace nada")
    void despertarSinDormir() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());

        DescansoService.ResultadoDespertar r = servicio.despertar(ID, AHORA);

        assertEquals(DescansoService.EstadoDespertar.NO_DORMIDO, r.estado());
        verify(personajeRepo, never()).sumarEnergiaConTope(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("estaDormido refleja el estado")
    void estaDormido() {
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, AHORA, null, 0, null, null));
        assertTrue(servicio.estaDormido(ID));
    }

    @Test
    @DisplayName("mas de 24 h sin despertar es fatiga")
    void fatigaTras24h() {
        DescansoEstado hace25h = new DescansoEstado(ID, null, AHORA.minus(Duration.ofHours(25)), 0, null, null);
        assertTrue(DescansoService.tieneFatiga(hace25h, AHORA));

        DescansoEstado hace2h = new DescansoEstado(ID, null, AHORA.minus(Duration.ofHours(2)), 0, null, null);
        assertFalse(DescansoService.tieneFatiga(hace2h, AHORA));
    }

    @Test
    @DisplayName("quien nunca ha dormido no arrastra fatiga")
    void sinHistorialNoHayFatiga() {
        assertFalse(DescansoService.tieneFatiga(despierto(), AHORA));
    }

    @Test
    @DisplayName("mientras duermes no tienes fatiga")
    void dormidoNoTieneFatiga() {
        DescansoEstado durmiendo = new DescansoEstado(ID, AHORA.minus(Duration.ofHours(1)),
                AHORA.minus(Duration.ofHours(30)), 0, null, null);
        assertFalse(DescansoService.tieneFatiga(durmiendo, AHORA));
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=DescansoServiceTest`
Expected: FAIL — no compila, no existen el constructor ni los métodos.

- [ ] **Step 3: Implementar**

Sustituir el constructor privado de `DescansoService` por dependencias y añadir los métodos. Imports nuevos: `com.gymprofit.bot.db.*`, `java.time.Instant`, `java.util.Map`.

```java
    /** Valor de {@code descanso.cama_pagada} cuando se paga hotel. */
    public static final String HOTEL_PAGADO = "hotel";

    /** Estado del intento de dormir. */
    public enum EstadoDormir { OK, YA_DORMIDO, SIN_SALDO }

    /** Estado del intento de despertar. */
    public enum EstadoDespertar { OK, NO_DORMIDO }

    /**
     * Resultado de acostarse.
     *
     * @param estado resultado
     * @param cama   dónde se ha acostado (para el mensaje), o {@code null} si no {@code OK}
     */
    public record ResultadoDormir(EstadoDormir estado, Camas cama) {
    }

    /**
     * Resultado de despertar.
     *
     * @param estado          resultado
     * @param energiaGanada   energía sumada
     * @param minutosDormidos minutos que ha dormido
     * @param cama            dónde durmió
     */
    public record ResultadoDespertar(EstadoDespertar estado, int energiaGanada,
                                     long minutosDormidos, Camas cama) {
    }

    /**
     * Vista de {@code /descansar estado}.
     *
     * @param dormido         si está durmiendo ahora
     * @param minutosDormidos minutos que lleva dormido (0 si está despierto)
     * @param cama            su mejor cama
     * @param fatiga          si arrastra fatiga (>24 h sin dormir)
     */
    public record Vista(boolean dormido, long minutosDormidos, Camas cama, boolean fatiga) {
    }

    private final DescansoRepositorio descanso;
    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public DescansoService(DescansoRepositorio descanso, PersonajeRepositorio personajes,
                           InventarioRepositorio inventario, EconomiaRepositorio economia,
                           UsuarioDiscordRepositorio usuarios) {
        this.descanso = descanso;
        this.personajes = personajes;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Acuesta al jugador. En hotel cobra {@link Camas#PRECIO_HOTEL} por adelantado. */
    public ResultadoDormir dormir(long discordId, boolean hotel, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado estado = descanso.obtenerOCrear(discordId);
        if (estado.dormido()) {
            return new ResultadoDormir(EstadoDormir.YA_DORMIDO, null);
        }
        Camas cama;
        if (hotel) {
            // Se cobra al acostarse, no al despertar: si no, dormiría gratis quien no despierte.
            if (!economia.gastar(discordId, Camas.PRECIO_HOTEL, "Noche de hotel")) {
                return new ResultadoDormir(EstadoDormir.SIN_SALDO, null);
            }
            cama = Camas.HOTEL;
        } else {
            cama = Camas.mejorDe(inventario.listar(discordId));
        }
        descanso.acostar(discordId, ahora, hotel ? HOTEL_PAGADO : null);
        return new ResultadoDormir(EstadoDormir.OK, cama);
    }

    /**
     * Levanta al jugador y le suma la energía del descanso.
     *
     * <p>La cama propia se resuelve <b>al despertar</b> (sale del inventario). Efecto secundario
     * aceptado: si compras una casa mientras duermes, despiertas mejor. Es indulgente y evita
     * guardar la cama en BD. El hotel sí se guarda al acostarse ({@code cama_pagada}): no se posee,
     * así que no podría deducirse del inventario.
     */
    public ResultadoDespertar despertar(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado estado = descanso.obtenerOCrear(discordId);
        if (!estado.dormido()) {
            return new ResultadoDespertar(EstadoDespertar.NO_DORMIDO, 0, 0, null);
        }
        Personaje p = personajes.obtenerOCrear(discordId);
        Camas cama = HOTEL_PAGADO.equals(estado.camaPagada())
                ? Camas.HOTEL : Camas.mejorDe(inventario.listar(discordId));
        long minutos = Duration.between(estado.dormidoDesde(), ahora).toMinutes();
        int ganada = energiaGanada(minutos, cama, p.salud(), p.energia(), p.resistencia());
        if (ganada > 0) {
            personajes.sumarEnergiaConTope(discordId, ganada, cama.tope());
        }
        descanso.levantar(discordId, ahora);
        return new ResultadoDespertar(EstadoDespertar.OK, ganada, minutos, cama);
    }

    /** Si el jugador está durmiendo ahora mismo (lo consultan trabajo, combate y minería). */
    public boolean estaDormido(long discordId) {
        return descanso.obtenerOCrear(discordId).dormido();
    }

    /** Vista de {@code /descansar estado}. */
    public Vista estado(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado e = descanso.obtenerOCrear(discordId);
        long minutos = e.dormido() ? Duration.between(e.dormidoDesde(), ahora).toMinutes() : 0;
        return new Vista(e.dormido(), minutos, Camas.mejorDe(inventario.listar(discordId)),
                tieneFatiga(e, ahora));
    }

    /**
     * Si el jugador arrastra fatiga: más de {@link #FATIGA} sin dormir. <b>Puro</b>.
     *
     * <p>Quien nunca ha dormido no tiene fatiga (no se castiga al que acaba de empezar), y quien
     * está durmiendo tampoco: ya se está curando.
     */
    public static boolean tieneFatiga(DescansoEstado estado, Instant ahora) {
        if (estado.dormido() || estado.ultimoDespertar() == null) {
            return false;
        }
        return Duration.between(estado.ultimoDespertar(), ahora).compareTo(FATIGA) > 0;
    }
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=DescansoServiceTest`
Expected: PASS (20 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/DescansoService.java src/test/java/com/gymprofit/bot/services/DescansoServiceTest.java
git commit -m "feat(descanso): dormir, despertar, estado y fatiga"
```

---

## Task 6: Comando `/descansar` + i18n — ✅ HECHA

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/economia/DescansoComando.java`
- Modify: `messages_es.properties`, `messages_en.properties`, `Main.java`

- [ ] **Step 1: Añadir i18n ES**

```properties
# ---- Descanso: /descansar (dormir, despertar, estado) ----
comando.descansar.familia=Descanso: dormir, despertar y ver tu estado
comando.descansar.dormir=Te acuestas a dormir (recuperas energía según lo que duermas)
comando.descansar.despertar=Te levantas y recuperas la energía del descanso
comando.descansar.estado=Mira si estás durmiendo, tu cama y si arrastras fatiga
comando.descansar.opcion.sitio=Dónde duermes: tu cama o un hotel (200 coins)
descansar.sitio.propio=Tu cama
descansar.sitio.hotel=Hotel (200 coins)
descansar.dormir.titulo=😴 A dormir
descansar.dormir.ok=Te acuestas en **{0}**. Recuperas **{1}** de energía por hora (tope {2}).\nDespierta con `/descansar despertar` — cuanto más duermas, más recuperas (máx. 9 h).
descansar.yadormido=😴 Ya estás durmiendo. Usa `/descansar despertar` para levantarte.
descansar.hotel.sinsaldo=❌ No te llega para el hotel ({0} coins).
descansar.despertar.titulo=☀️ Buenos días
descansar.despertar.ok=Has dormido **{0}** en **{1}**.\n⚡ Energía recuperada: **+{2}**
descansar.despertar.nada=Has dormido **{0}** en **{1}**, pero ya estabas descansado. ⚡ Sin cambios.
descansar.nodormido=❌ No estabas durmiendo. Acuéstate con `/descansar dormir`.
descansar.estado.titulo=🛏️ Tu descanso
descansar.estado.durmiendo=😴 Llevas durmiendo **{0}**.\n🛏️ Cama: **{1}** ({2}/h, tope {3})
descansar.estado.despierto=☀️ Estás despierto.\n🛏️ Tu cama: **{1}** ({2}/h, tope {3})
descansar.estado.fatiga=\n\n😵 **Fatiga**: llevas más de 24 h sin dormir. Recuperas energía a la mitad y cobras un 20 % menos.
descansar.cama.suelo=el suelo
descansar.cama.hotel=un hotel
descansar.bloqueado.titulo=😴 Estás dormido
descansar.bloqueado.desc=No puedes hacer eso mientras duermes. ¿Sigues durmiendo o te levantas?
descansar.boton.seguir=Seguir durmiendo
descansar.boton.despertar=Despertar
descansar.sigues=😴 Sigues durmiendo. Que descanses.
```

- [ ] **Step 2: Añadir i18n EN (mismas claves)**

```properties
# ---- Rest: /descansar (sleep, wake up, status) ----
comando.descansar.familia=Rest: sleep, wake up and check your status
comando.descansar.dormir=Go to sleep (you recover energy based on how long you sleep)
comando.descansar.despertar=Wake up and collect the energy from your rest
comando.descansar.estado=Check if you are asleep, your bed and whether you are fatigued
comando.descansar.opcion.sitio=Where you sleep: your own bed or a hotel (200 coins)
descansar.sitio.propio=Your bed
descansar.sitio.hotel=Hotel (200 coins)
descansar.dormir.titulo=😴 Off to bed
descansar.dormir.ok=You lie down on **{0}**. You recover **{1}** energy per hour (cap {2}).\nWake up with `/descansar despertar` — the longer you sleep, the more you get (max 9 h).
descansar.yadormido=😴 You are already asleep. Use `/descansar despertar` to get up.
descansar.hotel.sinsaldo=❌ You cannot afford the hotel ({0} coins).
descansar.despertar.titulo=☀️ Good morning
descansar.despertar.ok=You slept **{0}** on **{1}**.\n⚡ Energy recovered: **+{2}**
descansar.despertar.nada=You slept **{0}** on **{1}**, but you were already rested. ⚡ No change.
descansar.nodormido=❌ You were not asleep. Lie down with `/descansar dormir`.
descansar.estado.titulo=🛏️ Your rest
descansar.estado.durmiendo=😴 You have been asleep for **{0}**.\n🛏️ Bed: **{1}** ({2}/h, cap {3})
descansar.estado.despierto=☀️ You are awake.\n🛏️ Your bed: **{1}** ({2}/h, cap {3})
descansar.estado.fatiga=\n\n😵 **Fatigue**: over 24 h without sleep. You recover half the energy and earn 20 % less.
descansar.cama.suelo=the floor
descansar.cama.hotel=a hotel
descansar.bloqueado.titulo=😴 You are asleep
descansar.bloqueado.desc=You cannot do that while asleep. Keep sleeping or get up?
descansar.boton.seguir=Keep sleeping
descansar.boton.despertar=Wake up
descansar.sigues=😴 Still asleep. Sleep well.
```

- [ ] **Step 3: Crear el comando**

`src/main/java/com/gymprofit/bot/commands/economia/DescansoComando.java`:

```java
package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Camas;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.DescansoService.ResultadoDespertar;
import com.gymprofit.bot.services.DescansoService.ResultadoDormir;
import com.gymprofit.bot.services.DescansoService.Vista;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Instant;
import java.util.Locale;

/**
 * {@code /descansar} con subcomandos (dormir, despertar, estado). Dormir es un estado: te acuestas y
 * al despertar recuperas energía según lo que hayas dormido de verdad y según tu cama.
 *
 * <p>Los tres son <b>públicos</b>: no hay nada sensible y la economía se juega a la vista de todos.
 * Las vistas se construyen aquí y las reutiliza {@code DescansoListener} (patrón de
 * {@code PelearComando}).
 */
public final class DescansoComando implements Comando {

    private static final String NOMBRE = "descansar";

    private final DescansoService descanso;

    public DescansoComando(DescansoService descanso) {
        this.descanso = descanso;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData sitio = new OptionData(OptionType.STRING, "sitio",
                Messages.get(Messages.ES, "comando.descansar.opcion.sitio"), false)
                .addChoice(Messages.get(Messages.ES, "descansar.sitio.propio"), "propio")
                .addChoice(Messages.get(Messages.ES, "descansar.sitio.hotel"), "hotel")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.descansar.opcion.sitio"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.descansar.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.descansar.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.descansar.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("dormir", "comando.descansar.dormir").addOptions(sitio),
                        sub("despertar", "comando.descansar.despertar"),
                        sub("estado", "comando.descansar.estado"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "estado" : evento.getSubcommandName();
        switch (sub) {
            case "dormir" -> dormir(evento, locale);
            case "despertar" -> despertar(evento, locale);
            case "estado" -> estado(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void dormir(SlashCommandInteractionEvent evento, Locale locale) {
        boolean hotel = evento.getOption("sitio") != null
                && "hotel".equals(evento.getOption("sitio").getAsString());

        evento.deferReply(false).queue();
        ResultadoDormir r = descanso.dormir(evento.getUser().getIdLong(), hotel, Instant.now());
        MessageEmbed embed = switch (r.estado()) {
            case OK -> EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.dormir.titulo"),
                    Messages.get(locale, "descansar.dormir.ok", nombreCama(locale, r.cama()),
                            r.cama().energiaHora(), r.cama().tope())).build();
            case YA_DORMIDO -> EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.yadormido"));
            case SIN_SALDO -> EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.hotel.sinsaldo", Camas.PRECIO_HOTEL));
        };
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void despertar(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        ResultadoDespertar r = descanso.despertar(evento.getUser().getIdLong(), Instant.now());
        evento.getHook().sendMessageEmbeds(embedDespertar(locale, r)).queue();
    }

    /** Embed de despertar; lo reutiliza {@code DescansoListener} desde el botón. */
    public static MessageEmbed embedDespertar(Locale locale, ResultadoDespertar r) {
        if (r.estado() == DescansoService.EstadoDespertar.NO_DORMIDO) {
            return EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.nodormido"));
        }
        String dormido = Duraciones.formatear(r.minutosDormidos() * 60);
        String cama = nombreCama(locale, r.cama());
        String clave = r.energiaGanada() > 0 ? "descansar.despertar.ok" : "descansar.despertar.nada";
        return EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.despertar.titulo"),
                Messages.get(locale, clave, dormido, cama, r.energiaGanada())).build();
    }

    private void estado(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        Vista v = descanso.estado(evento.getUser().getIdLong(), Instant.now());
        String cama = nombreCama(locale, v.cama());
        String desc = v.dormido()
                ? Messages.get(locale, "descansar.estado.durmiendo",
                        Duraciones.formatear(v.minutosDormidos() * 60), cama,
                        v.cama().energiaHora(), v.cama().tope())
                : Messages.get(locale, "descansar.estado.despierto", "", cama,
                        v.cama().energiaHora(), v.cama().tope());
        if (v.fatiga()) {
            desc += Messages.get(locale, "descansar.estado.fatiga");
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.estado.titulo"), desc).build()).queue();
    }

    /** Nombre localizado de la cama: el ítem si lo hay, o «el suelo» / «un hotel». */
    private static String nombreCama(Locale locale, Camas cama) {
        if (cama == Camas.SUELO) {
            return Messages.get(locale, "descansar.cama.suelo");
        }
        if (cama == Camas.HOTEL) {
            return Messages.get(locale, "descansar.cama.hotel");
        }
        return Messages.get(locale, "item." + cama.itemId());
    }
}
```

- [ ] **Step 4: Wiring en Main**

Import `com.gymprofit.bot.commands.economia.DescansoComando`, `com.gymprofit.bot.db.DescansoRepositorio`, `com.gymprofit.bot.services.DescansoService`. Tras el bloque de tienda e inventario (donde ya existen `inventarioRepo`, `economiaRepo`, `personajeRepo`, `usuarios`):

```java
            // Descanso: dormir es un estado; al despertar se gana energía según cama y tiempo.
            DescansoService descansoService = new DescansoService(
                    new DescansoRepositorio(db.dataSource()), personajeRepo, inventarioRepo,
                    economiaRepo, usuarios);
            comandos.add(new DescansoComando(descansoService));
```

- [ ] **Step 5: Verificar**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/economia/DescansoComando.java src/main/java/com/gymprofit/bot/Main.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(descanso): comando /descansar (dormir, despertar, estado)"
```

---

## Task 7: Bloqueo de acciones + embed con botones — ✅ HECHA

**Files:**
- Create: `src/main/java/com/gymprofit/bot/events/DescansoListener.java`
- Modify: `TrabajoService.java`, `BatallaService.java`, `MineriaService.java` y sus comandos
- Modify: `Main.java`

- [ ] **Step 1: Añadir el estado DORMIDO a los tres services**

Cada service recibe `DescansoService` por constructor y comprueba al entrar. En `TrabajoService`:
añadir `DORMIDO` a `EstadoWork` y, al principio de `trabajar`:

```java
        if (descanso.estaDormido(discordId)) {
            return new ResultadoWork(EstadoWork.DORMIDO, 0, 0, 0);
        }
```

En `BatallaService.iniciar` e `iniciarMazmorra`, añadir `DORMIDO` a `InicioEstado` y la misma guarda.
En `MineriaService.minar`, añadir `DORMIDO` a `Estado` y la misma guarda.

**Ojo con el orden de construcción en `Main`:** `DescansoService` debe crearse **antes** que
`TrabajoService`, `BatallaService` y `MineriaService`. Mover su bloque justo detrás de
`inventarioRepo`/`itemService`.

- [ ] **Step 2: Crear las vistas del bloqueo en DescansoComando**

Añadir a `DescansoComando`:

```java
    /** customId del botón «seguir durmiendo» (lo maneja DescansoListener). */
    public static final String BOTON_SEGUIR = "descanso:seguir";
    /** customId del botón «despertar» (lo maneja DescansoListener). */
    public static final String BOTON_DESPERTAR = "descanso:despertar";

    /** Embed que se muestra al intentar actuar estando dormido. */
    public static MessageEmbed embedBloqueado(Locale locale) {
        return EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.bloqueado.titulo"),
                Messages.get(locale, "descansar.bloqueado.desc")).build();
    }

    /** Botones del embed de bloqueo, marcados con el dueño para que nadie más los pulse. */
    public static ActionRow botonesBloqueado(Locale locale, long ownerId) {
        return ActionRow.of(
                Button.secondary(BOTON_SEGUIR + ":" + ownerId,
                        Messages.get(locale, "descansar.boton.seguir")),
                Button.success(BOTON_DESPERTAR + ":" + ownerId,
                        Messages.get(locale, "descansar.boton.despertar")));
    }
```

Imports: `net.dv8tion.jda.api.interactions.components.ActionRow`, `...components.buttons.Button`.

- [ ] **Step 3: Usar el bloqueo en los comandos**

**Corregido tras verificar el código real:** `PelearComando` y `MazmorraComando` **no inician el
combate** — solo pintan el menú de rival; quien llama a `batalla.iniciar()` / `iniciarMazmorra()` es
**`CombateListener`** (`seleccionarRival` / `entrarMazmorra`). El bloqueo va ahí, no en esos comandos.
Los que sí lo llevan en el comando son `TrabajoComando.currar` y `MinarComando`.

El guard de dueño del botón usa **`descansar.noestuyo`** (clave nueva, ES+EN), siguiendo el patrón
del repo (`batalla.noestuyo`, `duelo.noestuyo`, `trueque.noestuyo`) — **no** `mod.nopuedes`, que es un
mensaje de moderación («no puedes moderar a ese miembro») y aquí quedaría absurdo.

En la rama `DORMIDO`:

```java
            case DORMIDO -> {
                evento.getHook().sendMessageEmbeds(DescansoComando.embedBloqueado(locale))
                        .setComponents(DescansoComando.botonesBloqueado(locale,
                                evento.getUser().getIdLong()))
                        .queue();
                return;
            }
```

- [ ] **Step 4: Crear el listener**

`src/main/java/com/gymprofit/bot/events/DescansoListener.java`:

```java
package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.DescansoComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.DescansoService.ResultadoDespertar;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Locale;

/**
 * Botones del embed «estás dormido» que sale al intentar actuar durmiendo: seguir durmiendo o
 * despertar. El customId lleva el id del dueño para que nadie despierte a otro.
 */
public final class DescansoListener extends ListenerAdapter {

    private final DescansoService descanso;

    public DescansoListener(DescansoService descanso) {
        this.descanso = descanso;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(DescansoComando.BOTON_SEGUIR)
                && !id.startsWith(DescansoComando.BOTON_DESPERTAR)) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // customId = "descanso:<accion>:<ownerId>": solo el dueño maneja su descanso.
        String[] partes = id.split(":");
        if (partes.length < 3 || !partes[2].equals(evento.getUser().getId())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "mod.nopuedes"))).setEphemeral(true).queue();
            return;
        }
        if (id.startsWith(DescansoComando.BOTON_SEGUIR)) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.sigues"))).setComponents().queue();
            return;
        }
        evento.deferEdit().queue();
        ResultadoDespertar r = descanso.despertar(evento.getUser().getIdLong(), Instant.now());
        evento.getHook().editOriginalEmbeds(DescansoComando.embedDespertar(locale, r))
                .setComponents().queue();
    }
}
```

- [ ] **Step 5: Wiring del listener en Main**

```java
            listeners.add(new DescansoListener(descansoService));
```

- [ ] **Step 6: Verificar**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`. Si `BatallaServiceTest` o `MineriaServiceTest` fallan por el constructor nuevo, añadir el mock de `DescansoService` con `when(descanso.estaDormido(anyLong())).thenReturn(false)` en su `setUp`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(descanso): bloquea acciones mientras duermes, con embed y botones"
```

---

## Task 8: Fatiga + job a +5 — ✅ HECHA

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/TrabajoService.java`
- Modify: `src/main/java/com/gymprofit/bot/jobs/EnergiaJob.java:22`
- Modify: `src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java`

- [ ] **Step 1: Escribir el test de la penalización**

Añadir a `TrabajoServiceTest`:

```java
    @Test
    @DisplayName("con fatiga el sueldo baja un 20 %")
    void fatigaBajaElSueldo() {
        assertEquals(80, TrabajoService.conPenalizacionFatiga(100, true));
        assertEquals(100, TrabajoService.conPenalizacionFatiga(100, false));
        // Redondeo: nunca baja de 1 coin.
        assertEquals(1, TrabajoService.conPenalizacionFatiga(1, true));
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=TrabajoServiceTest`
Expected: FAIL — `conPenalizacionFatiga` no existe.

- [ ] **Step 3: Implementar**

En `TrabajoService`, junto a `conBonoEstudios`:

```java
    /** Penalización del sueldo por fatiga: −20 %, con suelo de 1 coin. */
    public static final double PENAL_FATIGA = 0.8;

    /** Aplica la penalización por fatiga al sueldo ya calculado. <b>Puro</b>. */
    public static long conPenalizacionFatiga(long base, boolean fatiga) {
        return fatiga ? Math.max(1, Math.round(base * PENAL_FATIGA)) : base;
    }
```

Y en `trabajar`, tras calcular el pago con `conBonoEstudios`, envolverlo:

```java
        boolean fatiga = DescansoService.tieneFatiga(descanso.estadoDe(discordId), ahora);
        pago = conPenalizacionFatiga(pago, fatiga);
```

Añadir a `DescansoService` el acceso al estado crudo (lo necesita `TrabajoService`):

```java
    /** Estado crudo de descanso, para quien necesite calcular la fatiga (p. ej. el sueldo). */
    public DescansoEstado estadoDe(long discordId) {
        return descanso.obtenerOCrear(discordId);
    }
```

- [ ] **Step 4: Bajar el job a +5 y saltar a los dormidos**

En `EnergiaJob`, cambiar la constante y el Javadoc:

```java
    /** Energía recuperada por tick. Es la mitad que antes: el resto se gana durmiendo. */
    public static final int REGEN = 5;
```

Y en `PersonajeRepositorio.regenerarEnergia`, excluir a los dormidos para no contar dos veces:

```java
        // Los dormidos no regeneran por el job: ya ganan energía al despertar.
        "UPDATE personajes p SET p.energia = LEAST(100, p.energia + ?) WHERE p.energia < 100 "
                + "AND NOT EXISTS (SELECT 1 FROM descanso d WHERE d.discord_id = p.discord_id "
                + "AND d.dormido_desde IS NOT NULL)"
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(descanso): fatiga tras 24 h sin dormir y job de energia a +5"
```

---

## Task 9: Saciedad — ⬜ PENDIENTE

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/ItemService.java:62`
- Modify: `src/test/java/com/gymprofit/bot/services/ItemServiceTest.java`
- Modify: `messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: Escribir el test**

Añadir a `ItemServiceTest` (con `DescansoService` mockeado en el constructor de `ItemService`):

```java
    @Test
    @DisplayName("el cuarto consumible del dia se rechaza por saciedad")
    void saciedadCortaAlCuarto() {
        when(descanso.puedeConsumir(ID)).thenReturn(false);

        ItemService.ResultadoUso r = servicio.usar(ID, "cafe");

        assertEquals(ItemService.EstadoUso.LLENO, r.estado());
        verify(inventario, never()).quitar(anyLong(), anyString(), anyInt());
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=ItemServiceTest`
Expected: FAIL — no existen `LLENO` ni `puedeConsumir`.

- [ ] **Step 3: Implementar la saciedad en DescansoService**

```java
    /** Máximo de consumibles al día (saciedad): sin esto, con coins se salta el freno de energía. */
    public static final int MAX_CONSUMOS_DIA = 3;
    /** El día natural del bot, igual que /daily y el interés del banco. */
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    /** Si al jugador le caben más consumibles hoy. */
    public boolean puedeConsumir(long discordId) {
        DescansoEstado e = descanso.obtenerOCrear(discordId);
        LocalDate hoy = LocalDate.now(ZONA);
        return !hoy.equals(e.diaConsumos()) || e.consumidosHoy() < MAX_CONSUMOS_DIA;
    }

    /** Apunta un consumible del día. Llamar solo tras un uso con éxito. */
    public void registrarConsumo(long discordId) {
        descanso.registrarConsumo(discordId, LocalDate.now(ZONA));
    }
```

- [ ] **Step 4: Engancharlo en ItemService.usar**

Añadir `LLENO` a `EstadoUso`, `DescansoService` al constructor, y al principio de `usar` (tras validar que el ítem existe y es consumible):

```java
        if (!descanso.puedeConsumir(discordId)) {
            return new ResultadoUso(EstadoUso.LLENO, Efecto.NINGUNO, 0);
        }
```

Y tras aplicar el efecto con éxito: `descanso.registrarConsumo(discordId);`

En `InventarioComando.usar`, añadir la rama:

```java
            case LLENO -> Messages.get(locale, "usar.lleno");
```

- [ ] **Step 5: i18n de la saciedad**

ES: `usar.lleno=🤢 Estás lleno. No puedes tomar más de {0} consumibles al día. Descansa o duerme.`
EN: `usar.lleno=🤢 You are full. No more than {0} consumables a day. Rest or sleep.`

Pasar `DescansoService.MAX_CONSUMOS_DIA` como argumento.

- [ ] **Step 6: Ejecutar y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`, 0 fallos.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(descanso): saciedad, maximo 3 consumibles al dia"
```

---

## Task 10: RGPD — exportar el descanso — ⬜ PENDIENTE

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/PrivacidadService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/PrivacidadServiceTest.java`

- [ ] **Step 1: Escribir el test**

```java
    @Test
    @DisplayName("el export incluye el descanso")
    void exportIncluyeDescanso() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(
                new DescansoEstado(ID, null, Instant.parse("2026-07-15T08:00:00Z"), 2,
                        LocalDate.of(2026, 7, 15), null));

        DataObject datos = servicio.exportar(ID);

        assertTrue(datos.hasKey("descanso"));
        assertEquals(2, datos.getObject("descanso").getInt("consumidos_hoy"));
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o test -Dtest=PrivacidadServiceTest`
Expected: FAIL — no hay clave `descanso`.

- [ ] **Step 3: Implementar**

`DescansoRepositorio` al constructor de `PrivacidadService` y, en `exportar`:

```java
        // El descanso también es dato del usuario: derecho de acceso y portabilidad (ADR-009).
        DescansoEstado d = descanso.obtenerOCrear(discordId);
        datos.put("descanso", DataObject.empty()
                .put("dormido_desde", d.dormidoDesde() == null ? null : d.dormidoDesde().toString())
                .put("ultimo_despertar",
                        d.ultimoDespertar() == null ? null : d.ultimoDespertar().toString())
                .put("consumidos_hoy", d.consumidosHoy())
                .put("dia_consumos", d.diaConsumos() == null ? null : d.diaConsumos().toString()));
```

El borrado ya lo cubre el `ON DELETE CASCADE` de la FK: no hace falta tocar `borrar`.

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(descanso): incluye el descanso en el export de privacidad (RGPD)"
```

---

## Task 11: Documentación viva — ⬜ PENDIENTE

**Files:**
- Modify: `messages_es.properties` / `messages_en.properties` (`intro.economia.desc`, `intro.simulador.desc`)
- Modify: `README.md`, `README.en.md`, `CHANGELOG.md`, `docs/architecture.md`
- Modify: `src/main/resources/db/README.md` (si existe y lista tablas)

- [ ] **Step 1: Intros de /setup**

En `intro.economia.desc` (ES y EN), añadir una sección tras «👤 Personaje»:

```
**😴 Descanso**\n> `/descansar dormir` · `/descansar despertar` · `/descansar estado` — la energía se recupera durmiendo; con mejor cama descansas mejor
```

En `intro.simulador.desc`, añadir `**/descansar**` a la línea de personaje y progresión.

- [ ] **Step 2: README ES/EN**

En la sección «Personaje y progresión» añadir `/descansar` (dormir · despertar · estado).

- [ ] **Step 3: CHANGELOG**

Entrada en `### Añadido` de `[Sin publicar]`, formato largo con «Novedades» (alimenta el apartado
Noticias de la app):

```markdown
- **RPG — descanso y energía**: la energía ya no sube sola: ahora **se duerme**. `/descansar dormir`
  te acuesta y `/descansar despertar` te levanta dándote energía **según lo que hayas dormido de
  verdad** — una siesta de 20 min da poco, ocho horas te dejan nuevo (máx. 9 h). Dónde duermes
  importa: en el suelo no pasas de 60 de energía, con un colchón de 85 y con casa propia llegas a
  100, así que **las viviendas por fin sirven para algo**. Si aún no tienes casa, el hotel (200
  coins la noche) te saca del apuro. Tu **resistencia** también cuenta: cada punto te hace descansar
  un 1 % más rápido (hasta +50 %), así que entrenar ya no solo sirve para pelear. Mientras duermes
  no puedes currar ni pelear: si lo intentas, el bot te pregunta si sigues durmiendo o te levantas.
  Además: **fatiga** si pasas 24 h sin dormir (recuperas la mitad y cobras un 20 % menos),
  **saciedad** (máximo 3 consumibles al día) y con la salud por los suelos se duerme peor. La
  regeneración pasiva baja a +5/30 min: el ritmo total es parecido, pero ahora participas. Migración
  **V23**, `DescansoService` con cálculo puro y tests.
```

- [ ] **Step 4: architecture.md**

Añadir `/descansar` a la lista de familias con subcomandos y actualizar el total de comandos (55 → 56).

- [ ] **Step 5: Verificar y commit**

Run: `JAVA_HOME=~/.jdks/ms-21.0.11 ./mvnw.cmd -o verify`
Expected: `BUILD SUCCESS`

```bash
git add -A
git commit -m "docs(descanso): documenta el sistema de descanso y energia"
```

---

## Cierre: qué desplegar

Al terminar, decirle al usuario (regla fija al tocar comandos):

> **Para verlo en el servidor:** reinicia el bot (`scripts/run-local.ps1`) y luego `/setup` normal.
> El reinicio registra `/descansar` y aplica la migración V23; `/setup` refresca las intros de
> `💰・economía` y `🎮 VIDA`, que ahora mencionan el descanso. **No hace falta `desde_cero`.**

Y marcar **pendiente de smoke test manual**: dormir, despertar tras un rato, intentar `/trabajo currar`
dormido y probar los dos botones, el hotel sin saldo y la saciedad al cuarto consumible.
