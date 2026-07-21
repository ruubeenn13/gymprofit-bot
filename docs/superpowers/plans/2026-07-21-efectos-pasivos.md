# Efectos pasivos de equipo y bienes — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar efectos pasivos reales (sueldo, cooldown, XP, energía, minería y combate) a los 30 ítems inertes del catálogo —20 de `EQUIPO` y los 10 vehículos de `BIEN`— mediante un sistema de ranuras equipables con topes globales.

**Architecture:** Catálogo paralelo `services/Pasivos` (patrón `Picos`/`Camas`/`Cofres`: se empareja por `itemId` y `Items.java` **no se toca**), tabla `pasivos_equipados` que guarda solo una **referencia** (ranura → itemId) y `PasivoService` que **recalcula siempre contra el inventario**, de modo que vender/regalar/publicar un ítem apaga su bono sin hooks de limpieza en `VentaService`, `RegaloService`, `MercadoService`, `TruequeService` ni `RoboService`. Cada service consumidor (`TrabajoService`, `XpService`, `MineriaService`, `BatallaService`, `EnergiaJob`) lee **solo el tipo que le importa** vía `bonoDe(...)`/`bonosDe(...)`, con la suma y el topado en funciones puras estáticas testeables sin BD.

**Tech Stack:** Java 21, JDA 5, Flyway (V25), JDBC plano sobre HikariCP, JUnit 5 + Mockito + Testcontainers (MySQL 8.0).

**Spec:** [`docs/superpowers/specs/2026-07-21-efectos-pasivos-design.md`](../specs/2026-07-21-efectos-pasivos-design.md) (commits `1a95f1c` + `516b40a`)

---

## Notas para el ejecutor (leer antes de cada tarea)

Los implementadores son subagentes sin contexto previo. Todo lo que hace falta saber está aquí.

### Build

- **Comando:** `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd <goal>`.
  El `java` por defecto de esta máquina es **8**: sin fijar `JAVA_HOME` el build falla con
  `UnsupportedClassVersionError` o `invalid target release: 21`.
- **Siempre `clean verify`, nunca `verify` a secas.** Un jar sombreado viejo en `target/` hace
  que `maven-shade-plugin` reviente con `ZipException: invalid LOC header (bad signature)`.
  Para correr un solo test sí vale `test` sin `clean` (no empaqueta).
- **Nunca dos builds de Maven a la vez.** El repositorio local `~/.m2` no está pensado para
  accesos concurrentes y el segundo build falla con checksums corruptos.
- **PowerShell 5.1:** un `-D` con comas hay que **entrecomillarlo**:
  `.\mvnw.cmd "-Dtest=PasivosTest,PasivoServiceTest" test`. Sin comillas, PowerShell parte el
  argumento por la coma y Maven recibe basura.
- **Testcontainers:** `MigracionesTest`, `PasivoRepositorioTest` y el resto de `db/*Test` se
  **saltan en local** (`assumeTrue(DockerClientFactory.instance().isDockerAvailable())`, el
  transporte npipe de Docker Desktop no es alcanzable desde el cliente Java) y **corren en CI
  (Linux)**. Un `verify` verde en local **NO** valida esos tests: se marcan como *pendientes de
  CI* y no se dan por probados hasta que el workflow esté en verde.

### Baseline de tests

El último plan cerrado (`2026-07-20-consultas-ejercicios.md`) dejó el proyecto en **364 tests**.
Ese es el número de partida esperado, pero **el ejecutor debe re-medirlo** antes de la Task 1:

```
.\mvnw.cmd clean verify
```

y anotar el `Tests run: N` del resumen de Surefire. Cada tarea de este plan indica cuántos tests
**añade**; el total esperado tras cada tarea es `baseline_medido + suma de los añadidos hasta ahí`.
Si el baseline medido no es 364, se usa el medido y se ajustan los totales (los deltas por tarea
siguen siendo válidos).

### Convenciones obligatorias del repo

- **Cabecera en cada archivo:** Javadoc de clase (o comentario de bloque en SQL) explicando qué es
  y qué papel juega. Aplica también a `.sql`, `.yaml` y `pom.xml`.
- **Comentarios inline con el *porqué*,** no el qué. El repo tiene ejemplos muy explícitos
  (ver `DescansoRepositorio.registrarConsumo` o el comentario de la fatiga en `TrabajoService`).
- **Javadoc en todo método público no trivial.**
- **i18n:** todo texto visible sale de `Messages`. Cada clave nueva va en
  `src/main/resources/messages_es.properties` **y** `src/main/resources/messages_en.properties`.
  Prohibido hardcodear strings visibles. El inglés **no es traducción literal**: tono equivalente.
- **Embeds:** ninguno se construye a mano; todos por `EmbedFactory.base(...)` /
  `EmbedFactory.aviso(...)`. Tipo `ECONOMIA` para todo lo de `/pasivos`.
- **Visibilidad:** respuestas **públicas** por defecto (`deferReply(false)`); **efímero solo los
  errores** de validación.
- **Un test por service nuevo.** Azar inyectable, funciones puras `public static`.
- **Migraciones Flyway:** nunca se edita una aplicada. `MigracionesTest` se actualiza con **cada**
  migración nueva.

### Commits

- **Sin** trailer `Co-Authored-By` y **sin** pie "Generated with Claude Code".
- Mensaje largo y estructurado con una sección **`Novedades:`** (alimenta el apartado Noticias de
  la app).
- **Documentación viva:** cada commit incluye, en el mismo commit, la doc afectada por el cambio
  (README de la carpeta tocada, `docs/architecture.md`, `CHANGELOG.md`, tablas de comandos de
  `README.md`/`README.en.md`).
- **Prohibido inventar hashes.** Si algo debe referenciar un commit, se obtiene con
  `git rev-parse --short HEAD` **después** de commitear.

### Datos verificados contra el código real (no dar por buenos los del spec sin leer esto)

| Cosa | Valor real verificado | Dónde |
|---|---|---|
| Última migración | **`V24__ejercicio_dia.sql`** → el hueco libre es **V25** | `src/main/resources/db/migration/` |
| `EnergiaJob.REGEN` | **5** (no 10) | `jobs/EnergiaJob.java:29` |
| `MineriaService.CANTIDAD_MAX` | **5**, tope duro en `tirar` | `services/MineriaService.java:31` |
| `TrabajoService.COOLDOWN_WORK` | `Duration.ofMinutes(60)` | `services/TrabajoService.java:21` |
| `BONO_ESTUDIOS_MAX` | `0.25` | `services/TrabajoService.java:27` |
| `BatallaService.nuevaSesion` | **`private`** | `services/BatallaService.java:234` |
| `XpService` deps | solo `UsuarioDiscordRepositorio` | `services/XpService.java:13` |
| Comandos de primer nivel | **59** hoy (`grep -c "Commands.slash("` en `src/main`) → **60** con `/pasivos`; el límite de Discord es 100 | — |
| Autocompletado | **no existe infraestructura**: `RouterComandos` no sobrescribe `onCommandAutoCompleteInteraction` y no hay ningún `setAutoComplete(true)` en el repo | `commands/RouterComandos.java` |
| `Personaje` (record) | 14 componentes; por eso las ranuras van en tabla aparte | `db/Personaje.java` |
| Orden de wiring | `xpService` se crea en `Main.java:320`, **antes** que `inventarioRepo` (`:407`) | `Main.java` |

### Desviaciones conscientes respecto al spec (justificadas; no son erratas)

1. **No se crea `db/PasivoEquipado`.** El spec lo insinuaba junto a un `List<PasivoDe> conTipo(Tipo)`
   pensado para `EnergiaJob`. Pero el segundo pase de energía se resuelve **enteramente en SQL**
   (no trae filas a memoria), así que `conTipo` no existe y la forma natural del repositorio es
   `Map<Integer, String>` (ranura → itemId). Un record de dos campos no aportaría nada.
2. **No se crean claves `pasivo.<id>.nombre`.** El nombre localizado de cada ítem ya vive en
   `item.<id>` (los 30 ids son ítems de `Items`). Duplicarlo crearía dos fuentes de verdad para
   el mismo texto. Solo se añaden las **30** claves `pasivo.<id>.desc` por idioma.
3. **`quitar` devuelve `ResultadoQuitar`, no `boolean`.** El comando necesita saber **qué** ítem
   salió y **cómo quedan los totales** para pintar la respuesta; con un `boolean` habría que hacer
   dos consultas más desde el comando.
4. **El test 7 del spec («monotonía razonable con el precio») se sustituye.** Es *inaplicable*: los
   nueve tipos tienen unidades incomparables y la regla se rompe con el propio catálogo aprobado
   (`moto_agua`, 60 000 coins, «pesa» menos que `herramientas`, 800 coins, porque uno es ocio y el
   otro es trabajo; y el `cohete`, 3 000 000, pesa menos que el `helicoptero`, 350 000, porque el
   combate es marginal a propósito). En su lugar van **dos** tests que sí pillan el typo `0.7` por
   `0.07` y además son más fuertes: **rangos por tipo** y **pin exacto de la tabla de balance del
   spec** (la suma de los 4 mejores de cada tipo). Ver Task 2.
5. **El autocompletado de `/pasivos equipar` exige infraestructura nueva.** Se añade una interfaz
   `ComandoAutocompletable` y el enrutado en `RouterComandos` (Task 5). Sin ella no hay forma de
   cumplir el requisito del spec (25 choices máximo en Discord < 30 ítems, así que `addChoice`
   tampoco vale).

---

## Estructura de archivos

```
CREA
  src/main/resources/db/migration/V25__pasivos_equipados.sql   Tabla de ranuras (referencia, no derecho)
  src/main/java/com/gymprofit/bot/services/Pasivos.java        Catálogo: 30 ítems, 9 tipos, topes
  src/main/java/com/gymprofit/bot/db/PasivoRepositorio.java    JDBC de pasivos_equipados
  src/main/java/com/gymprofit/bot/services/PasivoService.java  Ranuras, filtrado por inventario, suma y topado
  src/main/java/com/gymprofit/bot/commands/ComandoAutocompletable.java  Contrato de autocompletado
  src/main/java/com/gymprofit/bot/commands/economia/PasivosComando.java /pasivos ver|equipar|quitar
  src/main/java/com/gymprofit/bot/commands/economia/PasivosTexto.java   Formateo compartido con /perfil
  src/test/java/com/gymprofit/bot/services/PasivosTest.java             Integridad del catálogo
  src/test/java/com/gymprofit/bot/db/PasivoRepositorioTest.java         Testcontainers
  src/test/java/com/gymprofit/bot/services/PasivoServiceTest.java       Lógica con mocks
  src/test/java/com/gymprofit/bot/commands/economia/PasivosTextoTest.java  Vistas estáticas

MODIFICA
  src/test/java/com/gymprofit/bot/db/MigracionesTest.java      Asserción de V25
  src/main/java/com/gymprofit/bot/services/TrabajoService.java conBonoPasivos + cooldownEfectivo
  src/main/java/com/gymprofit/bot/services/XpService.java      conBonoPasivos (suelo +1)
  src/main/java/com/gymprofit/bot/services/MineriaService.java cantidad + CANTIDAD_MAX + durabilidad
  src/main/java/com/gymprofit/bot/services/BatallaService.java conPasivos (pura) dentro de nuevaSesion
  src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java regenerarEnergiaPasivos (2.º pase)
  src/main/java/com/gymprofit/bot/jobs/EnergiaJob.java         Llama al 2.º pase
  src/main/java/com/gymprofit/bot/commands/RouterComandos.java Enruta autocompletado
  src/main/java/com/gymprofit/bot/commands/economia/PerfilComando.java  Línea ✨ Pasivos
  src/main/java/com/gymprofit/bot/commands/economia/MinarComando.java   Aviso de durabilidad ahorrada
  src/main/java/com/gymprofit/bot/Main.java                    Wiring (pasivoRepo/pasivoService antes de xpService)
  src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java  Topic de 💰・economía
  src/main/resources/messages_es.properties                    i18n ES
  src/main/resources/messages_en.properties                    i18n EN
  src/test/java/com/gymprofit/bot/services/{TrabajoService,XpService,MineriaService,BatallaService}Test.java
  README.md · README.en.md · CHANGELOG.md · docs/architecture.md
  src/main/java/com/gymprofit/bot/db/README.md · services/README.md
```

---

## Tabla de tareas → commits

| # | Tarea | Commit |
|---|---|---|
| 1 | Migración V25 `pasivos_equipados` | **C1** |
| 2 | Catálogo `services/Pasivos` + `PasivosTest` | **C1** |
| 3 | `db/PasivoRepositorio` + test Testcontainers | **C1** |
| 4 | `services/PasivoService` + `PasivoServiceTest` | **C1** |
| 5 | `/pasivos ver\|equipar\|quitar` + i18n + autocompletado + wiring | **C2** |
| 6 | Integración `TrabajoService` (sueldo + cooldown) | **C3** |
| 7 | Integración `XpService` | **C3** |
| 8 | Integración `MineriaService` (cantidad + tope + durabilidad) | **C3** |
| 9 | Integración combate (`BatallaService`) | **C4** |
| 10 | Integración `EnergiaJob` (segundo pase) | **C4** |
| 11 | Línea `✨ Pasivos` en `/perfil ver` | **C5** |
| 12 | Intro de `💰・economía` + docs + `clean verify` final | **C6** |

---

### Task 1: Migración V25 `pasivos_equipados`

**Files:**
- Create: `src/main/resources/db/migration/V25__pasivos_equipados.sql`
- Modify: `src/test/java/com/gymprofit/bot/db/MigracionesTest.java`

**Verificado:** la última migración del repo es `V24__ejercicio_dia.sql`, así que **V25 es el hueco
libre**. Comprobarlo antes de escribir con
`Get-ChildItem src/main/resources/db/migration | Sort-Object Name`.

- [ ] **Step 1: Escribir la migración**

Crear `src/main/resources/db/migration/V25__pasivos_equipados.sql` con exactamente:

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
    -- PK (discord_id, ranura): equipar es un INSERT … ON DUPLICATE KEY UPDATE, así que reemplazar el
    -- contenido de una ranura es una sola sentencia atómica.
    PRIMARY KEY (discord_id, ranura),
    -- La regla «un ítem no puede ocupar dos ranuras» queda garantizada EN EL ESQUEMA, no solo en el
    -- service: si dos comandos simultáneos corren la carrera, gana la base de datos.
    CONSTRAINT uq_pasivos_item UNIQUE (discord_id, item_id),
    CONSTRAINT fk_pasivos_equipados_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Ítems con efecto pasivo equipados por jugador, por ranura.';
```

No se guarda el nivel de desbloqueo: se deriva del nivel actual con `PasivoService.ranurasDe(nivel)`.
Si el nivel bajara (hoy no puede pasar), las ranuras sobrantes dejan de contar sin borrarse, y
recuperar el nivel las devuelve intactas.

- [ ] **Step 2: Actualizar `MigracionesTest`**

En `src/test/java/com/gymprofit/bot/db/MigracionesTest.java`, justo después del bloque de la
asserción de V24 (`"ejercicio_dia debe existir y arrancar vacía"`), añadir:

```java
                // V25 aplicada: las ranuras de pasivos existen y arrancan vacías.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM pasivos_equipados"),
                        "pasivos_equipados debe existir y arrancar vacía");
                // El UNIQUE que impide el mismo ítem en dos ranuras debe existir en el esquema (el
                // service lo comprueba antes para dar un error bonito, pero la garantía dura es esta).
                assertEquals(1, contar(st, "SELECT COUNT(*) FROM information_schema.statistics "
                                + "WHERE table_name = 'pasivos_equipados' "
                                + "AND index_name = 'uq_pasivos_item' AND seq_in_index = 1"),
                        "pasivos_equipados debe tener el UNIQUE (discord_id, item_id)");
```

- [ ] **Step 3: Comprobar que compila y que el test se salta en local**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=MigracionesTest" test
```
`Expected: Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` (se salta: Docker npipe).
**Queda pendiente de CI.** No dar la migración por validada hasta ver el workflow en verde.

---

### Task 2: Catálogo `services/Pasivos`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/Pasivos.java`
- Create: `src/test/java/com/gymprofit/bot/services/PasivosTest.java`

- [ ] **Step 1: Escribir el test de integridad primero**

Crear `src/test/java/com/gymprofit/bot/services/PasivosTest.java`:

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.services.Pasivos.Bono;
import com.gymprofit.bot.services.Pasivos.Pasivo;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Barridos de integridad del catálogo de pasivos (sin mocks, al estilo de {@code CamasTest} y
 * {@code RarezaTest}). La tabla de bonos ES el entregable de este módulo: sin estos tests, un typo en
 * una magnitud pasaría en verde y solo se vería jugando (o peor: rompiendo la economía).
 */
class PasivosTest {

    /** Los BIEN que no son camas, es decir los 10 vehículos. */
    private static List<String> vehiculos() {
        Set<String> camas = Camas.CATALOGO.stream().map(Camas::itemId).collect(Collectors.toSet());
        return Items.CATALOGO.stream()
                .filter(i -> i.categoria() == Items.Categoria.BIEN)
                .map(Items::id)
                .filter(id -> !camas.contains(id))
                .toList();
    }

    /** Los 20 ítems de categoría EQUIPO. */
    private static List<String> equipo() {
        return Items.CATALOGO.stream()
                .filter(i -> i.categoria() == Items.Categoria.EQUIPO)
                .map(Items::id).toList();
    }

    @Test
    @DisplayName("1 · los 30 ids del catálogo existen en Items")
    void todosLosIdsExistenEnItems() {
        for (Pasivo p : Pasivos.CATALOGO) {
            assertTrue(Items.porId(p.itemId()).isPresent(),
                    "el pasivo " + p.itemId() + " debe existir en Items");
        }
    }

    @Test
    @DisplayName("2 · todos son EQUIPO o vehículo BIEN; ninguna cama tiene pasivo")
    void ningunaCamaTienePasivo() {
        Set<String> camas = Camas.CATALOGO.stream().map(Camas::itemId).collect(Collectors.toSet());
        for (Pasivo p : Pasivos.CATALOGO) {
            Items i = Items.porId(p.itemId()).orElseThrow();
            assertFalse(camas.contains(p.itemId()),
                    p.itemId() + " es una cama: su efecto ya lo da Camas, sería doble recompensa");
            assertTrue(i.categoria() == Items.Categoria.EQUIPO
                            || i.categoria() == Items.Categoria.BIEN,
                    p.itemId() + " debe ser EQUIPO o BIEN, no " + i.categoria());
        }
    }

    @Test
    @DisplayName("3 · cobertura total: los 20 EQUIPO y los 10 vehículos tienen pasivo")
    void coberturaTotal() {
        Set<String> conPasivo = Pasivos.CATALOGO.stream().map(Pasivo::itemId)
                .collect(Collectors.toSet());
        Set<String> esperados = new HashSet<>(equipo());
        esperados.addAll(vehiculos());
        assertEquals(30, esperados.size(), "deben ser 20 EQUIPO + 10 vehículos BIEN");
        assertEquals(esperados, conPasivo,
                "si se añade un EQUIPO o un vehículo a Items sin pasivo, este test debe fallar");
        assertEquals(30, Pasivos.CATALOGO.size(), "el catálogo tiene exactamente 30 entradas");
    }

    @Test
    @DisplayName("4 · sin ids duplicados")
    void sinDuplicados() {
        Set<String> vistos = new HashSet<>();
        for (Pasivo p : Pasivos.CATALOGO) {
            assertTrue(vistos.add(p.itemId()), "id duplicado en el catálogo: " + p.itemId());
        }
    }

    @Test
    @DisplayName("5 · ningún ítem alcanza por sí solo el tope de ningún tipo")
    void ningunItemSaturaSolo() {
        for (Pasivo p : Pasivos.CATALOGO) {
            for (Bono b : p.bonos()) {
                assertTrue(b.magnitud() < Pasivos.TOPES.get(b.tipo()),
                        p.itemId() + " llega solo al tope de " + b.tipo()
                                + ": el sistema debe exigir combinar al menos dos ranuras");
            }
        }
    }

    @Test
    @DisplayName("6 · magnitudes > 0 y todos los tipos con fuente y tope")
    void magnitudesPositivasYTiposCubiertos() {
        for (Pasivo p : Pasivos.CATALOGO) {
            assertFalse(p.bonos().isEmpty(), p.itemId() + " no puede tener cero bonos");
            for (Bono b : p.bonos()) {
                assertTrue(b.magnitud() > 0, p.itemId() + " tiene un bono no positivo de " + b.tipo());
            }
        }
        for (Tipo t : Tipo.values()) {
            assertTrue(Pasivos.TOPES.containsKey(t), "falta el tope de " + t);
            assertFalse(Pasivos.fuentesDe(t).isEmpty(), "ningún ítem da " + t);
        }
    }

    @Test
    @DisplayName("7a · las magnitudes caen en el rango plausible de su tipo (caza el 0.7 por 0.07)")
    void magnitudesEnRango() {
        for (Pasivo p : Pasivos.CATALOGO) {
            for (Bono b : p.bonos()) {
                if (Pasivos.esPorcentual(b.tipo())) {
                    assertTrue(b.magnitud() <= 0.15,
                            p.itemId() + ": " + b.tipo() + " porcentual fuera de rango ("
                                    + b.magnitud() + "); ¿un 0.7 donde iba 0.07?");
                    assertEquals(Math.rint(b.magnitud() * 100), b.magnitud() * 100, 1e-9,
                            p.itemId() + ": " + b.tipo() + " no es un % entero");
                } else {
                    assertEquals(Math.rint(b.magnitud()), b.magnitud(), 1e-9,
                            p.itemId() + ": " + b.tipo() + " plano debe ser entero");
                    assertTrue(b.magnitud() >= 1 && b.magnitud() <= 5,
                            p.itemId() + ": " + b.tipo() + " plano fuera de [1,5]");
                }
            }
        }
    }

    /** Suma de las 4 magnitudes más altas de un tipo: el máximo teórico con las 4 ranuras. */
    private static double mejores4(Tipo tipo) {
        return Pasivos.fuentesDe(tipo).values().stream()
                .sorted(Comparator.reverseOrder()).limit(4)
                .mapToDouble(Double::doubleValue).sum();
    }

    @Test
    @DisplayName("7b · el mejor build de 4 ranuras coincide con la tabla de balance de la spec")
    void tablaDeBalanceDeLaSpec() {
        assertEquals(0.35, mejores4(Tipo.SUELDO), 1e-9, "jet 11 + avioneta 9 + traje 8 + coche_lujo 7");
        assertEquals(0.38, mejores4(Tipo.COOLDOWN_WORK), 1e-9, "jet 11 + helicoptero 10 + coche 9 + moto 8");
        assertEquals(0.24, mejores4(Tipo.XP), 1e-9, "yate 7 + jet 6 + avioneta 6 + reloj 5");
        assertEquals(7.0, mejores4(Tipo.ENERGIA_REGEN), 1e-9, "yate 3 + moto_agua 2 + 1 + 1");
        assertEquals(6.0, mejores4(Tipo.MINERIA_CANTIDAD), 1e-9, "dron 2 + camion 2 + 1 + 1");
        assertEquals(0.38, mejores4(Tipo.MINERIA_DURABILIDAD), 1e-9, "helicoptero 12 + dron 10 + 8 + 8");
        assertEquals(11.0, mejores4(Tipo.COMBATE_ATAQUE), 1e-9, "solo hay 3 fuentes: 5 + 3 + 3");
        assertEquals(9.0, mejores4(Tipo.COMBATE_DEFENSA), 1e-9, "cohete 4 + zapatillas 2 + coche_lujo 2 + uniforme 1");
        assertEquals(0.07, mejores4(Tipo.CRITICO), 1e-9, "cohete 3 + coche_lujo 2 + gafas 1 + traje 1");
    }

