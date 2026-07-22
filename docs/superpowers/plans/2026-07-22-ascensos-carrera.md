# Ascensos de carrera — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** el tier de trabajo se gana currando: carrera por rama con 4 requisitos por ascenso
(antigüedad, estudios, stat, coins quemados) según la spec
`docs/superpowers/specs/2026-07-22-ascensos-carrera-design.md`.

**Architecture:** catálogo satélite `Ascensos` (ramas, requisitos, stat) sin tocar `Trabajos`;
tabla `carreras` (tier alcanzado por rama, referencia recalculable) + contador `turnos_puesto` en
`personajes`; lógica en `TrabajoService` (dueño de elegir/currar); dos subcomandos nuevos en
`/trabajo` (0 top-level nuevos).

**Tech Stack:** Java 21, JDA 5, MySQL/Flyway, JUnit 5 + Mockito, Testcontainers (solo CI).

**Build (PowerShell):** `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"` antes de cada
`.\mvnw.cmd`. Siempre `clean verify` para el verify completo. Baseline actual: **420 tests,
0 fallos, 7 skipped**.

**Convenciones obligatorias:** cabecera Javadoc en cada archivo, comentarios del *porqué*, i18n
ES+EN (nada hardcodeado), EmbedFactory, BD tras `deferReply`, commits sin trailers.

---

### Task 1: Migración V26 (tabla `carreras` + contador + borrón)

**Files:**
- Create: `src/main/resources/db/migration/V26__carreras.sql`
- Modify: `src/test/java/com/gymprofit/bot/db/MigracionesTest.java`

- [ ] **Step 1: Verificar el nombre de la PK de `usuarios_discord`**

```
grep -n "REFERENCES usuarios_discord" src/main/resources/db/migration/V25__pasivos_equipados.sql
```
Copiar la forma exacta de la FK (columna y `ON DELETE CASCADE`) que usa V25.

- [ ] **Step 2: Escribir la migración**

`src/main/resources/db/migration/V26__carreras.sql`:

```sql
-- V26: ascensos de carrera.
-- carreras guarda el tier alcanzado POR RAMA (catálogo Ascensos, en código): es una referencia,
-- no un derecho — la elegibilidad se valida siempre contra el catálogo en tiempo de ejecución.
-- Sin fila = el usuario está en el tier de entrada de esa rama.
CREATE TABLE carreras (
    discord_id     BIGINT      NOT NULL,
    rama           VARCHAR(32) NOT NULL,
    tier_alcanzado TINYINT     NOT NULL,
    PRIMARY KEY (discord_id, rama),
    -- CASCADE: /privacidad borrar se lleva también las carreras (RGPD, como pasivos_equipados).
    CONSTRAINT fk_carreras_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
);

-- Antigüedad en el puesto actual: suma 1 por turno currado, se resetea al cambiar de puesto.
ALTER TABLE personajes
    ADD COLUMN turnos_puesto INT NOT NULL DEFAULT 0;

-- Borrón y cuenta nueva acordado: no hay jugadores reales aún y el modelo de acceso al tier
-- cambia; nadie hereda un puesto que ya no podría elegir.
UPDATE personajes SET trabajo = NULL, turnos_puesto = 0;
```

(Si el Step 1 muestra otra columna/sintaxis de FK, calcarla.)

- [ ] **Step 3: Ampliar `MigracionesTest`**

Añadir junto a los asserts de tablas vacías existentes (mismo patrón `contar`):

```java
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM carreras"),
                        "carreras nace vacía: sin fila = tier de entrada de la rama");
```

- [ ] **Step 4: Verificar en local**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=MigracionesTest" test
```
`Expected: SKIPPED en local (Testcontainers) pero COMPILA. Queda pendiente de CI.`

*(Sin commit aún: C1 cierra en la Task 3.)*

---

### Task 2: Catálogo `Ascensos`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/Ascensos.java`
- Create: `src/test/java/com/gymprofit/bot/services/AscensosTest.java`

- [ ] **Step 1: Escribir los tests primero**

`src/test/java/com/gymprofit/bot/services/AscensosTest.java`:

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Barridos de integridad del catálogo de ascensos: si alguien añade un sector nuevo a
 * {@code Trabajos} y olvida mapearlo a una rama, o rompe la monotonía de los requisitos,
 * estos tests lo paran en el build, no en producción.
 */
class AscensosTest {

    @Test
    @DisplayName("todo sector del catálogo de trabajos pertenece a exactamente una rama")
    void todoSectorTieneRama() {
        for (Trabajos t : Trabajos.CATALOGO) {
            assertNotNull(Ascensos.ramaDe(t.sector()),
                    "sector sin rama: " + t.sector() + " (añadirlo a SECTOR_A_RAMA)");
        }
    }

    @Test
    @DisplayName("toda rama tiene al menos un puesto y un tier de entrada")
    void todaRamaTienePuestos() {
        for (Ascensos.Rama rama : Ascensos.Rama.values()) {
            assertTrue(Ascensos.tierEntrada(rama) >= 1,
                    "rama sin puestos en el catálogo: " + rama);
        }
    }

    @Test
    @DisplayName("los requisitos crecen con el tier destino (monotonía)")
    void requisitosMonotonos() {
        Ascensos.Requisitos r2 = Ascensos.requisitosPara(2);
        Ascensos.Requisitos r3 = Ascensos.requisitosPara(3);
        Ascensos.Requisitos r4 = Ascensos.requisitosPara(4);
        assertTrue(r2.turnos() < r3.turnos() && r3.turnos() < r4.turnos());
        assertTrue(r2.estudios() < r3.estudios() && r3.estudios() < r4.estudios());
        assertTrue(r2.stat() < r3.stat() && r3.stat() < r4.stat());
        assertTrue(r2.coins() < r3.coins() && r3.coins() < r4.coins());
    }

