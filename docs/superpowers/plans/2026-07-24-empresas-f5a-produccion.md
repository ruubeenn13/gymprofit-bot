# Empresas Fase 5a — Producción y ventas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La empresa genera **mercancía** como subproducto del trabajo de sus miembros (tope por nivel) y la vende (`/empresa vender`) para engordar el bote, con un impuesto de venta quemado.

**Architecture:** Sobre F1–F4. Una columna `empresas.mercancia` (V31) + una función pura `Produccion` con los números. `TrabajoService.currar` suma mercancía al almacén de la empresa del miembro (clamp al tope en SQL). `EmpresaVentaService.vender` convierte mercancía en coins al bote menos el impuesto quemado. Todo dinero pasa por el patrón atómico ya usado (gate = descontar mercancía antes de abonar).

**Tech Stack:** Java 21, JDA 5, JDBC (HikariCP), Flyway, JUnit 5 + Mockito, Testcontainers (MySQL).

**Spec:** `docs/superpowers/specs/2026-07-24-empresas-f5a-produccion-design.md`.

**Precondición:** F4 desplegada y funcionando.

**Convenciones (obligatorias):** dominio español; i18n ES+EN en AMBOS `messages_*.properties` (nunca hardcodear); embeds solo por `EmbedFactory`; `Messages.get(locale, key, args...)` usa **MessageFormat posicional** (`{0}`,`{1}`…), NO `.replace`; cabecera Javadoc por archivo + inline del *porqué*; migraciones Flyway. Build: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify` (PowerShell; SIEMPRE `clean`; no dos builds a la vez; `-Dtest=A,B` entrecomillado). Baseline: **530 tests**.

## File Structure

- **Create** `src/main/resources/db/migration/V31__empresa_mercancia.sql`
- **Create** `src/main/java/com/gymprofit/bot/services/Produccion.java` — función pura (números).
- **Create** `src/main/java/com/gymprofit/bot/services/EmpresaVentaService.java` — vender.
- **Modify** `src/main/java/com/gymprofit/bot/db/Empresa.java` — campo `long mercancia`.
- **Modify** `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — `mercancia` en lecturas; `sumarMercancia`; `gastarMercancia`.
- **Modify** `src/main/java/com/gymprofit/bot/services/TrabajoService.java` — sumar mercancía en `currar`.
- **Modify** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — subcomando `vender`; línea `almacén` en `info`.
- **Modify** `src/main/java/com/gymprofit/bot/Main.java` — wiring de `EmpresaVentaService` en el comando.
- **Modify** `messages_es.properties`, `messages_en.properties`.
- **Modify** docs: `docs/decisions.md` (ADR-020), `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.
- **Test** `PrestigioTest`-style: `ProduccionTest` (nuevo); `EmpresaRepositorioTest` (mercancía, Testcontainers); `EmpresaVentaServiceTest` (nuevo, Mockito); `TrabajoServiceTest` (mercancía en currar).

---

### Task 1: V31 + `Produccion` + mercancía en el repo

**Files:**
- Create: `src/main/resources/db/migration/V31__empresa_mercancia.sql`
- Create: `src/main/java/com/gymprofit/bot/services/Produccion.java`
- Test: `src/test/java/com/gymprofit/bot/services/ProduccionTest.java`
- Modify: `src/main/java/com/gymprofit/bot/db/Empresa.java`
- Modify: `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java`

- [ ] **Step 1: Migración V31**

```sql
-- V31: almacen de mercancia de la empresa (F5a produccion). La produce el trabajo de los miembros
-- (subproducto de /trabajo currar, con tope por nivel) y se vende con /empresa vender. NOT NULL DEFAULT 0.
ALTER TABLE empresas ADD COLUMN mercancia BIGINT NOT NULL DEFAULT 0;
```

- [ ] **Step 2: `Empresa` record gana `long mercancia`**

Añade el parámetro al final del record `Empresa` (tras `canalId`) y documenta ("unidades de mercancia en el almacen (F5a); 0 al fundar"). Recuerda: esto rompe todos los `new Empresa(...)` — arréglalos en el Step 8.

- [ ] **Step 3: Test rojo `ProduccionTest`** (JUnit 5, sin AssertJ — el repo no lo trae):

```java
package com.gymprofit.bot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ProduccionTest {
    @Test void unidadesPorCurroEsBaseMasNivel() {
        assertEquals(6, Produccion.unidadesPorCurro(1));   // 5 + 1
        assertEquals(15, Produccion.unidadesPorCurro(10));  // 5 + 10
    }
    @Test void capacidadEsNivelPorFactor() {
        assertEquals(100, Produccion.capacidad(1));
        assertEquals(1_000, Produccion.capacidad(10));
    }
    @Test void impuestoYNetoDeUnaVenta() {
        // 100 uds * 50 = 5_000 bruto; 15% = 750 quemado; neto 4_250
        long bruto = 100 * Produccion.PRECIO_UNIDAD;
        long impuesto = Produccion.impuesto(bruto);
        assertEquals(750, impuesto);
        assertEquals(4_250, bruto - impuesto);
    }
}
```

- [ ] **Step 4: Corre, verifica FAIL** (`Produccion` no existe)

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=ProduccionTest" test`