    @Test
    @DisplayName("8 · los topes son los de la spec y cuatro quedan holgados a propósito")
    void topesDeLaSpec() {
        assertEquals(0.30, Pasivos.TOPES.get(Tipo.SUELDO), 1e-9);
        assertEquals(0.25, Pasivos.TOPES.get(Tipo.COOLDOWN_WORK), 1e-9);
        assertEquals(0.20, Pasivos.TOPES.get(Tipo.XP), 1e-9);
        assertEquals(5.0, Pasivos.TOPES.get(Tipo.ENERGIA_REGEN), 1e-9);
        assertEquals(3.0, Pasivos.TOPES.get(Tipo.MINERIA_CANTIDAD), 1e-9);
        assertEquals(0.40, Pasivos.TOPES.get(Tipo.MINERIA_DURABILIDAD), 1e-9);
        assertEquals(12.0, Pasivos.TOPES.get(Tipo.COMBATE_ATAQUE), 1e-9);
        assertEquals(10.0, Pasivos.TOPES.get(Tipo.COMBATE_DEFENSA), 1e-9);
        assertEquals(0.08, Pasivos.TOPES.get(Tipo.CRITICO), 1e-9);
        // Durabilidad, ataque, defensa y crítico NO se tocan ni con el mejor build: es margen
        // deliberado para añadir ítems en el futuro sin bajarle el bono a nadie.
        assertTrue(mejores4(Tipo.MINERIA_DURABILIDAD) < Pasivos.TOPES.get(Tipo.MINERIA_DURABILIDAD));
        assertTrue(mejores4(Tipo.COMBATE_ATAQUE) < Pasivos.TOPES.get(Tipo.COMBATE_ATAQUE));
        assertTrue(mejores4(Tipo.COMBATE_DEFENSA) < Pasivos.TOPES.get(Tipo.COMBATE_DEFENSA));
        assertTrue(mejores4(Tipo.CRITICO) < Pasivos.TOPES.get(Tipo.CRITICO));
    }

    @Test
    @DisplayName("9 · cuantos más bonos, más caro (regla de diseño 4)")
    void masBonosMasCaro() {
        for (Pasivo p : Pasivos.CATALOGO) {
            long precio = Items.porId(p.itemId()).orElseThrow().precio();
            assertTrue(p.bonos().size() <= 3, p.itemId() + " no puede pasar de 3 bonos");
            if (p.bonos().size() >= 3) {
                assertTrue(precio >= 90_000,
                        p.itemId() + " tiene 3 bonos y cuesta " + precio + ": los de 3 son de lujo");
            }
            if (p.bonos().size() == 1) {
                assertTrue(precio <= 1_500,
                        p.itemId() + " tiene 1 bono y cuesta " + precio + ": los de 1 son baratos");
            }
        }
    }

    @Test
    @DisplayName("10 · porId encuentra lo que hay y no inventa lo que no")
    void porId() {
        assertTrue(Pasivos.porId("jet").isPresent());
        assertEquals(3, Pasivos.porId("jet").orElseThrow().bonos().size());
        assertTrue(Pasivos.porId("fruta").isEmpty(), "un consumible no tiene pasivo");
        assertTrue(Pasivos.porId("casa").isEmpty(), "una cama no tiene pasivo");
        assertTrue(Pasivos.porId("no_existe").isEmpty());
    }

    @Test
    @DisplayName("11 · fuentesDe devuelve ítem → magnitud en el orden del catálogo")
    void fuentesDe() {
        var energia = Pasivos.fuentesDe(Tipo.ENERGIA_REGEN);
        assertEquals(6, energia.size(), "zapatillas, mancuernas, bici, moto_agua, avioneta, yate");
        assertEquals(3.0, energia.get("yate"), 1e-9);
        assertEquals(1.0, energia.get("zapatillas"), 1e-9);
        assertFalse(energia.containsKey("jet"), "el jet no da energía");
    }
}
```

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivosTest" test
```
`Expected: COMPILATION ERROR — cannot find symbol: class Pasivos`

- [ ] **Step 3: Escribir el catálogo**

Crear `src/main/java/com/gymprofit/bot/services/Pasivos.java`:

```java
package com.gymprofit.bot.services;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de <b>efectos pasivos</b> de los 30 ítems que hasta ahora se compraban y no hacían nada:
 * los 20 de {@link Items.Categoria#EQUIPO} y los 10 vehículos de {@link Items.Categoria#BIEN} (los
 * otros 10 BIEN son camas y ya tienen efecto vía {@link Camas}; darles además un pasivo sería doble
 * recompensa por la misma compra y obligaría a reabrir el balance del descanso).
 *
 * <p><b>Catálogo paralelo, igual que {@link Picos}, {@link Camas} y {@link Cofres}:</b> se empareja
 * por {@code itemId} y {@link Items} <b>no se toca</b>. Meter los bonos en {@code Items} obligaría a
 * ampliar un record de 8 componentes con campos nulos en 79 de 109 filas, y los precios de
 * {@code Items} son carga estructural probada por {@code RarezaTest} y por {@code Camas}.
 *
 * <p><b>Convenio de unidades</b> (crítico al implementar):
 * <ul>
 *   <li>Tipos <b>porcentuales</b> ({@link Tipo#SUELDO}, {@link Tipo#COOLDOWN_WORK}, {@link Tipo#XP},
 *       {@link Tipo#MINERIA_DURABILIDAD}, {@link Tipo#CRITICO}): la magnitud es una <b>fracción</b>
 *       ({@code 0.11} = 11 %), mismo convenio que {@code TrabajoService.BONO_ESTUDIOS} y
 *       {@code Encantamiento.magnitud()}.</li>
 *   <li>Tipos <b>planos</b> ({@link Tipo#ENERGIA_REGEN}, {@link Tipo#MINERIA_CANTIDAD},
 *       {@link Tipo#COMBATE_ATAQUE}, {@link Tipo#COMBATE_DEFENSA}): enteros guardados en un
 *       {@code double}; se redondean con {@code Math.round} al aplicarlos.</li>
 *   <li>{@link Tipo#COOLDOWN_WORK} es una <b>reducción</b>: la magnitud es positiva y se resta.</li>
 * </ul>
 *
 * <p><b>Reglas de diseño que el catálogo cumple</b> (y que {@code PasivosTest} vigila): las
 * magnitudes escalan con el precio; ningún ítem alcanza por sí solo el tope de su tipo (siempre hacen
 * falta ≥ 2 ranuras bien elegidas); los bonos son temáticos, no aleatorios; y cuantos más bonos, más
 * caro (3 solo en los vehículos de lujo).
 */
public final class Pasivos {

    private Pasivos() {
    }

    /** Los nueve tipos de bono. Cada uno tiene un tope global en {@link #TOPES}. */
    public enum Tipo {
        /** % extra sobre el pago de {@code /trabajo currar}. */
        SUELDO,
        /** Recorte del cooldown de 60 min de {@code /trabajo currar}. */
        COOLDOWN_WORK,
        /** % extra de XP de <b>cualquier</b> fuente. */
        XP,
        /** Energía extra en cada tick de {@code EnergiaJob}. */
        ENERGIA_REGEN,
        /** Minerales extra por {@code /minar} (sube también el tope duro del minado). */
        MINERIA_CANTIDAD,
        /** Probabilidad de <b>no</b> gastar durabilidad del pico. */
        MINERIA_DURABILIDAD,
        /** Ataque plano en batalla. */
        COMBATE_ATAQUE,
        /** Defensa plana en batalla. */
        COMBATE_DEFENSA,
        /** Probabilidad de crítico (aditiva, antes del tope propio de {@code CombateService}). */
        CRITICO
    }

    /**
     * Un bono concreto: qué mejora y cuánto.
     *
     * @param tipo     qué mejora
     * @param magnitud fracción (tipos porcentuales) o entero (tipos planos); ver el convenio arriba
     */
    public record Bono(Tipo tipo, double magnitud) {
    }

    /**
     * Los efectos pasivos de un ítem. Un ítem puede llevar varios bonos (1–3): un {@code jet} con un
     * único +11 % de sueldo es aburrido; uno que además acorta el cooldown y da XP se <i>siente</i>
     * como un jet.
     *
     * @param itemId id en {@link Items}
     * @param bonos  bonos que aporta (nunca vacío)
     */
    public record Pasivo(String itemId, List<Bono> bonos) {
    }

    /** Azúcar para que la tabla del catálogo se lea como la tabla del diseño. */
    private static Pasivo p(String itemId, Bono... bonos) {
        return new Pasivo(itemId, List.of(bonos));
    }

    private static Bono b(Tipo tipo, double magnitud) {
        return new Bono(tipo, magnitud);
    }

    /**
     * Los 30 ítems con efecto pasivo, ordenados por precio dentro de cada bloque. El comentario de
     * cada línea justifica el bono; esa justificación va también a i18n ({@code pasivo.<id>.desc})
     * para que {@code /pasivos ver} y el autocompletado se expliquen solos.
     */
    public static final List<Pasivo> CATALOGO = List.of(
            // --- Equipo (20) ---
            // Te la pones y ya vas al gym: bono de entrada, casi simbólico.
            p("gorra", b(Tipo.XP, 0.02)),
            // Ves venir el golpe antes que el otro.
            p("gafas", b(Tipo.CRITICO, 0.01)),
            // Cargas más piedra por viaje.
            p("mochila", b(Tipo.MINERIA_CANTIDAD, 1)),
            // Vas de uniforme: te pagan mejor y el chaleco algo protege.
            p("uniforme", b(Tipo.SUELDO, 0.04), b(Tipo.COMBATE_DEFENSA, 1)),
            // Con música entrenas más y mejor.
            p("auriculares", b(Tipo.XP, 0.03)),
            // Te organizas: turnos y rutinas siempre a mano.
            p("movil", b(Tipo.SUELDO, 0.03), b(Tipo.XP, 0.02)),
            // Buen calzado, menos castigo en las piernas.
            p("zapatillas", b(Tipo.ENERGIA_REGEN, 1), b(Tipo.COMBATE_DEFENSA, 2)),
            // Hierro en casa: pegas más fuerte y aguantas más.
            p("mancuernas", b(Tipo.COMBATE_ATAQUE, 3), b(Tipo.ENERGIA_REGEN, 1)),
            // Con tu propio juego de herramientas rindes más y rompes menos.
            p("herramientas", b(Tipo.SUELDO, 0.05), b(Tipo.MINERIA_DURABILIDAD, 0.05)),
            // Reflejos, competitividad y horas de práctica.
            p("consola", b(Tipo.XP, 0.04)),
            // Llegas antes al curro.
            p("patinete", b(Tipo.COOLDOWN_WORK, 0.04)),
            // Vas antes y encima haces cardio.
            p("bici", b(Tipo.COOLDOWN_WORK, 0.06), b(Tipo.ENERGIA_REGEN, 1)),
            // Aprender un instrumento entrena la cabeza.
            p("guitarra", b(Tipo.XP, 0.05)),
            // La herramienta que sirve para todo.
            p("portatil", b(Tipo.SUELDO, 0.06), b(Tipo.XP, 0.04)),
            // Documentas la veta y aprendes del terreno.
            p("camara", b(Tipo.MINERIA_CANTIDAD, 1), b(Tipo.XP, 0.03)),
            // Puntual y midiendo cada sesión.
            p("reloj", b(Tipo.SUELDO, 0.04), b(Tipo.XP, 0.05)),
            // Localizas el filón antes de picar a ciegas.
            p("telescopio", b(Tipo.MINERIA_CANTIDAD, 1), b(Tipo.MINERIA_DURABILIDAD, 0.08)),
            // Traje bueno: mejor sueldo y más presencia.
            p("traje", b(Tipo.SUELDO, 0.08), b(Tipo.CRITICO, 0.01)),
            // Explora la mina por ti y te dice dónde picar.
            p("dron", b(Tipo.MINERIA_CANTIDAD, 2), b(Tipo.MINERIA_DURABILIDAD, 0.10)),
            // Te mueves rápido y llegas a más sitios.
            p("moto", b(Tipo.COOLDOWN_WORK, 0.08), b(Tipo.SUELDO, 0.03)),
            // --- Bienes: vehículos (10). Las 10 camas quedan fuera a propósito (ya usan Camas). ---
            // El primer vehículo de verdad: cambia tu día entero.
            p("coche", b(Tipo.COOLDOWN_WORK, 0.09), b(Tipo.SUELDO, 0.04)),
            // Cabe todo: herramienta, material y lo que saques.
            p("furgoneta", b(Tipo.COOLDOWN_WORK, 0.07), b(Tipo.MINERIA_CANTIDAD, 1)),
            // Ocio de verdad: desconectas y vuelves entero.
            p("moto_agua", b(Tipo.ENERGIA_REGEN, 2), b(Tipo.XP, 0.03)),
            // Trabajo pesado: mueves tonelaje y cobras por ello.
            p("camion", b(Tipo.MINERIA_CANTIDAD, 2), b(Tipo.SUELDO, 0.06),
                    b(Tipo.MINERIA_DURABILIDAD, 0.08)),
            // Deportivo blindado: impone y protege.
            p("coche_lujo", b(Tipo.SUELDO, 0.07), b(Tipo.CRITICO, 0.02), b(Tipo.COMBATE_DEFENSA, 2)),
            // Apoyo aéreo: llegas a la veta y a la pelea desde arriba.
            p("helicoptero", b(Tipo.COOLDOWN_WORK, 0.10), b(Tipo.MINERIA_DURABILIDAD, 0.12),
                    b(Tipo.COMBATE_ATAQUE, 3)),
            // Vuelas a donde haga falta y aprendes por el camino.
            p("avioneta", b(Tipo.SUELDO, 0.09), b(Tipo.XP, 0.06), b(Tipo.ENERGIA_REGEN, 1)),
            // Jet privado: el mejor paquete de trabajo del juego.
            p("jet", b(Tipo.SUELDO, 0.11), b(Tipo.COOLDOWN_WORK, 0.11), b(Tipo.XP, 0.06)),
            // Descanso de lujo: vuelves nuevo y con contactos.
            p("yate", b(Tipo.ENERGIA_REGEN, 3), b(Tipo.XP, 0.07), b(Tipo.SUELDO, 0.07)),
            // El tope absoluto del catálogo y la única pieza sci-fi: rompe el techo en combate.
            p("cohete", b(Tipo.COMBATE_ATAQUE, 5), b(Tipo.COMBATE_DEFENSA, 4), b(Tipo.CRITICO, 0.03)));

    /**
     * Tope <b>global y saturante</b> de cada tipo: se topa <b>la suma</b> de las ranuras, nunca cada
     * bono por separado. Mismo mecanismo que {@code TrabajoService.BONO_ESTUDIOS_MAX} y que los topes
     * de {@code CombateService.probCritico}. Sin topes, cuatro ranuras de sueldo romperían la
     * economía lenta (ADR-010).
     *
     * <p>Cuatro topes (durabilidad, ataque, defensa y crítico) <b>no se alcanzan</b> ni con el mejor
     * build posible: es margen deliberado para añadir ítems en el futuro sin retocar los topes ni
     * bajarle el bono a nadie (que es lo que peor sienta).
     */
    public static final Map<Tipo, Double> TOPES = Map.copyOf(new EnumMap<>(Map.of(
            Tipo.SUELDO, 0.30,
            Tipo.COOLDOWN_WORK, 0.25,
            Tipo.XP, 0.20,
            Tipo.ENERGIA_REGEN, 5.0,
            Tipo.MINERIA_CANTIDAD, 3.0,
            Tipo.MINERIA_DURABILIDAD, 0.40,
            Tipo.COMBATE_ATAQUE, 12.0,
            Tipo.COMBATE_DEFENSA, 10.0,
            Tipo.CRITICO, 0.08)));

    /** ¿La magnitud de este tipo es una fracción (%) o un entero plano? */
    public static boolean esPorcentual(Tipo tipo) {
        return switch (tipo) {
            case SUELDO, COOLDOWN_WORK, XP, MINERIA_DURABILIDAD, CRITICO -> true;
            case ENERGIA_REGEN, MINERIA_CANTIDAD, COMBATE_ATAQUE, COMBATE_DEFENSA -> false;
        };
    }

    /** Busca el pasivo de un ítem. Vacío si el ítem no existe o no tiene efecto pasivo. */
    public static Optional<Pasivo> porId(String itemId) {
        return CATALOGO.stream().filter(p -> p.itemId().equals(itemId)).findFirst();
    }

    /**
     * Ítems que dan un tipo de bono, con su magnitud, en el orden del catálogo.
     *
     * <p>La usa {@code EnergiaJob} para <b>generar</b> el SQL del segundo pase: los ids y las
     * magnitudes salen siempre de aquí, nunca escritos a mano en la consulta. El orden es estable
     * ({@link LinkedHashMap}) para que el enlazado de parámetros del {@code PreparedStatement} sea
     * determinista.
     */
    public static Map<String, Double> fuentesDe(Tipo tipo) {
        Map<String, Double> res = new LinkedHashMap<>();
        for (Pasivo p : CATALOGO) {
            for (Bono b : p.bonos()) {
                if (b.tipo() == tipo) {
                    res.put(p.itemId(), b.magnitud());
                }
            }
        }
        return res;
    }
}
```

> **Ojo:** `Map.of(...)` admite como mucho 10 pares; aquí son 9, así que compila. Si algún día se
> añade un décimo tipo hay que pasar a `Map.ofEntries(...)`.

- [ ] **Step 4: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivosTest" test
```
`Expected: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

**+11 tests.**

---

### Task 3: `db/PasivoRepositorio`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/db/PasivoRepositorio.java`
- Create: `src/test/java/com/gymprofit/bot/db/PasivoRepositorioTest.java`

**Nota de diseño:** no se crea ningún record `PasivoEquipado`. La forma natural del dato es
`ranura → itemId` (`Map<Integer, String>`), y el `List<PasivoDe> conTipo(Tipo)` que insinuaba la
spec no hace falta: el segundo pase de energía se resuelve **entero en SQL** (Task 10) sin traer
filas a memoria.

- [ ] **Step 1: Escribir el repositorio (JDBC plano, estilo `InventarioRepositorio`)**

Crear `src/main/java/com/gymprofit/bot/db/PasivoRepositorio.java`:

```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio JDBC de las ranuras de efectos pasivos ({@code pasivos_equipados}: jugador → ranura →
 * ítem). Guarda solo una <b>referencia</b>: el bono se recalcula siempre contra el inventario en
 * {@code PasivoService}, así que aquí no hay ninguna noción de «derecho adquirido».
 *
 * <p>No hay método de borrado por jugador a propósito: el olvido RGPD borra {@code usuarios_discord}
 * y estas filas se van por el {@code ON DELETE CASCADE} de la FK, igual que {@code descanso} o
 * {@code durabilidad_picos}.
 */
public final class PasivoRepositorio {

    /**
     * El ítem ya ocupa otra ranura de ese jugador. Lo lanza el {@code UNIQUE (discord_id, item_id)}
     * del esquema, que es la garantía dura frente a dos comandos simultáneos; el service comprueba
     * antes para poder dar un error bonito, pero si pierde la carrera, esta excepción es la red.
     */
    public static final class ItemYaEquipadoException extends RuntimeException {
        public ItemYaEquipadoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final DataSource dataSource;

    public PasivoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Ranuras ocupadas del jugador (ranura → itemId), ordenadas por ranura. Vacío si no hay nada. */
    public Map<Integer, String> equipados(long discordId) {
        String sql = "SELECT ranura, item_id FROM pasivos_equipados "
                + "WHERE discord_id = ? ORDER BY ranura";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, String> res = new LinkedHashMap<>();
                while (rs.next()) {
                    res.put(rs.getInt("ranura"), rs.getString("item_id"));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando los pasivos de " + discordId, e);
        }
    }

    /**
     * Pone un ítem en una ranura, reemplazando lo que hubiera. Una sola sentencia atómica gracias a
     * la PK {@code (discord_id, ranura)}.
     *
     * @throws ItemYaEquipadoException si el ítem ya ocupa otra ranura del mismo jugador
     */
    public void equipar(long discordId, int ranura, String itemId) {
        String sql = "INSERT INTO pasivos_equipados (discord_id, ranura, item_id) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE item_id = VALUES(item_id), "
                + "equipado_en = CURRENT_TIMESTAMP";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, ranura);
            ps.setString(3, itemId);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            // Choque con uq_pasivos_item: el mismo ítem en dos ranuras duplicaría el bono con una
            // sola compra, que es el exploit más obvio del sistema.
            throw new ItemYaEquipadoException(
                    "El ítem " + itemId + " ya está equipado por " + discordId, e);
        } catch (SQLException e) {
            throw new DatabaseException("Error equipando " + itemId + " para " + discordId, e);
        }
    }

    /** Vacía una ranura. Devuelve {@code false} si ya estaba vacía. */
    public boolean quitar(long discordId, int ranura) {
        String sql = "DELETE FROM pasivos_equipados WHERE discord_id = ? AND ranura = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, ranura);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error quitando la ranura " + ranura + " de " + discordId, e);
        }
    }

    /** Ranura que ocupa un ítem en ese jugador, si está equipado. */
    public Optional<Integer> ranuraDe(long discordId, String itemId) {
        String sql = "SELECT ranura FROM pasivos_equipados WHERE discord_id = ? AND item_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la ranura de " + itemId, e);
        }
    }
}
```

- [ ] **Step 2: Escribir el test de Testcontainers**

Crear `src/test/java/com/gymprofit/bot/db/PasivoRepositorioTest.java`:

```java
package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link PasivoRepositorio} contra MySQL real (Testcontainers): equipar,
 * reemplazar por {@code ON DUPLICATE KEY}, quitar, el {@code UNIQUE} que impide el mismo ítem en dos
 * ranuras y el borrado en cascada (requisito RGPD). Se salta si el cliente Docker no es alcanzable
 * en local; corre en CI (Linux).
 */
class PasivoRepositorioTest {

    @Test
    void equiparReemplazarQuitarYCascada() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();

            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                UsuarioDiscordRepositorio usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                PasivoRepositorio repo = new PasivoRepositorio(db.dataSource());

                // La FK exige que el usuario exista antes.
                usuarios.obtenerOCrear(77L);

                // Arranca sin nada equipado.
                assertEquals(Map.of(), repo.equipados(77L));

                // Equipar dos ranuras.
                repo.equipar(77L, 1, "jet");
                repo.equipar(77L, 2, "dron");
                assertEquals(Map.of(1, "jet", 2, "dron"), repo.equipados(77L));
                assertEquals(1, repo.ranuraDe(77L, "jet").orElseThrow());
                assertTrue(repo.ranuraDe(77L, "yate").isEmpty());

                // Reemplazo en la misma ranura (ON DUPLICATE KEY): no duplica filas.
                repo.equipar(77L, 1, "yate");
                assertEquals(Map.of(1, "yate", 2, "dron"), repo.equipados(77L));

                // El mismo ítem en dos ranuras lo rechaza el esquema, no el service.
                assertThrows(PasivoRepositorio.ItemYaEquipadoException.class,
                        () -> repo.equipar(77L, 3, "dron"));

                // Quitar: la primera vez borra, la segunda ya no encuentra nada.
                assertTrue(repo.quitar(77L, 2));
                assertFalse(repo.quitar(77L, 2));
                assertEquals(Map.of(1, "yate"), repo.equipados(77L));

                // RGPD: borrar el usuario se lleva sus ranuras por ON DELETE CASCADE.
                usuarios.borrar(77L);
                assertEquals(Map.of(), repo.equipados(77L));
            }
        }
    }
}
```

