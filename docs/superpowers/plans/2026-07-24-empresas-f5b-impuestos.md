# Empresas Fase 5b — Impuestos y quiebra — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La empresa paga una **cuota semanal** escalada por nivel, quemada del bote; si no puede pagar entra en **morosidad** y, a las 3 semanas de impago consecutivo, **quiebra** (disolución, conservando los miembros su trabajo).

**Architecture:** Una columna `empresas.impagos` (V32) + una función pura `Impuesto` (números). `ImpuestoEmpresasService` toma la **decisión pura** por empresa (PAGA/MOROSA/QUIEBRA) y la aplica sobre el repo (`gastarDelBote` como gate, `fijarImpagos`, `disolver`). `ImpuestoEmpresasJob` (espejo de `NominaEmpresasJob`) corre los **lunes 02:00 Europe/Madrid**, itera todas las empresas, aplica el efecto y avisa al canal privado de la empresa (F4).

**Tech Stack:** Java 21, JDA 5, JDBC (HikariCP), Flyway, JUnit 5 + Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-24-empresas-f5b-impuestos-design.md`.

**Precondición:** F5a desplegada y funcionando.

**Ajuste de diseño (descubierto al planificar):** los avisos van **solo al canal privado de la empresa**
(F4, `Empresa.canalId()`), no a `#bot-logs`: las empresas no tienen `guild_id` en el esquema, así que el
`#bot-logs` (por-guild) sería ambiguo. Aviso en **ES** (comunidad ES-first), best-effort.

**Convenciones (obligatorias):** dominio español; i18n ES+EN en AMBOS `messages_*.properties`; embeds solo
por `EmbedFactory`; `Messages.get` = MessageFormat posicional; Javadoc cabecera + inline del *porqué*;
migraciones Flyway. Build: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
(SIEMPRE `clean`; no dos builds a la vez; `-Dtest=A,B` entrecomillado). Baseline: **544 tests**.

## File Structure

- **Create** `src/main/resources/db/migration/V32__empresa_impagos.sql`
- **Create** `src/main/java/com/gymprofit/bot/services/Impuesto.java` — función pura (números).
- **Create** `src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java` — decisión + aplicación.
- **Create** `src/main/java/com/gymprofit/bot/jobs/ImpuestoEmpresasJob.java` — job semanal + avisos.
- **Modify** `src/main/java/com/gymprofit/bot/db/Empresa.java` — campo `int impagos`.
- **Modify** `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — `impagos` en lecturas; `fijarImpagos`; `todas()`.
- **Modify** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — línea morosidad en `info`.
- **Modify** `src/main/java/com/gymprofit/bot/Main.java` — wiring del job.
- **Modify** `messages_es.properties`, `messages_en.properties`.
- **Modify** docs (`decisions.md`, `architecture.md`, `CHANGELOG.md`, READMEs).
- **Test** `ImpuestoTest`, `ImpuestoEmpresasServiceTest` (nuevos); `EmpresaRepositorioTest` (impagos, Testcontainers).

---

### Task 1: V32 + `Impuesto` + `impagos` en el repo

**Files:**
- Create: `src/main/resources/db/migration/V32__empresa_impagos.sql`
- Create: `src/main/java/com/gymprofit/bot/services/Impuesto.java`
- Test: `src/test/java/com/gymprofit/bot/services/ImpuestoTest.java`
- Modify: `src/main/java/com/gymprofit/bot/db/Empresa.java`
- Modify: `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java`

- [ ] **Step 1: Migración V32**
```sql
-- V32: contador de impagos consecutivos de la cuota semanal (F5b impuestos). 0 = al dia; a
-- MOROSIDAD_MAX (3) impagos seguidos la empresa quiebra. Se resetea a 0 en cuanto paga una cuota.
ALTER TABLE empresas ADD COLUMN impagos INT NOT NULL DEFAULT 0;
```

- [ ] **Step 2: `Empresa` record gana `int impagos`** (último parámetro, tras `mercancia`; Javadoc: "impagos consecutivos de la cuota semanal (F5b); 0 = al dia"). Rompe los `new Empresa(...)` → Step 8.

- [ ] **Step 3: Test rojo `ImpuestoTest`** (JUnit 5, sin AssertJ):
```java
package com.gymprofit.bot.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class ImpuestoTest {
    @Test void cuotaEsNivelPorFactor() {
        assertEquals(2_500, Impuesto.cuota(1));
        assertEquals(25_000, Impuesto.cuota(10));
    }
    @Test void morosidadMaxEsTres() { assertEquals(3, Impuesto.MOROSIDAD_MAX); }
}
```

- [ ] **Step 4: Corre, FAIL** (`Impuesto` no existe): `.\mvnw.cmd "-Dtest=ImpuestoTest" test`

- [ ] **Step 5: Implementa `Impuesto`**
```java
package com.gymprofit.bot.services;