- [ ] **Step 5: Implementa `Produccion`**

```java
package com.gymprofit.bot.services;

/**
 * Numeros puros de la produccion de empresas (F5a). Sin estado: define cuanta mercancia genera un curro,
 * la capacidad del almacen por nivel, y el precio/impuesto de la venta. Pesos tunables si el balance en
 * vivo lo pide.
 */
public final class Produccion {

    /** Mercancia base por curro, antes de sumar el nivel de la empresa. */
    private static final int BASE_POR_CURRO = 5;
    /** Capacidad de almacen por nivel: capacidad = nivel * este factor. */
    private static final int FACTOR_CAPACIDAD = 100;
    /** Precio de venta por unidad de mercancia, en coins. */
    public static final long PRECIO_UNIDAD = 50L;
    /** Fraccion del bruto que se QUEMA como impuesto en cada venta (sumidero antiinflacion). */
    public static final double IMPUESTO_VENTA = 0.15;

    private Produccion() {
    }

    /** Unidades de mercancia que genera un curro de un miembro de una empresa de este nivel. */
    public static int unidadesPorCurro(int nivel) {
        return BASE_POR_CURRO + nivel;
    }

    /** Capacidad del almacen de una empresa de este nivel (tope de mercancia). */
    public static long capacidad(int nivel) {
        return (long) nivel * FACTOR_CAPACIDAD;
    }

    /** Impuesto (quemado) de una venta de este bruto: floor(bruto * IMPUESTO_VENTA). */
    public static long impuesto(long bruto) {
        return (long) Math.floor(bruto * IMPUESTO_VENTA);
    }
}
```

- [ ] **Step 6: `ProduccionTest` verde** (3 tests)

- [ ] **Step 7: `mercancia` en las lecturas del repo**

- Añade `mercancia` a `SELECT_EMPRESA` y al SELECT explícito de `deMiembro`.
- En `mapearEmpresa`, lee `rs.getLong("mercancia")` y pásalo como último arg del `new Empresa(...)`.

- [ ] **Step 8: Arregla todos los `new Empresa(...)`**

`grep -rn "new Empresa(" src` y añade el nuevo arg `mercancia` (0L en fixtures/mocks). Sin esto no compila.

- [ ] **Step 9: `sumarMercancia` (clamp al tope en SQL)**