> **Antes de escribirlo, verificar el nombre real del método de borrado de
> `UsuarioDiscordRepositorio`** (`grep -n "public .*borrar\|public void eliminar"
> src/main/java/com/gymprofit/bot/db/UsuarioDiscordRepositorio.java`). El módulo de privacidad ya
> tiene uno; si se llama distinto (p. ej. `eliminar`), usar ese nombre. Si no existiera ninguno,
> sustituir esas dos líneas por un `DELETE FROM usuarios_discord WHERE discord_id = 77` ejecutado
> con un `Statement` sobre `DriverManager.getConnection(...)`, como hace `MigracionesTest`.

- [ ] **Step 3: Ejecutar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivoRepositorioTest" test
```
`Expected: Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` (se salta en local; **pendiente de CI**).

**+1 test (se salta en local).**

---

### Task 4: `services/PasivoService`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/PasivoService.java`
- Create: `src/test/java/com/gymprofit/bot/services/PasivoServiceTest.java`

**Contrato que fijan esta tarea y usan las Tasks 5–11 (no cambiarlo después):**

```java
public static int ranurasDe(int nivel)                                   // 1/2/3/4
public static int nivelDeRanura(int ranura)                              // 0/10/25/50
public static Map<Pasivos.Tipo, Double> sumarYTopar(List<Pasivos.Pasivo>) // pura
public Map<Pasivos.Tipo, Double> bonosDe(long discordId)
public double bonoDe(long discordId, Pasivos.Tipo tipo)
public List<EstadoRanura> ranuras(long discordId)                        // siempre 4 elementos
public List<String> equipablesDe(long discordId)                         // para el autocompletado
public ResultadoEquipar equipar(long discordId, String itemId, Integer ranura)
public ResultadoQuitar quitar(long discordId, int ranura)
```

- [ ] **Step 1: Escribir el service**