/**
 * Numeros puros del impuesto semanal de empresas (F5b). Sin estado: la cuota escala con el nivel (coste
 * de existir) y a los {@link #MOROSIDAD_MAX} impagos consecutivos la empresa quiebra. Tunables.
 */
public final class Impuesto {

    /** Cuota semanal = nivel * este factor, en coins (se QUEMA del bote). */
    private static final long FACTOR_CUOTA = 2_500L;
    /** Impagos consecutivos que llevan a la quiebra (disolucion forzosa). */
    public static final int MOROSIDAD_MAX = 3;

    private Impuesto() {
    }

    /** Cuota semanal de una empresa de este nivel. */
    public static long cuota(int nivel) {
        return nivel * FACTOR_CUOTA;
    }
}
```

- [ ] **Step 6: `ImpuestoTest` verde** (2 tests).

- [ ] **Step 7: `impagos` en las lecturas del repo** — añade `impagos` a `SELECT_EMPRESA` y al SELECT de `deMiembro`; en `mapearEmpresa`, `rs.getInt("impagos")` como último arg del `new Empresa(...)`.

- [ ] **Step 8: Arregla todos los `new Empresa(...)`** (`grep -rn "new Empresa(" src`) añadiendo el arg `impagos` (`0` en fixtures/mocks).

- [ ] **Step 9: `fijarImpagos`** (estilo del repo):
```java
/** Fija el contador de impagos de una empresa (F5b): 0 al pagar, o el nuevo valor tras un impago. */
public void fijarImpagos(long empresaId, int impagos) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement("UPDATE empresas SET impagos = ? WHERE id = ?")) {
        ps.setInt(1, impagos);
        ps.setLong(2, empresaId);
        ps.executeUpdate();
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo fijar impagos de la empresa " + empresaId, e);
    }
}
```

- [ ] **Step 10: `todas()`** — lista TODAS las empresas (el job cobra a todas, no solo a las que tienen bote):
```java
/** Todas las empresas (para el cobro del impuesto semanal, F5b: tambien las de bote 0 acumulan impago). */
public List<Empresa> todas() {
    List<Empresa> lista = new ArrayList<>();
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(SELECT_EMPRESA);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            lista.add(mapearEmpresa(rs));
        }
    } catch (SQLException e) {
        throw new DatabaseException("No se pudieron listar las empresas", e);
    }
    return lista;
}
```
(Si `SELECT_EMPRESA` ya termina sin `WHERE`, esto funciona; si lleva algo, quita el filtro.)

- [ ] **Step 11: Tests de repo (Testcontainers)** en `EmpresaRepositorioTest`:
```java
@Test void fijarImpagosYListarTodas() {
    long a = repo.fundar("SALUD", 1L, "Alfa");
    long b = repo.fundar("TECNICA", 2L, "Beta");
    assertEquals(0, repo.porId(a).orElseThrow().impagos()); // default 0
    repo.fijarImpagos(a, 2);
    assertEquals(2, repo.porId(a).orElseThrow().impagos());
    repo.fijarImpagos(a, 0);
    assertEquals(0, repo.porId(a).orElseThrow().impagos());
    assertTrue(repo.todas().stream().map(Empresa::nombre).toList().containsAll(java.util.List.of("Alfa", "Beta")));
}
```

- [ ] **Step 12: `clean verify` + commit** (esperado ~547; ImpuestoTest 2 + repo 1):
```bash
git add src/main/resources/db/migration/V32__empresa_impagos.sql src/main/java/com/gymprofit/bot/services/Impuesto.java src/test/java/com/gymprofit/bot/services/ImpuestoTest.java src/main/java/com/gymprofit/bot/db/Empresa.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java
# + tests con new Empresa( tocados
git commit -m "feat(empresas): V32 impagos + impuesto (numeros y repo)"
```

---

### Task 2: `ImpuestoEmpresasService` (decisión + aplicación) — LLEVA REVIEW

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java`
- Test: `src/test/java/com/gymprofit/bot/services/ImpuestoEmpresasServiceTest.java`

