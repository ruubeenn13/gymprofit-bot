# Registro de cambios de `/setup` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que `/setup` y `/setup desde_cero` produzcan un informe con los cambios reales de esa ejecución (nuevos/actualizados/eliminados, con nombres), en la respuesta y como entrada persistente en `bot-logs`.

**Architecture:** Un colector puro `RegistroCambios` se pasa por los helpers de `SetupComando`/`SetupServidorPlan`; cada helper compara lo deseado con lo actual y registra el cambio. Un render puro `InformeSetup` convierte el registro en líneas localizadas, que se trocean (util compartido `Embeds`) y se envían a la respuesta y a `bot-logs`.

**Tech Stack:** Java 21, JDA 5, JUnit 5 + Mockito, i18n `messages_es/en.properties`.

**Precondición:** asume presente el fix de `/trabajo lista` que añadió `partirEnBloques` a `TrabajoComando` (Task 1 lo extrae a un util). Build de referencia: **442 tests, 0 fallos, 8 skipped**.

---

### Task 1: Extraer el troceador a `util/Embeds` y reusarlo en `/trabajo`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/util/Embeds.java`
- Create: `src/test/java/com/gymprofit/bot/util/EmbedsTest.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/TrabajoComando.java`
- Delete: `src/test/java/com/gymprofit/bot/commands/economia/TrabajoComandoTest.java` (sus tests se mueven a `EmbedsTest`)

- [ ] **Step 1: Crear `Embeds` con el troceador**

```java
package com.gymprofit.bot.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades de composición de embeds de Discord. Hoy solo el troceado de una lista larga de
 * líneas en bloques que respetan el tope de 4096 caracteres de la descripción de un embed.
 */
public final class Embeds {

    /** Tope de caracteres de la descripción de un embed de Discord. */
    public static final int MAX_DESC = 4096;

    private Embeds() {}

    /**
     * Reparte líneas en bloques cuyo texto unido con saltos de línea no supera {@code limite}
     * caracteres. Rompe solo entre líneas: nunca parte una línea por la mitad.
     */
    public static List<String> partirEnBloques(List<String> lineas, int limite) {
        List<String> bloques = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String linea : lineas) {
            if (actual.length() > 0 && actual.length() + 1 + linea.length() > limite) {
                bloques.add(actual.toString());
                actual.setLength(0);
            }
            if (actual.length() > 0) {
                actual.append('\n');
            }
            actual.append(linea);
        }
        if (actual.length() > 0) {
            bloques.add(actual.toString());
        }
        return bloques;
    }
}
```

- [ ] **Step 2: Mover los tests a `EmbedsTest`**

```java
package com.gymprofit.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedsTest {

    @Test
    @DisplayName("partirEnBloques: ningún bloque supera el límite")
    void respetaLimite() {
        List<String> lineas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lineas.add("🔒 `puesto" + i + "` · **Puesto de ejemplo " + i + "** (sector) · 10-20 🪙 · req. nivel 5 — se llega por ascenso");
        }
        List<String> bloques = Embeds.partirEnBloques(lineas, Embeds.MAX_DESC);
        for (String b : bloques) {
            assertTrue(b.length() <= Embeds.MAX_DESC, "bloque de " + b.length() + " chars");
        }
        assertTrue(bloques.size() > 1, "200 líneas largas no caben en un solo embed");
    }

    @Test
    @DisplayName("partirEnBloques: no pierde ni parte líneas, mantiene el orden")
    void noParteLineas() {
        List<String> lineas = List.of("aaa", "bbb", "ccc", "ddd");
        List<String> bloques = Embeds.partirEnBloques(lineas, 7);
        List<String> reunidas = new ArrayList<>();
        for (String b : bloques) {
            reunidas.addAll(List.of(b.split("\n", -1)));
        }
        assertEquals(lineas, reunidas);
    }

    @Test
    @DisplayName("partirEnBloques: un solo bloque si todo cabe")
    void unBloqueSiCabe() {
        assertEquals(1, Embeds.partirEnBloques(List.of("hola", "mundo"), Embeds.MAX_DESC).size());
    }
}
```