Crear `src/main/java/com/gymprofit/bot/services/PasivoService.java`:

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PasivoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lógica de los <b>efectos pasivos</b>: ranuras (equipar/quitar), desbloqueo por nivel y cálculo de
 * los bonos activos ya sumados y ya topados. El catálogo de bonos vive en {@link Pasivos}; aquí solo
 * va la lógica.
 *
 * <p><b>La fuente de verdad es siempre el inventario.</b> {@code pasivos_equipados} guarda una
 * referencia, no un derecho: en cada consulta se cruzan las ranuras con
 * {@link InventarioRepositorio#listar(long)} y se <b>descartan las ranuras cuyo ítem ya no se posee</b>.
 * Esto cierra de golpe cuatro exploits (vender, regalar, publicar en el mercado, trocar y seguir
 * cobrando el bono) y, sobre todo, significa que {@code VentaService}, {@code RegaloService},
 * {@code MercadoService}, {@code TruequeService} y {@code RoboService} <b>no se tocan</b>: no hay
 * hooks de limpieza que recordar al añadir el sexto sitio donde se puede perder un ítem. La fila
 * muerta se queda ahí, inofensiva, y {@code /pasivos ver} la marca con un aviso.
 *
 * <p>La suma y el topado son una <b>función pura y estática</b> ({@link #sumarYTopar}) para poder
 * testear el balance sin base de datos, igual que {@code TrabajoService.conBonoEstudios} o
 * {@code CombateService.dano}.
 */
public final class PasivoService {

    /** Ranuras que existen como máximo. Más allá de esto, {@code RANURA_INVALIDA}. */
    public static final int RANURAS_MAX = 4;

    /**
     * Nivel al que se desbloquea cada ranura (índice 0 = ranura 1). Son los <b>mismos umbrales que
     * {@code Rango}</b> (Novato/Habitual/Veterano/Leyenda): el desbloqueo cae a la vez que el rol
     * nuevo, así el subidón de nivel se nota doble y no hay que explicarle al jugador dos escalas.
     */
    private static final int[] NIVEL_RANURA = {0, 10, 25, 50};

    /** Estados de {@code equipar} y {@code quitar}. Uno por fila de la tabla de errores del diseño. */
    public enum Estado {
        OK, NO_EXISTE, SIN_PASIVO, NO_TIENE, RANURA_INVALIDA, RANURA_BLOQUEADA, YA_EQUIPADO,
        SIN_HUECO, VACIA
    }

    /**
     * Estado de una ranura para {@code /pasivos ver}.
     *
     * @param ranura    número de ranura (1..4)
     * @param itemId    ítem equipado, o {@code null} si está vacía
     * @param bloqueada la ranura aún no está desbloqueada por nivel
     * @param falta     hay un ítem equipado pero <b>ya no se posee</b>: la ranura no cuenta
     */
    public record EstadoRanura(int ranura, String itemId, boolean bloqueada, boolean falta) {
    }

    /**
     * Resultado de equipar.
     *
     * @param estado         resultado
     * @param ranura         ranura afectada (0 si no aplica)
     * @param itemId         ítem que se intentaba equipar
     * @param reemplazado    ítem que salía de esa ranura, o {@code null}
     * @param nivelRequerido nivel que pide la ranura (solo con {@code RANURA_BLOQUEADA})
     * @param totales        bonos activos <b>tras</b> la operación (vacío si falló)
     */
    public record ResultadoEquipar(Estado estado, int ranura, String itemId, String reemplazado,
                                   int nivelRequerido, Map<Pasivos.Tipo, Double> totales) {
    }

    /**
     * Resultado de quitar.
     *
     * @param estado  resultado
     * @param itemId  ítem que sale de la ranura, o {@code null} si falló
     * @param totales bonos activos <b>tras</b> la operación (vacío si falló)
     */
    public record ResultadoQuitar(Estado estado, String itemId, Map<Pasivos.Tipo, Double> totales) {
    }

    private final PasivoRepositorio pasivos;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public PasivoService(PasivoRepositorio pasivos, InventarioRepositorio inventario,
                         UsuarioDiscordRepositorio usuarios) {
        this.pasivos = pasivos;
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Ranuras desbloqueadas según el nivel: 1 / 2 / 3 / 4 a partir de los niveles 0 / 10 / 25 / 50. */
    public static int ranurasDe(int nivel) {
        int n = 1;
        for (int i = 1; i < NIVEL_RANURA.length; i++) {
            if (nivel >= NIVEL_RANURA[i]) {
                n = i + 1;
            }
        }
        return n;
    }

    /** Nivel al que se desbloquea una ranura (1..4). Para ranuras fuera de rango, {@link Integer#MAX_VALUE}. */
    public static int nivelDeRanura(int ranura) {
        return ranura >= 1 && ranura <= RANURAS_MAX ? NIVEL_RANURA[ranura - 1] : Integer.MAX_VALUE;
    }

    /**
     * Suma los bonos de una lista de pasivos y aplica los topes. <b>Pura.</b>
     *
     * <p>Se topa <b>la suma</b>, nunca cada bono por separado: dos ítems de 20 % con tope 30 % dan
     * 30 %, no 40 % ni 20 %. El mapa devuelto tiene <b>siempre los nueve tipos</b> (a 0,0 los que no
     * tienen fuente) para que ningún llamante tenga que comprobar ausencias.
     */
    public static Map<Pasivos.Tipo, Double> sumarYTopar(List<Pasivos.Pasivo> equipados) {
        Map<Pasivos.Tipo, Double> total = new EnumMap<>(Pasivos.Tipo.class);
        for (Pasivos.Tipo t : Pasivos.Tipo.values()) {
            total.put(t, 0.0);
        }
        for (Pasivos.Pasivo p : equipados) {
            for (Pasivos.Bono b : p.bonos()) {
                total.merge(b.tipo(), b.magnitud(), Double::sum);
            }
        }
        total.replaceAll((t, v) -> Math.min(Pasivos.TOPES.get(t), v));
        return total;
    }

    /** Bonos activos del jugador: sumados sobre las ranuras válidas y topados. Nunca {@code null}. */
    public Map<Pasivos.Tipo, Double> bonosDe(long discordId) {
        return sumarYTopar(activosDe(discordId));
    }

    /** Atajo tipado; {@code 0.0} si no hay bono de ese tipo. */
    public double bonoDe(long discordId, Pasivos.Tipo tipo) {
        return bonosDe(discordId).getOrDefault(tipo, 0.0);
    }

    /**
     * Vista completa de las 4 ranuras para {@code /pasivos ver}, en orden. Siempre devuelve 4
     * elementos, marcando cuáles están bloqueadas por nivel y cuáles apuntan a un ítem que ya no se
     * posee.
     */
    public List<EstadoRanura> ranuras(long discordId) {
        Map<Integer, String> eq = pasivos.equipados(discordId);
        Map<String, Integer> inv = inventario.listar(discordId);
        int disponibles = ranurasDe(nivelDe(discordId));
        List<EstadoRanura> res = new ArrayList<>(RANURAS_MAX);
        for (int r = 1; r <= RANURAS_MAX; r++) {
            String item = eq.get(r);
            boolean bloqueada = r > disponibles;
            boolean falta = item != null && inv.getOrDefault(item, 0) <= 0;
            res.add(new EstadoRanura(r, item, bloqueada, falta));
        }
        return res;
    }

    /**
     * Ítems que el jugador <b>posee</b>, <b>tienen pasivo</b> y <b>no están ya equipados</b>. Es lo
     * que alimenta el autocompletado de {@code /pasivos equipar}: filtrar ahí evita la mitad de los
     * errores antes de que ocurran.
     */
    public List<String> equipablesDe(long discordId) {
        Map<String, Integer> inv = inventario.listar(discordId);
        var yaEquipados = pasivos.equipados(discordId).values();
        List<String> res = new ArrayList<>();
        for (Pasivos.Pasivo p : Pasivos.CATALOGO) {
            if (inv.getOrDefault(p.itemId(), 0) > 0 && !yaEquipados.contains(p.itemId())) {
                res.add(p.itemId());
            }
        }
        return res;
    }

    /**
     * Equipa un ítem en una ranura. <b>No consume el ítem</b> (precedente:
     * {@code CombateService.equipar} solo referencia el id y exige poseerlo), así que se puede quitar
     * y volver a poner las veces que se quiera sin coste.
     *
     * @param ranura ranura 1..4, o {@code null} para usar la primera libre desbloqueada
     */
    public ResultadoEquipar equipar(long discordId, String itemId, Integer ranura) {
        if (Items.porId(itemId).isEmpty()) {
            return fallo(Estado.NO_EXISTE, itemId);
        }
        if (Pasivos.porId(itemId).isEmpty()) {
            return fallo(Estado.SIN_PASIVO, itemId);
        }
        usuarios.obtenerOCrear(discordId);
        if (inventario.cantidad(discordId, itemId) <= 0) {
            return fallo(Estado.NO_TIENE, itemId);
        }

        Map<Integer, String> eq = pasivos.equipados(discordId);
        Optional<Integer> yaEn = eq.entrySet().stream()
                .filter(e -> e.getValue().equals(itemId))
                .map(Map.Entry::getKey).findFirst();
        if (yaEn.isPresent()) {
            // Un ítem en dos ranuras duplicaría el bono con una sola compra.
            return new ResultadoEquipar(Estado.YA_EQUIPADO, yaEn.get(), itemId, null, 0, Map.of());
        }

        int disponibles = ranurasDe(nivelDe(discordId));
        int destino;
        if (ranura == null) {
            destino = 0;
            for (int r = 1; r <= disponibles; r++) {
                if (!eq.containsKey(r)) {
                    destino = r;
                    break;
                }
            }
            // Sin hueco NO se elige por el jugador: pisar en silencio un cohete de 3 000 000 de
            // coins es inaceptable aunque sea reversible. El comando lista las ranuras y pregunta.
            if (destino == 0) {
                return fallo(Estado.SIN_HUECO, itemId);
            }
        } else {
            if (ranura < 1 || ranura > RANURAS_MAX) {
                return fallo(Estado.RANURA_INVALIDA, itemId);
            }
            if (ranura > disponibles) {
                return new ResultadoEquipar(Estado.RANURA_BLOQUEADA, ranura, itemId, null,
                        nivelDeRanura(ranura), Map.of());
            }
            destino = ranura;
        }

        String reemplazado = eq.get(destino);
        try {
            pasivos.equipar(discordId, destino, itemId);
        } catch (PasivoRepositorio.ItemYaEquipadoException e) {
            // Carrera entre dos comandos simultáneos: el UNIQUE del esquema ha ganado. Se traduce al
            // mismo error bonito en vez de soltar un fallo genérico.
            return new ResultadoEquipar(Estado.YA_EQUIPADO,
                    pasivos.ranuraDe(discordId, itemId).orElse(0), itemId, null, 0, Map.of());
        }
        return new ResultadoEquipar(Estado.OK, destino, itemId, reemplazado, 0, bonosDe(discordId));
    }

    /** Vacía una ranura. El ítem nunca salió del inventario: solo deja de contar. */
    public ResultadoQuitar quitar(long discordId, int ranura) {
        if (ranura < 1 || ranura > RANURAS_MAX) {
            return new ResultadoQuitar(Estado.RANURA_INVALIDA, null, Map.of());
        }
        usuarios.obtenerOCrear(discordId);
        String itemId = pasivos.equipados(discordId).get(ranura);
        if (itemId == null) {
            return new ResultadoQuitar(Estado.VACIA, null, Map.of());
        }
        pasivos.quitar(discordId, ranura);
        return new ResultadoQuitar(Estado.OK, itemId, bonosDe(discordId));
    }

    // ---------------------- internos ----------------------

    private static ResultadoEquipar fallo(Estado estado, String itemId) {
        return new ResultadoEquipar(estado, 0, itemId, null, 0, Map.of());
    }

    /**
     * Pasivos que de verdad cuentan: ranura desbloqueada por nivel <b>y</b> ítem todavía en el
     * inventario.
     */
    private List<Pasivos.Pasivo> activosDe(long discordId) {
        Map<Integer, String> eq = pasivos.equipados(discordId);
        if (eq.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> inv = inventario.listar(discordId);
        int disponibles = ranurasDe(nivelDe(discordId));
        List<Pasivos.Pasivo> res = new ArrayList<>();
        for (Map.Entry<Integer, String> e : eq.entrySet()) {
            if (e.getKey() > disponibles || inv.getOrDefault(e.getValue(), 0) <= 0) {
                continue;
            }
            Pasivos.porId(e.getValue()).ifPresent(res::add);
        }
        return res;
    }

    /**
     * Nivel del jugador <b>sin crear su fila</b>: {@code bonosDe} y {@code ranuras} son caminos de
     * lectura y consultar no puede generar datos nuevos (RGPD, ADR-009).
     */
    private int nivelDe(long discordId) {
        return usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
    }
}
```

- [ ] **Step 2: Escribir el test**

Crear `src/test/java/com/gymprofit/bot/services/PasivoServiceTest.java`:

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PasivoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.PasivoService.Estado;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.PasivoService.ResultadoEquipar;
import com.gymprofit.bot.services.PasivoService.ResultadoQuitar;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link PasivoService} con los repositorios mockeados: desbloqueo de ranuras por nivel,
 * filtrado contra el inventario (el anti-exploit), saturación por tipo y los ocho estados de error
 * de la tabla del diseño.
 */
class PasivoServiceTest {

    private final PasivoRepositorio repo = mock(PasivoRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private final PasivoService svc = new PasivoService(repo, inventario, usuarios);

    /** Fija el nivel del jugador 1 (y por tanto sus ranuras disponibles). */
    private void nivel(int nivel) {
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, nivel, 0, 0, null, "es", false)));
    }

    @Test
    @DisplayName("8 · ranurasDe: los bordes exactos 0/10/25/50")
    void ranurasPorNivel() {
        assertEquals(1, PasivoService.ranurasDe(0));
        assertEquals(1, PasivoService.ranurasDe(9));
        assertEquals(2, PasivoService.ranurasDe(10));
        assertEquals(2, PasivoService.ranurasDe(24));
        assertEquals(3, PasivoService.ranurasDe(25));
        assertEquals(3, PasivoService.ranurasDe(49));
        assertEquals(4, PasivoService.ranurasDe(50));
        assertEquals(4, PasivoService.ranurasDe(100));
        assertEquals(0, PasivoService.nivelDeRanura(1));
        assertEquals(10, PasivoService.nivelDeRanura(2));
        assertEquals(25, PasivoService.nivelDeRanura(3));
        assertEquals(50, PasivoService.nivelDeRanura(4));
    }

    @Test
    @DisplayName("9 · el bono solo cuenta si el ítem sigue en el inventario (anti-exploit)")
    void filtradoPorInventario() {
        nivel(50);
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate"));
        // Vendió el yate: la fila sigue ahí, pero deja de contar.
        when(inventario.listar(1L)).thenReturn(Map.of("jet", 1));

        Map<Tipo, Double> bonos = svc.bonosDe(1L);
        assertEquals(0.11, bonos.get(Tipo.SUELDO), 1e-9, "solo el jet: 11 %, no 18 %");
        assertEquals(0.06, bonos.get(Tipo.XP), 1e-9, "solo el jet: 6 %, no 13 %");
        assertEquals(0.0, bonos.get(Tipo.ENERGIA_REGEN), 1e-9, "la energía la daba el yate");
    }

    @Test
    @DisplayName("9b · una ranura por encima del nivel del jugador tampoco cuenta")
    void ranuraBloqueadaNoCuenta() {
        nivel(0); // solo 1 ranura
        when(repo.equipados(1L)).thenReturn(Map.of(1, "gorra", 2, "jet"));
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1, "jet", 1));

        assertEquals(0.02, svc.bonoDe(1L, Tipo.XP), 1e-9, "solo la gorra: la ranura 2 no está abierta");
        assertEquals(0.0, svc.bonoDe(1L, Tipo.SUELDO), 1e-9);
    }

    @Test
    @DisplayName("10 · saturación por tipo: 35 % de sueldo se queda en 30 %")
    void saturacionPorTipo() {
        Map<Tipo, Double> bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("jet").orElseThrow(),        // 11 %
                Pasivos.porId("avioneta").orElseThrow(),   //  9 %
                Pasivos.porId("traje").orElseThrow(),      //  8 %
                Pasivos.porId("coche_lujo").orElseThrow()));//  7 % → 35 %
        assertEquals(0.30, bonos.get(Tipo.SUELDO), 1e-9, "el tope satura, no da error");
    }

    @Test
    @DisplayName("11 · se topa la SUMA, no cada bono por separado")
    void seTopaLaSuma() {
        // yate 7 % + jet 6 % + avioneta 6 % + reloj 5 % = 24 % de XP, tope 20 %.
        Map<Tipo, Double> bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("yate").orElseThrow(),
                Pasivos.porId("jet").orElseThrow(),
                Pasivos.porId("avioneta").orElseThrow(),
                Pasivos.porId("reloj").orElseThrow()));
        assertEquals(0.20, bonos.get(Tipo.XP), 1e-9, "20 %, ni 24 % ni el máximo individual (7 %)");
    }

    @Test
    @DisplayName("12 · un tipo sin fuentes devuelve 0.0, no null ni ausencia")
    void tipoSinFuentesDevuelveCero() {
        Map<Tipo, Double> vacio = PasivoService.sumarYTopar(List.of());
        assertEquals(Tipo.values().length, vacio.size(), "siempre los nueve tipos");
        for (Tipo t : Tipo.values()) {
            assertEquals(0.0, vacio.get(t), 1e-9, "el tipo " + t + " debe venir a 0.0");
        }
    }

    @Test
    @DisplayName("13a · error NO_EXISTE: el ítem no está en el catálogo de Items")
    void errorNoExiste() {
        assertEquals(Estado.NO_EXISTE, svc.equipar(1L, "no_existe", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13b · error SIN_PASIVO: el ítem existe pero no da nada (consumibles, camas…)")
    void errorSinPasivo() {
        assertEquals(Estado.SIN_PASIVO, svc.equipar(1L, "fruta", null).estado());
        assertEquals(Estado.SIN_PASIVO, svc.equipar(1L, "casa", null).estado(),
                "una cama no da pasivo: su efecto ya lo da Camas");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13c · error NO_TIENE: no está en el inventario")
    void errorNoTiene() {
        when(inventario.cantidad(1L, "jet")).thenReturn(0);
        assertEquals(Estado.NO_TIENE, svc.equipar(1L, "jet", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13d · error RANURA_INVALIDA: fuera de 1..4")
    void errorRanuraInvalida() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of());
        nivel(50);
        assertEquals(Estado.RANURA_INVALIDA, svc.equipar(1L, "gorra", 0).estado());
        assertEquals(Estado.RANURA_INVALIDA, svc.equipar(1L, "gorra", 5).estado());
        assertEquals(Estado.RANURA_INVALIDA, svc.quitar(1L, 9).estado());
    }

    @Test
    @DisplayName("13e · error RANURA_BLOQUEADA: informa del nivel que hace falta")
    void errorRanuraBloqueada() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of());
        nivel(18);
        ResultadoEquipar r = svc.equipar(1L, "gorra", 3);
        assertEquals(Estado.RANURA_BLOQUEADA, r.estado());
        assertEquals(25, r.nivelRequerido(), "la ranura 3 se abre en el nivel 25");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13f · error YA_EQUIPADO: el mismo ítem no puede ocupar dos ranuras")
    void errorYaEquipado() {
        when(inventario.cantidad(1L, "jet")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of(2, "jet"));
        nivel(50);
        ResultadoEquipar r = svc.equipar(1L, "jet", 3);
        assertEquals(Estado.YA_EQUIPADO, r.estado());
        assertEquals(2, r.ranura(), "hay que decirle en qué ranura lo tiene");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13g · error VACIA: quitar de una ranura que ya está vacía")
    void errorVacia() {
        when(repo.equipados(1L)).thenReturn(Map.of());
        assertEquals(Estado.VACIA, svc.quitar(1L, 2).estado());
        verify(repo, never()).quitar(anyLong(), anyInt());
    }

    @Test
    @DisplayName("14a · equipar sin ranura usa la primera libre DESBLOQUEADA")
    void equiparSinRanuraUsaLaPrimeraLibre() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1, "jet", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));
        nivel(25); // 3 ranuras

        ResultadoEquipar r = svc.equipar(1L, "gorra", null);
        assertEquals(Estado.OK, r.estado());
        assertEquals(2, r.ranura(), "la 1 está ocupada; la 2 está libre y abierta");
        assertNull(r.reemplazado(), "no ha pisado nada");
        verify(repo).equipar(1L, 2, "gorra");
    }

    @Test
    @DisplayName("14b · equipar sin ranura y sin huecos devuelve SIN_HUECO sin tocar nada")
    void equiparSinHueco() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate", 3, "dron", 4, "cohete"));
        nivel(50);

        assertEquals(Estado.SIN_HUECO, svc.equipar(1L, "gorra", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("14c · equipar sobre ranura ocupada reemplaza y dice qué ha salido")
    void equiparReemplaza() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));
        nivel(50);

        ResultadoEquipar r = svc.equipar(1L, "gorra", 1);
        assertEquals(Estado.OK, r.estado());
        assertEquals("jet", r.reemplazado(), "pisar un jet no puede ser silencioso");
        verify(repo).equipar(1L, 1, "gorra");
    }

    @Test
    @DisplayName("15 · quitar devuelve el ítem que sale y los totales resultantes")
    void quitarOk() {
        when(repo.equipados(1L)).thenReturn(Map.of(2, "gorra"));
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1));
        nivel(50);

        ResultadoQuitar r = svc.quitar(1L, 2);
        assertEquals(Estado.OK, r.estado());
        assertEquals("gorra", r.itemId());
        verify(repo).quitar(1L, 2);
    }

    @Test
    @DisplayName("16 · ranuras() devuelve siempre 4, marcando bloqueadas y ausentes")
    void vistaDeRanuras() {
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate"));
        when(inventario.listar(1L)).thenReturn(Map.of("jet", 1)); // el yate ya no lo tiene
        nivel(10); // 2 ranuras

        List<EstadoRanura> rs = svc.ranuras(1L);
        assertEquals(4, rs.size());
        assertEquals("jet", rs.get(0).itemId());
        assertFalse(rs.get(0).bloqueada());
        assertFalse(rs.get(0).falta());
        assertEquals("yate", rs.get(1).itemId());
        assertTrue(rs.get(1).falta(), "equipado pero ausente del inventario: no cuenta");
        assertTrue(rs.get(2).bloqueada(), "la 3 pide nivel 25");
        assertTrue(rs.get(3).bloqueada(), "la 4 pide nivel 50");
        assertNull(rs.get(2).itemId());
    }

    @Test
    @DisplayName("17 · equipablesDe: lo que posees, tiene pasivo y no está ya equipado")
    void equipables() {
        when(inventario.listar(1L)).thenReturn(Map.of(
                "jet", 1, "gorra", 1, "fruta", 5, "casa", 1, "yate", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));

        List<String> res = svc.equipablesDe(1L);
        assertEquals(List.of("gorra", "yate"), res,
                "fruta no tiene pasivo, casa es cama y el jet ya está equipado; orden del catálogo");
    }
}
```

> **Comprobar la firma real del record `UsuarioDiscord`** antes de escribir el helper `nivel(int)`:
> según `XpServiceTest` es `new UsuarioDiscord(discordId, xp, nivel, coins, racha, ultimaRachaFecha,
> idioma, optOutLogros)`. Si hubiera cambiado, ajustar solo esa línea.

- [ ] **Step 3: Ejecutar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivoServiceTest" test
```
`Expected: Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`

**+18 tests.**

- [ ] **Step 4: Verify completo y COMMIT 1**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 30` (los de Testcontainers, saltados en local).

Antes de commitear, actualizar la doc que este commit hace cierta:

- `src/main/java/com/gymprofit/bot/db/README.md`: en «Migraciones actuales» / «Migrations so far»
  cambiar `V3`–`V24` → `V3`–`V25` y añadir en la enumeración inglesa `V25` pasivos equipados
  (*passive-effect slots*).
- `src/main/java/com/gymprofit/bot/services/README.md`: añadir una línea (ES y EN) diciendo que los
  catálogos satélite (`Picos`, `Camas`, `Cofres`, **`Pasivos`**) se emparejan por `itemId` contra
  `Items` sin tocarlo.

Commit (sin `Co-Authored-By`, sin pie de "Generated with"):

```
feat(pasivos): catálogo de efectos pasivos, ranuras y cálculo de bonos

Los 20 ítems de EQUIPO y los 10 vehículos de BIEN dejan de ser decoración: pasan a dar
bonos pasivos sobre lo que el jugador ya hace a diario. Esta primera pieza monta los
cimientos (aún no se aplican en ningún sistema: eso llega en los commits siguientes).

- V25__pasivos_equipados: ranura -> ítem por jugador. PK (discord_id, ranura) para que
  reemplazar sea atómico y UNIQUE (discord_id, item_id) para que el «un ítem en dos
  ranuras» lo garantice el esquema, no solo el service. ON DELETE CASCADE para el RGPD.
- services/Pasivos: catálogo paralelo con los 30 ítems, nueve tipos de bono y sus topes
  globales saturantes. Items.java no se toca (mismo patrón que Picos, Camas y Cofres).
- db/PasivoRepositorio: JDBC plano; traduce el choque del UNIQUE a un error propio.
- services/PasivoService: ranuras por nivel (1/2/3/4 a 0/10/25/50), suma y topado en una
  función pura, y filtrado contra el inventario en cada consulta: si vendes, regalas o
  publicas el ítem, el bono deja de contar sin necesidad de hooks en venta, regalos,
  mercado, trueque ni robo.
- 30 tests nuevos: integridad del catálogo (incluida la tabla de balance pineada), lógica
  de ranuras y errores, y repositorio contra MySQL real.

Novedades:
- Tu equipo y tus vehículos van a empezar a servir para algo. Se han definido los efectos
  pasivos de 30 ítems del catálogo y el sistema de ranuras que los activa: una ranura al
  empezar, la segunda en el nivel 10, la tercera en el 25 y la cuarta en el 50.
- Las camas no cambian: siguen funcionando igual con /descansar.
```

---

### Task 5: Familia `/pasivos ver | equipar | quitar`

Es la tarea más larga del plan. Se hace en cinco bloques: (A) i18n, (B) `PasivosTexto` + su test,
(C) infraestructura de autocompletado, (D) `PasivosComando`, (E) wiring en `Main` + commit.

**Files:**
- Modify: `src/main/resources/messages_es.properties`
- Modify: `src/main/resources/messages_en.properties`
- Create: `src/main/java/com/gymprofit/bot/commands/economia/PasivosTexto.java`
- Create: `src/test/java/com/gymprofit/bot/commands/economia/PasivosTextoTest.java`
- Create: `src/main/java/com/gymprofit/bot/commands/ComandoAutocompletable.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/RouterComandos.java`
- Create: `src/main/java/com/gymprofit/bot/commands/economia/PasivosComando.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `README.md`, `README.en.md` (tabla/lista de comandos)

#### Bloque A — i18n

- [ ] **Step 1: Añadir las claves ES**

Al final de `src/main/resources/messages_es.properties` (respetando el estilo del archivo: sin
espacios alrededor del `=`, `\n` literales, `{0}` para los parámetros):

```properties
# --- Efectos pasivos: comando /pasivos ---
comando.pasivos.familia=Efectos pasivos de tu equipo y tus vehículos
comando.pasivos.ver.descripcion=Ve tus ranuras y los bonos que tienes activos
comando.pasivos.ver.opcion.usuario=Mira los pasivos de otro miembro
comando.pasivos.equipar.descripcion=Pon un ítem con efecto pasivo en una ranura
comando.pasivos.equipar.opcion.item=Ítem con efecto pasivo que tengas
comando.pasivos.equipar.opcion.ranura=Ranura donde ponerlo (1-4). Si no la dices, la primera libre
comando.pasivos.quitar.descripcion=Vacía una ranura (el ítem no se pierde)
comando.pasivos.quitar.opcion.ranura=Ranura que quieres vaciar (1-4)
pasivos.ver.titulo=✨ Pasivos de {0}
pasivos.ver.ranuras=**🔓 Ranuras**
pasivos.ver.ranura.ocupada={0} {1} **{2}**
pasivos.ver.ranura.vacia={0} ➖ Vacía
pasivos.ver.ranura.bloqueada={0} 🔒 Se desbloquea en el nivel {1}
pasivos.ver.ranura.sinitem={0} ⚠️ {1} {2} — ya no lo tienes, no cuenta
pasivos.ver.bonos=**⚙️ Bonos activos**
pasivos.ver.sinbonos=Sin bonos activos todavía. Equipa algo con `/pasivos equipar`.
pasivos.ver.tope=(tope)
pasivos.ver.pie=Ranura {0} en el nivel {1} (te faltan {2} niveles).
pasivos.ver.pie.completo=Ya tienes las 4 ranuras desbloqueadas. A repartir.
pasivos.bono.pct={0}{1} % {2}
pasivos.bono.plano={0}{1} {2}
pasivos.tipo.sueldo=sueldo
pasivos.tipo.cooldown=cooldown de trabajo
pasivos.tipo.xp=XP
pasivos.tipo.energia=energía por tick
pasivos.tipo.mineria=minerales por minado
pasivos.tipo.durabilidad=ahorro de durabilidad
pasivos.tipo.ataque=ataque
pasivos.tipo.defensa=defensa
pasivos.tipo.critico=crítico
pasivos.equipar.titulo=✨ Ranura {0} equipada
pasivos.equipar.ok=Has puesto {0} **{1}** en la ranura **{2}**.\n_{3}_\n\n{4}
pasivos.equipar.reemplazo=\n🔁 Sale de la ranura: {0} **{1}** (lo sigues teniendo).
pasivos.quitar.titulo=✨ Ranura {0} vacía
pasivos.quitar.ok=Sale de la ranura {0}: {1} **{2}**. Sigue en tu inventario.\n\n{3}
pasivos.error.noexiste=Ese ítem no existe. Mira `/tienda` a ver qué hay.
pasivos.error.sinpasivo={0} **{1}** no da ningún efecto pasivo. Solo el equipo y los vehículos; mira los tuyos con `/pasivos ver`.
pasivos.error.notiene=No tienes {0} **{1}**. Cómpralo con `/comprar`.
pasivos.error.ranurainvalida=Las ranuras van de la **1** a la **4**.
pasivos.error.ranurabloqueada=La ranura **{0}** se desbloquea en el nivel **{1}**. Vas por el **{2}**.
pasivos.error.yaequipado=Ya lo tienes en la ranura **{0}**. Un ítem no puede ocupar dos ranuras.
pasivos.error.sinhueco=No te queda ninguna ranura libre. Dime cuál reemplazar con `/pasivos equipar item:… ranura:…`\n{0}
pasivos.error.vacia=La ranura **{0}** ya está vacía.
# --- Efectos pasivos: descripción de cada ítem (tono entrenador; con números) ---
pasivo.gorra.desc=Te la pones y ya vas al gym: +2 % de XP.
pasivo.gafas.desc=Ves venir el golpe antes que el otro: +1 % de crítico.
pasivo.mochila.desc=Cargas más piedra por viaje: +1 mineral por minado.
pasivo.uniforme.desc=De uniforme te pagan mejor y el chaleco algo protege: +4 % de sueldo y +1 de defensa.
pasivo.auriculares.desc=Con música entrenas más y mejor: +3 % de XP.
pasivo.movil.desc=Turnos y rutinas siempre a mano: +3 % de sueldo y +2 % de XP.
pasivo.zapatillas.desc=Buen calzado, menos castigo: +1 de energía por tick y +2 de defensa.
pasivo.mancuernas.desc=Hierro en casa: +3 de ataque y +1 de energía por tick.
pasivo.herramientas.desc=Con tu propio juego rindes más y rompes menos: +5 % de sueldo y 5 % de ahorro de durabilidad.
pasivo.consola.desc=Reflejos, competitividad y horas de práctica: +4 % de XP.
pasivo.patinete.desc=Llegas antes al curro: −4 % de cooldown de trabajo.
pasivo.bici.desc=Vas antes y encima haces cardio: −6 % de cooldown y +1 de energía por tick.
pasivo.guitarra.desc=Aprender un instrumento entrena la cabeza: +5 % de XP.
pasivo.portatil.desc=La herramienta que sirve para todo: +6 % de sueldo y +4 % de XP.
pasivo.camara.desc=Documentas la veta y aprendes del terreno: +1 mineral y +3 % de XP.
pasivo.reloj.desc=Puntual y midiendo cada sesión: +4 % de sueldo y +5 % de XP.
pasivo.telescopio.desc=Localizas el filón antes de picar a ciegas: +1 mineral y 8 % de ahorro de durabilidad.
pasivo.traje.desc=Traje bueno: +8 % de sueldo y +1 % de crítico.
pasivo.dron.desc=Explora la mina por ti y te dice dónde picar: +2 minerales y 10 % de ahorro de durabilidad.
pasivo.moto.desc=Te mueves rápido y llegas a más sitios: −8 % de cooldown y +3 % de sueldo.
pasivo.coche.desc=El primer vehículo de verdad: −9 % de cooldown y +4 % de sueldo.
pasivo.furgoneta.desc=Cabe todo: −7 % de cooldown y +1 mineral por minado.
pasivo.moto_agua.desc=Ocio de verdad: +2 de energía por tick y +3 % de XP.
pasivo.camion.desc=Trabajo pesado: +2 minerales, +6 % de sueldo y 8 % de ahorro de durabilidad.
pasivo.coche_lujo.desc=Deportivo blindado: +7 % de sueldo, +2 % de crítico y +2 de defensa.
pasivo.helicoptero.desc=Apoyo aéreo: −10 % de cooldown, 12 % de ahorro de durabilidad y +3 de ataque.
pasivo.avioneta.desc=Vuelas a donde haga falta: +9 % de sueldo, +6 % de XP y +1 de energía por tick.
pasivo.jet.desc=Jet privado, el mejor paquete de trabajo del juego: +11 % de sueldo, −11 % de cooldown y +6 % de XP.
pasivo.yate.desc=Descanso de lujo: +3 de energía por tick, +7 % de XP y +7 % de sueldo.
pasivo.cohete.desc=El tope absoluto del catálogo: +5 de ataque, +4 de defensa y +3 % de crítico.
# --- Efectos pasivos: línea del perfil ---
perfil.pasivos.linea=\n\n✨ **Pasivos:** {0}
```

- [ ] **Step 2: Añadir las claves EN** (tono equivalente, **no** traducción literal)

Al final de `src/main/resources/messages_en.properties`:

```properties
# --- Passive effects: /pasivos command ---
comando.pasivos.familia=Passive effects from your gear and vehicles
comando.pasivos.ver.descripcion=See your slots and the bonuses you have active
comando.pasivos.ver.opcion.usuario=Check another member's passives
comando.pasivos.equipar.descripcion=Slot an item that grants a passive effect
comando.pasivos.equipar.opcion.item=A passive-granting item you own
comando.pasivos.equipar.opcion.ranura=Slot to use (1-4). Leave it out for the first free one
comando.pasivos.quitar.descripcion=Empty a slot (you keep the item)
comando.pasivos.quitar.opcion.ranura=Slot you want to empty (1-4)
pasivos.ver.titulo=✨ {0}'s passives
pasivos.ver.ranuras=**🔓 Slots**
pasivos.ver.ranura.ocupada={0} {1} **{2}**
pasivos.ver.ranura.vacia={0} ➖ Empty
pasivos.ver.ranura.bloqueada={0} 🔒 Unlocks at level {1}
pasivos.ver.ranura.sinitem={0} ⚠️ {1} {2} — you no longer own it, it doesn't count
pasivos.ver.bonos=**⚙️ Active bonuses**
pasivos.ver.sinbonos=No active bonuses yet. Slot something with `/pasivos equipar`.
pasivos.ver.tope=(cap)
pasivos.ver.pie=Slot {0} at level {1} ({2} levels to go).
pasivos.ver.pie.completo=All 4 slots unlocked. Now spread them well.
pasivos.bono.pct={0}{1} % {2}
pasivos.bono.plano={0}{1} {2}
pasivos.tipo.sueldo=pay
pasivos.tipo.cooldown=work cooldown
pasivos.tipo.xp=XP
pasivos.tipo.energia=energy per tick
pasivos.tipo.mineria=minerals per dig
pasivos.tipo.durabilidad=durability saved
pasivos.tipo.ataque=attack
pasivos.tipo.defensa=defence
pasivos.tipo.critico=crit
pasivos.equipar.titulo=✨ Slot {0} equipped
pasivos.equipar.ok=You slotted {0} **{1}** into slot **{2}**.\n_{3}_\n\n{4}
pasivos.equipar.reemplazo=\n🔁 Out of the slot: {0} **{1}** (you still own it).
pasivos.quitar.titulo=✨ Slot {0} emptied
pasivos.quitar.ok=Out of slot {0}: {1} **{2}**. Still in your inventory.\n\n{3}
pasivos.error.noexiste=That item doesn't exist. Have a look at `/tienda`.
pasivos.error.sinpasivo={0} **{1}** grants no passive effect. Only gear and vehicles do — check yours with `/pasivos ver`.
pasivos.error.notiene=You don't own {0} **{1}**. Buy it with `/comprar`.
pasivos.error.ranurainvalida=Slots go from **1** to **4**.
pasivos.error.ranurabloqueada=Slot **{0}** unlocks at level **{1}**. You're level **{2}**.
pasivos.error.yaequipado=It's already in slot **{0}**. One item can't fill two slots.
pasivos.error.sinhueco=No free slots left. Tell me which one to replace with `/pasivos equipar item:… ranura:…`\n{0}
pasivos.error.vacia=Slot **{0}** is already empty.
# --- Passive effects: per-item description ---
pasivo.gorra.desc=Cap on, straight to the gym: +2 % XP.
pasivo.gafas.desc=You see the hit coming first: +1 % crit.
pasivo.mochila.desc=More rock per trip: +1 mineral per dig.
pasivo.uniforme.desc=In uniform you get paid better and the vest helps: +4 % pay, +1 defence.
pasivo.auriculares.desc=With music you train harder: +3 % XP.
pasivo.movil.desc=Shifts and routines always at hand: +3 % pay, +2 % XP.
pasivo.zapatillas.desc=Good shoes, less punishment: +1 energy per tick, +2 defence.
pasivo.mancuernas.desc=Iron at home: +3 attack, +1 energy per tick.
pasivo.herramientas.desc=Your own kit means more output and less breakage: +5 % pay, 5 % durability saved.
pasivo.consola.desc=Reflexes, drive and hours of practice: +4 % XP.
pasivo.patinete.desc=You get to work sooner: −4 % work cooldown.
pasivo.bici.desc=Faster commute plus cardio: −6 % cooldown, +1 energy per tick.
pasivo.guitarra.desc=Learning an instrument trains the head: +5 % XP.
pasivo.portatil.desc=The tool that does everything: +6 % pay, +4 % XP.
pasivo.camara.desc=You log the vein and learn the ground: +1 mineral, +3 % XP.
pasivo.reloj.desc=On time and measuring every session: +4 % pay, +5 % XP.
pasivo.telescopio.desc=You spot the seam before swinging blind: +1 mineral, 8 % durability saved.
pasivo.traje.desc=A good suit: +8 % pay, +1 % crit.
pasivo.dron.desc=It scouts the mine and tells you where to dig: +2 minerals, 10 % durability saved.
pasivo.moto.desc=Fast and everywhere: −8 % cooldown, +3 % pay.
pasivo.coche.desc=Your first real vehicle: −9 % cooldown, +4 % pay.
pasivo.furgoneta.desc=Everything fits: −7 % cooldown, +1 mineral per dig.
pasivo.moto_agua.desc=Proper time off: +2 energy per tick, +3 % XP.
pasivo.camion.desc=Heavy work pays: +2 minerals, +6 % pay, 8 % durability saved.
pasivo.coche_lujo.desc=Armoured and loud: +7 % pay, +2 % crit, +2 defence.
pasivo.helicoptero.desc=Air support: −10 % cooldown, 12 % durability saved, +3 attack.
pasivo.avioneta.desc=Fly wherever it takes: +9 % pay, +6 % XP, +1 energy per tick.
pasivo.jet.desc=Private jet — the best work package in the game: +11 % pay, −11 % cooldown, +6 % XP.
pasivo.yate.desc=Luxury downtime: +3 energy per tick, +7 % XP, +7 % pay.
pasivo.cohete.desc=The absolute top of the catalogue: +5 attack, +4 defence, +3 % crit.
# --- Passive effects: profile line ---
perfil.pasivos.linea=\n\n✨ **Passives:** {0}
```

- [ ] **Step 3: Comprobar que no falta ninguna clave en un idioma**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=MessagesTest" test
```
Si el repo tiene un test de paridad de claves (buscarlo con
`Get-ChildItem -Recurse src/test -Filter *Messages*`), debe seguir en verde. Si no existe, hacer la
comprobación a mano:

```
$es = (Get-Content src/main/resources/messages_es.properties | Where-Object { $_ -match '^[a-z]' } | ForEach-Object { $_.Split('=')[0] }) | Sort-Object
$en = (Get-Content src/main/resources/messages_en.properties | Where-Object { $_ -match '^[a-z]' } | ForEach-Object { $_.Split('=')[0] }) | Sort-Object
Compare-Object $es $en
```
`Expected: sin salida` (ninguna clave descuadrada).

#### Bloque B — `PasivosTexto` (formateo compartido con `/perfil`)

- [ ] **Step 4: Escribir el test primero**

Crear `src/test/java/com/gymprofit/bot/commands/economia/PasivosTextoTest.java`:

```java
package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.Pasivos;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica los constructores de vista de {@code /pasivos} (estáticos y puros: no tocan JDA ni BD).
 * Se prueba el <b>resultado</b> que ve el jugador, no la aritmética: los bonos se enseñan ya sumados
 * y ya topados, y el tope se marca.
 */
class PasivosTextoTest {

    private static Map<Tipo, Double> bonos(Object... pares) {
        Map<Tipo, Double> m = new EnumMap<>(Tipo.class);
        for (Tipo t : Tipo.values()) {
            m.put(t, 0.0);
        }
        for (int i = 0; i < pares.length; i += 2) {
            m.put((Tipo) pares[i], (Double) pares[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("los bonos se muestran sumados, no la aritmética, y con el signo correcto")
    void lineaDeBonos() {
        String linea = PasivosTexto.bonos(Messages.ES,
                bonos(Tipo.SUELDO, 0.18, Tipo.COOLDOWN_WORK, 0.11, Tipo.ENERGIA_REGEN, 2.0));
        assertTrue(linea.contains("+18 % sueldo"), linea);
        assertTrue(linea.contains("−11 % cooldown de trabajo"), linea + " (el cooldown resta)");
        assertTrue(linea.contains("+2 energía por tick"), linea);
        assertFalse(linea.contains("XP"), "los tipos a 0 no se pintan: " + linea);
    }

    @Test
    @DisplayName("un tipo saturado se marca con (tope)")
    void marcaDeTope() {
        String linea = PasivosTexto.bonos(Messages.ES, bonos(Tipo.SUELDO, 0.30));
        assertTrue(linea.contains("+30 % sueldo"), linea);
        assertTrue(linea.contains(Messages.get(Messages.ES, "pasivos.ver.tope")), linea);
    }

    @Test
    @DisplayName("sin ningún bono la línea sale vacía (no se ensucia el perfil de un novato)")
    void sinBonosCadenaVacia() {
        assertEquals("", PasivosTexto.bonos(Messages.ES, bonos()));
        assertEquals("", PasivosTexto.bonos(Messages.ES, Map.of()));
    }

    @Test
    @DisplayName("las cuatro ranuras se pintan con su estado: ocupada, vacía, bloqueada y ausente")
    void listaDeRanuras() {
        String texto = PasivosTexto.ranuras(Messages.ES, List.of(
                new EstadoRanura(1, "jet", false, false),
                new EstadoRanura(2, "yate", false, true),
                new EstadoRanura(3, null, true, false),
                new EstadoRanura(4, null, true, false)));
        assertTrue(texto.contains("1️⃣"), texto);
        assertTrue(texto.contains(Messages.get(Messages.ES, "item.jet")), texto);
        assertTrue(texto.contains("⚠️"), "el yate vendido se marca: " + texto);
        assertTrue(texto.contains("🔒"), "las bloqueadas llevan candado: " + texto);
        assertTrue(texto.contains("25") && texto.contains("50"),
                "cada bloqueada dice su nivel: " + texto);
    }

    @Test
    @DisplayName("el pie anuncia el siguiente desbloqueo, y desaparece con las 4 abiertas")
    void pieDeSiguienteDesbloqueo() {
        String pie = PasivosTexto.pie(Messages.ES, 21);
        assertTrue(pie.contains("3"), "la siguiente es la ranura 3: " + pie);
        assertTrue(pie.contains("25"), pie);
        assertTrue(pie.contains("4"), "le faltan 4 niveles: " + pie);
        assertEquals(Messages.get(Messages.ES, "pasivos.ver.pie.completo"),
                PasivosTexto.pie(Messages.ES, 50));
    }

    @Test
    @DisplayName("todos los ítems del catálogo tienen descripción en ES y EN")
    void descripcionesCompletas() {
        for (Pasivos.Pasivo p : Pasivos.CATALOGO) {
            String es = Messages.get(Messages.ES, "pasivo." + p.itemId() + ".desc");
            String en = Messages.get(Messages.EN, "pasivo." + p.itemId() + ".desc");
            assertFalse(es.isBlank() || es.startsWith("!"), "falta pasivo." + p.itemId() + ".desc (ES)");
            assertFalse(en.isBlank() || en.startsWith("!"), "falta pasivo." + p.itemId() + ".desc (EN)");
        }
    }

    @Test
    @DisplayName("todos los tipos tienen nombre traducido en ES y EN")
    void nombresDeTipoCompletos() {
        for (Tipo t : Tipo.values()) {
            assertFalse(PasivosTexto.nombreTipo(Messages.ES, t).isBlank(), "ES: " + t);
            assertFalse(PasivosTexto.nombreTipo(Messages.EN, t).isBlank(), "EN: " + t);
        }
        assertEquals(4, PasivoService.RANURAS_MAX, "el formateo asume 4 ranuras");
    }
}
```

> **Nota:** `Messages.get` con una clave inexistente devuelve en este repo la propia clave o un
> marcador; el test comprueba que no está en blanco. Verificar el comportamiento real de
> `Messages.get` (`src/main/java/com/gymprofit/bot/i18n/Messages.java`) y ajustar el `startsWith("!")`
> si el marcador de clave ausente es otro.

- [ ] **Step 5: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivosTextoTest" test
```
`Expected: COMPILATION ERROR — cannot find symbol: class PasivosTexto`

- [ ] **Step 6: Escribir `PasivosTexto`**

Crear `src/main/java/com/gymprofit/bot/commands/economia/PasivosTexto.java`:

```java
package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.Pasivos;
import com.gymprofit.bot.services.Pasivos.Tipo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Constructores de vista de los efectos pasivos: estáticos, puros y sin JDA, para poder testearlos
 * sin levantar nada. Viven en {@code commands/economia} porque los usan dos comandos del mismo
 * paquete: {@code /pasivos ver} y la línea nueva de {@code /perfil ver}.
 *
 * <p>Regla de presentación: se enseña el <b>resultado</b>, no la aritmética (+18 % sueldo, nunca
 * «+11 % +7 %»), y si un tipo está saturado se marca con «(tope)» para que nadie equipe una cuarta
 * pieza del mismo tipo sin enterarse de que no le suma nada.
 */
public final class PasivosTexto {

    /** Emojis de ranura, en orden 1..4. */
    private static final String[] NUMEROS = {"1️⃣", "2️⃣", "3️⃣", "4️⃣"};

    private PasivosTexto() {
    }

    /** Nombre localizado de un tipo de bono ({@code sueldo}, {@code XP}…). */
    public static String nombreTipo(Locale locale, Tipo tipo) {
        return Messages.get(locale, "pasivos.tipo." + switch (tipo) {
            case SUELDO -> "sueldo";
            case COOLDOWN_WORK -> "cooldown";
            case XP -> "xp";
            case ENERGIA_REGEN -> "energia";
            case MINERIA_CANTIDAD -> "mineria";
            case MINERIA_DURABILIDAD -> "durabilidad";
            case COMBATE_ATAQUE -> "ataque";
            case COMBATE_DEFENSA -> "defensa";
            case CRITICO -> "critico";
        });
    }

    /**
     * Línea de bonos activos, ya sumados y ya topados, separados por «·». Cadena <b>vacía</b> si no
     * hay ninguno: así el perfil de un jugador nuevo no se ensucia con una línea a ceros.
     */
    public static String bonos(Locale locale, Map<Tipo, Double> bonos) {
        StringJoiner sj = new StringJoiner(" · ");
        for (Tipo t : Tipo.values()) {
            double v = bonos.getOrDefault(t, 0.0);
            if (v <= 0) {
                continue;
            }
            // El cooldown es lo único que resta: se pinta con el menos tipográfico, no con guion.
            String signo = t == Tipo.COOLDOWN_WORK ? "−" : "+";
            String texto = Pasivos.esPorcentual(t)
                    ? Messages.get(locale, "pasivos.bono.pct", signo,
                            Math.round(v * 100), nombreTipo(locale, t))
                    : Messages.get(locale, "pasivos.bono.plano", signo,
                            Math.round(v), nombreTipo(locale, t));
            // Saturado: hay que decirlo, o el jugador seguirá comprando piezas del mismo tipo.
            if (v >= Pasivos.TOPES.get(t)) {
                texto = texto + " " + Messages.get(locale, "pasivos.ver.tope");
            }
            sj.add(texto);
        }
        return sj.toString();
    }

    /** Las cuatro ranuras en orden, cada una con su estado (ocupada, vacía, bloqueada o ausente). */
    public static String ranuras(Locale locale, List<EstadoRanura> ranuras) {
        StringBuilder sb = new StringBuilder();
        for (EstadoRanura r : ranuras) {
            String num = NUMEROS[r.ranura() - 1];
            if (r.bloqueada()) {
                sb.append(Messages.get(locale, "pasivos.ver.ranura.bloqueada", num,
                        PasivoService.nivelDeRanura(r.ranura())));
            } else if (r.itemId() == null) {
                sb.append(Messages.get(locale, "pasivos.ver.ranura.vacia", num));
            } else {
                String emoji = Items.porId(r.itemId()).map(Items::emoji).orElse("❔");
                String nombre = Messages.get(locale, "item." + r.itemId());
                sb.append(Messages.get(locale,
                        r.falta() ? "pasivos.ver.ranura.sinitem" : "pasivos.ver.ranura.ocupada",
                        num, emoji, nombre));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Pie con el siguiente desbloqueo, o el mensaje de «ya las tienes todas». */
    public static String pie(Locale locale, int nivel) {
        int abiertas = PasivoService.ranurasDe(nivel);
        if (abiertas >= PasivoService.RANURAS_MAX) {
            return Messages.get(locale, "pasivos.ver.pie.completo");
        }
        int siguiente = abiertas + 1;
        int nivelNecesario = PasivoService.nivelDeRanura(siguiente);
        return Messages.get(locale, "pasivos.ver.pie", siguiente, nivelNecesario,
                nivelNecesario - nivel);
    }

    /** Descripción localizada del pasivo de un ítem (la que explica el sistema desde dentro). */
    public static String descripcion(Locale locale, String itemId) {
        return Messages.get(locale, "pasivo." + itemId + ".desc");
    }
}
```

- [ ] **Step 7: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PasivosTextoTest" test
```
`Expected: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

**+7 tests.**

#### Bloque C — Infraestructura de autocompletado

**Por qué hace falta:** el diseño pide autocompletado filtrado por inventario en
`/pasivos equipar <item>`, y el repo **no tiene nada**: `RouterComandos` no sobrescribe
`onCommandAutoCompleteInteraction` y no hay ningún `setAutoComplete(true)`. `addChoice` no vale
(Discord admite 25 choices como máximo y los ítems son 30).

- [ ] **Step 8: Crear el contrato**

Crear `src/main/java/com/gymprofit/bot/commands/ComandoAutocompletable.java`:

```java
package com.gymprofit.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/**
 * Contrato opcional para los comandos que ofrecen <b>autocompletado</b> en alguna de sus opciones.
 * {@link RouterComandos} enruta el evento a quien lo implemente.
 *
 * <p>Se usa cuando la lista de valores válidos depende del jugador (p. ej. los ítems con efecto
 * pasivo que <i>ese</i> jugador posee) o cuando pasa de los 25 {@code choices} que admite Discord.
 * Filtrar aquí evita la mitad de los errores antes de que ocurran.
 */
public interface ComandoAutocompletable extends Comando {

    /**
     * Responde al autocompletado. <b>Debe contestar siempre</b> (aunque sea con lista vacía) y
     * hacerlo rápido: Discord da 3 s y no admite {@code defer}, así que nada de llamadas lentas.
     */
    void autocompletar(CommandAutoCompleteInteractionEvent evento);
}
```

- [ ] **Step 9: Enrutar el evento en `RouterComandos`**

En `src/main/java/com/gymprofit/bot/commands/RouterComandos.java` añadir el import
`net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;` y, después de
`onSlashCommandInteraction`, el método:

```java
    /**
     * Enruta el autocompletado a los comandos que lo implementan y aísla sus errores.
     *
     * <p>Discord <b>exige</b> una respuesta en 3 s y no admite diferirla: si el comando revienta, se
     * contesta con una lista vacía en vez de dejar la interacción colgada.
     */
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent evento) {
        Comando comando = comandosPorNombre.get(evento.getName());
        if (!(comando instanceof ComandoAutocompletable autocompletable)) {
            return;
        }
        try {
            autocompletable.autocompletar(evento);
        } catch (RuntimeException e) {
            log.error("Error autocompletando /{}", evento.getName(), e);
            evento.replyChoices(List.of()).queue();
        }
    }
```

`List` ya está importado en el archivo.

#### Bloque D — `PasivosComando`

- [ ] **Step 10: Escribir el comando**

Crear `src/main/java/com/gymprofit/bot/commands/economia/PasivosComando.java`:

```java
package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.PasivoService.ResultadoEquipar;
import com.gymprofit.bot.services.PasivoService.ResultadoQuitar;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /pasivos} con subcomandos (ver, equipar, quitar). Los efectos pasivos del equipo y los
 * vehículos se activan poniéndolos en <b>ranuras</b>: 1 al empezar y la 2.ª, 3.ª y 4.ª a los niveles
 * 10, 25 y 50 (los mismos umbrales que los rangos). El ítem <b>no se consume</b>: se puede quitar y
 * volver a poner sin coste.
 *
 * <p>Las tres respuestas son <b>públicas</b> (las acciones de economía se juegan a la vista de todos,
 * y enseñar el yate es media gracia de comprarlo); solo los <b>errores</b> de validación salen en
 * efímero.
 *
 * <p>El bono se recalcula siempre contra el inventario en {@code PasivoService}: si el jugador vende,
 * regala o publica el ítem, la ranura se queda pero deja de contar, y {@code ver} lo marca con ⚠️.
 */
public final class PasivosComando implements ComandoAutocompletable {

    private static final String NOMBRE = "pasivos";
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;

    private final PasivoService pasivos;
    private final UsuarioDiscordRepositorio usuarios;

    public PasivosComando(PasivoService pasivos, UsuarioDiscordRepositorio usuarios) {
        this.pasivos = pasivos;
        this.usuarios = usuarios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.pasivos.ver.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.ver.opcion.usuario"));

        // Autocompletado en vez de choices: son 30 ítems y Discord solo admite 25 choices; además
        // así se filtra por lo que el jugador tiene de verdad.
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.pasivos.equipar.opcion.item"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.equipar.opcion.item"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.pasivos.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.pasivos.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.pasivos.ver.descripcion").addOptions(usuario),
                        sub("equipar", "comando.pasivos.equipar.descripcion")
                                .addOptions(item, ranura("comando.pasivos.equipar.opcion.ranura", false)),
                        sub("quitar", "comando.pasivos.quitar.descripcion")
                                .addOptions(ranura("comando.pasivos.quitar.opcion.ranura", true)));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData ranura(String claveDesc, boolean obligatoria) {
        return new OptionData(OptionType.INTEGER, "ranura",
                Messages.get(Messages.ES, claveDesc), obligatoria)
                .setMinValue(1)
                .setMaxValue(PasivoService.RANURAS_MAX)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        switch (sub) {
            case "ver" -> ver(evento, locale);
            case "equipar" -> equipar(evento, locale);
            case "quitar" -> quitar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Sugiere los ítems que el jugador <b>posee</b>, <b>tienen pasivo</b> y <b>no están ya
     * equipados</b>, filtrados por lo que va escribiendo. Cada sugerencia enseña el nombre del ítem
     * para que el sistema se explique solo sin abrir documentación.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        if (!"item".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String escrito = evento.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> opciones = pasivos.equipablesDe(evento.getUser().getIdLong()).stream()
                .map(id -> new Command.Choice(etiqueta(locale, id), id))
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(escrito))
                .limit(MAX_SUGERENCIAS)
                .toList();
        evento.replyChoices(opciones).queue();
    }

    /** Etiqueta de una sugerencia: «🛫 Jet privado». Discord limita el nombre a 100 caracteres. */
    private static String etiqueta(Locale locale, String itemId) {
        String texto = Items.porId(itemId).map(Items::emoji).orElse("❔")
                + " " + Messages.get(locale, "item." + itemId);
        return texto.length() > 100 ? texto.substring(0, 100) : texto;
    }

    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        User objetivo = evento.getOption("usuario") != null
                ? evento.getOption("usuario").getAsUser() : evento.getUser();

        evento.deferReply(false).queue();
        long id = objetivo.getIdLong();
        List<EstadoRanura> ranuras = pasivos.ranuras(id);
        String bonos = PasivosTexto.bonos(locale, pasivos.bonosDe(id));
        int nivel = usuarios.buscar(id).map(UsuarioDiscord::nivel).orElse(0);

        String desc = Messages.get(locale, "pasivos.ver.ranuras") + "\n"
                + PasivosTexto.ranuras(locale, ranuras) + "\n"
                + Messages.get(locale, "pasivos.ver.bonos") + "\n"
                + (bonos.isEmpty() ? Messages.get(locale, "pasivos.ver.sinbonos") : bonos);

        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "pasivos.ver.titulo", objetivo.getName()), desc)
                .setThumbnail(objetivo.getEffectiveAvatarUrl())
                .setFooter(PasivosTexto.pie(locale, nivel), EmbedFactory.iconoUrl())
                .build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void equipar(SlashCommandInteractionEvent evento, Locale locale) {
        String itemId = evento.getOption("item").getAsString();
        OptionMapping opcionRanura = evento.getOption("ranura");
        Integer ranura = opcionRanura == null ? null : opcionRanura.getAsInt();
        long id = evento.getUser().getIdLong();

        ResultadoEquipar r = pasivos.equipar(id, itemId, ranura);
        if (r.estado() != PasivoService.Estado.OK) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    errorEquipar(locale, r, id))).setEphemeral(true).queue();
            return;
        }

        String cuerpo = Messages.get(locale, "pasivos.equipar.ok",
                emoji(itemId), nombre(locale, itemId), r.ranura(),
                PasivosTexto.descripcion(locale, itemId),
                PasivosTexto.bonos(locale, r.totales()));
        if (r.reemplazado() != null) {
            cuerpo += Messages.get(locale, "pasivos.equipar.reemplazo",
                    emoji(r.reemplazado()), nombre(locale, r.reemplazado()));
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "pasivos.equipar.titulo", r.ranura()), cuerpo).build()).queue();
    }

    private void quitar(SlashCommandInteractionEvent evento, Locale locale) {
        int ranura = evento.getOption("ranura").getAsInt();
        ResultadoQuitar r = pasivos.quitar(evento.getUser().getIdLong(), ranura);
        if (r.estado() != PasivoService.Estado.OK) {
            String mensaje = r.estado() == PasivoService.Estado.RANURA_INVALIDA
                    ? Messages.get(locale, "pasivos.error.ranurainvalida")
                    : Messages.get(locale, "pasivos.error.vacia", ranura);
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                    .setEphemeral(true).queue();
            return;
        }
        String cuerpo = Messages.get(locale, "pasivos.quitar.ok", ranura,
                emoji(r.itemId()), nombre(locale, r.itemId()),
                PasivosTexto.bonos(locale, r.totales()));
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "pasivos.quitar.titulo", ranura), cuerpo).build()).queue();
    }

    /** Traduce el estado de error a su mensaje. Uno por fila de la tabla de errores del diseño. */
    private String errorEquipar(Locale locale, ResultadoEquipar r, long discordId) {
        String itemId = r.itemId();
        return switch (r.estado()) {
            case NO_EXISTE -> Messages.get(locale, "pasivos.error.noexiste");
            case SIN_PASIVO -> Messages.get(locale, "pasivos.error.sinpasivo",
                    emoji(itemId), nombre(locale, itemId));
            case NO_TIENE -> Messages.get(locale, "pasivos.error.notiene",
                    emoji(itemId), nombre(locale, itemId));
            case RANURA_INVALIDA -> Messages.get(locale, "pasivos.error.ranurainvalida");
            case RANURA_BLOQUEADA -> Messages.get(locale, "pasivos.error.ranurabloqueada",
                    r.ranura(), r.nivelRequerido(),
                    usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0));
            case YA_EQUIPADO -> Messages.get(locale, "pasivos.error.yaequipado", r.ranura());
            // Sin hueco NO se elige por el jugador: se listan las ranuras y se le pide que decida.
            // Pisar en silencio un cohete de 3 000 000 de coins es inaceptable aunque sea reversible.
            case SIN_HUECO -> Messages.get(locale, "pasivos.error.sinhueco",
                    PasivosTexto.ranuras(locale, pasivos.ranuras(discordId)));
            default -> Messages.get(locale, "comando.error.generico");
        };
    }

    private static String emoji(String itemId) {
        return Items.porId(itemId).map(Items::emoji).orElse("❔");
    }

    private static String nombre(Locale locale, String itemId) {
        return Items.porId(itemId).map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
    }
}
```

> **Verificar antes de escribir:** que `EmbedFactory` expone `iconoUrl()` (lo hace: `EmbedFactory.java:110`)
> y que `EmbedBuilder.setFooter(String, String)` se usa así en otros comandos; si el repo tiene un
> helper de pie propio, usarlo en su lugar. `OptionData(OptionType, name, desc, required,
> autocomplete)` es el constructor de 5 argumentos de JDA 5: comprobar que existe en la versión
> fijada en `pom.xml` (JDA 5.6.1 sí lo tiene).

#### Bloque E — Wiring en `Main` y commit

- [ ] **Step 11: Mover la creación del inventario y crear los pasivos antes de `XpService`**

En `src/main/java/com/gymprofit/bot/Main.java`, dentro del `if (db != null)`, **antes** de
`XpService xpService = new XpService(usuarios);` (hoy en la línea ~320):

```java
            // El inventario y las ranuras de pasivos se crean aquí arriba (antes que XpService)
            // porque los efectos pasivos entran en TODOS los sistemas, incluida la XP por mensaje:
            // el bono de XP se aplica dentro de XpService.ganarXp, el único punto por el que pasa
            // toda la XP del bot.
            InventarioRepositorio inventarioRepo = new InventarioRepositorio(db.dataSource());
            PasivoService pasivoService = new PasivoService(
                    new PasivoRepositorio(db.dataSource()), inventarioRepo, usuarios);
            comandos.add(new PasivosComando(pasivoService, usuarios));
