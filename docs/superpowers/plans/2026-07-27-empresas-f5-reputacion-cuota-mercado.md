# Reputación y cuota de mercado de empresa (F5) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada empresa tiene una cuota de mercado (su parte del prestigio de su rama) que escala sus ingresos por venta (±25 %, neutro en la cuota justa 1/N); `/empresa ranking` gana vista por rama con el % de cuota.

**Architecture:** Función pura (`Cuota`) + `CuotaEmpresasService` (deriva la cuota del ranking de la rama, sin persistir) + hook de una línea en `EmpresaVentaService.vender` (bruto × factor de cuota, compone con el clima económico) + vista por rama en `/empresa ranking` + línea en `/empresa info`. Sin migración, sin job.

**Tech Stack:** Java 21, JDBC, JDA 5, JUnit 5 + Mockito + Testcontainers.

**Convenciones:** dominio español; i18n ES **y** EN (nunca hardcodear); embeds vía `EmbedFactory`; header Javadoc por archivo + Javadoc en públicos no triviales; migración Flyway para esquema (aquí NO hay); una clase por comando.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw -B clean verify`. Siempre `clean verify`. Testcontainers se saltan en local (solo CI).

**Firmas reales (verificadas):**
- `EmpresaRepositorio`: `List<EmpresaRanking> ranking()` (record `EmpresaRanking(String nombre, String rama, int nivel, int miembros, long bote)`), `Optional<Empresa> porId(long)`.
- `EmpresaService`: `List<FilaRanking> ranking(int limite)` (record `FilaRanking(String nombre, String rama, int nivel, int miembros, long bote, long prestigio)`); mapea el repo con `Prestigio.calcular` y ordena por prestigio desc.
- `Prestigio.calcular(int nivel, int numMiembros, long bote)` → long.
- `EmpresaVentaService`: ctor **actual** `(EmpresaRepositorio repo, EventoEconomicoService eventos)`; en `vender`, `long bruto = Math.round(aVender * Produccion.PRECIO_UNIDAD * eventos.ventaMult());`.
- `Empresa` record tiene `String rama()`, `String nombre()`, `int nivel()`, `long bote()`, `long id()`.
- Ramas: `com.gymprofit.bot.services.Ascensos.Rama { SALUD, TECNICA, TRANSPORTE, HOSTELERIA, NEGOCIOS, ARTE, SERVICIOS }`; i18n `rama.<minúsculas>`. **CONFIRMAR** cómo se guarda `empresas.rama` (qué string recibe `EmpresaRepositorio.fundar`: ¿`Rama.name()`? ¿minúsculas? ¿el sector?) y hacer que los `value` de los choices del ranking coincidan EXACTAMENTE con ese formato.

---

## File Structure

**Nuevos:**
- `src/main/java/com/gymprofit/bot/services/Cuota.java` — pura (SENSIBILIDAD/MIN/MAX, relativa, factorVenta).
- `src/main/java/com/gymprofit/bot/services/CuotaEmpresasService.java` — factorVentaDe/cuotaDe.
- Tests: `CuotaTest`, `CuotaEmpresasServiceTest`.

**Modificados:**
- `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — `rankingDeRama(String)`.
- `src/main/java/com/gymprofit/bot/services/EmpresaService.java` — `rankingDeRama(String)`.
- `src/main/java/com/gymprofit/bot/services/EmpresaVentaService.java` — ctor + hook en el bruto.
- `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — opción `rama` en `ranking` + línea de cuota en `info`.
- `src/main/java/com/gymprofit/bot/Main.java` — construir `CuotaEmpresasService`, inyectarlo.
- `messages_es.properties` / `messages_en.properties`, docs.
- Tests: `EmpresaVentaServiceTest`, `EmpresaRepositorioTest` (rankingDeRama).

---

## Task 1: `Cuota` (pura) + `rankingDeRama` + `CuotaEmpresasService`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/Cuota.java`
- Create: `src/main/java/com/gymprofit/bot/services/CuotaEmpresasService.java`
- Modify: `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/services/CuotaTest.java`
- Test: `src/test/java/com/gymprofit/bot/services/CuotaEmpresasServiceTest.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java` (añadir caso rankingDeRama)