    @Test
    @DisplayName("siguienteTier salta los tiers huecos y devuelve vacío en el tope de la rama")
    void siguienteTierSaltaHuecos() {
        // La rama de arte no tiene t1: su entrada es t2 y de t2 se pasa a t3 (actor).
        assertEquals(2, Ascensos.tierEntrada(Ascensos.Rama.ARTE));
        assertEquals(Optional.of(3), Ascensos.siguienteTier(Ascensos.Rama.ARTE, 2));
        assertEquals(Optional.empty(), Ascensos.siguienteTier(Ascensos.Rama.ARTE, 3),
                "t3 es el tope de la rama de arte: no hay más ascensos");
        // Salud llega a t4 (cirujano/astronauta).
        assertEquals(Optional.of(4), Ascensos.siguienteTier(Ascensos.Rama.SALUD, 3));
    }

    @Test
    @DisplayName("puestosDe devuelve los puestos de la rama en ese tier, y solo esos")
    void puestosDeFiltraBien() {
        List<Trabajos> t4Salud = Ascensos.puestosDe(Ascensos.Rama.SALUD, 4);
        assertFalse(t4Salud.isEmpty());
        for (Trabajos t : t4Salud) {
            assertEquals(4, t.tier());
            assertEquals(Ascensos.Rama.SALUD, Ascensos.ramaDe(t.sector()));
        }
    }

    @Test
    @DisplayName("toda rama tiene una stat válida del personaje")
    void statValidaPorRama() {
        for (Ascensos.Rama rama : Ascensos.Rama.values()) {
            String stat = Ascensos.statDe(rama);
            assertTrue(List.of("fuerza", "resistencia", "carisma").contains(stat),
                    "stat desconocida en " + rama + ": " + stat);
        }
    }
}
```

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=AscensosTest" test
```
`Expected: COMPILATION ERROR — cannot find symbol Ascensos`

- [ ] **Step 3: Escribir el catálogo**

`src/main/java/com/gymprofit/bot/services/Ascensos.java`:

```java
package com.gymprofit.bot.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de los <b>ascensos de carrera</b> (datos, en código): agrupa los sectores de
 * {@link Trabajos} en ramas, define la stat dominante de cada rama y los requisitos de cada salto
 * de tier. Satélite de {@code Trabajos} — mismo patrón que {@code Pasivos} con {@code Items} —
 * para no ampliar el record del catálogo base ni tocar sus tests.
 *
 * <p>El porqué de las ramas: el catálogo tiene ~26 sectores para ~50 puestos, así que la mayoría
 * no da para una carrera. Las ramas los agrupan en 7 recorridos con identidad; si una rama no
 * tiene un tier (hueco) el ascenso salta al siguiente existente, y si topa por debajo de t4 su
 * carrera simplemente acaba antes (el catálogo puede crecer por rama sin tocar esto).
 */
public final class Ascensos {

    /** Ramas de carrera. El nombre visible sale de i18n ({@code rama.<minúsculas>}). */
    public enum Rama { SALUD, TECNICA, TRANSPORTE, HOSTELERIA, NEGOCIOS, ARTE, SERVICIOS }

    /**
     * Requisitos de un salto de tier (los del <b>tier destino</b>).
     *
     * @param turnos   turnos currados en el puesto actual (antigüedad)
     * @param estudios puntos de estudios mínimos
     * @param stat     valor mínimo de la stat dominante de la rama
     * @param coins    coste en coins; se <b>queman</b> (sumidero antiinflación)
     */
    public record Requisitos(int turnos, int estudios, int stat, long coins) {
    }

    /** Sector del catálogo de trabajos → rama. Integridad vigilada por {@code AscensosTest}. */
    private static final Map<String, Rama> SECTOR_A_RAMA = Map.ofEntries(
            Map.entry("Sanidad", Rama.SALUD),
            Map.entry("Ciencia", Rama.SALUD),
            Map.entry("Deporte", Rama.SALUD),
            Map.entry("Tecnología", Rama.TECNICA),
            Map.entry("Oficios", Rama.TECNICA),
            Map.entry("Automoción", Rama.TECNICA),
            Map.entry("Construcción", Rama.TECNICA),
            Map.entry("Agricultura", Rama.TECNICA),
            Map.entry("Transporte", Rama.TRANSPORTE),
            Map.entry("Logística", Rama.TRANSPORTE),
            Map.entry("Aviación", Rama.TRANSPORTE),
            Map.entry("Hostelería", Rama.HOSTELERIA),
            Map.entry("Comercio", Rama.HOSTELERIA),
            Map.entry("Pesca", Rama.HOSTELERIA),
            Map.entry("Belleza", Rama.HOSTELERIA),
            Map.entry("Negocios", Rama.NEGOCIOS),
            Map.entry("Finanzas", Rama.NEGOCIOS),
            Map.entry("Derecho", Rama.NEGOCIOS),
            Map.entry("Educación", Rama.NEGOCIOS),
            Map.entry("Atención", Rama.NEGOCIOS),
            Map.entry("Arte", Rama.ARTE),
            Map.entry("Medios", Rama.ARTE),
            Map.entry("Entretenimiento", Rama.ARTE),
            Map.entry("Servicios", Rama.SERVICIOS),
            Map.entry("Seguridad", Rama.SERVICIOS),
            Map.entry("Emergencias", Rama.SERVICIOS));

    /** Stat dominante de cada rama (nombre de columna de {@code personajes}). */
    private static final Map<Rama, String> STAT_DE_RAMA = Map.of(
            Rama.SALUD, "resistencia",
            Rama.TECNICA, "fuerza",
            Rama.TRANSPORTE, "fuerza",
            Rama.HOSTELERIA, "carisma",
            Rama.NEGOCIOS, "carisma",
            Rama.ARTE, "carisma",
            Rama.SERVICIOS, "resistencia");

    /**
     * Requisitos por tier destino. Escala pegada a la economía lenta: t4 (50k) es el precio de un
     * bien caro, para que el último ascenso sea una decisión de late-game, no un trámite.
     */
    private static final Map<Integer, Requisitos> REQUISITOS = Map.of(
            2, new Requisitos(10, 5, 10, 500L),
            3, new Requisitos(25, 15, 25, 5_000L),
            4, new Requisitos(50, 30, 40, 50_000L));

    private Ascensos() {
    }

    /** Rama de un sector del catálogo, o {@code null} si el sector no está mapeado (test lo caza). */
    public static Rama ramaDe(String sector) {
        return SECTOR_A_RAMA.get(sector);
    }

    /** Stat dominante de la rama (columna de {@code personajes}). */
    public static String statDe(Rama rama) {
        return STAT_DE_RAMA.get(rama);
    }

    /** Requisitos del salto AL tier indicado. */
    public static Requisitos requisitosPara(int tierDestino) {
        return REQUISITOS.get(tierDestino);
    }

    /** Tier más bajo con puestos en la rama: el punto de entrada, siempre elegible. */
    public static int tierEntrada(Rama rama) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama)
                .mapToInt(Trabajos::tier).min().orElse(0);
    }

    /**
     * Siguiente tier <b>existente</b> de la rama por encima del actual, o vacío si la rama topa
     * ahí. Saltar huecos aquí (y no en el llamante) mantiene la regla en un único sitio.
     */
    public static Optional<Integer> siguienteTier(Rama rama, int tierActual) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama && t.tier() > tierActual)
                .map(Trabajos::tier)
                .min(Integer::compareTo);
    }

    /** Puestos de la rama en un tier concreto (para el autocompletado de {@code ascender}). */
    public static List<Trabajos> puestosDe(Rama rama, int tier) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama && t.tier() == tier)
                .toList();
    }
}
```