```

Y **borrar** la creación duplicada de `InventarioRepositorio` que hoy está en la línea ~407 (la del
comentario «El inventario se crea aquí arriba (y no con la tienda) porque el descanso lo necesita»),
dejando solo el comentario adaptado:

```java
            // (inventarioRepo y pasivoService se crean más arriba: los necesitan XpService,
            // el descanso —la cama sale del inventario— y todo lo que va después.)
```

Añadir los imports `com.gymprofit.bot.db.PasivoRepositorio`,
`com.gymprofit.bot.services.PasivoService` y
`com.gymprofit.bot.commands.economia.PasivosComando`.

- [ ] **Step 12: Compilar y verificar el conteo de comandos**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 37`

Recontar los comandos de primer nivel:
```
(Select-String -Path src/main/java -Include *.java -Pattern "Commands.slash\(" -Recurse).Count
```
`Expected: 60` (eran 59; el límite de Discord es 100, así que sigue habiendo margen de sobra).

- [ ] **Step 13: Actualizar la doc de comandos y COMMIT 2**

- `README.md`: en la lista de comandos de economía (línea ~78, la que enumera
  `` `/inventario` (ver · usar · vender) · `/mejoras` · `/mejorar` ``) añadir
  `` · `/pasivos` (ver · equipar · quitar) ``.
- `README.en.md`: lo mismo en su línea equivalente (~76).

Commit:

```
feat(pasivos): comando /pasivos (ver, equipar, quitar) con autocompletado

La familia /pasivos es la cara visible del sistema de ranuras. Aún no aplica los bonos a
ningún sistema (eso llega en los commits siguientes): de momento se equipa, se quita y se
ve el estado.

- /pasivos ver [usuario]: las 4 ranuras con su estado (ocupada, vacía, bloqueada por nivel,
  o equipada pero ausente del inventario), los bonos ya sumados y ya topados, y un pie con
  el siguiente desbloqueo. Público, como el resto de la economía.
- /pasivos equipar <item> [ranura]: sin ranura usa la primera libre desbloqueada; si no
  queda ninguna NO elige por ti, lista las cuatro y pregunta cuál reemplazar. El ítem no se
  consume nunca.
- /pasivos quitar <ranura>: vacía la ranura; el ítem sigue en el inventario.
- Autocompletado nuevo en el bot: se añade el contrato ComandoAutocompletable y su enrutado
  en RouterComandos. Era necesario (30 ítems > los 25 choices que admite Discord) y además
  filtra por lo que cada jugador tiene de verdad.
- commands/economia/PasivosTexto: formateo compartido con /perfil, estático y testeable.
- i18n completo ES + EN, incluidas las 30 descripciones que hacen que el sistema se explique
  desde dentro del bot.
- 60 comandos de primer nivel (de 100 que permite Discord).

Novedades:
- ¡Nuevo comando /pasivos! Tu equipo y tus vehículos ya se pueden equipar en ranuras.
  Empiezas con 1 ranura; la 2.ª llega en el nivel 10, la 3.ª en el 25 y la 4.ª en el 50.
- Equipar no gasta el ítem: puedes cambiar de build cuando quieras, gratis.
- Ojo: el bono solo cuenta mientras tengas el ítem. Si lo vendes, lo regalas o lo pones en
  el mercado, la ranura se queda pero deja de contar (y /pasivos ver te avisa con un ⚠️).
```