```java
/**
 * Suma mercancia al almacen de una empresa, recortada a la capacidad por nivel (F5a): el rebose se
 * descarta atomicamente con LEAST, sin leer-modificar-escribir. La usa el curro de cada miembro.
 */
public void sumarMercancia(long empresaId, long cantidad) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(
                 "UPDATE empresas SET mercancia = LEAST(mercancia + ?, nivel * 100) WHERE id = ?")) {
        ps.setLong(1, cantidad);
        ps.setLong(2, empresaId);
        ps.executeUpdate();
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo sumar mercancia a la empresa " + empresaId, e);
    }
}
```
(El `nivel * 100` debe coincidir con `Produccion.capacidad`; documenta el acople con un comentario que apunte a `Produccion.FACTOR_CAPACIDAD`.)

- [ ] **Step 10: `gastarMercancia` (condicional)**

```java
/**
 * Descuenta mercancia del almacen solo si hay suficiente (gate atomico de la venta, F5a). Devuelve
 * {@code true} si afecto filas (habia bastante), {@code false} si no (no se vende de mas).
 */
public boolean gastarMercancia(long empresaId, long cantidad) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(
                 "UPDATE empresas SET mercancia = mercancia - ? WHERE id = ? AND mercancia >= ?")) {
        ps.setLong(1, cantidad);
        ps.setLong(2, empresaId);
        ps.setLong(3, cantidad);
        return ps.executeUpdate() > 0;
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo gastar mercancia de la empresa " + empresaId, e);
    }
}
```

- [ ] **Step 11: Test de repo (Testcontainers)** en `EmpresaRepositorioTest`:

```java
@Test void sumarMercanciaRespetaElTopePorNivel() {
    long id = repo.fundar("SALUD", 1L, "Alfa"); // nivel 1 -> capacidad 100
    repo.sumarMercancia(id, 80);
    repo.sumarMercancia(id, 50); // 80+50=130 -> recorta a 100
    assertEquals(100, repo.porId(id).orElseThrow().mercancia());
}

@Test void gastarMercanciaCondicional() {
    long id = repo.fundar("TECNICA", 2L, "Beta");
    repo.sumarMercancia(id, 40);
    assertTrue(repo.gastarMercancia(id, 30));            // baja a 10
    assertEquals(10, repo.porId(id).orElseThrow().mercancia());
    assertFalse(repo.gastarMercancia(id, 999));          // no hay bastante -> false, sin cambio
    assertEquals(10, repo.porId(id).orElseThrow().mercancia());
}
```
(Usa el patrón `@Testcontainers` del archivo. Si `fundar` no fija nivel 1 por defecto, ajusta; sube el nivel con `subirNivel` si necesitas capacidad > 100 en el segundo test — nivel 2 = capacidad 200, así que 40 cabe.)

- [ ] **Step 12: `clean verify` + commit**

Esperado: 530 + ProduccionTest(3) + repo(2). BUILD SUCCESS.
```bash
git add src/main/resources/db/migration/V31__empresa_mercancia.sql src/main/java/com/gymprofit/bot/services/Produccion.java src/test/java/com/gymprofit/bot/services/ProduccionTest.java src/main/java/com/gymprofit/bot/db/Empresa.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java
# + los archivos de test con new Empresa( que hayas tocado
git commit -m "feat(empresas): V31 mercancia + produccion (numeros y repo)"
```

---

### Task 2: Producción en `TrabajoService.currar`

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/TrabajoService.java`
- Test: `src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java`

**Contexto:** `currar` ya aplica el corte de empresa en un método privado best-effort (~379-403) que hace `Optional<Empresa> emp = empresas.deMiembro(discordId)` → `empresas.incrementarBote(emp.id(), corte)` dentro de un try/catch que degrada a "cobrar el base sin corte" si algo falla. Ahí mismo, tras `incrementarBote`, se suma la mercancía.

- [ ] **Step 1: Test rojo** — un miembro de empresa suma mercancía al currar; uno sin empresa no.

En `TrabajoServiceTest`, con `EmpresaRepositorio` mockeado (mira cómo los tests existentes de F3 mockean `deMiembro`/`incrementarBote` para el corte — reúsalo):

```java
@Test
void currarSumaMercanciaSiEsMiembroDeEmpresa() {
    // arrange: personaje con trabajo, energia y saciedad suficientes, miembro de empresa nivel 3
    // (copia el arrange del test de F3 "currarAplicaCorteAlBote" y añade el stub de deMiembro con nivel 3)
    when(empresas.deMiembro(DISCORD_ID)).thenReturn(Optional.of(empresaNivel(3)));

    servicio.currar(DISCORD_ID);

    // 5 + 3 = 8 unidades por curro para nivel 3
    verify(empresas).sumarMercancia(eq(EMPRESA_ID), eq(8L));
}