- [ ] **Step 1: Test rojo `ImpuestoEmpresasServiceTest`** (Mockito). Casos de `evaluar(Empresa)` (pura) y `aplicar(Empresa)` (efecto sobre `repo` mockeado):
  - `boteCubreLaCuota -> PAGA`: empresa nivel 2 (cuota 5.000), bote 8.000, impagos 1 → `evaluar` = `Paga(5_000)`. `aplicar` → `verify(repo).gastarDelBote(id, 5_000L)` (true) + `verify(repo).fijarImpagos(id, 0)`; nunca `disolver`.
  - `boteNoCubreYPrimerImpago -> MOROSA(1)`: nivel 2 (cuota 5.000), bote 1.000, impagos 0 → `Morosa(1, 5_000, falta=4_000)`. `aplicar` → `verify(repo).fijarImpagos(id, 1)`; nunca `gastarDelBote` ni `disolver`.
  - `boteNoCubreConDosImpagos -> QUIEBRA`: impagos 2 (→ 3 = MAX), bote 0 → `Quiebra(cuota)`. `aplicar` → `verify(repo).disolver(id)`; nunca `gastarDelBote` ni `fijarImpagos`.
  - `gastarDelBoteFalseCuentaComoImpago`: bote parece cubrir pero `gastarDelBote` devuelve false (carrera) → `aplicar` trata como impago: `verify(repo).fijarImpagos(id, impagos+1)`, no revienta. (Ver nota en el service.)

- [ ] **Step 2: Corre, FAIL.**

- [ ] **Step 3: Implementa `ImpuestoEmpresasService`**
```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;

/**
 * Cobro del impuesto semanal de una empresa (F5b): decide y aplica PAGA / MOROSA / QUIEBRA. La decision
 * ({@link #evaluar}) es pura y testeable; {@link #aplicar} ejecuta el efecto sobre el repo. El dinero se
 * quema con {@link EmpresaRepositorio#gastarDelBote} (gate atomico): si el bote bajo entre evaluar y
 * cobrar, el gasto falla y se cuenta como impago (nunca se quema de mas ni se paga a medias).
 */
public final class ImpuestoEmpresasService {

    /** Tipo de resolucion del cobro. */
    public enum Tipo { PAGA, MOROSA, QUIEBRA }

    /** Resolucion del cobro de una empresa: tipo + cifras para el aviso. */
    public record Resolucion(Tipo tipo, long cuota, int impagos, long falta) {}

    private final EmpresaRepositorio repo;

    public ImpuestoEmpresasService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /** Decide (sin tocar nada) que hacer con una empresa este cobro. */
    public Resolucion evaluar(Empresa e) {
        long cuota = Impuesto.cuota(e.nivel());
        if (e.bote() >= cuota) {
            return new Resolucion(Tipo.PAGA, cuota, 0, 0);
        }
        int nuevos = e.impagos() + 1;
        if (nuevos >= Impuesto.MOROSIDAD_MAX) {
            return new Resolucion(Tipo.QUIEBRA, cuota, nuevos, cuota - e.bote());
        }
        return new Resolucion(Tipo.MOROSA, cuota, nuevos, cuota - e.bote());
    }

    /**
     * Aplica el cobro y devuelve la resolucion REAL (para el aviso). PAGA intenta el gate atomico
     * gastarDelBote: si falla (carrera), degrada a impago. QUIEBRA disuelve. MOROSA solo actualiza el
     * contador.
     */
    public Resolucion aplicar(Empresa e) {
        Resolucion r = evaluar(e);
        switch (r.tipo()) {
            case PAGA -> {
                if (repo.gastarDelBote(e.id(), r.cuota())) {
                    repo.fijarImpagos(e.id(), 0);
                    return r;
                }
                // Carrera: el bote ya no cubre. Recae en impago sobre el estado actual.
                int nuevos = e.impagos() + 1;
                if (nuevos >= Impuesto.MOROSIDAD_MAX) {
                    repo.disolver(e.id());
                    return new Resolucion(Tipo.QUIEBRA, r.cuota(), nuevos, r.cuota());
                }
                repo.fijarImpagos(e.id(), nuevos);
                return new Resolucion(Tipo.MOROSA, r.cuota(), nuevos, r.cuota());
            }
            case MOROSA -> repo.fijarImpagos(e.id(), r.impagos());
            case QUIEBRA -> repo.disolver(e.id());
        }
        return r;
    }
}
```