- [ ] **Step 4: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=AscensosTest" test
```
`Expected: Tests run: 6, Failures: 0, Errors: 0`

> Si `todoSectorTieneRama` falla, el mensaje dice qué sector falta: añadirlo a `SECTOR_A_RAMA`
> con la rama que toque por afinidad (NO borrar el sector del catálogo de trabajos).

*(Sin commit aún.)*

---

### Task 3: `CarreraRepositorio` + Testcontainers y COMMIT 1

**Files:**
- Create: `src/main/java/com/gymprofit/bot/db/CarreraRepositorio.java`
- Create: `src/test/java/com/gymprofit/bot/db/CarreraRepositorioTest.java`

- [ ] **Step 1: Escribir el repositorio**

`src/main/java/com/gymprofit/bot/db/CarreraRepositorio.java` (calcar el estilo de
`PasivoRepositorio`: `DataSource` por constructor, `DatabaseException` en los catch):

```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a la tabla {@code carreras}: tier alcanzado por usuario y rama (ascensos de carrera).
 * Sin fila = el usuario está en el tier de entrada de la rama; la fila solo aparece al ascender.
 */
public final class CarreraRepositorio {

    private final DataSource dataSource;

    public CarreraRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Tier alcanzado en la rama, o 0 si nunca ha ascendido en ella (el service aplica la entrada). */
    public int tierAlcanzado(long discordId, String rama) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT tier_alcanzado FROM carreras WHERE discord_id = ? AND rama = ?")) {
            ps.setLong(1, discordId);
            ps.setString(2, rama);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la carrera de " + discordId, e);
        }
    }

    /**
     * Fija el tier alcanzado en la rama. {@code GREATEST} hace la operación idempotente y evita
     * regresiones: un tier alcanzado nunca baja. {@code ON DUPLICATE KEY UPDATE} es seguro aquí
     * porque la PK compuesta es la <b>única</b> clave de la tabla (la trampa de
     * {@code pasivos_equipados} era tener dos claves únicas).
     */
    public void fijarTier(long discordId, String rama, int tier) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO carreras (discord_id, rama, tier_alcanzado) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE "
                             + "tier_alcanzado = GREATEST(tier_alcanzado, VALUES(tier_alcanzado))")) {
            ps.setLong(1, discordId);
            ps.setString(2, rama);
            ps.setInt(3, tier);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando la carrera de " + discordId, e);
        }
    }
}
```

- [ ] **Step 2: Test de Testcontainers**

`src/test/java/com/gymprofit/bot/db/CarreraRepositorioTest.java` — calcar los imports, el
`assumeTrue` y el montaje del contenedor de `PasivoRepositorioTest` (leerlo primero), con este
cuerpo:

```java
    @Test
    void tierAlcanzadoNuncaBajaYElBorradoArrastra() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var carreras = new CarreraRepositorio(db.dataSource());

                usuarios.obtenerOCrear(201L);
                assertEquals(0, carreras.tierAlcanzado(201L, "SALUD"), "sin fila = 0");

                carreras.fijarTier(201L, "SALUD", 3);
                assertEquals(3, carreras.tierAlcanzado(201L, "SALUD"));
                // GREATEST: reponer un tier menor no degrada la carrera.
                carreras.fijarTier(201L, "SALUD", 2);
                assertEquals(3, carreras.tierAlcanzado(201L, "SALUD"),
                        "el tier alcanzado nunca baja");
                // Ramas independientes.
                assertEquals(0, carreras.tierAlcanzado(201L, "ARTE"));

                // RGPD: borrar el usuario arrastra sus carreras (FK CASCADE).
                borrarUsuario(usuarios, 201L);
                assertEquals(0, carreras.tierAlcanzado(201L, "SALUD"));
            }
        }
    }
```

> `borrarUsuario`: usar el método real de borrado de `UsuarioDiscordRepositorio` (el mismo que usa
> `PasivoRepositorioTest` para su assert de CASCADE — leerlo de ahí, no inventarlo).

- [ ] **Step 3: Compilar y ver que se salta limpio en local**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=CarreraRepositorioTest,AscensosTest,MigracionesTest" test
```
`Expected: AscensosTest 6 PASS; los otros dos SKIPPED (sin Docker) pero compilando`

- [ ] **Step 4: Verify completo y COMMIT 1**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · ~426 run (+6), 0 fallos, 9 skipped (+2)` — medir, no fiarse.

```
git add src/main/resources/db/migration/V26__carreras.sql src/main/java/com/gymprofit/bot/services/Ascensos.java src/main/java/com/gymprofit/bot/db/CarreraRepositorio.java src/test/java/com/gymprofit/bot/services/AscensosTest.java src/test/java/com/gymprofit/bot/db/CarreraRepositorioTest.java src/test/java/com/gymprofit/bot/db/MigracionesTest.java
git commit -m "feat(ascensos): V26, catálogo de ramas y repositorio de carreras