- [ ] **Step 1: `CuotaTest` (write first, FAIL)**
```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuotaTest {

    @Test
    @DisplayName("relativa: cuota justa = 1.0; líder > 1; monopolio y total 0 = 1.0")
    void relativa() {
        // 2 empresas iguales: cada una prestigio 100, total 200, n=2 → relativa = 100*2/200 = 1.0
        assertEquals(1.0, Cuota.relativa(100, 200, 2), 1e-9);
        // líder: propio 150 de 200, n=2 → 150*2/200 = 1.5
        assertEquals(1.5, Cuota.relativa(150, 200, 2), 1e-9);
        // monopolio n=1 → 1.0 (sin competencia)
        assertEquals(1.0, Cuota.relativa(100, 100, 1), 1e-9);
        // total 0 (defensivo) → 1.0
        assertEquals(1.0, Cuota.relativa(0, 0, 3), 1e-9);
    }

    @Test
    @DisplayName("factorVenta: neutro en 1.0; clamp a MIN/MAX; prima/penalización intermedias")
    void factorVenta() {
        assertEquals(1.0, Cuota.factorVenta(1.0), 1e-9);
        assertEquals(1.125, Cuota.factorVenta(1.5), 1e-9); // 1 + 0.25*0.5
        assertEquals(0.875, Cuota.factorVenta(0.5), 1e-9); // 1 + 0.25*(-0.5)
        assertEquals(1.25, Cuota.factorVenta(3.0), 1e-9);  // clamp MAX (1+0.25*2=1.5 → 1.25)
        assertEquals(0.75, Cuota.factorVenta(-5.0), 1e-9); // clamp MIN
    }
}
```

- [ ] **Step 2: Run — FAIL** (`sh ./mvnw -B test -Dtest=CuotaTest`).

- [ ] **Step 3: `Cuota.java`**
```java
package com.gymprofit.bot.services;

/**
 * Números puros de la cuota de mercado de empresa (F5 reputación/competencia). La cuota de una empresa es
 * su parte del prestigio de su rama; comparada con la cuota "justa" (1/N) da una cuota <b>relativa</b>
 * ({@link #relativa}) que escala sus ingresos por venta ({@link #factorVenta}): líder de rama vende con
 * prima, rezagado con penalización, media neutra. Acotado a ±25 % (antiinflación). Sin estado.
 */
public final class Cuota {

    private Cuota() {}

    /** Pendiente del factor respecto a la cuota relativa. */
    public static final double SENSIBILIDAD = 0.25;
    /** Tope de penalización de venta (cuota marginal). */
    public static final double MIN = 0.75;
    /** Tope de prima de venta (dominio de la rama). */
    public static final double MAX = 1.25;

    /**
     * Cuota relativa: {@code cuota / cuotaJusta = (propio/total) / (1/n) = propio*n/total}. 1.0 = te toca
     * tu parte media. Defensivo: con {@code total <= 0} o {@code n <= 1} (sin competencia) devuelve 1.0.
     */
    public static double relativa(long prestigioPropio, long prestigioTotal, int n) {
        if (prestigioTotal <= 0 || n <= 1) {
            return 1.0;
        }
        return (double) prestigioPropio * n / prestigioTotal;
    }

    /** Factor de venta: {@code clamp(1 + SENSIBILIDAD*(relativa-1), MIN, MAX)}. */
    public static double factorVenta(double relativa) {
        double f = 1 + SENSIBILIDAD * (relativa - 1);
        return Math.max(MIN, Math.min(MAX, f));
    }
}
```

- [ ] **Step 4: Run — PASS** (`sh ./mvnw -B test -Dtest=CuotaTest`).