- [ ] **Step 3: Refactor `TrabajoComando`** — borrar el método local `partirEnBloques` y la constante `MAX_DESC_EMBED`; en `lista(...)` llamar a `Embeds.partirEnBloques(lineas, Embeds.MAX_DESC)`; añadir `import com.gymprofit.bot.util.Embeds;`. Borrar el archivo `TrabajoComandoTest.java`.

- [ ] **Step 4: Verify**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS, mismos 442 tests (los 3 del troceador ahora en `EmbedsTest`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/util/Embeds.java src/test/java/com/gymprofit/bot/util/EmbedsTest.java src/main/java/com/gymprofit/bot/commands/economia/TrabajoComando.java
git rm src/test/java/com/gymprofit/bot/commands/economia/TrabajoComandoTest.java
git commit -m "refactor(util): troceador de embeds compartido en util/Embeds"
```

---

### Task 2: `RegistroCambios` (colector puro)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/admin/RegistroCambios.java`
- Create: `src/test/java/com/gymprofit/bot/commands/admin/RegistroCambiosTest.java`

- [ ] **Step 1: Escribir el test primero**

```java
package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.commands.admin.RegistroCambios.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroCambiosTest {

    @Test
    @DisplayName("registro vacío: no hubo cambios y contadores a cero")
    void vacio() {
        RegistroCambios r = new RegistroCambios();
        assertFalse(r.huboCambios());
        assertEquals(0, r.cuenta(Tipo.CREADO));
        assertTrue(r.entradas().isEmpty());
    }

    @Test
    @DisplayName("cuenta por tipo y orden de inserción preservado")
    void cuentaYOrden() {
        RegistroCambios r = new RegistroCambios();
        r.creado(Categoria.ROL, "Coach");
        r.actualizado(Categoria.CANAL, "general");
        r.creado(Categoria.CANAL, "bot-logs");
        r.eliminado(Categoria.CANAL, "viejo");

        assertTrue(r.huboCambios());
        assertEquals(2, r.cuenta(Tipo.CREADO));
        assertEquals(1, r.cuenta(Tipo.ACTUALIZADO));
        assertEquals(1, r.cuenta(Tipo.ELIMINADO));
        assertEquals(4, r.entradas().size());
        assertEquals("Coach", r.entradas().get(0).nombre());
        assertEquals(Tipo.ELIMINADO, r.entradas().get(3).tipo());
    }
}
```

- [ ] **Step 2: Run test → falla** (`RegistroCambios` no existe).

Run: `.\mvnw.cmd -q "-Dtest=RegistroCambiosTest" test` — Expected: FAIL (compilación).

- [ ] **Step 3: Implementar `RegistroCambios`**

```java
package com.gymprofit.bot.commands.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Colector de los cambios que aplica una ejecución de {@code /setup}: qué se creó, actualizó o
 * eliminó, por categoría y con nombre. Puro (sin JDA ni i18n): la instrumentación de setup lo
 * rellena y {@link InformeSetup} lo renderiza. El orden de registro se conserva para que el
 * informe salga en el mismo orden en que setup montó las cosas.
 */
public final class RegistroCambios {

    /** Familia del elemento tocado (para agrupar el informe). */
    public enum Categoria { ROL, CATEGORIA, CANAL, INTRO, DESCRIPCION_SERVIDOR,
                            WELCOME, AFK, AUTOMOD, ANCLA, PANEL }

    /** Naturaleza del cambio. */
    public enum Tipo { CREADO, ACTUALIZADO, ELIMINADO }

    /** Un cambio concreto. */
    public record Entrada(Tipo tipo, Categoria categoria, String nombre) {}

    private final List<Entrada> entradas = new ArrayList<>();

    public void creado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.CREADO, categoria, nombre));
    }

    public void actualizado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.ACTUALIZADO, categoria, nombre));
    }

    public void eliminado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.ELIMINADO, categoria, nombre));
    }

    public boolean huboCambios() {
        return !entradas.isEmpty();
    }

    public int cuenta(Tipo tipo) {
        return (int) entradas.stream().filter(e -> e.tipo() == tipo).count();
    }

    /** Copia inmutable de los cambios en orden de registro. */
    public List<Entrada> entradas() {
        return List.copyOf(entradas);
    }
}
```

- [ ] **Step 4: Run test → pasa.**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/admin/RegistroCambios.java src/test/java/com/gymprofit/bot/commands/admin/RegistroCambiosTest.java
git commit -m "feat(setup): colector RegistroCambios para el informe de /setup"
```

---

### Task 3: `InformeSetup` (render puro) + claves i18n

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/admin/InformeSetup.java`
- Create: `src/test/java/com/gymprofit/bot/commands/admin/InformeSetupTest.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: Añadir claves i18n (ES)** — junto al bloque `setup.*`:

```properties
setup.registro.titulo=🧾 Registro de /setup
setup.registro.cabecera=**Servidor:** {0}\n**Por:** {1}\n**Modo:** {2}\n**Fecha:** {3}
setup.registro.modo.normal=normal
setup.registro.modo.desde_cero=desde cero
setup.registro.contadores=🆕 {0} · ✏️ {1} · 🗑️ {2}
setup.registro.sincambios=✅ Sin cambios: el servidor ya estaba al día.
setup.registro.nuevos=🆕 Nuevos
setup.registro.actualizados=✏️ Actualizados
setup.registro.eliminados=🗑️ Eliminados
setup.registro.categoria.rol=Roles
setup.registro.categoria.categoria=Categorías
setup.registro.categoria.canal=Canales
setup.registro.categoria.intro=Intros de canal
setup.registro.categoria.descripcion_servidor=Descripción del servidor
setup.registro.categoria.welcome=Pantalla de bienvenida
setup.registro.categoria.afk=Canal AFK
setup.registro.categoria.automod=Reglas AutoMod
setup.registro.categoria.ancla=Anclas de Comunidad
setup.registro.categoria.panel=Paneles
setup.descripcion_servidor=Comunidad fitness gamificada de GymProFit 💪 XP, retos, economía y duelos con GymProBot. Soporte: gymprofit.soporte@gmail.com
```

- [ ] **Step 2: Añadir las MISMAS claves en EN** (`messages_en.properties`), traducidas natural:

```properties
setup.registro.titulo=🧾 /setup log
setup.registro.cabecera=**Server:** {0}\n**By:** {1}\n**Mode:** {2}\n**Date:** {3}
setup.registro.modo.normal=normal
setup.registro.modo.desde_cero=from scratch
setup.registro.contadores=🆕 {0} · ✏️ {1} · 🗑️ {2}
setup.registro.sincambios=✅ No changes: the server was already up to date.
setup.registro.nuevos=🆕 New
setup.registro.actualizados=✏️ Updated
setup.registro.eliminados=🗑️ Removed
setup.registro.categoria.rol=Roles
setup.registro.categoria.categoria=Categories
setup.registro.categoria.canal=Channels
setup.registro.categoria.intro=Channel intros
setup.registro.categoria.descripcion_servidor=Server description
setup.registro.categoria.welcome=Welcome screen
setup.registro.categoria.afk=AFK channel
setup.registro.categoria.automod=AutoMod rules
setup.registro.categoria.ancla=Community anchors
setup.registro.categoria.panel=Panels
setup.descripcion_servidor=GymProFit's gamified fitness community 💪 XP, challenges, economy and duels with GymProBot. Support: gymprofit.soporte@gmail.com
```

- [ ] **Step 3: Escribir el test de render primero**

```java
package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.i18n.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InformeSetupTest {

    private InformeSetup.Contexto ctx() {
        return new InformeSetup.Contexto("GymProFit", "@ruben", false, 1_700_000_000L);
    }

    @Test
    @DisplayName("sin cambios: incluye cabecera, contadores a cero y la línea 'sin cambios'")
    void sinCambios() {
        List<String> lineas = InformeSetup.lineas(new RegistroCambios(), ctx(), Messages.ES);
        String texto = String.join("\n", lineas);
        assertTrue(texto.contains("GymProFit"), "cabecera con el servidor");
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.sincambios")));
    }

    @Test
    @DisplayName("con cambios: agrupa por tipo y por categoría con los nombres")
    void conCambios() {
        RegistroCambios r = new RegistroCambios();
        r.creado(Categoria.ROL, "Coach");
        r.creado(Categoria.CANAL, "bot-logs");
        r.actualizado(Categoria.INTRO, "general");
        List<String> lineas = InformeSetup.lineas(r, ctx(), Messages.ES);
        String texto = String.join("\n", lineas);
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.nuevos")), "bloque de nuevos");
        assertTrue(texto.contains("Coach") && texto.contains("bot-logs"), "nombres de los nuevos");
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.actualizados")), "bloque de actualizados");
        assertTrue(texto.contains("general"), "nombre del intro actualizado");
    }
}
```

- [ ] **Step 4: Run test → falla** (`InformeSetup` no existe).

- [ ] **Step 5: Implementar `InformeSetup`**

```java
package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.commands.admin.RegistroCambios.Entrada;
import com.gymprofit.bot.commands.admin.RegistroCambios.Tipo;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renderiza un {@link RegistroCambios} a las líneas de texto del informe de {@code /setup}, ya
 * localizadas. Puro y testeable: no envía nada; el troceado y el envío los hace {@code SetupComando}.
 * Agrupa por tipo de cambio (nuevos → actualizados → eliminados) y, dentro, por categoría.
 */
public final class InformeSetup {

    /** Datos de cabecera del informe. */
    public record Contexto(String servidor, String porMencion, boolean desdeCero, long epochSegundos) {}

    private InformeSetup() {}

    public static List<String> lineas(RegistroCambios registro, Contexto ctx, Locale locale) {
        List<String> l = new ArrayList<>();
        l.add(Messages.get(locale, "setup.registro.cabecera",
                ctx.servidor(), ctx.porMencion(),
                Messages.get(locale, ctx.desdeCero()
                        ? "setup.registro.modo.desde_cero" : "setup.registro.modo.normal"),
                EmbedFactory.fechaLarga(ctx.epochSegundos())));
        l.add(Messages.get(locale, "setup.registro.contadores",
                registro.cuenta(Tipo.CREADO), registro.cuenta(Tipo.ACTUALIZADO),
                registro.cuenta(Tipo.ELIMINADO)));

        if (!registro.huboCambios()) {
            l.add(Messages.get(locale, "setup.registro.sincambios"));
            return l;
        }

        for (Tipo tipo : Tipo.values()) {
            List<Entrada> deTipo = registro.entradas().stream().filter(e -> e.tipo() == tipo).toList();
            if (deTipo.isEmpty()) {
                continue;
            }
            l.add("");
            l.add("**" + Messages.get(locale, claveTitulo(tipo)) + "**");
            // Agrupa por categoría preservando el orden de aparición.
            Map<Categoria, List<String>> porCategoria = new LinkedHashMap<>();
            for (Entrada e : deTipo) {
                porCategoria.computeIfAbsent(e.categoria(), k -> new ArrayList<>()).add(e.nombre());
            }
            for (Map.Entry<Categoria, List<String>> ent : porCategoria.entrySet()) {
                l.add("__" + Messages.get(locale, claveCategoria(ent.getKey())) + "__: "
                        + String.join(", ", ent.getValue()));
            }
        }
        return l;
    }

    private static String claveTitulo(Tipo tipo) {
        return switch (tipo) {
            case CREADO -> "setup.registro.nuevos";
            case ACTUALIZADO -> "setup.registro.actualizados";
            case ELIMINADO -> "setup.registro.eliminados";
        };
    }

    private static String claveCategoria(Categoria categoria) {
        return "setup.registro.categoria." + categoria.name().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 6: Run test → pasa. Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/admin/InformeSetup.java src/test/java/com/gymprofit/bot/commands/admin/InformeSetupTest.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(setup): render InformeSetup y claves i18n del registro"
```

---

### Task 4: Instrumentar estructura (roles, categorías, canales, borrado)

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`
- Modify: `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java` (si los helpers de canal viven ahí)

Objetivo: los helpers reciben un `RegistroCambios reg` y registran creaciones/actualizaciones/borrados de estructura. Leer primero las firmas reales:

```
grep -n "crearRoles\|crearCategoriasYCanales\|crearCanal\|vaciarServidor\|reusarOCrear" src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java
```

- [ ] **Step 1: Pasar `RegistroCambios reg` a las firmas** de `crearRoles`, `crearCategoriasYCanales`, `crearCanal`, `vaciarServidor` (y helpers internos que creen/reutilicen). Crear el `reg` en `ejecutar` (Task 7 lo consume).

- [ ] **Step 2: `crearRoles`** — donde hoy crea un rol nuevo, añadir `reg.creado(Categoria.ROL, plan.nombre())`. Donde reutiliza uno existente, comparar el color:

```java
// rol existente: solo cuenta como cambio si el color difiere del plan
if (existente.getColorRaw() != (plan.colorRgb() & 0xFFFFFF)) {
    existente.getManager().setColor(plan.colorRgb()).queue();
    reg.actualizado(RegistroCambios.Categoria.ROL, plan.nombre());
}
```

(Ajustar al patrón real de reutilización; los roles separadores con `color == null` no comparan color.)

- [ ] **Step 3: `crearCanal`** — al crear un canal nuevo: `reg.creado(Categoria.CANAL, chPlan.nombre())`. Al reutilizar uno existente y detectar que cambia topic o slowmode respecto al plan, `reg.actualizado(Categoria.CANAL, chPlan.nombre())`. Igual para categorías nuevas: `reg.creado(Categoria.CATEGORIA, nombre)`.

- [ ] **Step 4: `vaciarServidor` (desde_cero)** — por cada canal/categoría borrado de verdad, `reg.eliminado(Categoria.CANAL, nombre)` (o `CATEGORIA`). Sustituye/duplica el contador `limpiados` actual por entradas con nombre; el contador de eliminados del informe saldrá de aquí.

- [ ] **Step 5: Verify** — `.\mvnw.cmd clean verify` en verde (el número de tests no cambia; esto es wiring). Compila y pasa.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java
git commit -m "feat(setup): registrar cambios de roles, canales y borrados"
```

---

### Task 5: Instrumentar contenido con diff (intros, welcome, AFK, automod, anclas, paneles)

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`

- [ ] **Step 1: Intros** — pasar `reg` a `fijarIntro` y `actualizarIntro`.
  - `fijarIntro` (canal nuevo, siempre publica): `reg.creado(Categoria.INTRO, canal.getName())`.
  - `actualizarIntro` (canal reutilizado): **comparar** la descripción del embed fijado por el bot con la nueva antes de editar; registrar solo si difiere:

```java
String descNueva = Messages.get(locale, chPlan.introKey() + ".desc");
var intro = pins.stream()... // el mensaje fijado del bot (patrón actual)
if (intro == null) {
    canal.sendMessageEmbeds(embed).queue(m -> m.pin().queue(null, e -> { }), e -> { });
    reg.creado(RegistroCambios.Categoria.INTRO, canal.getName());
} else {
    String descVieja = intro.getEmbeds().isEmpty() ? null : intro.getEmbeds().get(0).getDescription();
    if (!descNueva.equals(descVieja)) {
        intro.editMessageEmbeds(embed).queue(null, e -> { });
        reg.actualizado(RegistroCambios.Categoria.INTRO, canal.getName());
    }
    // idéntica: no editar (ahorra llamada) y no registrar
}
```

- [ ] **Step 2: Welcome screen** — en `configurarBienvenida`, leer `guild.retrieveWelcomeScreen()` (o el estado disponible) y comparar la descripción y la lista de canales con lo que se va a poner; registrar `reg.creado`/`reg.actualizado(Categoria.WELCOME, guild.getName())` solo si difiere. Si la lectura previa es cara/incierta, comparar al menos la descripción.

- [ ] **Step 3: AFK** — en `configurarAfk`, comparar `guild.getAfkChannel()` y `guild.getAfkTimeout()` con lo deseado; si difiere, aplicar y `reg.actualizado(Categoria.AFK, nombreCanalAfk)`.

- [ ] **Step 4: AutoMod** — en `crearReglasAutoMod`, por cada regla creada nueva (no preexistente), `reg.creado(Categoria.AUTOMOD, nombreRegla)`.

- [ ] **Step 5: Anclas y paneles** — anclas de Comunidad: `reg.creado`/`reg.actualizado(Categoria.ANCLA, nombre)`. Paneles (roles/ticket): si se publica uno nuevo (no había mensaje del bot fijado), `reg.creado(Categoria.PANEL, nombrePanel)`; si ya existe, no registrar.

- [ ] **Step 6: Verify + Commit**

```bash
.\mvnw.cmd clean verify
git add src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java
git commit -m "feat(setup): diff de contenido en intros, welcome, afk, automod y paneles"
```

---

### Task 6: Gestión nueva de la descripción del servidor

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`

- [ ] **Step 1: Nuevo helper `configurarDescripcionServidor`** — se llama desde el hilo de `ejecutar` junto a `configurarBienvenida`/`configurarAfk`. Solo aplica si el servidor tiene Comunidad (la API de descripción requiere `COMMUNITY`); si no, no hace nada.

```java
/**
 * Fija la descripción del servidor (visible en la tarjeta de invitación / descubrimiento) desde
 * i18n. Solo con Comunidad activada. Registra el cambio solo si el texto difiere del actual.
 */
private void configurarDescripcionServidor(Guild guild, RegistroCambios reg) {
    if (!guild.getFeatures().contains("COMMUNITY")) {
        return;
    }
    String nueva = Messages.get(Messages.ES, "setup.descripcion_servidor");
    if (nueva.equals(guild.getDescription())) {
        return;  // ya está puesta: no es un cambio
    }
    boolean existia = guild.getDescription() != null && !guild.getDescription().isBlank();
    guild.getManager().setDescription(nueva).queue(
            ok -> { },
            e -> log.warn("No se pudo fijar la descripción del servidor: {}", e.toString()));
    if (existia) {
        reg.actualizado(RegistroCambios.Categoria.DESCRIPCION_SERVIDOR, guild.getName());
    } else {
        reg.creado(RegistroCambios.Categoria.DESCRIPCION_SERVIDOR, guild.getName());
    }
}
```

- [ ] **Step 2: Llamarlo** en el hilo de `ejecutar`, tras `configurarAfk(guild)`: `configurarDescripcionServidor(guild, reg);`.

- [ ] **Step 3: Verify + Commit**

```bash
.\mvnw.cmd clean verify
git add src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java
git commit -m "feat(setup): fija y registra la descripción del servidor"
```

---

### Task 7: Entrega del informe (respuesta + bot-logs)

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`

- [ ] **Step 1: Construir el informe** al final del hilo de `ejecutar`, sustituyendo el bloque actual de `setup.resumen`:

```java
InformeSetup.Contexto ctx = new InformeSetup.Contexto(
        guild.getName(), evento.getUser().getAsMention(), desdeCero, Instant.now().getEpochSecond());
List<String> lineas = InformeSetup.lineas(reg, ctx, locale);
List<String> bloques = Embeds.partirEnBloques(lineas, Embeds.MAX_DESC);
```

(Imports: `com.gymprofit.bot.util.Embeds`, `com.gymprofit.bot.commands.admin.InformeSetup`, `java.time.Instant`, `java.util.List`.)

- [ ] **Step 2: Enviar a la respuesta** — un embed por bloque; el título `setup.registro.titulo` solo en el primero:

```java
String titulo = Messages.get(locale, "setup.registro.titulo");
for (int i = 0; i < bloques.size(); i++) {
    var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
            i == 0 ? titulo : null, bloques.get(i)).build();
    evento.getHook().sendMessageEmbeds(embed).queue(null,
            err -> log.info("Setup terminado, pero no se pudo enviar el informe: {}", err.toString()));
}
```

- [ ] **Step 3: Publicar en `bot-logs`** — localizar el canal por su tipo/nombre y publicar los mismos bloques (persistente). Comprobar cómo se resuelve el canal `BOT_LOGS` en el código (moderación ya escribe ahí):

```
grep -rn "BOT_LOGS\|bot-logs\|canalLogs\|logs" src/main/java/com/gymprofit/bot
```

Usar ese resolutor; si devuelve vacío, `log.warn` y no publicar (no rompe el setup):

```java
TextChannel logs = /* resolver BOT_LOGS */;
if (logs != null) {
    for (int i = 0; i < bloques.size(); i++) {
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                i == 0 ? titulo : null, bloques.get(i)).build();
        logs.sendMessageEmbeds(embed).queue(null, e -> { });
    }
} else {
    log.warn("No hay canal bot-logs: el informe de /setup no se archiva");
}
```

- [ ] **Step 4: Verify** — `.\mvnw.cmd clean verify` en verde. Retirar la clave `setup.resumen` de ambos `.properties` si ya no se usa (comprobar con grep antes de borrar).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(setup): informe de cambios en la respuesta y en bot-logs"
```

---

### Task 8: Documentación y verify final

**Files:**
- Modify: `docs/architecture.md`, `docs/decisions.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: `docs/decisions.md`** — nuevo ADR (comprobar el último con `grep -n "^## ADR" docs/decisions.md | tail -1`; tras los ascensos debería ser ADR-014 → nuevo ADR-015):

```markdown
## ADR-015 — informe de cambios de /setup

**Estado:** aceptada e implementada.

**Contexto.** /setup era idempotente pero opaco: solo daba contadores, no qué cambiaba. Sin registro
no había forma de saber qué se añadió o actualizó en cada tanda.

**Decisión.** Un colector `RegistroCambios` recorre los helpers de setup y registra creado/actualizado/
eliminado por nombre; el contenido reaplicado (intros, welcome, descripción del servidor) se compara y
solo cuenta si difiere. El informe se renderiza con `InformeSetup`, se trocea con `util/Embeds` y se
envía a la respuesta y, persistente, a bot-logs. Setup pasa además a gestionar la descripción del
servidor.

**Consecuencias.** Cada /setup deja un registro consultable. Coste: instrumentación repartida por
SetupComando/SetupServidorPlan y alguna lectura extra a la API para los diffs de contenido.
```

- [ ] **Step 2: `docs/architecture.md`** — en la sección de administración/setup, añadir que `/setup` produce un informe de cambios (RegistroCambios → InformeSetup → respuesta + bot-logs) y que gestiona la descripción del servidor.

- [ ] **Step 3: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`:

```markdown
- **Informe de /setup**: cada ejecución de `/setup` (y `/setup desde_cero`) muestra qué cambió esa
  tanda —nuevos, actualizados y eliminados, con nombres— en la respuesta y como registro persistente
  en `bot-logs`. `/setup` pasa a fijar también la descripción del servidor.
```

- [ ] **Step 4: READMEs** — si la tabla de comandos describe `/setup`, mencionar el informe de cambios.

- [ ] **Step 5: Verify final**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS, 0 fallos. **Pegar la salida real** (definición de terminado del repo).

- [ ] **Step 6: Commit**

```bash
git add docs/architecture.md docs/decisions.md CHANGELOG.md README.md README.en.md
git commit -m "docs(setup): ADR-015, changelog y documentación del informe de /setup"
```

---

## Despliegue (decírselo al usuario al terminar)

- Cambia la salida de `/setup` y añade la descripción del servidor → **reiniciar bot** + **`/setup`**.
- Sin migración Flyway.
- Smoke test: `/setup` en un servidor con cambios (crea algo nuevo → sale en 🆕) y sin cambios
  (sale «sin cambios»); comprobar que la entrada aparece también en `bot-logs`.

## Self-review del plan

- **Cobertura de la spec:** colector (T2), render + i18n (T3), diff de estructura (T4), diff de
  contenido incl. intros/welcome/afk/automod/anclas/paneles (T5), descripción del servidor (T6),
  entrega respuesta + bot-logs con troceado (T7), util compartido (T1), docs+ADR (T8). Destino y
  «solo cambios» y «diff de contenido» de las decisiones están en T5/T7.
- **Placeholders:** las nuevas clases llevan código completo (T1–T3, T6). Las tareas de
  instrumentación (T4, T5) referencian métodos existentes con su `grep` exacto y los fragmentos de
  registro a insertar, porque reproducir 900 líneas de setup no aporta; el implementador lee el
  cuerpo real (subagent-driven).
- **Consistencia de tipos:** `RegistroCambios.Categoria`/`Tipo`/`Entrada` (T2) se usan igual en T3–T7;
  `InformeSetup.Contexto(servidor, porMencion, desdeCero, epochSegundos)` coincide entre T3 y T7;
  `Embeds.partirEnBloques(List<String>, int)` y `Embeds.MAX_DESC` coinciden T1/T7.
```