Cimientos de los ascensos de carrera. Los ~26 sectores del catálogo de trabajos se agrupan
en 7 ramas en un catálogo satélite (Ascensos), sin tocar Trabajos: la mayoría de sectores
no tenía puestos suficientes para una carrera propia. La tabla carreras guarda el tier
alcanzado por rama como referencia (GREATEST: nunca baja) y la V26 hace borrón del trabajo
elegido: el modelo de acceso al tier cambia y no hay jugadores reales que migrar.

Requisitos por salto (turnos, estudios, stat de la rama, coins quemados) definidos en el
catálogo con tests de integridad: todo sector mapeado, monotonía y saltos de tier huecos."
```

---

### Task 4: contador `turnos_puesto` en `Personaje`

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/db/Personaje.java`
- Modify: `src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java`

- [ ] **Step 1: Añadir el componente al record (AL FINAL)**

En `Personaje.java`, añadir el `@param` y el componente al final:

```java
 * @param turnosPuesto  turnos currados en el puesto actual; se resetea al cambiar de puesto
 */
public record Personaje(long discordId, int fuerza, int resistencia, int carisma,
                        int energia, int salud, String trabajo, Instant ultimoWork,
                        String arma, String armadura, Instant ultimoCombate,
                        int armaNivel, String armaEncanto, int estudios, int turnosPuesto) {
```

- [ ] **Step 2: Actualizar el repositorio**

En `PersonajeRepositorio`:
1. El mapeo de filas (buscar el método que construye `new Personaje(...)`) lee la columna nueva:
   `rs.getInt("turnos_puesto")` como último argumento.
2. `fijarTrabajo` resetea el contador:

```java
    public void fijarTrabajo(long discordId, String trabajo) {
        // Cambiar de puesto (elegir o ascender) resetea la antigüedad: los turnos son DEL puesto.
        ejecutar("UPDATE personajes SET trabajo = ?, turnos_puesto = 0 WHERE discord_id = ?", ps -> {
            ps.setString(1, trabajo);
            ps.setLong(2, discordId);
        });
    }
```

3. `trabajar` suma el turno en el mismo UPDATE atómico:

```java
                     "UPDATE personajes SET energia = energia - ?, ultimo_work = NOW(), "
                             + "turnos_puesto = turnos_puesto + 1 "
                             + "WHERE discord_id = ? AND energia >= ?")) {
```

- [ ] **Step 3: Arreglar todos los `new Personaje(` del código y los tests**

```
grep -rn "new Personaje(" src/ --include=*.java
```
Añadir `, 0` (o el valor que el test necesite) como último argumento en cada uno. No cambiar
ninguna otra cosa de esos tests.

- [ ] **Step 4: Compilar todo**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test
```
`Expected: PASS (los tests existentes no cambian de comportamiento: el contador aún no lo lee nadie)`

*(Sin commit aún: C2 cierra en la Task 5.)*

---

### Task 5: `TrabajoService` — gate de `elegir` y `ascender`, COMMIT 2

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/TrabajoService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

- [ ] **Step 1: Tests primero**

Leer el montaje actual de `TrabajoServiceTest` (mocks de repos). Añadir un mock
`CarreraRepositorio carreras` al montaje y pasarlo en el constructor del service en TODOS los
helpers existentes (Mockito sin stubs devuelve 0 en `tierAlcanzado` = tier de entrada, así que los
tests existentes no cambian de comportamiento **salvo** los de `elegir` con tier alto, que ahora
esperan `TIER`). Tests nuevos:

```java
    @Test
    @DisplayName("elegir: el tier de entrada de la rama es libre; un tier superior exige carrera")
    void elegirRespetaLaCarrera() {
        var u = usuarioNivel(50); // helper existente o stub equivalente con nivel sobrado
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "camarero"), "t1: entrada libre");
        assertEquals(ResultadoElegir.TIER, svc().elegir(1L, "cocinero"),
                "t2 sin carrera: hay que ascender, no elegir");

        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(2);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "cocinero"),
                "con t2 alcanzado en la rama, el t2 se elige libremente");
    }

    @Test
    @DisplayName("elegir: la rama de arte entra por t2 (no tiene t1)")
    void entradaDeRamaSinT1() {
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "disenador"),
                "t2 es la entrada de ARTE: libre aunque no haya carrera");
    }

    @Test
    @DisplayName("ascender: valida los 4 requisitos en orden y devuelve qué falta")
    void ascenderValidaRequisitos() {
        // Personaje t1 camarero con 10 turnos, 5 estudios, carisma 10 → cumple el salto a t2.
        // Ajustar el stub del personaje a la forma del montaje existente (new Personaje(...)).
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);

        // Cada requisito que falla, en orden: turnos, estudios, stat, coins.
        assertEquals(EstadoAscenso.TURNOS, svcConPersonaje(camarero(9, 5, 10)).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.ESTUDIOS, svcConPersonaje(camarero(10, 4, 10)).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.STAT, svcConPersonaje(camarero(10, 5, 9)).ascender(1L, "cocinero").estado());
        when(economia.gastar(1L, 500L, "ascenso")).thenReturn(false);
        assertEquals(EstadoAscenso.COINS, svcConPersonaje(camarero(10, 5, 10)).ascender(1L, "cocinero").estado());
    }

    @Test
    @DisplayName("ascender OK: quema los coins, fija el tier de la rama y cambia el puesto")
    void ascenderOkQuemaYFija() {
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);
        when(economia.gastar(1L, 500L, "ascenso")).thenReturn(true);

        var r = svcConPersonaje(camarero(10, 5, 10)).ascender(1L, "cocinero");
        assertEquals(EstadoAscenso.OK, r.estado());
        verify(economia).gastar(1L, 500L, "ascenso");
        verify(carreras).fijarTier(1L, "HOSTELERIA", 2);
        verify(personajes).fijarTrabajo(1L, "cocinero"); // fijarTrabajo ya resetea la antigüedad
    }

    @Test
    @DisplayName("ascender rechaza destino de otra rama, de tier equivocado o sin trabajo actual")
    void ascenderRechazaDestinosInvalidos() {
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(EstadoAscenso.SIN_TRABAJO, svcConPersonaje(sinTrabajo()).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.DESTINO, svcConPersonaje(camarero(99, 99, 99)).ascender(1L, "policia").estado(),
                "policia es de SERVICIOS, no de la rama del camarero");
        assertEquals(EstadoAscenso.DESTINO, svcConPersonaje(camarero(99, 99, 99)).ascender(1L, "panadero").estado(),
                "panadero es t1: no es el siguiente tier");
    }

    @Test
    @DisplayName("ascender en el tope de la rama devuelve TOPE")
    void ascenderEnElTope() {
        when(carreras.tierAlcanzado(1L, "ARTE")).thenReturn(3);
        assertEquals(EstadoAscenso.TOPE, svcConPersonaje(conTrabajo("actor", 99, 99, 99)).ascender(1L, "actor").estado());
    }