---

### Task 6: Integración `TrabajoService` (sueldo y cooldown)

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/TrabajoService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

**Orden de la tubería (decisión documentada, no tocar):**

```
calcularPago(min, max, azar)
  → conBonoEstudios(base, estudios)      // tope +25 %
  → conBonoPasivos(base, bonoSueldo)     // NUEVO, tope +30 %
  → conPenalizacionFatiga(base, fatiga)  // ×0,8, SIGUE SIENDO LA ÚLTIMA
```

La fatiga va la última **a propósito**: es un recorte del sueldo **final**, no del base, para que
empuje al ciclo diario de dormir sin anular los bonos que el jugador se ha ganado. Los dos bonos son
**multiplicativos entre sí** (`base × 1,25 × 1,30`), no aditivos: así cada tope queda independiente
y ninguno de los dos sistemas eclipsa al otro.

- [ ] **Step 1: Escribir los tests primero**

Añadir a `src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java` (los imports
`java.time.Duration` y `org.junit.jupiter.api.DisplayName` ya están o hay que añadirlos):

```java
    @Test
    @DisplayName("conBonoPasivos aplica el bono de sueldo y satura en el tope del +30 %")
    void bonoDeSueldoPorPasivos() {
        assertEquals(100, TrabajoService.conBonoPasivos(100, 0.0), "sin pasivos no cambia nada");
        assertEquals(111, TrabajoService.conBonoPasivos(100, 0.11), "el jet solo: +11 %");
        assertEquals(130, TrabajoService.conBonoPasivos(100, 0.30), "justo en el tope");
        assertEquals(130, TrabajoService.conBonoPasivos(100, 0.35),
                "35 % bruto satura a 30 %: el tope no es un error, es el diseño");
    }

    @Test
    @DisplayName("el orden de la tubería es estudios → pasivos → fatiga, y no otro")
    void ordenDeLaTuberiaDeSueldo() {
        // 100 base · 25 de estudios (+25 %) · +30 % de pasivos · fatiga (×0,8).
        int conEstudios = TrabajoService.conBonoEstudios(100, 25);
        assertEquals(125, conEstudios);
        int conPasivos = TrabajoService.conBonoPasivos(conEstudios, 0.30);
        assertEquals(163, conPasivos, "round(125 × 1,30) = 163");
        assertEquals(130, TrabajoService.conPenalizacionFatiga(conPasivos, true),
                "round(163 × 0,8) = 130; la fatiga recorta el sueldo FINAL, no el base");
        // Si alguien invirtiera el orden (fatiga antes que pasivos) saldría 130 también por
        // casualidad en este caso, así que se comprueba un caso donde el orden sí se nota:
        assertEquals(11, TrabajoService.conPenalizacionFatiga(
                TrabajoService.conBonoPasivos(TrabajoService.conBonoEstudios(9, 25), 0.30), true),
                "9 → 11 (estudios) → 14 (pasivos) → 11 (fatiga)");
    }

    @Test
    @DisplayName("cooldownEfectivo: 60 min de base, 45 min con el tope del −25 %")
    void cooldownConPasivos() {
        assertEquals(Duration.ofMinutes(60), TrabajoService.cooldownEfectivo(0.0));
        assertEquals(Duration.ofMinutes(45), TrabajoService.cooldownEfectivo(0.25));
        assertEquals(Duration.ofMinutes(45), TrabajoService.cooldownEfectivo(0.90),
                "el tope satura: el suelo del cooldown es 45 min pase lo que pase");
        assertEquals(Duration.ofMinutes(54), TrabajoService.cooldownEfectivo(0.10),
                "el helicóptero solo: 60 × 0,9 = 54 min");
    }
```

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test
```
`Expected: COMPILATION ERROR — cannot find symbol: method conBonoPasivos / cooldownEfectivo`

- [ ] **Step 3: Modificar `TrabajoService`**

1. Añadir el import `com.gymprofit.bot.services.Pasivos;` no hace falta (mismo paquete).
2. Añadir el campo y el constructor nuevo (el de 4 argumentos se conserva para no reescribir los
   tests que no tienen nada que ver con pasivos):

```java
    /** Efectos pasivos del jugador. {@code null} en los tests que no los usan. */
    private final PasivoService pasivos;

    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          PasivoService pasivos) {
        this.personajes = personajes;
        this.economia = economia;
        this.usuarios = usuarios;
        this.descanso = descanso;
        this.pasivos = pasivos;
    }

    /** Constructor sin pasivos (tests y arranques degradados): equivale a no tener ninguno equipado. */
    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso) {
        this(personajes, economia, usuarios, descanso, null);
    }
```

(El constructor de 4 argumentos existente pasa a delegar; hay que borrar su cuerpo antiguo.)

3. Añadir el helper privado y las dos funciones puras:

```java
    /** Bono pasivo de un tipo, o 0 si el service no está inyectado. */
    private double bono(long discordId, Pasivos.Tipo tipo) {
        return pasivos == null ? 0.0 : pasivos.bonoDe(discordId, tipo);
    }

    /**
     * Aplica el bono de sueldo de los efectos pasivos al pago <b>ya bonificado por estudios</b>.
     * <b>Puro.</b>
     *
     * <p>Es multiplicativo respecto al bono de estudios ({@code base × 1,25 × 1,30}), no aditivo:
     * mantiene los dos topes independientes y evita que un sistema eclipse al otro. Satura en
     * {@code Pasivos.TOPES.get(SUELDO)} (+30 %), igual que {@code conBonoEstudios} con el suyo.
     *
     * @param base pago ya bonificado por estudios
     * @param bono bono de sueldo de los pasivos (fracción; ya viene topado del service, pero se
     *             vuelve a topar aquí para que la función sea segura por sí sola)
     */
    public static int conBonoPasivos(int base, double bono) {
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.SUELDO), Math.max(0, bono));
        return (int) Math.round(base * (1 + aplicado));
    }

    /**
     * Cooldown efectivo de currar con el bono de pasivos aplicado. <b>Puro.</b>
     *
     * <p>{@link #COOLDOWN_WORK} sigue siendo el valor base de 60 min y no se toca ni se renombra;
     * con el tope del −25 % el suelo es de <b>45 minutos</b>.
     */
    public static Duration cooldownEfectivo(double bono) {
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.COOLDOWN_WORK), Math.max(0, bono));
        return Duration.ofSeconds(Math.round(COOLDOWN_WORK.getSeconds() * (1 - aplicado)));
    }
```

4. En `trabajar(...)`, sustituir el bloque del cooldown:

```java
        if (p.ultimoWork() != null) {
            // El cooldown ya no es una constante: los pasivos de COOLDOWN_WORK lo recortan hasta
            // un suelo de 45 min.
            Duration cooldown = cooldownEfectivo(bono(discordId, Pasivos.Tipo.COOLDOWN_WORK));
            long restante = cooldown.getSeconds()
                    - Duration.between(p.ultimoWork(), ahora).getSeconds();
            if (restante > 0) {
                return new ResultadoWork(EstadoWork.EN_COOLDOWN, 0, p.energia(), restante);
            }
        }
```

5. Y la línea del pago:

```java
        int pago = conPenalizacionFatiga(
                conBonoPasivos(conBonoEstudios(base, p.estudios()),
                        bono(discordId, Pasivos.Tipo.SUELDO)),
                fatiga);
```

- [ ] **Step 4: Actualizar el wiring en `Main`**

```java
            TrabajoService trabajoService = new TrabajoService(
                    personajeRepo, economiaRepo, usuarios, descansoService, pasivoService);
```

- [ ] **Step 5: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test
```
`Expected: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` (eran 6)

**+3 tests.**

---

### Task 7: Integración `XpService`

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/XpService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/XpServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

El bono se aplica dentro de `ganarXp(discordId, cantidad)`, **el único punto por el que pasa toda la
XP del bot** (mensajes, `/daily`, trivia, misiones, victorias de combate, bonus de mazmorra). No hay
que tocar a ningún llamante.

- [ ] **Step 1: Escribir los tests primero**

Añadir a `src/test/java/com/gymprofit/bot/services/XpServiceTest.java` (añadir los imports
`com.gymprofit.bot.services.Pasivos` no hace falta —mismo paquete— y sí
`org.junit.jupiter.api.DisplayName`, `org.mockito.Mockito.mock`, `org.mockito.Mockito.lenient` si
no estuvieran):

```java
    @Test
    @DisplayName("conBonoPasivos: +20 % sobre 100 son 120, y satura en el tope")
    void bonoDeXpPuro() {
        assertEquals(100, XpService.conBonoPasivos(100, 0.0), "sin pasivos no cambia nada");
        assertEquals(120, XpService.conBonoPasivos(100, 0.20));
        assertEquals(120, XpService.conBonoPasivos(100, 0.35), "el tope del +20 % satura");
        assertEquals(0, XpService.conBonoPasivos(0, 0.20), "sobre 0 no se inventa XP");
    }

    @Test
    @DisplayName("suelo de +1: un bono positivo sobre una cantidad pequeña siempre se nota")
    void sueloDeUnPunto() {
        // round(3 × 1,20) = 4, así que aquí el suelo no hace falta…
        assertEquals(4, XpService.conBonoPasivos(3, 0.20));
        // …pero con 2 XP y +20 % el redondeo daría 2 (sin ganancia) y se leería como un bug.
        assertEquals(3, XpService.conBonoPasivos(2, 0.20));
        assertEquals(2, XpService.conBonoPasivos(1, 0.20));
    }

    @Test
    @DisplayName("ganarXp aplica el bono de pasivos antes de persistir")
    void ganarXpConPasivos() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonoDe(5L, Pasivos.Tipo.XP)).thenReturn(0.20);
        when(repositorio.obtenerOCrear(5L))
                .thenReturn(new UsuarioDiscord(5L, 0, 0, 0, 0, null, "es", false));

        new XpService(repositorio, pasivos).ganarXp(5L, 100);

        ArgumentCaptor<UsuarioDiscord> guardado = ArgumentCaptor.forClass(UsuarioDiscord.class);
        verify(repositorio).guardar(guardado.capture());
        assertEquals(120, guardado.getValue().xp(), "100 XP + 20 % de pasivos");
    }
```

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=XpServiceTest" test
```
`Expected: COMPILATION ERROR — cannot find symbol: method conBonoPasivos`

- [ ] **Step 3: Modificar `XpService`**

Reemplazar el cuerpo de la clase por:

```java
public final class XpService {

    private final UsuarioDiscordRepositorio repositorio;
    /** Efectos pasivos del jugador. {@code null} en los tests que no los usan. */
    private final PasivoService pasivos;

    public XpService(UsuarioDiscordRepositorio repositorio, PasivoService pasivos) {
        this.repositorio = repositorio;
        this.pasivos = pasivos;
    }

    /**
     * Constructor sin pasivos: equivale a no tener ninguno equipado. Existe para no reescribir los
     * tests de XP, que no tienen nada que ver con este módulo.
     */
    public XpService(UsuarioDiscordRepositorio repositorio) {
        this(repositorio, null);
    }

    /**
     * Suma XP a un usuario (creándolo si no existía), recalcula el nivel y guarda.
     *
     * <p>Aquí es donde entra el bono de XP de los efectos pasivos: este es el <b>único punto</b> por
     * el que pasa toda la XP del bot (mensajes, {@code /daily}, trivia, misiones, victorias de
     * combate, bonus de mazmorra), así que ningún llamante tiene que enterarse.
     *
     * @param discordId ID del usuario de Discord
     * @param cantidad  XP a sumar ({@code > 0})
     * @return el resultado, incluyendo si ha subido de nivel
     */
    public XpResultado ganarXp(long discordId, int cantidad) {
        UsuarioDiscord actual = repositorio.obtenerOCrear(discordId);

        int ganada = conBonoPasivos(cantidad,
                pasivos == null ? 0.0 : pasivos.bonoDe(discordId, Pasivos.Tipo.XP));
        int xpNueva = actual.xp() + ganada;
        int nivelAnterior = NivelCalculadora.nivelDeXp(actual.xp());
        int nivelNuevo = NivelCalculadora.nivelDeXp(xpNueva);

        UsuarioDiscord actualizado = new UsuarioDiscord(
                actual.discordId(), xpNueva, nivelNuevo, actual.coins(), actual.racha(),
                actual.ultimaRachaFecha(), actual.idioma(), actual.optOutLogros());
        repositorio.guardar(actualizado);

        return new XpResultado(actualizado, nivelNuevo > nivelAnterior, nivelAnterior, nivelNuevo);
    }

    /**
     * Aplica el bono de XP de los efectos pasivos. <b>Puro.</b>
     *
     * <p>Con <b>suelo de +1</b> cuando el bono es positivo y la base ≥ 1: que un +20 % sobre 2 XP se
     * redondeara a 2 (sin ganancia) se leería como un bug, no como un redondeo.
     */
    public static int conBonoPasivos(int base, double bono) {
        if (bono <= 0 || base <= 0) {
            return base;
        }
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.XP), bono);
        return Math.max(base + 1, (int) Math.round(base * (1 + aplicado)));
    }
}
```

- [ ] **Step 4: Actualizar el wiring en `Main`**

```java
            XpService xpService = new XpService(usuarios, pasivoService);
```
(`pasivoService` ya existe: se creó en la Task 5, justo encima.)

- [ ] **Step 5: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=XpServiceTest" test
```
`Expected: Tests run: (los que hubiera) + 3, Failures: 0, Errors: 0`

**+3 tests.**

---

### Task 8: Integración `MineriaService` (cantidad, tope y durabilidad)

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/MineriaService.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/MinarComando.java`
- Modify: `src/test/java/com/gymprofit/bot/services/MineriaServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `src/main/resources/messages_es.properties` y `messages_en.properties`

**El punto delicado:** `CANTIDAD_MAX = 5` es un **tope duro** dentro de `tirar`. Si no sube con el
bono, el pasivo **no haría nada** para un minero de nivel alto que ya toca el tope — que es justo el
que se ha comprado el dron.

- [ ] **Step 1: Escribir los tests primero**

Añadir a `src/test/java/com/gymprofit/bot/services/MineriaServiceTest.java` (hará falta importar
`com.gymprofit.bot.services.Pasivos` no —mismo paquete— y sí `org.junit.jupiter.api.DisplayName`):

```java
    /** Servicio con azar fijo y un bono de pasivos concreto. */
    private MineriaService svc(double azar, Pasivos.Tipo tipo, double bono) {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonoDe(anyLong(), eq(tipo))).thenReturn(bono);
        return new MineriaService(mineria, personajes, inventario, economia, usuarios, descanso,
                pasivos, () -> azar);
    }

    @Test
    @DisplayName("MINERIA_CANTIDAD suma minerales Y sube el tope duro del minado")
    void bonoDeCantidadSubeTambienElTope() {
        picosATope();
        when(inventario.listar(1L)).thenReturn(Map.of("pico_mithril", 1));
        when(personajes.gastarEnergia(anyLong(), anyInt())).thenReturn(true);
        // Nivel de minería alto: sin bono ya estaría clavado en CANTIDAD_MAX = 5.
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 200, null));

        int sinBono = svc(0.4).minar(1L).minerales().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(MineriaService.CANTIDAD_MAX, sinBono, "sin bono, clavado en el tope");

        int conBono = svc(0.4, Pasivos.Tipo.MINERIA_CANTIDAD, 2.0)
                .minar(1L).minerales().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(MineriaService.CANTIDAD_MAX + 2, conBono,
                "el tope sube con el bono; si no, el dron no serviría de nada al que ya topa");
    }

    @Test
    @DisplayName("MINERIA_DURABILIDAD: con la tirada favorable el pico NO se gasta")
    void bonoDeDurabilidadAhorraElPico() {
        picosATope();
        when(inventario.listar(1L)).thenReturn(Map.of("pico_hierro", 1));
        when(personajes.gastarEnergia(anyLong(), anyInt())).thenReturn(true);
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 0, null));

        // azar = 0.0 < 0.12 → se ahorra la durabilidad.
        var r = svc(0.0, Pasivos.Tipo.MINERIA_DURABILIDAD, 0.12).minar(1L);
        assertEquals(Estado.OK, r.estado());
        assertTrue(r.durabilidadAhorrada(), "hay que decírselo al jugador o no verá el pasivo");
        verify(mineria, never()).gastarDurabilidad(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("MINERIA_DURABILIDAD: con la tirada desfavorable el pico sí se gasta")
    void sinSuerteElPicoSeGasta() {
        picosATope();
        when(inventario.listar(2L)).thenReturn(Map.of("pico_hierro", 1));
        when(personajes.gastarEnergia(anyLong(), anyInt())).thenReturn(true);
        when(mineria.obtenerOCrear(2L)).thenReturn(new MineriaEstado(2L, 0, null));

        // azar = 0.99, muy por encima del 0.12 del bono.
        var r = svc(0.99, Pasivos.Tipo.MINERIA_DURABILIDAD, 0.12).minar(2L);
        assertEquals(Estado.OK, r.estado());
        assertFalse(r.durabilidadAhorrada());
        verify(mineria).gastarDurabilidad(anyLong(), anyString(), anyInt());
    }
```

> Ajustar `new MineriaEstado(...)` a la firma real del record (`grep -n "record MineriaEstado"
> src/main/java/com/gymprofit/bot/db/MineriaEstado.java`) y añadir `assertFalse`, `eq` y
> `mock(PasivoService.class)` a los imports estáticos si faltan.

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=MineriaServiceTest" test
```
`Expected: COMPILATION ERROR — constructor MineriaService / method durabilidadAhorrada()`

- [ ] **Step 3: Modificar `MineriaService`**

1. Añadir el componente `durabilidadAhorrada` **al final** del record `Resultado` y documentarlo:

```java
    /**
     * Resultado de minar.
     *
     * @param estado              resultado
     * @param minerales           minerales obtenidos (id → cantidad), vacío si no {@code OK}
     * @param nivelNuevo          nivel de minería tras el minado
     * @param detalle             segundos de cooldown restantes o energía necesaria, según el fallo
     * @param picoId              pico usado (o {@code null})
     * @param durabilidad         durabilidad restante del pico usado
     * @param durabilidadMax      durabilidad máxima del pico usado
     * @param durabilidadAhorrada el pasivo de durabilidad ha evitado el desgaste en este minado
     */
    public record Resultado(Estado estado, Map<String, Integer> minerales, int nivelNuevo,
                            int detalle, String picoId, int durabilidad, int durabilidadMax,
                            boolean durabilidadAhorrada) {
    }
```

y en `fallo(...)`: `return new Resultado(estado, Map.of(), 0, detalle, null, 0, 0, false);`

2. Añadir `PasivoService pasivos` a **los dos** constructores (antes de `azar`):

```java
    private final PasivoService pasivos;

    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          PasivoService pasivos, BatallaService.Aleatorio azar) {
        this.mineria = mineria;
        this.personajes = personajes;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
        this.descanso = descanso;
        this.pasivos = pasivos;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          PasivoService pasivos) {
        this(mineria, personajes, inventario, economia, usuarios, descanso, pasivos,
                () -> ThreadLocalRandom.current().nextDouble());
    }
```

En `MineriaServiceTest` hay un helper `svc(double azar)`: cambiarlo a
`return new MineriaService(mineria, personajes, inventario, economia, usuarios, descanso,
mock(PasivoService.class), () -> azar);` — un mock sin stubs devuelve `0.0` en `bonoDe`, así que los
tests existentes no cambian de comportamiento.

3. En `minar(...)`, sustituir el bloque final:

```java
        int bonoCantidad = (int) Math.round(bono(discordId, Pasivos.Tipo.MINERIA_CANTIDAD));
        Map<String, Integer> obtenidos = tirar(pico.tier(), estado.nivelMineria(), bonoCantidad);
        obtenidos.forEach((id, cant) -> inventario.anadir(discordId, id, cant));
        mineria.registrarMinado(discordId);
        // Pasivo de durabilidad: con suerte el pico no se gasta este minado. Se tira con el mismo
        // Aleatorio inyectado, así que el test es determinista sin añadir infraestructura.
        boolean ahorrada = azar.next() < bono(discordId, Pasivos.Tipo.MINERIA_DURABILIDAD);
        if (!ahorrada) {
            mineria.gastarDurabilidad(discordId, pico.itemId(), pico.durabilidadMax());
        }
        return new Resultado(Estado.OK, obtenidos, estado.nivelMineria() + 1, 0,
                pico.itemId(), ahorrada ? durActual : Math.max(0, durActual - 1),
                pico.durabilidadMax(), ahorrada);
```

4. Cambiar la firma de `tirar` y su tope:

```java
    /**
     * Tira los minerales de un minado: la cantidad crece con el nivel; cada unidad se elige entre los
     * extraíbles por el pico, con peso inverso al tier (los raros salen menos).
     *
     * <p>{@code bonoCantidad} suma unidades <b>y sube el tope</b> {@link #CANTIDAD_MAX}. Lo segundo
     * es imprescindible: sin ello, un minero de nivel alto (que ya toca el tope) no notaría el pasivo,
     * y es justo el que se lo ha comprado.
     */
    private Map<String, Integer> tirar(int tierPico, int nivel, int bonoCantidad) {
        List<Minerales> elegibles = Minerales.extraiblesCon(tierPico);
        int cantidad = Math.min(CANTIDAD_MAX + bonoCantidad,
                1 + nivel / 25 + (azar.next() < 0.5 ? 1 : 0) + bonoCantidad);
        // … resto igual
    }
```

5. Añadir el helper privado:

```java
    /** Bono pasivo de un tipo, o 0 si el service no está inyectado. */
    private double bono(long discordId, Pasivos.Tipo tipo) {
        return pasivos == null ? 0.0 : pasivos.bonoDe(discordId, tipo);
    }
```

- [ ] **Step 4: Que `/minar` lo cuente**

En `src/main/java/com/gymprofit/bot/commands/economia/MinarComando.java`, donde se pinta la
durabilidad del pico tras un minado `OK`, añadir la línea si `r.durabilidadAhorrada()`:

```java
        if (r.durabilidadAhorrada()) {
            // Si no se dice, el jugador no se entera de que su pasivo está funcionando.
            cuerpo.append('\n').append(Messages.get(locale, "minar.durabilidad.ahorrada"));
        }
```

(adaptar `cuerpo` al nombre real del `StringBuilder`/variable del comando).

Y las claves i18n:

```properties
# messages_es.properties
minar.durabilidad.ahorrada=🛡️ Tu equipo ha evitado el desgaste: el pico sigue igual.
```
```properties
# messages_en.properties
minar.durabilidad.ahorrada=🛡️ Your gear saved the wear: the pick is untouched.
```

- [ ] **Step 5: Actualizar el wiring en `Main`**

```java
            MineriaService mineriaService = new MineriaService(mineriaRepo,
                    personajeRepo, inventarioRepo, economiaRepo, usuarios, descansoService,
                    pasivoService);
```

- [ ] **Step 6: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=MineriaServiceTest" test
```
`Expected: Tests run: (los que hubiera) + 3, Failures: 0, Errors: 0`

**+3 tests.**

- [ ] **Step 7: Verify completo y COMMIT 3**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 46`

Commit:

```
feat(pasivos): los bonos ya se notan al currar, ganar XP y minar

Primera tanda de integraciones. Cada service lee solo el tipo de bono que le importa; el
resto no se entera.