- [ ] **Step 4: Corre, PASS** (4 tests).

- [ ] **Step 5: `clean verify` + commit** (LLEVA REVIEW después):
```bash
git add src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java src/test/java/com/gymprofit/bot/services/ImpuestoEmpresasServiceTest.java
git commit -m "feat(empresas): decision del impuesto semanal (paga/morosa/quiebra)"
```

---

### Task 3: `ImpuestoEmpresasJob` + avisos + morosidad en `info` + i18n

**Files:**
- Create: `src/main/java/com/gymprofit/bot/jobs/ImpuestoEmpresasJob.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: `ImpuestoEmpresasJob`** — copia la estructura de `NominaEmpresasJob` (scheduler daemon, auto-reprograma, `iniciar()`/`detener()`, todo best-effort). Diferencias: dispara **lunes 02:00 Europe/Madrid** y necesita `JDA` para avisar. Estructura:
```java
public final class ImpuestoEmpresasJob {
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalTime HORA = LocalTime.of(2, 0);
    private static final DayOfWeek DIA = DayOfWeek.MONDAY;
    // campos: EmpresaRepositorio empresas; ImpuestoEmpresasService impuesto; JDA jda; Clock clock; scheduler daemon
    // iniciar() -> programarSiguiente(); tick() -> programarSiguiente(); cobrar();