```

Helpers del test (`camarero(turnos, estudios, carisma)`, `conTrabajo(id, ...)`, `sinTrabajo()`,
`svcConPersonaje(...)`): construir el `Personaje` con la firma real del record (Task 4) y stubear
`personajes.obtenerOCrear`. Escribirlos junto a los helpers existentes, calcando su estilo.
El requisito de nivel de servidor en `ascender` se stubea con nivel sobrado en estos tests y se
cubre con un test más: nivel insuficiente → `EstadoAscenso.NIVEL`.

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test
```
`Expected: COMPILATION ERROR — EstadoAscenso / ascender / ResultadoElegir.TIER`

- [ ] **Step 3: Implementar en `TrabajoService`**

1. Campo y constructores: añadir `CarreraRepositorio carreras` a los DOS constructores (antes de
   `pasivos`); el corto delega. Import `com.gymprofit.bot.db.CarreraRepositorio`.
2. `ResultadoElegir` gana `TIER`. En `elegir(...)`, tras el check de nivel:

```java
        // El tier ya no es libre: o es la entrada de la rama, o lo tienes alcanzado por carrera.
        Ascensos.Rama rama = Ascensos.ramaDe(trabajo.get().sector());
        int alcanzado = Math.max(Ascensos.tierEntrada(rama), carreras.tierAlcanzado(discordId, rama.name()));
        if (trabajo.get().tier() > alcanzado) {
            return ResultadoElegir.TIER;
        }
```

3. Nuevo enum + record + método:

```java
    public enum EstadoAscenso { OK, SIN_TRABAJO, NO_EXISTE, DESTINO, TOPE, NIVEL, TURNOS, ESTUDIOS, STAT, COINS }

    /**
     * Resultado de un intento de ascenso.
     *
     * @param estado   resultado
     * @param requisito requisitos del salto intentado (para pintar cuánto falta), o {@code null}
     * @param actual   valor actual del requisito incumplido (turnos/estudios/stat), o 0
     */
    public record ResultadoAscenso(EstadoAscenso estado, Ascensos.Requisitos requisito, int actual) {
    }

    /**
     * Asciende al puesto indicado del siguiente tier de la rama del trabajo actual. Valida en orden:
     * destino (existe, misma rama, tier correcto), nivel de servidor, antigüedad, estudios, stat y
     * por último el cobro <b>atómico</b> (gastar solo si todo lo demás cumple: nunca se cobra un
     * ascenso fallido). Los coins se queman: gasto sin contrapartida en el ledger.
     */
    public ResultadoAscenso ascender(long discordId, String puestoId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return new ResultadoAscenso(EstadoAscenso.SIN_TRABAJO, null, 0);
        }
        var destino = Trabajos.porId(puestoId);
        if (destino.isEmpty()) {
            return new ResultadoAscenso(EstadoAscenso.NO_EXISTE, null, 0);
        }
        Trabajos actual = Trabajos.porId(p.trabajo()).orElseThrow();
        Ascensos.Rama rama = Ascensos.ramaDe(actual.sector());
        int tierActual = Math.max(Ascensos.tierEntrada(rama),
                carreras.tierAlcanzado(discordId, rama.name()));
        var siguiente = Ascensos.siguienteTier(rama, tierActual);
        if (siguiente.isEmpty()) {
            return new ResultadoAscenso(EstadoAscenso.TOPE, null, 0);
        }
        if (Ascensos.ramaDe(destino.get().sector()) != rama
                || destino.get().tier() != siguiente.get()) {
            return new ResultadoAscenso(EstadoAscenso.DESTINO, null, 0);
        }
        Ascensos.Requisitos req = Ascensos.requisitosPara(siguiente.get());
        UsuarioDiscord u = usuarios.obtenerOCrear(discordId);
        if (u.nivel() < destino.get().requisitoNivel()) {
            return new ResultadoAscenso(EstadoAscenso.NIVEL, req, u.nivel());
        }
        if (p.turnosPuesto() < req.turnos()) {
            return new ResultadoAscenso(EstadoAscenso.TURNOS, req, p.turnosPuesto());
        }
        if (p.estudios() < req.estudios()) {
            return new ResultadoAscenso(EstadoAscenso.ESTUDIOS, req, p.estudios());
        }
        int stat = statDelPersonaje(p, Ascensos.statDe(rama));
        if (stat < req.stat()) {
            return new ResultadoAscenso(EstadoAscenso.STAT, req, stat);
        }
        if (!economia.gastar(discordId, req.coins(), "ascenso")) {
            return new ResultadoAscenso(EstadoAscenso.COINS, req, 0);
        }
        carreras.fijarTier(discordId, rama.name(), siguiente.get());
        personajes.fijarTrabajo(discordId, puestoId); // resetea también la antigüedad
        return new ResultadoAscenso(EstadoAscenso.OK, req, 0);
    }

    /** Valor de la stat dominante de la rama en este personaje. */
    private static int statDelPersonaje(Personaje p, String stat) {
        return switch (stat) {
            case "fuerza" -> p.fuerza();
            case "resistencia" -> p.resistencia();
            case "carisma" -> p.carisma();
            default -> throw new IllegalArgumentException("stat desconocida: " + stat);
        };
    }
```