@Test
void currarNoSumaMercanciaSinEmpresa() {
    when(empresas.deMiembro(DISCORD_ID)).thenReturn(Optional.empty());
    servicio.currar(DISCORD_ID);
    verify(empresas, never()).sumarMercancia(anyLong(), anyLong());
}
```
(Ajusta nombres a los reales del test de F3: el helper `empresaNivel(int)` que construya un `Empresa` con `EMPRESA_ID` y ese nivel; reutiliza los stubs de energía/saciedad/trabajo que ya usan los tests de currar. Si el corte de F3 ya tiene un test, clónalo.)

- [ ] **Step 2: Corre, verifica FAIL**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test`
Expected: el nuevo test falla (no se llama a `sumarMercancia`).

- [ ] **Step 3: Añade la producción en el método del corte**

En el método privado que aplica el corte (donde está `empresas.incrementarBote(emp.get().id(), ingreso.corte());`), añade justo después, dentro del MISMO try:

```java
empresas.incrementarBote(emp.get().id(), ingreso.corte());
// F5a: el curro tambien produce mercancia para el almacen de la empresa (tope por nivel via LEAST en
// el repo). Mismo bloque best-effort que el corte: si falla, se degrada sin romper el curro.
empresas.sumarMercancia(emp.get().id(), Produccion.unidadesPorCurro(emp.get().nivel()));
```

- [ ] **Step 4: Corre, verifica PASS** (ambos tests nuevos verdes; los de F3 del corte siguen verdes)

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=TrabajoServiceTest" test`

- [ ] **Step 5: `clean verify` + commit**

```bash
git add src/main/java/com/gymprofit/bot/services/TrabajoService.java src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java
git commit -m "feat(empresas): el curro produce mercancia para la empresa"
```

---