- [ ] **Step 5: `EmpresaRepositorio.rankingDeRama(String rama)`** — clon de `ranking()` con `WHERE e.rama = ?`. Añadir tras `ranking()`:
```java
    /** Como {@link #ranking()} pero solo las empresas de una rama (para la cuota de mercado, F5). */
    public List<EmpresaRanking> rankingDeRama(String rama) {
        String sql = "SELECT e.nombre, e.rama, e.nivel, e.bote, COUNT(m.discord_id) AS miembros "
                + "FROM empresas e LEFT JOIN empresa_miembros m ON m.empresa_id = e.id "
                + "WHERE e.rama = ? "
                + "GROUP BY e.id, e.nombre, e.rama, e.nivel, e.bote";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rama);
            try (ResultSet rs = ps.executeQuery()) {
                List<EmpresaRanking> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new EmpresaRanking(rs.getString("nombre"), rs.getString("rama"),
                            rs.getInt("nivel"), rs.getInt("miembros"), rs.getLong("bote")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error calculando el ranking de la rama " + rama, e);
        }
    }
```

- [ ] **Step 6: `CuotaEmpresasServiceTest` (write first, FAIL)**
```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio.EmpresaRanking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CuotaEmpresasServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    // Empresa de test: solo importan nombre/rama/nivel/bote; adapta el helper a los 13 campos del record.
    private static Empresa empresa(String nombre, String rama, int nivel, long bote) {
        return new Empresa(1L, rama, 1L, nombre, nivel, bote, java.time.Instant.EPOCH, null, 0L, 0, false, 0L, 0L);
    }

    @Test
    @DisplayName("líder de rama tiene factor > 1; rezagado < 1")
    void liderYRezagado() {
        // rama con 2 empresas: A nivel 5 bote 0 (prestigio 5*10000+0*1000+0=50000, miembros 0),
        //                      B nivel 1 bote 0 (prestigio 10000). total 60000, n=2.
        when(repo.rankingDeRama("NEGOCIOS")).thenReturn(List.of(
                new EmpresaRanking("A", "NEGOCIOS", 5, 0, 0),
                new EmpresaRanking("B", "NEGOCIOS", 1, 0, 0)));
        CuotaEmpresasService svc = new CuotaEmpresasService(repo);
        double fa = svc.factorVentaDe(empresa("A", "NEGOCIOS", 5, 0));
        double fb = svc.factorVentaDe(empresa("B", "NEGOCIOS", 1, 0));
        assertTrue(fa > 1.0, "líder con prima");
        assertTrue(fb < 1.0, "rezagado con penalización");
    }

    @Test
    @DisplayName("empresa sola en su rama → factor 1.0 (sin competencia)")
    void monopolio() {
        when(repo.rankingDeRama("ARTE")).thenReturn(List.of(new EmpresaRanking("U", "ARTE", 3, 0, 0)));
        CuotaEmpresasService svc = new CuotaEmpresasService(repo);
        assertEquals(1.0, svc.factorVentaDe(empresa("U", "ARTE", 3, 0)), 1e-9);
    }
}
```
(Adjust the `Empresa` constructor to the real 13-field record order — copy it from an existing service test that builds `Empresa`.)

- [ ] **Step 7: Run — FAIL.**

- [ ] **Step 8: `CuotaEmpresasService.java`**
```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio.EmpresaRanking;

import java.util.List;

/**
 * Cuota de mercado de una empresa (F5): la parte del prestigio de su rama. Deriva (no persiste) el factor
 * que escala sus ventas ({@link #factorVentaDe}) y el porcentaje de cuota para el display
 * ({@link #cuotaDe}). Sin competencia (una sola empresa en la rama) el factor es neutro (1.0).
 */
public final class CuotaEmpresasService {

    private final EmpresaRepositorio repo;

    public CuotaEmpresasService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /** Factor multiplicador de venta según la cuota de la empresa en su rama (±25 %, neutro 1.0). */
    public double factorVentaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        if (rama.size() <= 1) {
            return 1.0;
        }
        long total = 0;
        long propio = 0;
        for (EmpresaRanking r : rama) {
            long p = Prestigio.calcular(r.nivel(), r.miembros(), r.bote());
            total += p;
            if (r.nombre().equals(e.nombre())) {
                propio = p;
            }
        }
        return Cuota.factorVenta(Cuota.relativa(propio, total, rama.size()));
    }

    /** Cuota de la empresa en su rama en tanto por uno (0..1) para el display; 0 si la rama no suma. */
    public double cuotaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        long total = 0;
        long propio = 0;
        for (EmpresaRanking r : rama) {
            long p = Prestigio.calcular(r.nivel(), r.miembros(), r.bote());
            total += p;
            if (r.nombre().equals(e.nombre())) {
                propio = p;
            }
        }
        return total <= 0 ? 0 : (double) propio / total;
    }
}
```
Note: `EmpresaRanking` is a nested record of `EmpresaRepositorio` — import it as shown (`EmpresaRepositorio.EmpresaRanking`).