4. Actualizar el Javadoc de clase (párrafo nuevo: ascensos por rama, 4 requisitos, coins quemados).

- [ ] **Step 4: Wiring en `Main`**

Crear `CarreraRepositorio carreraRepo = new CarreraRepositorio(db.dataSource());` junto a los otros
repos y pasarlo al constructor de `TrabajoService` (con comentario del porqué, como los demás).

- [ ] **Step 5: Verlo pasar y COMMIT 2**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · +~8 tests sobre el baseline de la Task 3` — medir.

```
git add -- src/main/java/com/gymprofit/bot/db/Personaje.java src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java src/main/java/com/gymprofit/bot/services/TrabajoService.java src/test/ src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(ascensos): el tier se gana currando — gate de elegir y lógica de ascender

- Personaje gana turnos_puesto: se suma en el mismo UPDATE atómico de currar y se resetea
  en fijarTrabajo (los turnos son DEL puesto, no del jugador).
- elegir deja de ser libre: o el puesto es del tier de entrada de su rama, o tienes ese
  tier alcanzado por carrera. Nuevo resultado TIER para que el comando dirija a ascender.
- ascender valida en orden destino → nivel → antigüedad → estudios → stat de la rama →
  cobro, y solo cobra si todo lo demás cumple (nunca se paga un ascenso fallido). Los
  coins se queman: sumidero antiinflación nuevo. El tier alcanzado se guarda por rama y
  nunca baja (GREATEST), así que cambiar de rama y volver conserva la carrera."
```

---

### Task 6: `/trabajo ascender` + `/trabajo carrera` + i18n, COMMIT 3

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/TrabajoComando.java`
- Modify: `src/main/resources/messages_es.properties` y `messages_en.properties`

- [ ] **Step 1: Leer el patrón de autocompletado de `PasivosComando`**

```
sed -n '100,150p' src/main/java/com/gymprofit/bot/commands/economia/PasivosComando.java
```
(cómo implementa `autocompletar`, cómo marca la opción con `setAutoComplete(true)` y cómo responde
`replyChoices`). `RouterComandos` ya enruta a cualquier `ComandoAutocompletable`: no tocarlo.

- [ ] **Step 2: Ampliar la definición del comando**

`TrabajoComando` pasa a `implements ComandoAutocompletable`. En `definicion()`:

```java
        OptionData puesto = new OptionData(OptionType.STRING, "puesto",
                Messages.get(Messages.ES, "comando.trabajo.ascender.opcion.puesto"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trabajo.ascender.opcion.puesto"));
```

y en `.addSubcommands(...)` añadir:

```java
                        sub("ascender", "comando.trabajo.ascender.descripcion").addOptions(puesto),
                        sub("carrera", "comando.trabajo.carrera.descripcion"));
```

Ramas nuevas del `switch`: `case "ascender" -> ascender(evento, locale);` y
`case "carrera" -> carrera(evento, locale);`.

- [ ] **Step 3: Implementar `ascender`, `carrera` y `autocompletar`**

El comando necesita leer datos para autocompletar y pintar la carrera: inyectar lo necesario vía
`TrabajoService` (añadir ahí los métodos de consulta que falten, la clase del comando no toca
repos). Métodos de servicio de apoyo (añadir a `TrabajoService` con tests triviales):

```java
    /** Puestos elegibles para ascender desde el trabajo actual (para el autocompletado). */
    public List<Trabajos> opcionesAscenso(long discordId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return List.of();
        }
        Ascensos.Rama rama = Ascensos.ramaDe(Trabajos.porId(p.trabajo()).orElseThrow().sector());
        int tierActual = Math.max(Ascensos.tierEntrada(rama),
                carreras.tierAlcanzado(discordId, rama.name()));
        return Ascensos.siguienteTier(rama, tierActual)
                .map(t -> Ascensos.puestosDe(rama, t))
                .orElse(List.of());
    }
```

En el comando:

```java
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // Solo hay una opción autocompletable (puesto): responder siempre, aunque sea vacío.
        List<Command.Choice> opciones = trabajos.opcionesAscenso(evento.getUser().getIdLong())
                .stream()
                .map(t -> new Command.Choice(Messages.get(locale, "trabajo." + t.id()), t.id()))
                .limit(25)
                .toList();
        evento.replyChoices(opciones).queue();
    }

    private void ascender(SlashCommandInteractionEvent evento, Locale locale) {
        String id = evento.getOption("puesto").getAsString();
        evento.deferReply(false).queue();
        var r = trabajos.ascender(evento.getUser().getIdLong(), id);
        if (r.estado() == TrabajoService.EstadoAscenso.OK) {
            if (evento.getMember() != null) {
                asignarRolTrabajo(evento.getGuild(), evento.getMember(), id);
            }
            // Celebración pública: un ascenso se enseña (tono casual, SPEC §6).
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "ascender.titulo"),
                    Messages.get(locale, "ascender.ok",
                            evento.getUser().getAsMention(),
                            Messages.get(locale, "trabajo." + id),
                            r.requisito().coins())).build()).queue();
            return;
        }
        String mensaje = switch (r.estado()) {
            case SIN_TRABAJO -> Messages.get(locale, "ascender.sintrabajo");
            case NO_EXISTE, DESTINO -> Messages.get(locale, "ascender.destino");
            case TOPE -> Messages.get(locale, "ascender.tope");
            case NIVEL -> Messages.get(locale, "ascender.nivel", r.actual());
            case TURNOS -> Messages.get(locale, "ascender.turnos", r.actual(), r.requisito().turnos());
            case ESTUDIOS -> Messages.get(locale, "ascender.estudios", r.actual(), r.requisito().estudios());
            case STAT -> Messages.get(locale, "ascender.stat", r.actual(), r.requisito().stat());
            case COINS -> Messages.get(locale, "ascender.coins", r.requisito().coins());
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
        // Los errores del ascenso son efímeros: son tu progreso privado, no espectáculo.
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }
```