### Task 3: `EmpresaVentaService` + `/empresa vender` + almacén en `info` (LLEVA REVIEW)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/EmpresaVentaService.java`
- Test: `src/test/java/com/gymprofit/bot/services/EmpresaVentaServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: Test rojo `EmpresaVentaServiceTest`** (Mockito; mira `EmpresaGestionServiceTest` para el estilo de mockear `EmpresaRepositorio.deMiembro`/`miembros`):

```java
// Casos:
// - ventaOkQuemaImpuestoYAbonaNeto: alto cargo, mercancia 100, vende 100 -> gastarMercancia(id,100) true,
//   incrementarBote(id, 4_250) (5_000 bruto - 750 impuesto). Resultado OK con las cifras.
// - ventaSinCantidadVendeTodo: cantidadOpt vacío -> vende toda la mercancia actual.
// - ventaSinMercancia: mercancia 0 -> SIN_MERCANCIA, never incrementarBote.
// - ventaNoAutorizadoSiNoEsAltoCargo: rango EMPLEADO -> NO_AUTORIZADO, never gastarMercancia.
// - ventaSinEmpresa: deMiembro vacío -> SIN_EMPRESA.
// - gastarMercanciaFalseNoAbona: si gastarMercancia devuelve false (carrera) -> no incrementarBote.
```
Escribe los 6 tests con sus asserts (`verify(repo).incrementarBote(EMPRESA_ID, 4_250L)`, `verify(repo, never())...`, etc.).

- [ ] **Step 2: Corre, verifica FAIL** (no existe `EmpresaVentaService`)

- [ ] **Step 3: Implementa `EmpresaVentaService`**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Venta de la mercancia producida por la empresa (F5a). Los altos cargos (DUENO/DIRECTIVO) venden la
 * mercancia del almacen: entra al bote el neto y se QUEMA el impuesto de venta. El descuento de mercancia
 * es el gate atomico (nunca se abona al bote una venta que no pudo descontar mercancia).
 */
public final class EmpresaVentaService {

    /** Estado del intento de venta. */
    public enum Estado { OK, SIN_EMPRESA, NO_AUTORIZADO, SIN_MERCANCIA }

    /** Salida de una venta: estado y, en OK, las cifras para el embed. */
    public record Resultado(Estado estado, long unidades, long bruto, long impuesto, long neto, long restante) {
        static Resultado de(Estado estado) {
            return new Resultado(estado, 0, 0, 0, 0, 0);
        }
    }

    private final EmpresaRepositorio repo;

    public EmpresaVentaService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /**
     * Vende mercancia del almacen de la empresa del actor. {@code cantidad} vacio = vender todo. Valida:
     * el actor pertenece a una empresa, es alto cargo, y hay mercancia. Ejecuta:
     * gastarMercancia (gate) -> incrementarBote(neto); el impuesto se quema.
     */
    public Resultado vender(long actorId, OptionalLong cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) {
            return Resultado.de(Estado.SIN_EMPRESA);
        }
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) {
            return Resultado.de(Estado.NO_AUTORIZADO);
        }
        long disponible = emp.mercancia();
        long aVender = cantidad.isPresent() ? Math.min(cantidad.getAsLong(), disponible) : disponible;
        if (aVender <= 0) {
            return Resultado.de(Estado.SIN_MERCANCIA);
        }
        // Gate atomico: si otro vendio en paralelo y ya no hay bastante, no se abona nada.
        if (!repo.gastarMercancia(emp.id(), aVender)) {
            return Resultado.de(Estado.SIN_MERCANCIA);
        }
        long bruto = aVender * Produccion.PRECIO_UNIDAD;
        long impuesto = Produccion.impuesto(bruto);
        long neto = bruto - impuesto;
        repo.incrementarBote(emp.id(), neto); // el impuesto NO se ingresa a nadie: se quema
        return new Resultado(Estado.OK, aVender, bruto, impuesto, neto, disponible - aVender);
    }

    /** ¿El actor es DUENO o DIRECTIVO de la empresa? (autorizacion de la venta). */
    private boolean esAltoCargo(long empresaId, long actorId) {
        List<MiembroEmpresa> miembros = repo.miembros(empresaId);
        return miembros.stream()
                .filter(m -> m.discordId() == actorId)
                .anyMatch(m -> m.rango() == RangoEmpresa.DUENO || m.rango() == RangoEmpresa.DIRECTIVO);
    }
}
```
(Verifica que `MiembroEmpresa` tiene `rango()` y `discordId()`, y que `RangoEmpresa` tiene `DUENO`/`DIRECTIVO` — sí, de F2.)

- [ ] **Step 4: Corre `EmpresaVentaServiceTest`, verifica PASS** (6 tests)

- [ ] **Step 5: Wiring en `Main`**

Crea el servicio junto a los otros de empresa (`empresaRepo` ya existe) y pásalo al `EmpresaComando`:
```java
EmpresaVentaService empresaVenta = new EmpresaVentaService(empresaRepo);
```
Añade el parámetro al `new EmpresaComando(...)` (y al constructor + campo del comando).

- [ ] **Step 6: Subcomando `/empresa vender [cantidad]`**