    /** Espera hasta el proximo lunes 02:00 local (si ya paso, la semana que viene). Package-private para test. */
    static Duration esperaHastaProximoLunes(ZonedDateTime ahora) {
        ZonedDateTime prox = ahora.toLocalDate().atTime(HORA).atZone(ahora.getZone())
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(DIA));
        if (!prox.isAfter(ahora)) {
            prox = prox.plusWeeks(1);
        }
        return Duration.between(ahora, prox);
    }

    /** Cobra la cuota a todas las empresas. Publico para invocarlo en manual/tests (como NominaEmpresasJob.repartir). */
    public void cobrar() {
        List<Empresa> todas;
        try { todas = empresas.todas(); }
        catch (RuntimeException e) { log.error("No se pudo listar empresas; impuesto abortado", e); return; }
        for (Empresa e : todas) {
            try {
                ImpuestoEmpresasService.Resolucion r = impuesto.aplicar(e);
                avisar(e, r); // best-effort, solo MOROSA/QUIEBRA
            } catch (RuntimeException ex) {
                log.warn("Fallo cobrando el impuesto de la empresa {}", e.id(), ex);
            }
        }
    }

    /** Aviso al canal privado de la empresa (F4, si existe). PAGA no avisa (anti-spam). Best-effort. */
    private void avisar(Empresa e, ImpuestoEmpresasService.Resolucion r) {
        if (jda == null || e.canalId() == null || r.tipo() == ImpuestoEmpresasService.Tipo.PAGA) {
            return;
        }
        TextChannel canal = jda.getTextChannelById(e.canalId());
        if (canal == null) { return; }
        String clave = r.tipo() == ImpuestoEmpresasService.Tipo.QUIEBRA
                ? "empresa.impuesto.quiebra" : "empresa.impuesto.morosa";
        // Aviso en ES (comunidad ES-first); serio (dinero/gobernanza). Tipo de embed real (AVISO/serio).
        var embed = EmbedFactory.base(EmbedFactory.Tipo.<AVISO_REAL>, Messages.ES,
                Messages.get(Messages.ES, "empresa.impuesto.titulo"),
                Messages.get(Messages.ES, clave, r.cuota(), r.impagos(), Impuesto.MOROSIDAD_MAX, r.falta()))
                .build();
        canal.sendMessageEmbeds(embed).queue(null, err ->
                log.warn("No se pudo avisar del impuesto a la empresa {}", e.id(), err));
    }
}
```
**Ajusta:** el `EmbedFactory.Tipo` a uno real para avisos serios (mira los que usa la moderación/tickets, p. ej. un tipo de aviso/serio; NO inventes). Copia imports/estructura exactos de `NominaEmpresasJob` (scheduler daemon con nombre `gymprobot-impuesto-empresas`) y el patrón de envío de `EjercicioDiaJob` (`jda.getTextChannelById` + `sendMessageEmbeds(...).queue(null, err->log)`).

- [ ] **Step 2: Test del cálculo de fecha** `src/test/java/com/gymprofit/bot/jobs/ImpuestoEmpresasJobTest.java`:
```java
@Test void esperaApuntaAlProximoLunes0200() {
    // un miercoles cualquiera a las 10:00 -> siguiente lunes 02:00; comprueba dia y hora del resultado
    ZonedDateTime miercoles = ZonedDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneId.of("Europe/Madrid"));
    Duration d = ImpuestoEmpresasJob.esperaHastaProximoLunes(miercoles);
    ZonedDateTime destino = miercoles.plus(d);
    assertEquals(DayOfWeek.MONDAY, destino.getDayOfWeek());
    assertEquals(LocalTime.of(2, 0), destino.toLocalTime());
}
@Test void siEsLunesAntesDeLas2VaHoy_yDespuesVaLaProximaSemana() {
    // lunes 01:00 -> hoy 02:00 (dentro de 1h); lunes 03:00 -> lunes que viene
    // (dos asserts con dos instantes)
}
```
(Sigue el patrón de `esperaHastaLasTres` de `NominaEmpresasJob` si tiene test; si no, escribe estos.)

- [ ] **Step 3: Wiring en `Main`** — tras conectar JDA y crear `empresaRepo`, junto a `NominaEmpresasJob`:
```java
new ImpuestoEmpresasJob(empresaRepo, new ImpuestoEmpresasService(empresaRepo), jda,
        Clock.system(ZoneId.of("Europe/Madrid"))).iniciar();
```
(Comprueba que `jda` puede ser null en el arranque degradado sin token: si `NominaEmpresasJob` se crea igualmente, sigue el mismo criterio; el job tolera `jda == null` en `avisar`.)

- [ ] **Step 4: Línea de morosidad en `/empresa info`** — en el método `info`, tras montar el cuerpo, si `e.impagos() > 0` añade (o incluye en la clave única `empresa.info.cuerpo`) una línea `⚠️ Morosa: {impagos}/{MOROSIDAD_MAX}`. **Mira cómo `info` arma la descripción** (clave única con `{0..9}`): lo más limpio aquí es **concatenar una línea extra condicional** al `String` de descripción tras el `Messages.get(...)`, para no renumerar otra vez los placeholders:
```java
String desc = Messages.get(locale, "empresa.info.cuerpo", /* ...args... */);
if (e.impagos() > 0) {
    desc += "\n" + Messages.get(locale, "empresa.info.morosa", e.impagos(), Impuesto.MOROSIDAD_MAX);
}
```
(Si `info` construye el embed directamente sin variable intermedia, extrae la descripción a una variable primero.)

- [ ] **Step 5: i18n ES**
```properties
empresa.impuesto.titulo=🏛️ Impuesto de empresa
empresa.impuesto.morosa=No se pudo cobrar la cuota semanal de **{0}** 🪙: el bote no llega (faltan **{3}** 🪙). Impagos: **{1}/{2}**. Vended mercancía o llenad el bote antes del próximo lunes o la empresa quebrará.
empresa.impuesto.quiebra=La empresa ha **quebrado**: {1} semanas sin pagar la cuota de **{0}** 🪙. Se disuelve. Los miembros conservan su trabajo.
empresa.info.morosa=⚠️ Morosa: {0}/{1}
```

- [ ] **Step 6: i18n EN**
```properties
empresa.impuesto.titulo=🏛️ Company tax
empresa.impuesto.morosa=Couldn't collect the weekly fee of **{0}** 🪙: the pot falls short (**{3}** 🪙 missing). Missed payments: **{1}/{2}**. Sell goods or fill the pot before next Monday or the company goes bankrupt.
empresa.impuesto.quiebra=The company has gone **bankrupt**: {1} weeks without paying the **{0}** 🪙 fee. It is dissolved. Members keep their job.
empresa.info.morosa=⚠️ Delinquent: {0}/{1}
```
(Placeholders: {0} cuota, {1} impagos, {2} MOROSIDAD_MAX, {3} falta.)

- [ ] **Step 7: `clean verify`** — BUILD SUCCESS, ~549 tests. Paridad ES/EN a mano.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/gymprofit/bot/jobs/ImpuestoEmpresasJob.java src/test/java/com/gymprofit/bot/jobs/ImpuestoEmpresasJobTest.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/java/com/gymprofit/bot/Main.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(empresas): job del impuesto semanal + avisos y morosidad en info"
```