- [ ] **Step 9: Run — CuotaEmpresasServiceTest PASS.**

- [ ] **Step 10: rankingDeRama repo test (Testcontainers).** In `EmpresaRepositorioTest`, add a case: fund 2 empresas in rama X and 1 in rama Y (reuse the test's funding helper), assert `rankingDeRama("X").size()==2` and every row has `rama.equals("X")`. Use whatever rama string the funding helper already uses.

- [ ] **Step 11: `sh ./mvnw -B clean verify` → BUILD SUCCESS.**

- [ ] **Step 12: Commit**
```bash
git add src/main/java/com/gymprofit/bot/services/Cuota.java src/main/java/com/gymprofit/bot/services/CuotaEmpresasService.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/test/java/com/gymprofit/bot/services/CuotaTest.java src/test/java/com/gymprofit/bot/services/CuotaEmpresasServiceTest.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java
git commit -m "feat(empresas): cuota de mercado (Cuota pura + rankingDeRama + service)"
```

---

## Task 2: hook de venta + vista por rama del ranking + línea en info + i18n + Main — con review

**Files:**
- Modify: `services/EmpresaVentaService.java`, `services/EmpresaService.java`, `commands/economia/EmpresaComando.java`, `Main.java`
- Modify: `messages_es.properties`, `messages_en.properties`
- Test: `src/test/java/com/gymprofit/bot/services/EmpresaVentaServiceTest.java`

- [ ] **Step 1: test del hook (write first, FAIL).** En `EmpresaVentaServiceTest`, el helper que construye el service pasa a recibir un mock `CuotaEmpresasService cuota`; en `@BeforeEach` stubear neutro `when(cuota.factorVentaDe(any())).thenReturn(1.0);` (los tests existentes, que ya stubean `eventos.ventaMult()==1.0`, quedan igual). Añadir:
```java
    @Test
    @DisplayName("con cuota de líder (factor 1.20) el bruto rinde x1.20")
    void ventaConCuotaLider() {
        when(cuota.factorVentaDe(any())).thenReturn(1.20);
        // vender 10 unidades: bruto neutro 10*50=500; con x1.20 = 600
        EmpresaVentaService.Resultado r = svc().vender(ACTOR_ID, OptionalLong.of(10));
        assertEquals(600, r.bruto());
    }
```
(Reusa el setup de venta OK existente: actor alto cargo, empresa con ≥10 mercancía, `gastarMercancia` true.)

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: hook en `EmpresaVentaService`.** Añadir campo `private final CuotaEmpresasService cuota;` y parámetro al constructor: `public EmpresaVentaService(EmpresaRepositorio repo, EventoEconomicoService eventos, CuotaEmpresasService cuota)`. Cambiar la línea del bruto:
```java
        // F5 cuota: el bruto se escala también por la cuota de mercado de la empresa en su rama
        // (compone con el multiplicador del clima económico). Ambos son factores multiplicativos.
        long bruto = Math.round(aVender * Produccion.PRECIO_UNIDAD * eventos.ventaMult() * cuota.factorVentaDe(emp));
```
(`emp` es la `Empresa` que ya tiene `vender`.)

- [ ] **Step 4: Run — EmpresaVentaServiceTest PASS.**

- [ ] **Step 5: `EmpresaService.rankingDeRama(String rama)`** (para el display). Añadir junto a `ranking(int)`:
```java
    /** Ranking de una rama (todas sus empresas) ordenado por prestigio desc, con el prestigio calculado. */
    public List<FilaRanking> rankingDeRama(String rama) {
        return repo.rankingDeRama(rama).stream()
                .map(e -> new FilaRanking(e.nombre(), e.rama(), e.nivel(), e.miembros(), e.bote(),
                        Prestigio.calcular(e.nivel(), e.miembros(), e.bote())))
                .sorted(java.util.Comparator.comparingLong(FilaRanking::prestigio).reversed())
                .toList();
    }
```
(Copia el patrón exacto del `ranking(int)` existente para el mapeo/orden.)

- [ ] **Step 6: i18n (ambos idiomas).** Añadir a `messages_es.properties` (+ EN natural):
```properties
comando.empresa.ranking.opcion.rama=Rama (opcional): ranking y cuota de esa rama
empresa.ranking.rama.titulo=🏭 Ranking de {0}
empresa.ranking.rama.fila={0} **{1}** — {2} % de cuota (prestigio {3})
empresa.ranking.rama.vacio=No hay empresas en esa rama todavía.
empresa.info.cuota=🏪 Cuota de rama: {0} % (venta ×{1})
```
El `{0}` de `empresa.ranking.rama.titulo` es el nombre visible de la rama (de `rama.<minúsculas>`). En `empresa.ranking.rama.fila`: {0}=medalla/posición, {1}=nombre empresa, {2}=cuota %, {3}=prestigio.

- [ ] **Step 7: opción `rama` en `/empresa ranking`.** En `EmpresaComando.definicion()`, al `SubcommandData` de `ranking`, añadir una `OptionData(OptionType.STRING, "rama", <desc ES>, false)` (no requerida) localizada EN, con `.addChoice(Messages.get(Messages.ES, "rama."+r.name().toLowerCase()), <valorRamaGuardado>)` por cada `Ascensos.Rama r`. **El `value` del choice DEBE coincidir con el string guardado en `empresas.rama`** (confírmalo: mira qué pasa `EmpresaRepositorio.fundar` como `rama` y qué produce `/empresa fundar`; usa ese mismo formato, p. ej. `r.name()` si se guarda en mayúsculas). En el método `ranking(evento, locale)`: si viene la opción `rama`, llamar a `empresa.rankingDeRama(valor)`, calcular `total = Σ prestigio`, y pintar cada fila con `empresa.ranking.rama.fila` y `cuota% = round(prestigio*100.0/total)`; troceo con `util/Embeds` si hiciera falta (imita el `ranking` global, que ya usa un StringBuilder). Si la lista está vacía → `empresa.ranking.rama.vacio`. Sin opción, el comportamiento actual (top global) intacto.

- [ ] **Step 8: línea de cuota en `/empresa info`.** En el método `info` de `EmpresaComando`, añadir la línea `empresa.info.cuota` con `cuota.cuotaDe(emp)*100` (redondeado) y `cuota.factorVentaDe(emp)` (formateado a 2 decimales). Inyecta `CuotaEmpresasService` en el constructor de `EmpresaComando`. Imita cómo F5a/F5d/F5-acciones añadieron sus líneas de info (líneas apéndice tras el cuerpo base; no toques los placeholders de `empresa.info.cuerpo`).

- [ ] **Step 9: Main wiring.** Construir `CuotaEmpresasService cuotaService = new CuotaEmpresasService(empresaRepo);` en `iniciarDiscord` (antes de `EmpresaVentaService` y `EmpresaComando`). Pasarlo a `new EmpresaVentaService(empresaRepo, eventosEconomicos, cuotaService)` y al constructor de `EmpresaComando`. Imports.

- [ ] **Step 10: `sh ./mvnw -B clean verify` → BUILD SUCCESS** (630 + nuevos, verde).

- [ ] **Step 11: Commit**
```bash
git add -A
git commit -m "feat(empresas): la cuota de mercado escala la venta + ranking por rama en /empresa"
```

- [ ] **Step 12: REVIEW** — spec-reviewer sobre este commit (base = commit de T1): el bruto compone `ventaMult × factorCuota` sin romper impuesto/no-regresión; la opción `rama` usa valores que casan con `empresas.rama`; la vista por rama calcula bien el %; `/empresa info` intacto salvo la línea; i18n ES+EN. Iterar si hay hallazgos.

---

## Task 3: Documentación + verificación final

**Files:** Modify `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

- [ ] **Step 1: ADR-026 en `docs/decisions.md`** (tras ADR-025; confirmar). Estilo ADR-021..025. Contenido: contexto (varias empresas por rama pero sin competir de verdad; ranking-por-rama pendiente desde F4); decisión (cuota = prestigio propio/Σ prestigio de la rama; factor de venta = clamp(1+0.25×(relativa−1),0.75,1.25), neutro en la cuota justa 1/N, monopolio neutro; hook en la venta que compone con el clima; vista por rama en `/empresa ranking`; sin migración ni job, cuota derivada); consecuencias (nuevos `Cuota`/`CuotaEmpresasService` + `rankingDeRama`; antiinflación por acotado ±25 % centrado en 1.0; fuera: reputación persistente, cuota por ventas recientes, efectos en impuesto/contratación/acciones).

- [ ] **Step 2: `docs/architecture.md`** — viñeta "**Empresas (Fase 5 — cuota de mercado)**" tras la de acciones; nota del hook de venta (compone con el clima) y de la vista por rama del ranking. **Sin cambio de migraciones** (sigue V6–V36); añadir "cuota de mercado" a la lista de temas.

- [ ] **Step 3: `CHANGELOG.md`** — primer bullet bajo `[Sin publicar]/Añadido`: F5 reputación/cuota (la cuota de mercado por rama escala la venta ±25 %; `/empresa ranking rama:<X>` con % de cuota; sin migración).

- [ ] **Step 4: READMEs** — nota de que `/empresa ranking` admite filtro por rama con cuota (no hay comando nuevo). ES y EN.

- [ ] **Step 5: `clean verify` final** — BUILD SUCCESS; anotar recuento de tests.

- [ ] **Step 6: Commit**
```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5 cuota de mercado — ADR-026, architecture, changelog y READMEs"
```

- [ ] **Step 7: Push + aviso.** El controlador hace `git push origin main` y avisa: **desplegar = reiniciar bot** (hook de venta + opción `rama` del ranking; **sin migración ni job**). Cambia la definición de `/empresa` → reiniciar para re-registrar; no requiere `/setup`.

---

## Notas de implementación

- **Composición de factores:** en la venta, `bruto = round(unidades × PRECIO_UNIDAD × ventaMult(clima) × factorCuota)`. Ambos son multiplicativos y neutros en 1.0; el impuesto de venta se sigue calculando sobre el bruto resultante (sin cambios).
- **Cuota derivada:** nada se persiste; `CuotaEmpresasService` relee `rankingDeRama` en cada cálculo (una consulta por venta/`info`; volumen bajo, aceptable, como el resto de lecturas del proyecto).
- **Match por nombre:** en la rama, la empresa propia se localiza por `nombre` (único por rama, `uq_empresa_nombre_rama`). El `nombre` de `Empresa` y el de `EmpresaRanking` vienen de la misma columna.
- **Valores de los choices:** el `value` del choice `rama` debe ser el string EXACTO guardado en `empresas.rama` (confirmar contra `fundar`), o el filtro `WHERE e.rama = ?` no casará.
- **i18n:** toda clave en ES y EN. Formatear el factor a 2 decimales para el display; la cuota % como entero redondeado.
- **Tests de comando:** el display no se testea unitariamente (JDA vivo); la lógica testeable vive en `Cuota`/`CuotaEmpresasService`/el hook de venta.