En `EmpresaComando`:
- `addSubcommands(...)`: `new SubcommandData("vender", Messages.get(Messages.ES, "comando.empresa.vender.desc")).addOptions(new OptionData(OptionType.INTEGER, "cantidad", Messages.get(Messages.ES, "comando.empresa.vender.cantidad"), false).setMinValue(1))` (usa el helper `sub(...)` si encaja, pero aquí hay opción → constrúyelo explícito como los otros subcomandos con opciones).
- `switch (sub)`: `case "vender" -> vender(evento, locale);`
- Método (público en canal; usa MessageFormat posicional, firma `EmbedFactory.base(Tipo, Locale, titulo, desc)`):
```java
private void vender(SlashCommandInteractionEvent evento, Locale locale) {
    var opt = evento.getOption("cantidad");
    OptionalLong cantidad = opt == null ? OptionalLong.empty() : OptionalLong.of(opt.getAsLong());
    EmpresaVentaService.Resultado r = venta.vender(evento.getUser().getIdLong(), cantidad);
    String msg = switch (r.estado()) {
        case SIN_EMPRESA -> Messages.get(locale, "empresa.venta.sin_empresa");
        case NO_AUTORIZADO -> Messages.get(locale, "empresa.venta.no_autorizado");
        case SIN_MERCANCIA -> Messages.get(locale, "empresa.venta.sin_mercancia");
        case OK -> Messages.get(locale, "empresa.venta.ok",
                r.unidades(), r.bruto(), r.impuesto(), r.neto(), r.restante());
    };
    EmbedFactory.Tipo tipo = r.estado() == EmpresaVentaService.Estado.OK
            ? EmbedFactory.Tipo.ECONOMIA : EmbedFactory.Tipo.AVISO; // usa los Tipo reales del proyecto
    evento.replyEmbeds(EmbedFactory.base(tipo, locale,
            Messages.get(locale, "empresa.venta.titulo"), msg).build()).queue();
}
```
**Ajusta los `EmbedFactory.Tipo`** a los que existan (mira los que usa el resto de `EmpresaComando`, p. ej. el de `mejorar`/`info`); no inventes constantes. `venta` = el campo `EmpresaVentaService` nuevo. Importa `OptionalLong`.

- [ ] **Step 7: Línea `almacén` en `/empresa info`**

En el método `info`, donde se construye la descripción del embed (cerca de `e.nivel()`, `e.bote()`), añade una línea con la mercancía y la capacidad:
```java
// F5a: almacen de mercancia X/Y (Y = capacidad por nivel)
... Messages.get(locale, "empresa.info.almacen", e.mercancia(), Produccion.capacidad(e.nivel())) ...
```
Intégralo en el mismo `MessageFormat`/campos que ya usa `info` (mira cómo arma la descripción; si usa una sola clave con varios `{n}`, añade dos args más y actualiza esa clave en ES+EN; si concatena líneas, añade una línea nueva). Documenta el acople de `capacidad` con `Produccion`.

- [ ] **Step 8: i18n ES**

```properties
comando.empresa.vender.desc=Vende la mercancía del almacén de tu empresa
comando.empresa.vender.cantidad=Cuántas unidades vender (vacío = todo)
empresa.venta.titulo=📦 Venta de mercancía
empresa.venta.ok=Vendidas **{0}** unidades por **{1}** coins. Impuesto quemado: **{2}**. Al bote: **{3}**. Quedan **{4}** en almacén.
empresa.venta.sin_mercancia=El almacén está vacío: no hay mercancía que vender.
empresa.venta.no_autorizado=Solo el dueño o un directivo pueden vender la mercancía.
empresa.venta.sin_empresa=No perteneces a ninguna empresa.
empresa.info.almacen=📦 Almacén: {0}/{1}
```
(Si `info` usa una clave única, integra `empresa.info.almacen` como línea aparte en el StringBuilder de la descripción.)

- [ ] **Step 9: i18n EN**

```properties
comando.empresa.vender.desc=Sell your company's warehouse goods
comando.empresa.vender.cantidad=How many units to sell (empty = all)
empresa.venta.titulo=📦 Goods sale
empresa.venta.ok=Sold **{0}** units for **{1}** coins. Tax burned: **{2}**. To the pot: **{3}**. **{4}** left in stock.
empresa.venta.sin_mercancia=The warehouse is empty: nothing to sell.
empresa.venta.no_autorizado=Only the owner or a director can sell the goods.
empresa.venta.sin_empresa=You don't belong to any company.
empresa.info.almacen=📦 Warehouse: {0}/{1}
```