- TrabajoService: el bono de sueldo entra en la tubería entre estudios y fatiga (los dos
  bonos son multiplicativos entre sí y la fatiga sigue siendo la última, porque recorta el
  sueldo final y no el base). El cooldown de currar deja de ser una constante:
  cooldownEfectivo() lo recorta hasta un suelo de 45 min con el tope del -25 %.
- XpService: el bono entra en ganarXp, el único punto por el que pasa toda la XP del bot,
  así que vale para mensajes, /daily, trivia, misiones y combate sin tocar a nadie más.
  Con suelo de +1 para que un bono positivo sobre poca XP no se pierda en el redondeo.
- MineriaService: el bono de cantidad suma minerales Y sube el tope duro del minado (si no,
  el minero de nivel alto -que es quien se compra el dron- no notaría nada). El bono de
  durabilidad tira contra el mismo azar inyectable y, con suerte, el pico no se desgasta;
  /minar lo dice para que se vea que funciona.
- 9 tests nuevos, incluido el guardián del orden de la tubería del sueldo.

Novedades:
- Los pasivos ya se notan: si tienes equipo de sueldo, cobras más al currar; si tienes
  vehículos rápidos, esperas menos entre turnos (hasta 45 min en vez de 60).
- Todo lo que da XP en el servidor -mensajes, /daily, trivia, misiones, combate- se
  beneficia del bono de XP.
- Al minar puedes sacar más minerales y, con suerte, tu pico no se desgasta: cuando pase,
  /minar te lo dice.
```

---

### Task 9: Integración combate — el problema de `nuevaSesion` privado

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/BatallaService.java`
- Modify: `src/test/java/com/gymprofit/bot/services/BatallaServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

**El problema y su solución.** `BatallaService.nuevaSesion(p, monstruo, mundoId)` es **`private`**
(línea 234) y es el sitio correcto: ahí se congelan ataque, defensa, crítico, esquiva y robo de vida
antes de crear la `CombateSesion`. No se hace público (dejaría de ser un detalle interno y ampliaría
la superficie del service sin motivo) y no se toca `CombateService`. La solución es doble:

1. **Se extrae la aplicación de los bonos a una función pura, estática y pública**
   `conPasivos(ataque, defensa, crit, bonos)` que devuelve un record `BonosCombate`. Eso es lo que se
   testea directamente, sin BD y sin JDA.
2. **`PasivoService` se inyecta por constructor** (el service ya recibe un `Aleatorio` inyectable, así
   que el patrón está), y `nuevaSesion` la llama con `pasivos.bonosDe(p.discordId())`. El test de
   integración se hace a través de `iniciar(...)`, que **sí es público** y devuelve la sesión ya
   construida: se comprueban `sesion.ataqueJugador()`, `sesion.defensaJugador()` y
   `sesion.critJugador()`.

**Orden dentro de `nuevaSesion`: pasivos ANTES que encantamientos** para el ataque, porque el
encantamiento `DANO_PCT` multiplica y debe multiplicar sobre el ataque completo (que es la lectura
intuitiva de «+X % de daño»). Es una decisión y va escrita en el código.

- [ ] **Step 1: Escribir los tests primero**

Añadir a `src/test/java/com/gymprofit/bot/services/BatallaServiceTest.java`:

```java
    /** Mock de pasivos sin ningún bono: es lo que usan todos los tests que no van de esto. */
    private PasivoService sinPasivos() {
        PasivoService p = mock(PasivoService.class);
        when(p.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of()));
        return p;
    }

    @Test
    @DisplayName("conPasivos suma ataque y defensa planos y el crítico aditivo, con techo 0,9")
    void conPasivosEsPuro() {
        var bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow(),       // +5 at, +4 def, +3 % crit
                Pasivos.porId("mancuernas").orElseThrow()));  // +3 at
        var r = BatallaService.conPasivos(100, 50, 0.20, bonos);
        assertEquals(108, r.ataque());
        assertEquals(54, r.defensa());
        assertEquals(0.23, r.critico(), 1e-9);

        // Mismo techo duro que el encantamiento CRITICO: un jugador ya saturado no gana nada.
        assertEquals(0.9, BatallaService.conPasivos(1, 1, 0.89, bonos).critico(), 1e-9);
        // Sin bonos no cambia nada.
        var cero = PasivoService.sumarYTopar(List.of());
        assertEquals(100, BatallaService.conPasivos(100, 50, 0.20, cero).ataque());
    }

    @Test
    @DisplayName("la sesión nace con el ataque, la defensa y el crítico ya sumados")
    void laSesionNaceConLosPasivos() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow())));

        // (Reutilizar el mismo montaje de mocks que usan los tests de iniciar() de esta clase:
        //  personaje, mundo desbloqueado, nivel suficiente, sin cooldown y con energía.)
        var conPasivos = svcConPasivos(0.5, pasivos).iniciar(1L, MONSTRUO_DE_PRUEBA);
        var sinPasivosRes = svc(0.5).iniciar(1L, MONSTRUO_DE_PRUEBA);

        assertEquals(InicioEstado.OK, conPasivos.estado());
        assertEquals(sinPasivosRes.sesion().ataqueJugador() + 5,
                conPasivos.sesion().ataqueJugador(), "el cohete da +5 de ataque");
        assertEquals(sinPasivosRes.sesion().defensaJugador() + 4,
                conPasivos.sesion().defensaJugador(), "y +4 de defensa");
        assertEquals(sinPasivosRes.sesion().critJugador() + 0.03,
                conPasivos.sesion().critJugador(), 1e-9, "y +3 % de crítico");
    }

    @Test
    @DisplayName("la sesión es un snapshot: cambiar los pasivos a mitad de mazmorra no la altera")
    void elSnapshotNoCambiaAMitadDePelea() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of()));

        var sesion = svcConPasivos(0.5, pasivos).iniciar(1L, MONSTRUO_DE_PRUEBA).sesion();
        int ataqueAntes = sesion.ataqueJugador();

        // El jugador equipa el cohete justo antes del golpe final del jefe: no le vale de nada.
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow())));

        assertEquals(ataqueAntes, sesion.ataqueJugador(),
                "los bonos se congelan al empezar: nada de equipar el cohete a mitad de pelea");
    }
```

Y el helper nuevo junto al `svc(double)` existente:

```java
    /** Servicio con azar fijo y unos pasivos concretos. */
    private BatallaService svcConPasivos(double azar, PasivoService pasivos) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, descanso,
                pasivos, () -> azar);
    }
```

y cambiar el `svc(double azar)` existente para que pase `sinPasivos()`:

```java
    /** Servicio con azar fijo (factor de daño = 1.0) y sin ningún pasivo equipado. */
    private BatallaService svc(double azar) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, descanso,
                sinPasivos(), () -> azar);
    }
```

> `MONSTRUO_DE_PRUEBA` es el id que ya usan los tests de `iniciar` en este archivo (leerlo del test y
> reutilizarlo, no inventar uno). Importar `com.gymprofit.bot.services.PasivoService` no hace falta
> (mismo paquete); sí `org.junit.jupiter.api.DisplayName` si no estuviera.

- [ ] **Step 2: Verlo fallar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=BatallaServiceTest" test
```
`Expected: COMPILATION ERROR — constructor BatallaService / method conPasivos`

- [ ] **Step 3: Modificar `BatallaService`**

1. Añadir el record y la función pura (junto a los demás records públicos de la clase):

```java
    /**
     * Ataque, defensa y crítico tras aplicar los efectos pasivos. Existe para que la aplicación de
     * los bonos sea una función <b>pura y pública</b> —y por tanto testeable sin base de datos—
     * aunque {@link #nuevaSesion} siga siendo privado, que es donde debe estar el snapshot.
     */
    public record BonosCombate(int ataque, int defensa, double critico) {
    }

    /**
     * Aplica los bonos pasivos de combate al ataque, la defensa y el crítico base. <b>Pura.</b>
     *
     * <p>Los planos se redondean con {@code Math.round} y el crítico se suma <b>antes</b> del tope
     * existente de {@code CombateService.probCritico}, con el mismo techo duro de 0,9 que usa el
     * encantamiento {@code CRITICO}: un jugador ya saturado no gana nada, y eso está bien — el
     * crítico de pasivos es para quien aún no ha entrenado fuerza.
     */
    public static BonosCombate conPasivos(int ataque, int defensa, double crit,
                                          Map<Pasivos.Tipo, Double> bonos) {
        return new BonosCombate(
                ataque + (int) Math.round(bonos.getOrDefault(Pasivos.Tipo.COMBATE_ATAQUE, 0.0)),
                defensa + (int) Math.round(bonos.getOrDefault(Pasivos.Tipo.COMBATE_DEFENSA, 0.0)),
                Math.min(0.9, crit + bonos.getOrDefault(Pasivos.Tipo.CRITICO, 0.0)));
    }
```

Añadir el import `java.util.Map;`.

2. Añadir `PasivoService pasivos` a **los dos** constructores, antes de `azar`:

```java
    private final PasivoService pasivos;

    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos, DescansoService descanso,
                          PasivoService pasivos, Aleatorio azar) {
        this.personajes = personajes;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.economia = economia;
        this.xp = xp;
        this.mundos = mundos;
        this.descanso = descanso;
        this.pasivos = pasivos;
        this.azar = azar;
    }

    /** Constructor de producción: azar real ({@link ThreadLocalRandom}). */
    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos, DescansoService descanso,
                          PasivoService pasivos) {
        this(personajes, inventario, usuarios, economia, xp, mundos, descanso, pasivos,
                () -> ThreadLocalRandom.current().nextDouble());
    }
```

3. Reescribir `nuevaSesion` (sigue siendo `private`):

```java
    /** Construye la sesión aplicando ataque/crit/esquiva/robo del personaje (arma, nivel, encanto). */
    private CombateSesion nuevaSesion(Personaje p, Monstruos monstruo, String mundoId) {
        int ataque = CombateService.ataqueDe(p) + p.armaNivel() * CombateService.NIVEL_DANO;
        double crit = CombateService.probCritico(p);
        double esquiva = CombateService.probEsquiva(p);
        double roboVida = 0;

        // Los pasivos entran ANTES que el encantamiento a propósito: DANO_PCT multiplica y debe
        // multiplicar sobre el ataque completo, que es la lectura intuitiva de «+X % de daño».
        // Y entran AQUÍ, en el snapshot, para que valgan toda la pelea y toda la mazmorra: sin
        // consultas por turno y sin el exploit de equipar el cohete antes del golpe final del jefe.
        BonosCombate bp = conPasivos(ataque, CombateService.defensaDe(p), crit,
                pasivos == null ? Map.of() : pasivos.bonosDe(p.discordId()));
        ataque = bp.ataque();
        int defensa = bp.defensa();
        crit = bp.critico();

        Encantamiento e = p.armaEncanto() == null ? null
                : Encantamiento.porId(p.armaEncanto()).orElse(null);
        if (e != null) {
            switch (e.tipo()) {
                case DANO_PLANO -> ataque += (int) e.magnitud();
                case DANO_PCT -> ataque = (int) Math.round(ataque * (1 + e.magnitud()));
                case CRITICO -> crit = Math.min(0.9, crit + e.magnitud());
                case ESQUIVA -> esquiva = Math.min(0.6, esquiva + e.magnitud());
                case ROBO_VIDA -> roboVida = e.magnitud();
            }
        }
        return new CombateSesion(p.discordId(), mundoId, monstruo, ataque, defensa, crit, esquiva,
                roboVida, CombateService.hpCombate(p));
    }
```

- [ ] **Step 4: Actualizar el wiring en `Main`**

```java
            BatallaService batallaService = new BatallaService(personajeRepo, inventarioRepo,
                    usuarios, economiaRepo, xpService, mundoRepo, descansoService, pasivoService);
```

- [ ] **Step 5: Verlo pasar**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=BatallaServiceTest" test
```
`Expected: Tests run: (los que hubiera) + 3, Failures: 0, Errors: 0`

**+3 tests.**

**Recordatorio de balance (no hace falta tocar nada del bestiario):** el build más orientado a
combate posible (`cohete` + `helicoptero` + `mancuernas` + `coche_lujo`) aporta +11 de ataque, +4 de
defensa y +5 % de crítico, o sea **+15 sobre un poder de combate de 260 = +5,8 %**; aun con los topes
teóricos sería +8,5 %. Por debajo del 10 % en el peor caso: **no se rebalancea ni un monstruo, ni un
jefe, ni una oleada de mazmorra.**

---

### Task 10: Integración `EnergiaJob` — el segundo pase

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/db/PersonajeRepositorio.java`
- Modify: `src/main/java/com/gymprofit/bot/jobs/EnergiaJob.java`
- Modify: `src/test/java/com/gymprofit/bot/db/PasivoRepositorioTest.java`

**El planteamiento.** Hoy la regeneración es **un solo `UPDATE` masivo** para todos los personajes
(`PersonajeRepositorio.regenerarEnergia`), con un `NOT EXISTS` que salta a los dormidos: una consulta
cada 30 min para todo el servidor. Esa eficiencia **no se negocia**. El diseño **conserva ese
`UPDATE` global tal cual** y añade un **segundo pase** que toca únicamente a quien tiene equipado un
pasivo de `ENERGIA_REGEN` **y** conserva el ítem. Coste real: **una consulta más cada 30 minutos**.

Puntos que hay que respetar:
- El `JOIN` contra `inventario` es lo que aplica aquí la regla de «se recalcula contra el inventario»
  **sin traer nada a memoria**.
- Se repite el `NOT EXISTS` de dormidos: quien duerme **no** recibe tampoco el extra (ya cobra al
  despertar; sería doble ración).
- Los ids y las magnitudes **se generan desde `Pasivos.fuentesDe(...)`**, nunca escritos a mano en la
  SQL, y van como **parámetros** del `PreparedStatement`.
- El tope es **+5** porque `EnergiaJob.REGEN` vale **5**, no 10: así el pasivo **como mucho duplica**
  el goteo (5 → 10) y sigue muy por debajo de lo que da una noche de sueño (hasta 100). Con +10 lo
  habría triplicado y habría competido con el ciclo de descanso, que es justo lo que ese módulo
  existe para empujar.

- [ ] **Step 1: Añadir el segundo pase a `PersonajeRepositorio`**

Justo debajo de `regenerarEnergia(int)`:

```java
    /**
     * <b>Segundo pase</b> de la regeneración: suma la energía extra de los efectos pasivos, solo a
     * quien tiene uno equipado <b>y conserva el ítem</b>.
     *
     * <p>El {@code UPDATE} masivo de {@link #regenerarEnergia(int)} se conserva intacto: esto es una
     * consulta adicional cada 30 minutos, no un rediseño del job. El {@code JOIN} contra
     * {@code inventario} aplica aquí la regla de «el bono se recalcula contra el inventario» sin
     * traerse una sola fila a memoria, y el {@code NOT EXISTS} de dormidos se repite porque quien
     * duerme ya cobra su energía al despertar (sumar las dos vías sería doble ración).
     *
     * <p>Los ids y las magnitudes vienen del catálogo en código ({@code Pasivos.fuentesDe}); la SQL
     * se <b>genera</b> a partir de ese mapa y todo va como parámetro, nunca concatenado.
     *
     * @param fuentes itemId → energía extra que aporta (orden estable)
     * @param tope    tope global del tipo {@code ENERGIA_REGEN}
     * @return número de personajes afectados
     */
    public int regenerarEnergiaPasivos(Map<String, Double> fuentes, int tope) {
        if (fuentes.isEmpty()) {
            return 0;
        }
        StringBuilder casos = new StringBuilder();
        StringBuilder marcas = new StringBuilder();
        for (int i = 0; i < fuentes.size(); i++) {
            casos.append(" WHEN ? THEN ?");
            marcas.append(i == 0 ? "?" : ", ?");
        }
        String sql = "UPDATE personajes p JOIN ("
                + "SELECT pe.discord_id, SUM(CASE pe.item_id" + casos + " ELSE 0 END) AS extra "
                + "FROM pasivos_equipados pe "
                + "JOIN inventario i ON i.discord_id = pe.discord_id "
                + "AND i.item_id = pe.item_id AND i.cantidad > 0 "
                + "WHERE pe.item_id IN (" + marcas + ") "
                + "GROUP BY pe.discord_id) b ON b.discord_id = p.discord_id "
                + "SET p.energia = LEAST(100, p.energia + LEAST(?, b.extra)) "
                + "WHERE p.energia < 100 "
                + "AND NOT EXISTS (SELECT 1 FROM descanso d "
                + "WHERE d.discord_id = p.discord_id AND d.dormido_desde IS NOT NULL)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (Map.Entry<String, Double> e : fuentes.entrySet()) {
                ps.setString(i++, e.getKey());
                ps.setInt(i++, (int) Math.round(e.getValue()));
            }
            for (String itemId : fuentes.keySet()) {
                ps.setString(i++, itemId);
            }
            ps.setInt(i, tope);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error regenerando la energía de los pasivos", e);
        }
    }
```

Añadir el import `java.util.Map;`.

- [ ] **Step 2: Llamarlo desde `EnergiaJob`**

En `src/main/java/com/gymprofit/bot/jobs/EnergiaJob.java`, añadir el import
`com.gymprofit.bot.services.Pasivos;` y sustituir `regenerar()`:

```java
    private void regenerar() {
        try {
            int afectados = personajes.regenerarEnergia(REGEN);
            // Segundo pase: SOLO los que tienen equipado un pasivo de energía y conservan el ítem.
            // Es una consulta más cada 30 min; el UPDATE masivo de arriba se queda igual porque su
            // eficiencia es lo que permite que el job sea invisible.
            int conPasivos = personajes.regenerarEnergiaPasivos(
                    Pasivos.fuentesDe(Pasivos.Tipo.ENERGIA_REGEN),
                    (int) Math.round(Pasivos.TOPES.get(Pasivos.Tipo.ENERGIA_REGEN)));
            if (afectados > 0 || conPasivos > 0) {
                log.debug("Energía regenerada a {} personajes (+extra de pasivos a {})",
                        afectados, conPasivos);
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job de energía", e);
        }
    }
```

Y actualizar el Javadoc de la clase añadiendo un párrafo:

```java
 * <p>Desde los efectos pasivos hay un <b>segundo pase</b>: quien tenga equipado un ítem con bono de
 * energía (y siga teniéndolo en el inventario) recibe hasta {@code +5} extra. El tope está puesto
 * deliberadamente por debajo de lo que da una noche de sueño: dormir sigue siendo la vía principal.
```

- [ ] **Step 3: Ampliar el test de Testcontainers**

Añadir a `src/test/java/com/gymprofit/bot/db/PasivoRepositorioTest.java` un segundo `@Test` (mismo
patrón de `assumeTrue` + contenedor):

```java
    @Test
    void segundoPaseDeEnergiaSoloAQuienTieneElItemYNoDuerme() {
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
                var personajes = new PersonajeRepositorio(db.dataSource());
                var inventario = new InventarioRepositorio(db.dataSource());
                var pasivos = new PasivoRepositorio(db.dataSource());
                var descanso = new DescansoRepositorio(db.dataSource());

                // A: yate equipado y en el inventario → +3.
                // B: yate equipado pero VENDIDO → nada (la regla del inventario, aplicada en SQL).
                // C: yate equipado y en inventario, pero DORMIDO → nada (ya cobra al despertar).
                for (long id : new long[]{101L, 102L, 103L}) {
                    usuarios.obtenerOCrear(id);
                    personajes.obtenerOCrear(id);
                    descanso.obtenerOCrear(id);
                    pasivos.equipar(id, 1, "yate");
                }
                inventario.anadir(101L, "yate", 1);
                inventario.anadir(103L, "yate", 1);
                descanso.acostar(103L, java.time.Instant.now(), null);

                // Se les baja la energía para que el UPDATE (energia < 100) los alcance.
                personajes.gastarEnergia(101L, 50);
                personajes.gastarEnergia(102L, 50);
                personajes.gastarEnergia(103L, 50);
                int antesA = personajes.obtenerOCrear(101L).energia();
                int antesB = personajes.obtenerOCrear(102L).energia();
                int antesC = personajes.obtenerOCrear(103L).energia();

                int afectados = personajes.regenerarEnergiaPasivos(
                        com.gymprofit.bot.services.Pasivos.fuentesDe(
                                com.gymprofit.bot.services.Pasivos.Tipo.ENERGIA_REGEN),
                        5);

                assertEquals(1, afectados, "solo A cumple las tres condiciones");
                assertEquals(antesA + 3, personajes.obtenerOCrear(101L).energia(), "el yate da +3");
                assertEquals(antesB, personajes.obtenerOCrear(102L).energia(),
                        "vendió el yate: la ranura sigue ahí pero no cuenta");
                assertEquals(antesC, personajes.obtenerOCrear(103L).energia(),
                        "está dormido: cobrará al despertar, no por goteo");
            }
        }
    }
```

> Ajustar los nombres reales (`personajes.gastarEnergia`, `descanso.acostar`,
> `descanso.obtenerOCrear`) si difieren; están verificados contra `PersonajeRepositorio` y
> `DescansoRepositorio` a fecha de este plan.

- [ ] **Step 4: Verify completo y COMMIT 4**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 50` (2 de Testcontainers saltados en local;
**pendientes de CI**)

Commit:

```
feat(pasivos): los bonos llegan al combate y a la regeneración de energía

Segunda tanda de integraciones, y con esto los nueve tipos de bono ya hacen algo.

- BatallaService: los tres bonos de combate (ataque, defensa y crítico) entran en el
  snapshot que ya se hacía al empezar la pelea, así que valen toda la batalla y toda la
  mazmorra: ni consultas por turno, ni equipar el cohete justo antes del golpe final del
  jefe. Para poder testearlo sin abrir nuevaSesion (que sigue siendo privado), la
  aplicación de los bonos se extrae a conPasivos(), una función pura y pública. Los pasivos
  se aplican ANTES del encantamiento, para que un DANO_PCT multiplique sobre el ataque
  completo.
- EnergiaJob: se conserva el UPDATE masivo tal cual y se añade un segundo pase que toca
  solo a quien tiene equipado un pasivo de energía Y conserva el ítem (el JOIN contra
  inventario aplica esa regla sin traer nada a memoria). Los dormidos quedan fuera, como en
  el pase base. Coste: una consulta más cada 30 minutos.
- El balance del combate se queda por debajo del +6 % del poder en el peor caso realista,
  así que no se rebalancea ningún monstruo, jefe ni oleada de mazmorra.
- 5 tests nuevos (3 de combate, 2 contra MySQL real).

Novedades:
- Los pasivos de combate ya cuentan: ataque, defensa y crítico se aplican al empezar la
  pelea y valen para toda la mazmorra. Son un extra, no un atajo: el equipo y los
  atributos siguen mandando.
- Si tienes equipo que da energía, cada media hora recuperas un poco más. Dormir sigue
  siendo, con diferencia, la mejor forma de recuperarla.
```

---

### Task 11: Línea `✨ Pasivos` en `/perfil ver`

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/PerfilComando.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

La clave i18n `perfil.pasivos.linea` ya se añadió en la Task 5 (`\n\n✨ **Pasivos:** {0}` / EN
`\n\n✨ **Passives:** {0}`). Si no hay ningún bono, **la línea no aparece**: no se ensucia el perfil
de un jugador nuevo.

- [ ] **Step 1: Inyectar `PasivoService` en `PerfilComando`**

```java
    private final EconomiaService economia;
    private final InsigniaService insignias;
    private final PasivoService pasivos;

    public PerfilComando(EconomiaService economia, InsigniaService insignias, PasivoService pasivos) {
        this.economia = economia;
        this.insignias = insignias;
        this.pasivos = pasivos;
    }