`carrera(evento, locale)`: `deferReply(false)`, leer del service un método nuevo
`infoCarrera(discordId)` (record `InfoCarrera(Rama rama, int tierAlcanzado, String puestoActual,
int turnosPuesto, Optional<Integer> siguiente, Ascensos.Requisitos requisitos)` — construirlo con
la misma lógica de `opcionesAscenso`) y pintar embed público: rama (i18n `rama.<minúsculas>`), tier,
puesto actual, y checklist `✅/❌` por requisito comparando `turnosPuesto`/`estudios`/stat/saldo
contra `requisitos` (usar `economia.saldo(discordId)` para el de coins). Si `siguiente` está vacío,
línea `carrera.tope`. Si `puestoActual` es null, aviso `carrera.sintrabajo` con la lista de tiers
de entrada.

En `lista(...)`: añadir `evento.deferReply(false)` al principio (ahora necesita BD) y, por cada
puesto no elegible (`t.tier() > alcanzado de su rama`, calculado con un método de servicio
`tierAlcanzadoEn(discordId, rama)` que expone el `Math.max` de entrada), anteponer 🔒 usando la
clave `trabajos.linea.bloqueada` en vez de `trabajos.linea`. El envío pasa de `replyEmbeds` a
`getHook().sendMessageEmbeds`.

- [ ] **Step 4: Claves i18n (ES y EN, mismo conjunto)**

`messages_es.properties` (junto al bloque `work.*`/`trabajos.*`):

```properties
comando.trabajo.ascender.descripcion=Asciende al siguiente puesto de tu carrera
comando.trabajo.ascender.opcion.puesto=Puesto al que quieres ascender
comando.trabajo.carrera.descripcion=Tu carrera: rama, tier y qué te falta para ascender
ascender.titulo=📈 ¡Ascenso!
ascender.ok={0} asciende a **{1}** 🎉 Se ha dejado {2} 🪙 en el papeleo. ¡A seguir currando!
ascender.sintrabajo=😅 Primero necesitas un trabajo: `/trabajo elegir`.
ascender.destino=🤔 Ese puesto no es el siguiente paso de tu carrera. Mira las opciones del autocompletado.
ascender.tope=🏔️ Estás en la cima de tu rama: no hay más ascensos… de momento.
ascender.nivel=📊 Te falta nivel de servidor: tienes {0} y el puesto pide más. Sigue activo.
ascender.turnos=⏳ Antigüedad insuficiente: llevas **{0}**/{1} turnos en tu puesto.
ascender.estudios=📚 Te faltan estudios: tienes **{0}**/{1}. Pasa por /estudiar.
ascender.stat=💪 Tu cuerpo no acompaña: stat de la rama en **{0}**/{1}. Entrena con /entrenar.
ascender.coins=🪙 No te llega: el ascenso cuesta **{0}** coins.
carrera.titulo=🧭 Tu carrera
carrera.sintrabajo=Aún no tienes trabajo. Empieza por `/trabajo elegir` (los puestos de entrada están abiertos).
carrera.cuerpo=**Rama:** {0}\n**Tier alcanzado:** {1}\n**Puesto:** {2}\n**Turnos en el puesto:** {3}
carrera.requisitos=\n**Para ascender a tier {0}:**\n{1}
carrera.requisito.linea={0} {1}: **{2}**/{3}
carrera.tope=\n🏔️ Has llegado a la cima de tu rama.
trabajos.linea.bloqueada=🔒 `{0}` · **{1}** ({2}) · {3}-{4} 🪙 · req. nivel {5} — se llega por ascenso
rama.salud=🩺 Salud y ciencia
rama.tecnica=🔧 Técnica
rama.transporte=🚚 Transporte
rama.hosteleria=🍳 Hostelería y comercio
rama.negocios=💼 Negocios
rama.arte=🎬 Arte y medios
rama.servicios=🧹 Servicios públicos
```

`messages_en.properties` — las mismas claves traducidas con tono natural equivalente
(p. ej. `ascender.ok={0} got promoted to **{1}** 🎉 The paperwork cost {2} 🪙. Back to the grind!`);
traducir TODAS, ninguna copiada en español.

- [ ] **Step 5: Verify y COMMIT 3**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS` — medir el total.

```
git add -- src/main/java/com/gymprofit/bot/commands/economia/TrabajoComando.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties src/main/java/com/gymprofit/bot/services/TrabajoService.java src/test/
git commit -m "feat(ascensos): /trabajo ascender y /trabajo carrera

/trabajo pasa de 3 a 5 subcomandos (0 comandos top-level nuevos, ADR-011). ascender lleva
autocompletado con los puestos del siguiente tier de TU rama (mismo patrón que /pasivos):
el error de puesto equivocado casi no puede ocurrir porque las opciones ya vienen
filtradas. El ascenso logrado es un embed público de celebración con mención; los fallos
son efímeros con el dato exacto de lo que falta. /trabajo carrera pinta rama, tier,
antigüedad y la checklist de requisitos del siguiente salto. /trabajo lista marca con
candado los puestos que se consiguen ascendiendo, no eligiendo.

Novedades:
- Los trabajos ya tienen carrera: se empieza por abajo y se asciende currando. Cada
  ascenso pide antigüedad, estudios, la stat de tu rama y un pago que desaparece.