- [ ] **Step 10: `clean verify`**

Esperado: 530 + ProduccionTest(3) + repo(2) + currar(2) + venta(6) ≈ 543. BUILD SUCCESS. Verifica paridad ES/EN a mano.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/EmpresaVentaService.java src/test/java/com/gymprofit/bot/services/EmpresaVentaServiceTest.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/java/com/gymprofit/bot/Main.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(empresas): /empresa vender mercancia (neto al bote, impuesto quemado)"
```

**Esta task lleva review** (dinero: quema del impuesto, abono al bote, gate atómico de la mercancía).

---

### Task 4: Documentación + verify final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: `docs/decisions.md` — ADR-020** (comprueba que el último es 019):

```markdown
## ADR-020 — producción y ventas de empresas

**Estado:** aceptada e implementada (Fase 5a).

**Contexto.** Tras F4 la empresa tenía estatus pero ninguna actividad productiva propia.

**Decisión.** Cada `/trabajo currar` de un miembro produce **mercancía** (`5 + nivel` unidades) al almacén
de su empresa —además del corte del 10 % al bote de F3—, con **tope por nivel** (`nivel × 100`): el rebose
se pierde (sumidero suave). `/empresa vender [cantidad]` (altos cargos, sin voto) convierte mercancía en
coins al **bote** menos un **impuesto del 15 % que se quema**. El descuento de mercancía es el gate
atómico (nunca se abona una venta que no pudo descontar). Números tunables en `Produccion`.

**Consecuencias.** Migración V31 (`empresas.mercancia`). La producción es una fuente de coins moderada y
triplemente frenada (energía del curro, tope de almacén, impuesto quemado). Impuestos al Estado, insumos,
precio fluctuante y venta B2B quedan para subsistemas posteriores de F5.
```

- [ ] **Step 2: `docs/architecture.md`** — viñeta F5a tras la de F4; sube migraciones a **V6–V31**:

```markdown
- **Empresas (Fase 5a)**: producción y ventas. Cada `/trabajo currar` de un miembro genera **mercancía**
  (`5 + nivel`) al almacén de su empresa, con tope `nivel × 100` (el rebose se pierde). `/empresa vender`
  (altos cargos) la convierte en coins al bote menos un **15 % de impuesto quemado**. Números en
  `Produccion`; el descuento de mercancía es el gate atómico. Migración V31 (`mercancia`).
```

- [ ] **Step 3: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`, encima de F4:

```markdown
- **Empresas (Fase 5a)** (`/empresa vender`): la empresa produce **mercancía** con el trabajo de sus
  miembros (tope por nivel) y la vende para engordar el bote, con un impuesto de venta quemado. Migración
  `V31`.
```

- [ ] **Step 4: READMEs** — `/empresa` suma `vender` en `README.md` y `README.en.md`.

- [ ] **Step 5: `clean verify` final + commit**

```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5a produccion — ADR-020, architecture, changelog y READMEs"
```

---

## Despliegue (tras cerrar las 4 tasks)

**Reiniciar bot** (V31 + subcomando `vender` + producción en `currar`). **No** requiere `/setup`. Smoke
test: currar como miembro y ver subir `almacén X/Y` en `/empresa info`; `/empresa vender` parcial y total
(bote↑, impuesto quemado, almacén baja); llenar el almacén y ver el rebose; un no-alto-cargo no puede vender.

## Notas de riesgo

- **Acople del tope:** el `nivel * 100` de `sumarMercancia` (SQL) debe coincidir con `Produccion.capacidad`.
  Documentado en ambos sitios; si se cambia el factor, cambiar los dos.
- **Gate atómico:** `gastarMercancia` condicional antes de `incrementarBote` evita vender mercancía que ya
  no está (dos ventas concurrentes de altos cargos).