```

Añadir el import `com.gymprofit.bot.services.PasivoService;`.

- [ ] **Step 2: Añadir la línea al cuerpo de `ver`**

En el método `ver(...)`, tras construir `desc`:

```java
        String desc = Messages.get(locale, "perfil.cuerpo",
                p.coins(), p.personaje().energia(), p.personaje().salud(),
                p.personaje().fuerza(), p.personaje().resistencia(), p.personaje().carisma(),
                CombateService.poderCombate(p.personaje()), arma, armadura, p.personaje().estudios());
        // Los pasivos solo se pintan si hay alguno: un perfil recién creado no debe llevar una
        // línea a ceros.
        String bonos = PasivosTexto.bonos(locale, pasivos.bonosDe(objetivo.getIdLong()));
        if (!bonos.isEmpty()) {
            desc += Messages.get(locale, "perfil.pasivos.linea", bonos);
        }
```

- [ ] **Step 3: Actualizar el wiring en `Main`**

```java
            comandos.add(new PerfilComando(economiaService, insigniaService, pasivoService));
```

- [ ] **Step 4: Verify**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 50` (sin tests nuevos: la línea la cubre
`PasivosTextoTest`, que ya prueba que sin bonos la cadena sale vacía)

- [ ] **Step 5: COMMIT 5**

```
feat(pasivos): /perfil ver enseña tus bonos activos

La ficha del personaje gana una línea con los pasivos ya sumados y ya topados, en la misma
línea editorial que el resto del embed. Si no hay ninguno la línea no aparece: un perfil
recién creado no debe llevar una fila a ceros.

Reutiliza PasivosTexto.bonos, el mismo formateo de /pasivos ver, así que los dos comandos
no se pueden desincronizar.

Novedades:
- /perfil ver ya te enseña de un vistazo qué bonos pasivos llevas activos.
```

---

### Task 12: Intro del canal `💰・economía`, docs y verify final

**Files:**
- Modify: `src/main/resources/messages_es.properties` (`intro.economia.desc`)
- Modify: `src/main/resources/messages_en.properties` (`intro.economia.desc`)
- Modify: `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java` (topic del canal)
- Modify: `docs/architecture.md`, `CHANGELOG.md`, `docs/decisions.md`

**Dónde va y por qué.** Se revisó `SetupServidorPlan`: **no existe ningún canal `🌳・mejoras`**
(«🌳 Mejoras» es una **sección dentro** de `intro.economia.desc`). La elección real era entre
`💰・economía` y `⚔️・combate`, y va a **`💰・economía`** porque (1) ese canal ya documenta todo lo
que se compra y se posee, y los pasivos son exactamente eso; (2) **siete de los nueve tipos** de bono
son de economía y ponerlo en combate daría justo la impresión contraria a la real; (3) el jugador lo
descubre donde ya está mirando qué comprarse.

**Sitio exacto:** la sección `✨ Pasivos` va **justo después de `🌳 Mejoras`** (su vecino conceptual:
los dos son mejoras permanentes de personaje) y **antes de `⛏️ Minería y herrería`**.

- [ ] **Step 1: Insertar la sección en `intro.economia.desc` (ES)**

En `src/main/resources/messages_es.properties`, en la clave `intro.economia.desc` (línea 628), buscar
la subcadena:

```
**🌳 Mejoras**\n> `/mejoras` (árbol) · `/mejorar`\n**⛏️ Minería y herrería**
```

y sustituirla por:

```
**🌳 Mejoras**\n> `/mejoras` (árbol) · `/mejorar`\n**✨ Pasivos**\n> `/pasivos ver` · `/pasivos equipar` · `/pasivos quitar`\n> Tu equipo y tus vehículos **ya no son solo decoración**: dan bonos permanentes. Equipa lo que tengas en tus **ranuras** y el bono se aplica solo, sin gastar el ítem.\n> **🔓 Ranuras:** empiezas con **1**. La 2.ª en el **nivel 10**, la 3.ª en el **25** y la 4.ª en el **50** — las mismas fechas que tus rangos, así que suben juntas.\n> **⚙️ Qué mejoran:** 💼 sueldo · ⏱️ cooldown de currar · ⭐ XP · ⚡ energía · ⛏️ minerales y aguante del pico · ⚔️ ataque, defensa y crítico.\n> **📈 Topes:** cada tipo tiene un techo (p. ej. +30 % de sueldo). Al llegar, `/pasivos ver` te lo marca y la quinta pieza de lo mismo ya no suma: mejor reparte.\n> **⚠️ Ojo:** el bono cuenta **solo si tienes el ítem**. Si lo vendes, lo regalas o lo pones en `/mercado`, la ranura se queda ahí pero **deja de contar** (y `/pasivos ver` te avisa).\n> Empieza barato: 🧢 `gorra` (200) o 🎒 `mochila` (400) ya suman. Termina caro: 🛫 `jet`, 🛥️ `yate`, 🚀 `cohete`. **Nadie lo tiene todo — elige tu build.**\n**⛏️ Minería y herrería**
```

> **Única desviación respecto al texto de la spec:** los `> >` de la spec (citas anidadas) se aplanan
> a un solo `> `. Discord no anida blockquotes: `> >` se renderiza igual que `> ` pero deja un `>`
> literal visible en algunos clientes. El resto del texto es **verbatim**.

- [ ] **Step 2: Insertar la sección en `intro.economia.desc` (EN)**

En `src/main/resources/messages_en.properties`, buscar:

```
**🌳 Upgrades**\n> `/mejoras` (tree) · `/mejorar`\n**⛏️ Mining & smithing**
```

y sustituirla por:

```
**🌳 Upgrades**\n> `/mejoras` (tree) · `/mejorar`\n**✨ Passives**\n> `/pasivos ver` · `/pasivos equipar` · `/pasivos quitar`\n> Your gear and vehicles are **not just for show** any more: they grant permanent bonuses. Slot what you own and the bonus applies on its own — the item is never consumed.\n> **🔓 Slots:** you start with **1**. The 2nd at **level 10**, the 3rd at **25**, the 4th at **50** — same milestones as your ranks, so they unlock together.\n> **⚙️ What they boost:** 💼 pay · ⏱️ work cooldown · ⭐ XP · ⚡ energy · ⛏️ minerals and pick durability · ⚔️ attack, defence and crits.\n> **📈 Caps:** every type has a ceiling (e.g. +30 % pay). Once you hit it, `/pasivos ver` says so and a fifth piece of the same kind adds nothing — spread them out.\n> **⚠️ Heads up:** a bonus only counts **while you own the item**. Sell it, gift it or list it on `/mercado` and the slot stays but **stops counting** (`/pasivos ver` flags it).\n> Start cheap: 🧢 `gorra` (200) or 🎒 `mochila` (400) already help. Finish rich: 🛫 `jet`, 🛥️ `yate`, 🚀 `cohete`. **Nobody gets everything — pick your build.**\n**⛏️ Mining & smithing**
```

- [ ] **Step 3: Actualizar el topic del canal**

En `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java` (línea ~285):

```java
                    texto("💰・economía", null, "intro.economia")
                            .conTopic("Tu vida en el servidor: /perfil, /daily, /pasivos y más. 🪙"),
```

Comprobar que el topic no pasa de 1024 caracteres (Discord) — no llega ni de lejos.

- [ ] **Step 4: Comprobar que `SetupServidorPlanTest` sigue verde**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=SetupServidorPlanTest" test
```
`Expected: PASS`. Si ese test pinea el topic literal del canal, actualizarlo con el nuevo texto.

- [ ] **Step 5: Documentación**

1. **`docs/architecture.md`**, sección «RPG económico (Fase 2)»:
   - En la viñeta de **catálogos en código**, añadir `Pasivos` a la lista.
   - Añadir una viñeta nueva tras la de «Descanso como estado»:

```markdown
- **Efectos pasivos con ranuras**: el equipo y los vehículos dan bonos permanentes (sueldo,
  cooldown de trabajo, XP, energía, minería y combate) al equiparlos en ranuras que se desbloquean
  a los niveles 0/10/25/50. El catálogo vive en `Pasivos` (satélite de `Items`, como `Camas`); la
  tabla `pasivos_equipados` guarda solo la **referencia**, y `PasivoService` **recalcula contra el
  inventario** en cada consulta: vender, regalar, publicar en el mercado o trocar el ítem apaga su
  bono sin necesidad de hooks en `VentaService`, `RegaloService`, `MercadoService`,
  `TruequeService` ni `RoboService`. La suma y el topado son una función pura; los topes son
  **globales por tipo y saturantes**. Los bonos de combate entran en el **snapshot** de
  `BatallaService.nuevaSesion` (valen toda la pelea, sin consultas por turno) y la energía extra es
  un **segundo `UPDATE`** en `EnergiaJob`, sin tocar el pase masivo.
```

   - Actualizar la línea de migraciones: **`V6–V25`**, añadiendo «pasivos equipados».

2. **`docs/decisions.md`**: nueva ADR con el número siguiente al último que haya en el archivo
   (comprobarlo; **no inventarlo**). Contenido:

```markdown
## ADR-0NN: catálogo satélite y ranuras para los efectos pasivos

**Contexto.** 30 ítems del catálogo (20 `EQUIPO` + 10 vehículos `BIEN`) se compraban y no hacían
nada, entre 200 y 3 000 000 de coins. La tienda mentía, el late-game no tenía destino y la
progresión se aplanaba.

**Decisión.**
1. Los bonos viven en un **catálogo paralelo** `services/Pasivos`, emparejado por `itemId`, y
   `Items` no se toca. Precedente triple: `Picos`, `Camas`, `Cofres`. Meterlos en `Items` obligaría
   a ampliar un record de 8 componentes con campos nulos en 79 de 109 filas, y sus precios son
   carga estructural probada por `RarezaTest` y `Camas`.
2. **Ranuras** (1/2/3/4 a niveles 0/10/25/50) en vez de «basta con tenerlo»: sin ranuras el sistema
   se resolvería el día que terminas de comprar y no habría ninguna decisión que tomar nunca más.
3. La fila de `pasivos_equipados` es una **referencia, no un derecho**: el bono se recalcula contra
   el inventario en cada consulta. Eso cierra a la vez los exploits de vender, regalar, publicar en
   el mercado y trocar, y evita hooks de limpieza en cinco services.
4. **Topes globales por tipo, saturantes.** Se topa la suma, nunca cada bono. Sin topes, cuatro
   ranuras de sueldo romperían la economía lenta (ADR-010).
5. Las **10 camas quedan fuera**: ya tienen efecto vía `Camas` y darles además un pasivo sería
   doble recompensa por la misma compra.

**Consecuencias.** Un comando más que aprender (`/pasivos`), compensado con `/pasivos ver` y la
intro del canal. El combate queda por debajo del +6 % del poder en el peor caso realista, así que
no hay que rebalancear el bestiario. Queda pendiente en el backlog el coste de mantenimiento de los
vehículos (sumidero con su propio job y su propio diseño).
```

3. **`CHANGELOG.md`**, bajo `## [Sin publicar]`, en una sección `### Añadido`:

```markdown
### Añadido
- **Efectos pasivos de equipo y bienes** (`/pasivos ver` · `equipar` · `quitar`): los 20 ítems de
  equipo y los 10 vehículos dejan de ser decoración y dan bonos permanentes de sueldo, cooldown de
  trabajo, XP, energía, minería (cantidad y durabilidad) y combate (ataque, defensa y crítico). Se
  equipan en **ranuras** que se desbloquean a los niveles 0, 10, 25 y 50 —los mismos umbrales que
  los rangos— y el ítem **nunca se consume**. Cada tipo tiene un **tope global** que satura, y el
  bono cuenta **solo mientras tengas el ítem**: si lo vendes, lo regalas o lo publicas en el
  mercado, la ranura se queda pero deja de contar (`/pasivos ver` lo marca con ⚠️). `/perfil ver`
  enseña el resumen. Migración `V25__pasivos_equipados`. Las camas no cambian.
```

4. **`README.md` / `README.en.md`**: ya se actualizaron en la Task 5. Verificar que `/pasivos`
   aparece y que el conteo de comandos que se cite en el README (si lo hay) dice **60**.

- [ ] **Step 6: Verify final**

```
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify
```
`Expected: BUILD SUCCESS · Tests run: baseline + 50, Failures: 0, Errors: 0`
(3 saltados en local: `MigracionesTest` y los dos de `PasivoRepositorioTest`.)

**Pegar la salida real del build en la respuesta.** La definición de terminado del repo exige
evidencia; «debería funcionar» no vale.

- [ ] **Step 7: COMMIT 6**

```
docs(pasivos): intro del canal de economía, ADR y documentación

Cierra el módulo de efectos pasivos con la documentación que lo hace descubrible.

- intro.economia.desc (ES y EN) gana la sección ✨ Pasivos, justo después de 🌳 Mejoras, que
  es su vecino conceptual: los dos son mejoras permanentes de personaje. Va en 💰・economía
  y no en ⚔️・combate porque siete de los nueve tipos de bono son de economía y los dos de
  combate son marginales por diseño; ponerlo en combate daría justo la impresión contraria.
  La guía larga no hace falta: /pasivos ver y el autocompletado explican el sistema desde
  dentro, como ya hacen /mejoras, /cofres y /recetas.
- El topic del canal menciona /pasivos.
- docs/architecture.md: los pasivos entran en la sección del RPG económico y Pasivos en la
  lista de catálogos en código; migraciones V6-V25.
- docs/decisions.md: ADR del catálogo satélite, las ranuras, la referencia-no-derecho y los
  topes saturantes.
- CHANGELOG.md: entrada de la feature.

Novedades:
- El canal de economía ya explica los pasivos: cómo funcionan las ranuras, qué mejoran,
  qué son los topes y por qué el bono deja de contar si te deshaces del ítem.
- Recordatorio: nadie lo tiene todo. Con 4 ranuras como mucho, maximizar el sueldo implica
  renunciar a la minería o al combate. Elige tu build.
```

---

## Self-review

### Cobertura de la spec → tarea

| Requisito de la spec | Tarea |
|---|---|
| Migración `V25__pasivos_equipados` (PK, UNIQUE, CASCADE) | 1 |
| `MigracionesTest` actualizado | 1 |
| `Pasivos`: 9 tipos, `Bono`, `Pasivo`, `CATALOGO` (30), `TOPES`, `porId` | 2 |
| Convenio de unidades (fracción vs. plano; cooldown resta) | 2 (`esPorcentual`) |
| Regla «ningún ítem satura solo» | 2 (test 5) |
| Tabla de balance (mejores 4 por tipo) | 2 (test 7b) |
| Los 7 barridos de integridad del catálogo | 2 (tests 1–6 + 7a/7b; ver desviación 4) |
| `PasivoRepositorio` (`equipados`, `equipar`, `quitar`, `ranuraDe`) | 3 |
| Test Testcontainers: ON DUPLICATE, UNIQUE, CASCADE (RGPD) | 3 |
| `ranurasDe`, `sumarYTopar`, `bonosDe`, `bonoDe`, `ranuras`, `equipar`, `quitar` | 4 |
| Filtrado contra inventario (anti-exploit de 4 vías) | 4 (test 9) |
| Saturación / se topa la suma / 0.0 en vez de ausencia | 4 (tests 10–12) |
| Los 8 estados de error | 4 (tests 13a–13g) + 5 (mensajes) |
| `YA_EQUIPADO` también por choque del `UNIQUE` | 3 (excepción) + 4 (catch) |
| `/pasivos ver` (4 ranuras, bonos topados, pie, `[usuario]`) | 5 |
| `/pasivos equipar` (autocompletado, sin ranura, SIN_HUECO, reemplazo) | 5 |
| `/pasivos quitar` | 5 |
| Respuestas públicas; errores efímeros | 5 |
| i18n ES+EN: comandos, tipos, errores, 30 `desc` | 5 |
| `TrabajoService`: `conBonoPasivos` y orden de la tubería | 6 |
| `TrabajoService`: `cooldownEfectivo` (suelo 45 min) | 6 |
| `XpService`: bono en `ganarXp` + suelo de +1 + constructor compatible | 7 |
| `MineriaService`: cantidad, **subida de `CANTIDAD_MAX`**, durabilidad | 8 |
| `/minar` avisa del ahorro de durabilidad | 8 |
| Combate en el snapshot; pasivos antes que encantamientos | 9 |
| `nuevaSesion` privado → función pura `conPasivos` + inyección | 9 |
| `EnergiaJob` segundo pase (JOIN inventario, NOT EXISTS dormidos, tope 5) | 10 |
| Línea `✨ Pasivos` en `/perfil ver` (oculta si no hay) | 11 |
| Intro de `💰・economía` ES+EN verbatim + topic | 12 |
| Docs: architecture, ADR, CHANGELOG, READMEs, READMEs de paquete | 4, 5, 12 |
| Recuento de comandos (55 → verificar) | Notas + 5 (**59 → 60** verificado) |

**Sin cobertura, y por qué:**
- **«No se rebalancean monstruos, jefes ni mazmorras»** es un no-objetivo: no genera tarea. Se deja
  el cálculo del margen (+5,8 % / +8,5 %) escrito en la Task 9 para que nadie lo reabra sin datos.
- **Coste de mantenimiento de vehículos, sets y sinergias**: no-objetivos explícitos de la spec, van
  al backlog.
- **Test 7 de la spec («monotonía con el precio»)**: sustituido por 7a + 7b. Justificado en las notas
  (desviación 4): la regla literal se rompe con el propio catálogo aprobado y los tipos tienen
  unidades incomparables; 7b es estrictamente más fuerte (pinea la tabla de balance entera).

### Barrido de placeholders

Ningún paso dice «similar a la Task N», «TBD», «añadir manejo de errores» ni «etc.». Todo test y toda
implementación aparecen con su código completo. Los tres únicos puntos donde el ejecutor debe
**verificar antes de escribir** están marcados como tal y con el comando exacto para hacerlo:
el nombre del método de borrado de `UsuarioDiscordRepositorio` (Task 3), la firma del record
`MineriaEstado` (Task 8) y el id de monstruo que ya usan los tests de `iniciar` (Task 9). No son
huecos del plan: son datos que dependen de código que el plan no reproduce entero.

### Consistencia de tipos entre tareas

| Símbolo | Firma | Se define en | Se usa en |
|---|---|---|---|
| `Pasivos.Tipo` | enum de 9 | 2 | 4, 5, 6, 7, 8, 9, 10 |
| `Pasivos.TOPES` | `Map<Tipo, Double>` | 2 | 4, 6, 7, 9, 10 |
| `Pasivos.fuentesDe(Tipo)` | `Map<String, Double>` | 2 | 2 (test), 10 |
| `Pasivos.esPorcentual(Tipo)` | `boolean` | 2 | 2 (test), 5 |
| `PasivoRepositorio.equipados(long)` | `Map<Integer, String>` | 3 | 4 |
| `ItemYaEquipadoException` | `RuntimeException` | 3 | 4 |
| `PasivoService.ranurasDe(int)` | `static int` | 4 | 5 (`PasivosTexto.pie`) |
| `PasivoService.nivelDeRanura(int)` | `static int` | 4 | 5 |
| `PasivoService.RANURAS_MAX` | `static final int` = 4 | 4 | 5 |
| `PasivoService.sumarYTopar(List<Pasivo>)` | `static Map<Tipo, Double>` | 4 | 4, 9 (tests) |
| `PasivoService.bonosDe(long)` | `Map<Tipo, Double>` | 4 | 5, 9, 11 |
| `PasivoService.bonoDe(long, Tipo)` | `double` | 4 | 6, 7, 8 |
| `PasivoService.ranuras(long)` | `List<EstadoRanura>` | 4 | 5 |
| `PasivoService.equipablesDe(long)` | `List<String>` | 4 | 5 (autocompletado) |
| `ResultadoEquipar` | `(Estado, int, String, String, int, Map)` | 4 | 5 |
| `ResultadoQuitar` | `(Estado, String, Map)` | 4 | 5 |
| `PasivosTexto.bonos(Locale, Map)` | `static String` | 5 | 5, 11 |
| `TrabajoService.conBonoPasivos(int, double)` | `static int` | 6 | 6 |
| `TrabajoService.cooldownEfectivo(double)` | `static Duration` | 6 | 6 |
| `XpService.conBonoPasivos(int, double)` | `static int` | 7 | 7 |
| `MineriaService.Resultado` | +`boolean durabilidadAhorrada` | 8 | 8 (`/minar`) |
| `BatallaService.conPasivos(int, int, double, Map)` | `static BonosCombate` | 9 | 9 |
| `PersonajeRepositorio.regenerarEnergiaPasivos(Map<String,Double>, int)` | `int` | 10 | 10 |

Nota deliberada: hay **dos** métodos llamados `conBonoPasivos` (en `TrabajoService` y en
`XpService`). Son clases distintas, topes distintos y semánticas distintas (uno satura, el otro tiene
suelo de +1). Se mantiene el nombre porque es el que usan sus vecinos (`conBonoEstudios`,
`conPenalizacionFatiga`) y ambos son estáticos y siempre se llaman cualificados.

---

## Al terminar (avisar al usuario)

Cuando los seis commits estén hechos y `clean verify` en verde:

| Qué | ¿Hace falta? | Por qué |
|---|---|---|
| **Reiniciar el bot** | **Sí** | Registra el comando nuevo `/pasivos` en JDA y aplica la migración **V25** al arrancar (Flyway) |
| **`/setup`** | **Sí** | Reescribe la intro de `💰・economía` con la sección `✨ Pasivos` y actualiza el topic del canal |
| **`/setup desde_cero`** | **No** | No hay canales, categorías ni roles nuevos: solo cambia el texto de una intro existente. `desde_cero` borraría el servidor entero para nada |

**Estado: pendiente de smoke test manual.** Nada de esto está probado contra Discord en vivo. Hasta
que se ejecuten estos siete pasos en el **servidor de pruebas** (bot y token de test, nunca
producción), el módulo se marca explícitamente como *pendiente de smoke test manual*:

1. `/pasivos ver` sin nada equipado → 1 ranura libre y 3 con 🔒 y sus niveles.
2. `/comprar gorra` → `/pasivos equipar gorra` → `/pasivos ver` muestra `+2 % XP`.
3. `/inventario vender gorra` → `/pasivos ver` marca la ranura con ⚠️ y **el bono desaparece**.
4. `/pasivos equipar fruta` → error `SIN_PASIVO` (efímero).
5. Equipar el mismo ítem en dos ranuras → error `YA_EQUIPADO`.
6. Con un pasivo de `SUELDO` equipado: `/trabajo currar` y comprobar que el pago sube y que el
   cooldown baja de 60 min.
7. `/perfil ver` muestra la línea `✨ Pasivos`.

Además: los tests de **Testcontainers** (`MigracionesTest`, `PasivoRepositorioTest`) **se saltan en
local**. No dar por validada la migración V25 ni el segundo pase de energía hasta ver el workflow de
**CI en verde**.