---

### Task 4: Documentación + verify final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: ADR-021** (comprueba que el último es 020):
```markdown
## ADR-021 — impuestos y quiebra de empresas

**Estado:** aceptada e implementada (Fase 5b).

**Contexto.** Tras F5a la empresa tenía fuentes de ingresos pero ningún coste recurrente que las
equilibrara ni riesgo real de desaparecer.

**Decisión.** Una **cuota semanal** `nivel × 2.500` se **quema** del bote cada lunes 02:00 Europe/Madrid
(antes de la nómina). Si el bote no cubre la cuota, la empresa queda **morosa** (contador `impagos`); a
los **3** impagos consecutivos **quiebra** (disolución forzosa, los miembros conservan su trabajo). Los
avisos van al canal privado de la empresa (F4). La decisión vive en `ImpuestoEmpresasService` (pura,
testeable); el `ImpuestoEmpresasJob` la ejecuta y avisa.

**Consecuencias.** Migración V32 (`empresas.impagos`). Nuevo `ImpuestoEmpresasJob` (semanal). La cuota es
un sumidero recurrente que escala con el nivel. Impuesto progresivo, rescate y deuda acumulada quedan
fuera.
```

- [ ] **Step 2: `docs/architecture.md`** — viñeta F5b tras F5a; migraciones a **V6–V32**; añade `ImpuestoEmpresasJob` a la fila de `jobs/`.

- [ ] **Step 3: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`, encima de F5a:
```markdown
- **Empresas (Fase 5b)**: impuesto semanal. Cada empresa paga una cuota `nivel × 2.500` quemada del bote
  los lunes; si no puede, se vuelve morosa y a las 3 semanas quiebra (los miembros conservan su trabajo).
  Migración `V32`.
```

- [ ] **Step 4: READMEs** — nota breve de la cuota semanal en la sección de empresas (no hay comando nuevo).

- [ ] **Step 5: `clean verify` final + commit**
```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5b impuestos — ADR-021, architecture, changelog y READMEs"
```

---

## Despliegue (tras cerrar las 4 tasks)

**Reiniciar bot** (V32 + `ImpuestoEmpresasJob`). **No** requiere `/setup` ni añade comandos. **Smoke
test** (el cobro es semanal): la decisión está cubierta por tests unitarios; para verlo en vivo sin
esperar al lunes, invocar `cobrar()` manualmente (es público, como `NominaEmpresasJob.repartir()`) o bajar
temporalmente la constante de hora/día. Comprobar: empresa con bote < cuota → aviso de morosidad + `impagos`
sube en `/empresa info`; con bote suficiente → baja el bote la cuota, `impagos` a 0; 3 impagos → disolución
con los miembros conservando su trabajo.

## Notas de riesgo (para el review de la Task 2)

- **Gate `gastarDelBote`**: el cobro nunca quema de más ni paga a medias; si el bote bajó entre evaluar y
  cobrar, se degrada a impago.
- **Quiebra conserva el trabajo**: `repo.disolver` es un `DELETE FROM empresas` (cascada de FK saca a los
  miembros) que **no toca `personajes.trabajo`** — verificado. No usar `fijarTrabajo(null)`.
- **`impagos` consecutivos**: se resetea a 0 en cuanto se paga una cuota; solo cuentan los seguidos.