- /trabajo carrera te dice exactamente qué te falta para el siguiente puesto.
- Cambiar de rama no borra nada: si vuelves, tu carrera te espera."
```

---

### Task 7: docs, intro del canal y verify final — COMMIT 4

**Files:**
- Modify: `docs/architecture.md`, `docs/decisions.md`, `CHANGELOG.md`, `README.md`, `README.en.md`
- Modify: `src/main/resources/messages_es.properties` y `messages_en.properties`
  (`intro.economia.desc`, sección 💼 de trabajo)

- [ ] **Step 1: Intro del canal** — en `intro.economia.desc` (ES), localizar la sección de trabajo
(buscar `**💼` en la clave) y añadir al final de esa sección (mismo formato `\n> ` que el resto):

```
\n> **📈 Ascensos:** se empieza por abajo y se sube currando: `/trabajo ascender` cuando cumplas antigüedad, estudios, stat y precio. `/trabajo carrera` te dice qué te falta.
```

EN, equivalente:

```
\n> **📈 Promotions:** start at the bottom and climb by working: `/trabajo ascender` once you meet seniority, studies, stat and price. `/trabajo carrera` shows what you're missing.
```

Comprobar que la clave sigue siendo una sola línea lógica y no supera 4096 chars desescapada.

- [ ] **Step 2: `docs/architecture.md`** — en la sección del RPG económico: añadir `Ascensos` a la
lista de catálogos en código; viñeta nueva tras la de efectos pasivos:

```markdown
- **Ascensos de carrera**: los sectores del catálogo de trabajos se agrupan en 7 ramas
  (`Ascensos`, satélite de `Trabajos`); el tier deja de ser de libre acceso y se gana currando.
  La tabla `carreras` guarda el tier alcanzado por rama (referencia con `GREATEST`: nunca baja) y
  `personajes.turnos_puesto` cuenta la antigüedad del puesto. Cada salto exige antigüedad,
  estudios, la stat dominante de la rama y un coste en coins que se **quema** (sumidero
  antiinflación). El cobro es lo último que se valida: nunca se paga un ascenso fallido.
```

Actualizar la línea de migraciones a `V6–V26` añadiendo «carreras».

- [ ] **Step 3: `docs/decisions.md`** — comprobar el último ADR (`grep -n "^## ADR" docs/decisions.md | tail -1`,
debería ser ADR-013) y añadir el siguiente con el estilo del archivo (`## ADR-0NN — título` +
`**Estado:**`):

```markdown
## ADR-014 — ramas de carrera para los ascensos

**Estado:** aceptada e implementada.

**Contexto.** Cualquier puesto del catálogo se elegía directamente con solo nivel de servidor: un
nivel 30 pasaba de parado a CEO en un comando. 26 sectores para ~50 puestos hacían inviable una
carrera por sector (la mayoría tiene 1-2 puestos).

**Decisión.** (1) Carrera por **rama**: 7 ramas agrupan los sectores en un catálogo satélite
`Ascensos` — `Trabajos` no se toca (precedente: `Pasivos`/`Camas`/`Picos`/`Cofres`). (2) Cuatro
requisitos por salto — antigüedad en el puesto, estudios, stat dominante de la rama y coins que se
queman — validados antes del cobro atómico. (3) La carrera **se conserva por rama** y el tier
alcanzado nunca baja (`GREATEST`): cambiar de rama es explorar, no un castigo. (4) Ramas con
huecos saltan al siguiente tier existente y las que topan por debajo de t4 acaban antes: el
catálogo puede crecer por rama sin tocar el diseño. (5) Migración V26 con borrón del trabajo
elegido: no había jugadores reales que migrar.

**Consecuencias.** El late-game gana un destino más (t4 cuesta 50 000 coins quemados) y los
estudios y las stats ganan un segundo uso. `/trabajo` pasa a 5 subcomandos. Queda fuera el rango
interno por puesto (descartado) y `/trabajo dimitir` (cambiar = elegir otro, YAGNI).
```

- [ ] **Step 4: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`:

```markdown
- **Ascensos de carrera** (`/trabajo ascender` · `/trabajo carrera`): los trabajos se agrupan en
  7 ramas y el tier se gana currando. Cada ascenso exige antigüedad en el puesto, estudios, la
  stat de la rama y un pago que se quema. La carrera se conserva por rama al cambiar de trabajo, y
  `/trabajo lista` marca con 🔒 lo que se consigue ascendiendo. Migración `V26__carreras`.
```

- [ ] **Step 5: READMEs** — actualizar la fila de `/trabajo` en la tabla de comandos de
`README.md` y `README.en.md` (subcomandos `lista · elegir · currar · ascender · carrera`).

- [ ] **Step 6: Verify final**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS, 0 fallos.` **Pegar la salida real en la respuesta** (definición de
terminado del repo).

- [ ] **Step 7: COMMIT 4**

```
git add -- docs/architecture.md docs/decisions.md CHANGELOG.md README.md README.en.md src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "docs(ascensos): intro del canal, ADR-014 y documentación

Cierra los ascensos de carrera con la documentación que los hace descubribles: la sección
de trabajo de la intro de economía explica cómo se asciende, la ADR-014 recoge el porqué
de las ramas (26 sectores para 50 puestos no daban carrera) y de quemar los coins, y la
tabla de comandos refleja los 5 subcomandos de /trabajo.

Novedades:
- El canal de economía ya explica los ascensos: antigüedad, estudios, stat y precio.
- Consejo: /trabajo carrera antes de gastar — te dice exactamente qué te falta."
```

---

## Despliegue (decírselo al usuario al terminar)

- Subcomandos nuevos → **reiniciar el bot** (registro en `onGuildReady`; `/setup` no registra
  comandos).
- Intro de economía tocada → **`/setup` normal** tras reiniciar (edita la intro fijada).
- La V26 se aplica sola al arrancar (Flyway) e incluye el borrón del trabajo elegido: avisar de
  que hay que volver a `/trabajo elegir`.

## Self-review del plan

- **Cobertura de la spec:** modelo por rama (T2/T5), 4 requisitos (T2/T5), conservación por rama
  con GREATEST (T3), elección del puesto con autocompletado (T6), migración con borrón (T1),
  tier de entrada libre y ramas con hueco/tope (T2/T5/T6), coins quemados vía ledger (T5),
  `/trabajo carrera` con checklist (T6), 🔒 en lista (T6), docs+ADR+intro (T7). Los importes y la
  tabla sector→rama de la spec están literales en T2.
- **Placeholders:** los tres puntos de «verificar antes de escribir» están marcados con su comando
  exacto (FK de V25, método de borrado de usuario, patrón de autocompletado de PasivosComando);
  el resto lleva código completo.
- **Consistencia de tipos:** `Ascensos.Rama`/`Requisitos` (T2) se usan en T5/T6 con las mismas
  firmas; `carreras.tierAlcanzado(long, String)` recibe `rama.name()` en todos los usos;
  `ResultadoAscenso(estado, requisito, actual)` coincide entre T5 y T6.
