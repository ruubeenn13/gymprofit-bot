# Eventos económicos (F5 empresas) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un "clima económico" global (un evento a la vez, duración fija) que modifica ventas, producción, impuesto semanal, faucet del curro y bolsa, anunciado en `💰・economía`.

**Architecture:** Catálogo puro (`EventoEconomico`) + estado persistido en fila única (`EventoEconomicoRepositorio`, V35) + `EventoEconomicoService` (getters de multiplicador con default neutro, `tick`/`lanzar`) + `EventoEconomicoJob` (periódico, anuncia) + comando `/economia` + 5 hooks de una línea en los consumidores. Cada consumidor pregunta al service su multiplicador; sin evento, todo es neutro (1.0).

**Tech Stack:** Java 21, JDBC (HikariCP), Flyway, JDA 5, JUnit 5 + Mockito + Testcontainers.

**Convenciones del repo (obligatorias):** dominio en español; i18n en `messages_es.properties` **y** `messages_en.properties` (nunca hardcodear); todo embed vía `EmbedFactory`; cabecera Javadoc por archivo + Javadoc en públicos no triviales + inline del *porqué*; migración Flyway para el esquema; una clase por comando. Azar inyectable con `BatallaService.Aleatorio` (interfaz funcional `double next()` en `[0,1)`), patrón de `BolsaService`.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw -B clean verify`. Testcontainers se saltan en local (Docker npipe) y solo corren en CI. **Usar siempre `clean verify`** (sin `clean`, el shade revienta con `ZipException`). En PowerShell: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`.

---

## File Structure

**Nuevos:**
- `src/main/resources/db/migration/V35__evento_economico.sql` — tabla de fila única.
- `src/main/java/com/gymprofit/bot/services/EventoEconomico.java` — catálogo puro (enum).
- `src/main/java/com/gymprofit/bot/db/EventoActivo.java` — record del evento activo persistido.
- `src/main/java/com/gymprofit/bot/db/EventoEconomicoRepositorio.java` — JDBC fila única.
- `src/main/java/com/gymprofit/bot/services/EventoEconomicoService.java` — estado + multiplicadores + tick.
- `src/main/java/com/gymprofit/bot/jobs/EventoEconomicoJob.java` — job periódico + anuncios.
- `src/main/java/com/gymprofit/bot/commands/economia/EconomiaComando.java` — `/economia ver|lanzar`.
- Tests: `EventoEconomicoTest`, `EventoEconomicoServiceTest`, `EventoEconomicoRepositorioTest` (Testcontainers), y añadidos a `EmpresaVentaServiceTest`, `TrabajoServiceTest`, `ImpuestoEmpresasServiceTest`, `BolsaServiceTest`.

**Modificados (hooks + wiring):**
- `services/EmpresaVentaService.java` — ctor + `ventaMult` en el bruto.
- `services/TrabajoService.java` — ctor (8º param nullable) + `curroMult` y `produccionMult`.
- `services/ImpuestoEmpresasService.java` — ctor + `impuestoMult` en `evaluar`.
- `services/BolsaService.java` — ctor + `bolsaSesgo` en `tick`.
- `Main.java` — construir el service/repo/job y pasar a los 4 consumidores + registrar el comando.
- `messages_es.properties` / `messages_en.properties` — claves del catálogo y del comando.
- Docs: `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

---

## Task 1: Migración V35 + catálogo puro + record + repositorio

**Files:**
- Create: `src/main/resources/db/migration/V35__evento_economico.sql`
- Create: `src/main/java/com/gymprofit/bot/services/EventoEconomico.java`
- Create: `src/main/java/com/gymprofit/bot/db/EventoActivo.java`
- Create: `src/main/java/com/gymprofit/bot/db/EventoEconomicoRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/services/EventoEconomicoTest.java`
- Test: `src/test/java/com/gymprofit/bot/db/EventoEconomicoRepositorioTest.java`

- [ ] **Step 1: Migración V35**

```sql
-- V35: clima económico global (F5 eventos). Fila ÚNICA (id siempre 1): describe el evento activo.
-- Tabla vacía = sin evento (estado neutro). No hay FKs: es estado global del servidor, no de una entidad.
CREATE TABLE evento_economico (
    id     TINYINT     NOT NULL DEFAULT 1 COMMENT 'Fila única: siempre 1',
    tipo   VARCHAR(32) NOT NULL COMMENT 'EventoEconomico.name()',
    inicio TIMESTAMP   NOT NULL COMMENT 'Cuándo empezó el evento activo',
    fin    TIMESTAMP   NOT NULL COMMENT 'Cuándo caduca (inicio + DURACION)',
    PRIMARY KEY (id)
);
```

- [ ] **Step 2: Test puro del catálogo (falla)**

`EventoEconomicoTest.java`:

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventoEconomicoTest {

    @Test
    @DisplayName("cada palanca no tocada es neutra (1.0 / NINGUNO)")
    void palancasNeutrasPorDefecto() {
        // BOOM_CONSUMO solo toca venta: el resto neutro.
        EventoEconomico e = EventoEconomico.BOOM_CONSUMO;
        assertEquals(1.30, e.ventaMult());
        assertEquals(1.0, e.produccionMult());
        assertEquals(1.0, e.impuestoMult());
        assertEquals(1.0, e.curroMult());
        assertEquals(EventoEconomico.BolsaSesgo.NINGUNO, e.bolsaSesgo());
    }

    @Test
    @DisplayName("catálogo balanceado: 4 buenos y 4 malos")
    void catalogoBalanceado() {
        long buenos = java.util.Arrays.stream(EventoEconomico.values())
                .filter(e -> e.tono() == EventoEconomico.Tono.BUENO).count();
        long malos = java.util.Arrays.stream(EventoEconomico.values())
                .filter(e -> e.tono() == EventoEconomico.Tono.MALO).count();
        assertEquals(4, buenos);
        assertEquals(4, malos);
    }

    @Test
    @DisplayName("aleatorio: 0.0 da el primero, cerca de 1.0 da el último, siempre en rango")
    void aleatorioEnRango() {
        EventoEconomico[] v = EventoEconomico.values();
        assertEquals(v[0], EventoEconomico.aleatorio(0.0));
        assertEquals(v[v.length - 1], EventoEconomico.aleatorio(0.999));
        for (double a = 0; a < 1; a += 0.05) {
            assertNotNull(EventoEconomico.aleatorio(a));
        }
    }
}
```

- [ ] **Step 3: Run test — FAIL (no compila, `EventoEconomico` no existe)**

Run: `sh ./mvnw -B test -Dtest=EventoEconomicoTest` (con JAVA_HOME de JDK 21). Expected: FAIL de compilación.

- [ ] **Step 4: Implementar `EventoEconomico`**

```java
package com.gymprofit.bot.services;

/**
 * Catálogo (en código) de los eventos del clima económico global (F5). Cada entrada declara qué
 * palancas de la economía mueve mientras está activo: multiplicadores de venta, producción, impuesto
 * semanal y faucet del curro (neutro = {@code 1.0}) y el sesgo que impone a la bolsa. El estado activo
 * (cuál y hasta cuándo) vive en BD ({@code evento_economico}); aquí solo van los datos fijos. El nombre
 * y el efecto legibles salen de i18n con clave {@code evento.<name en minúsculas>.*}. Función pura.
 *
 * <p>El catálogo está balanceado a propósito (4 buenos / 4 malos, {@link Tono}): por cada evento que
 * crea coins hay uno que los reduce, para no romper el principio antiinflación del proyecto.
 */
public enum EventoEconomico {
    BOOM_CONSUMO("📈", Tono.BUENO, 1.30, 1.0, 1.0, 1.0, BolsaSesgo.NINGUNO),
    RECESION("📉", Tono.MALO, 0.70, 1.0, 1.0, 1.0, BolsaSesgo.NINGUNO),
    AUGE_INDUSTRIAL("🏭", Tono.BUENO, 1.0, 1.50, 1.0, 1.0, BolsaSesgo.NINGUNO),
    CRISIS_SUMINISTRO("🦠", Tono.MALO, 1.0, 0.50, 1.0, 1.0, BolsaSesgo.NINGUNO),
    SUBIDA_IMPUESTOS("🏛️", Tono.MALO, 1.0, 1.0, 1.50, 1.0, BolsaSesgo.NINGUNO),
    AYUDAS_PUBLICAS("🎁", Tono.BUENO, 1.0, 1.0, 0.50, 1.0, BolsaSesgo.NINGUNO),
    MERCADO_ALCISTA("🐂", Tono.BUENO, 1.0, 1.0, 1.0, 1.0, BolsaSesgo.ALCISTA),
    MERCADO_BAJISTA("🐻", Tono.MALO, 1.0, 1.0, 1.0, 1.0, BolsaSesgo.BAJISTA);

    /** Signo del evento (para el color/emoji del anuncio y para balancear el catálogo). */
    public enum Tono { BUENO, MALO }

    /** Cómo sesga la bolsa: NINGUNO no la toca; ALCISTA/BAJISTA inclinan el 50/50 de crash/boom. */
    public enum BolsaSesgo { NINGUNO, ALCISTA, BAJISTA }

    private final String emoji;
    private final Tono tono;
    private final double ventaMult;
    private final double produccionMult;
    private final double impuestoMult;
    private final double curroMult;
    private final BolsaSesgo bolsaSesgo;

    EventoEconomico(String emoji, Tono tono, double ventaMult, double produccionMult,
                    double impuestoMult, double curroMult, BolsaSesgo bolsaSesgo) {
        this.emoji = emoji;
        this.tono = tono;
        this.ventaMult = ventaMult;
        this.produccionMult = produccionMult;
        this.impuestoMult = impuestoMult;
        this.curroMult = curroMult;
        this.bolsaSesgo = bolsaSesgo;
    }

    public String emoji() { return emoji; }
    public Tono tono() { return tono; }
    public double ventaMult() { return ventaMult; }
    public double produccionMult() { return produccionMult; }
    public double impuestoMult() { return impuestoMult; }
    public double curroMult() { return curroMult; }
    public BolsaSesgo bolsaSesgo() { return bolsaSesgo; }

    /** Clave i18n base del evento ({@code evento.boom_consumo}); el service/comando añade {@code .nombre}/{@code .efecto}. */
    public String claveI18n() { return "evento." + name().toLowerCase(java.util.Locale.ROOT); }

    /** Un evento al azar del catálogo. {@code azar} en {@code [0,1)} (inyectado, testeable). */
    public static EventoEconomico aleatorio(double azar) {
        EventoEconomico[] v = values();
        return v[Math.min(v.length - 1, (int) (azar * v.length))];
    }
}
```

- [ ] **Step 5: Run test — PASS**

Run: `sh ./mvnw -B test -Dtest=EventoEconomicoTest`. Expected: PASS (3 tests).

- [ ] **Step 6: Implementar `EventoActivo` (record)**

```java
package com.gymprofit.bot.db;

import com.gymprofit.bot.services.EventoEconomico;

import java.time.Instant;

/**
 * Evento del clima económico actualmente activo, tal y como está persistido (F5). Es la fila única de
 * {@code evento_economico}: qué evento y su ventana temporal. {@link #caducado(Instant)} decide si ya
 * expiró (lo comprueba el job en cada pasada).
 *
 * @param tipo   evento del catálogo
 * @param inicio cuándo empezó
 * @param fin    cuándo caduca (inicio + duración)
 */
public record EventoActivo(EventoEconomico tipo, Instant inicio, Instant fin) {

    /** ¿Ya expiró a fecha {@code ahora}? (fin inclusive). */
    public boolean caducado(Instant ahora) {
        return !ahora.isBefore(fin);
    }
}
```

- [ ] **Step 7: Test del repositorio (Testcontainers, falla)**

`EventoEconomicoRepositorioTest.java` — mirror del estilo de los tests de repo con Testcontainers del proyecto (arranque de contenedor MySQL + Flyway + `DataSource`; copiar el andamiaje de `EmpresaRepositorioTest` o `BolsaRepositorioTest`). Cuerpo de los casos:

```java
    @Test
    @DisplayName("sin evento fijado, activo() es vacío")
    void vacioAlPrincipio() {
        assertTrue(repo.activo().isEmpty());
    }

    @Test
    @DisplayName("fijar persiste tipo y ventana; activo() lo devuelve")
    void fijarYLeer() {
        Instant ini = Instant.parse("2026-07-27T10:00:00Z");
        Instant fin = ini.plusSeconds(86_400);
        repo.fijar(EventoEconomico.RECESION, ini, fin);
        EventoActivo a = repo.activo().orElseThrow();
        assertEquals(EventoEconomico.RECESION, a.tipo());
        assertEquals(fin, a.fin());
    }

    @Test
    @DisplayName("fijar de nuevo sobrescribe (fila única, no duplica)")
    void fijarUpsert() {
        Instant ini = Instant.parse("2026-07-27T10:00:00Z");
        repo.fijar(EventoEconomico.RECESION, ini, ini.plusSeconds(10));
        repo.fijar(EventoEconomico.BOOM_CONSUMO, ini, ini.plusSeconds(20));
        assertEquals(EventoEconomico.BOOM_CONSUMO, repo.activo().orElseThrow().tipo());
    }

    @Test
    @DisplayName("limpiar deja activo() vacío")
    void limpiar() {
        Instant ini = Instant.parse("2026-07-27T10:00:00Z");
        repo.fijar(EventoEconomico.BOOM_CONSUMO, ini, ini.plusSeconds(10));
        repo.limpiar();
        assertTrue(repo.activo().isEmpty());
    }
```

- [ ] **Step 8: Run test — FAIL (repo no existe; en local se SKIPea Testcontainers)**

Run: `sh ./mvnw -B test -Dtest=EventoEconomicoRepositorioTest`. Expected: FAIL de compilación (o skip en local; validará en CI).

- [ ] **Step 9: Implementar `EventoEconomicoRepositorio`**

Mirror del estilo JDBC de `BolsaRepositorio` (try-with-resources, `DatabaseException`). Fila única `id=1`:

```java
package com.gymprofit.bot.db;

import com.gymprofit.bot.services.EventoEconomico;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Repositorio JDBC del clima económico (F5): la tabla {@code evento_economico} guarda como mucho UNA
 * fila (id=1) con el evento activo y su ventana. Tabla vacía = sin evento (estado neutro). {@code fijar}
 * hace upsert de esa fila; {@code limpiar} la borra.
 */
public final class EventoEconomicoRepositorio {

    private final DataSource dataSource;

    public EventoEconomicoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Evento activo, si lo hay. */
    public Optional<EventoActivo> activo() {
        String sql = "SELECT tipo, inicio, fin FROM evento_economico WHERE id = 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new EventoActivo(
                    EventoEconomico.valueOf(rs.getString("tipo")),
                    rs.getTimestamp("inicio").toInstant(),
                    rs.getTimestamp("fin").toInstant()));
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo el evento económico activo", e);
        }
    }

    /** Fija (upsert de la fila única) el evento activo y su ventana. */
    public void fijar(EventoEconomico tipo, Instant inicio, Instant fin) {
        String sql = "INSERT INTO evento_economico (id, tipo, inicio, fin) VALUES (1, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE tipo = VALUES(tipo), inicio = VALUES(inicio), fin = VALUES(fin)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ps.setTimestamp(2, Timestamp.from(inicio));
            ps.setTimestamp(3, Timestamp.from(fin));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error fijando el evento económico", e);
        }
    }

    /** Borra el evento activo (vuelve al estado neutro). */
    public void limpiar() {
        String sql = "DELETE FROM evento_economico WHERE id = 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error limpiando el evento económico", e);
        }
    }
}
```

- [ ] **Step 10: Run — build entero verde**

Run: `sh ./mvnw -B clean verify`. Expected: BUILD SUCCESS, `EventoEconomicoTest` en verde, `EventoEconomicoRepositorioTest` skip en local.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/db/migration/V35__evento_economico.sql src/main/java/com/gymprofit/bot/services/EventoEconomico.java src/main/java/com/gymprofit/bot/db/EventoActivo.java src/main/java/com/gymprofit/bot/db/EventoEconomicoRepositorio.java src/test/java/com/gymprofit/bot/services/EventoEconomicoTest.java src/test/java/com/gymprofit/bot/db/EventoEconomicoRepositorioTest.java
git commit -m "feat(empresas): V35 evento economico (catalogo puro + repo fila unica)"
```

---

## Task 2: `EventoEconomicoService` (estado, multiplicadores, tick) — con review

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/EventoEconomicoService.java`
- Test: `src/test/java/com/gymprofit/bot/services/EventoEconomicoServiceTest.java`

- [ ] **Step 1: Test del service (falla)**

`EventoEconomicoServiceTest.java`:

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoActivo;
import com.gymprofit.bot.db.EventoEconomicoRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventoEconomicoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-07-27T10:00:00Z");

    private final EventoEconomicoRepositorio repo = mock(EventoEconomicoRepositorio.class);

    /** Service con azar fijo inyectado. */
    private EventoEconomicoService svc(double azar) {
        return new EventoEconomicoService(repo, () -> azar);
    }

    @Test
    @DisplayName("sin evento activo, todos los multiplicadores son neutros")
    void neutroSinEvento() {
        when(repo.activo()).thenReturn(Optional.empty());
        EventoEconomicoService s = svc(0.0);
        assertEquals(1.0, s.ventaMult());
        assertEquals(1.0, s.produccionMult());
        assertEquals(1.0, s.impuestoMult());
        assertEquals(1.0, s.curroMult());
        assertEquals(EventoEconomico.BolsaSesgo.NINGUNO, s.bolsaSesgo());
    }

    @Test
    @DisplayName("con evento activo, el getter devuelve su multiplicador")
    void multiplicadorDelActivo() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.BOOM_CONSUMO, AHORA, AHORA.plusSeconds(10))));
        assertEquals(1.30, svc(0.0).ventaMult());
    }

    @Test
    @DisplayName("tick sin activo y azar < PROB: inicia un evento aleatorio")
    void tickInicia() {
        when(repo.activo()).thenReturn(Optional.empty());
        // primer next() = azar del dado (< PROB), segundo next() = elección del catálogo
        EventoEconomicoService s = new EventoEconomicoService(repo, dadoLuegoCatalogo(0.0, 0.0));
        EventoEconomicoService.ResultadoTick r = s.tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.INICIADO, r.estado());
        verify(repo).fijar(any(), eq(AHORA), eq(AHORA.plus(EventoEconomicoService.DURACION)));
    }

    @Test
    @DisplayName("tick sin activo y azar >= PROB: no pasa nada")
    void tickNada() {
        when(repo.activo()).thenReturn(Optional.empty());
        EventoEconomicoService.ResultadoTick r = svc(0.99).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.NADA, r.estado());
        verify(repo, never()).fijar(any(), any(), any());
    }

    @Test
    @DisplayName("tick con evento caducado: lo finaliza y limpia")
    void tickFinaliza() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.RECESION, AHORA.minusSeconds(100), AHORA.minusSeconds(1))));
        EventoEconomicoService.ResultadoTick r = svc(0.0).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.FINALIZADO, r.estado());
        assertEquals(EventoEconomico.RECESION, r.evento());
        verify(repo).limpiar();
    }

    @Test
    @DisplayName("tick con evento vigente: no lo toca")
    void tickVigente() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.RECESION, AHORA.minusSeconds(10), AHORA.plusSeconds(100))));
        EventoEconomicoService.ResultadoTick r = svc(0.0).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.NADA, r.estado());
        verify(repo, never()).limpiar();
        verify(repo, never()).fijar(any(), any(), any());
    }

    /** Aleatorio que devuelve valores en secuencia (para tick: dado, luego elección de catálogo). */
    private static BatallaService.Aleatorio dadoLuegoCatalogo(double... valores) {
        int[] i = {0};
        return () -> valores[Math.min(i[0]++, valores.length - 1)];
    }
}
```

- [ ] **Step 2: Run test — FAIL (service no existe)**

Run: `sh ./mvnw -B test -Dtest=EventoEconomicoServiceTest`. Expected: FAIL de compilación.

- [ ] **Step 3: Implementar `EventoEconomicoService`**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoActivo;
import com.gymprofit.bot.db.EventoEconomicoRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clima económico global (F5): mantiene el evento activo (persistido en {@link EventoEconomicoRepositorio})
 * y expone su efecto como multiplicadores que consultan los consumidores de la economía (venta,
 * producción, impuesto, curro, bolsa). Sin evento activo todos los getters son <b>neutros</b> (1.0 /
 * NINGUNO), así que un consumidor puede multiplicar siempre sin ramas.
 *
 * <p>{@link #tick(Instant)} lo llama el job periódico: cierra un evento caducado o, si no hay ninguno,
 * a veces ({@link #PROB_EVENTO}) lanza uno aleatorio. El azar se inyecta para los tests (patrón de
 * {@link BolsaService}).
 */
public final class EventoEconomicoService {

    /** Probabilidad de iniciar un evento por pasada del job (cuando no hay ninguno activo). */
    public static final double PROB_EVENTO = 0.04;
    /** Cuánto dura un evento activo. */
    public static final Duration DURACION = Duration.ofHours(24);

    /** Qué hizo el tick (para que el job anuncie inicio/fin). */
    public enum EstadoTick { NADA, INICIADO, FINALIZADO }

    /** Resultado de un tick: qué pasó y con qué evento (null si NADA). */
    public record ResultadoTick(EstadoTick estado, EventoEconomico evento) {
        static ResultadoTick nada() { return new ResultadoTick(EstadoTick.NADA, null); }
    }

    private final EventoEconomicoRepositorio repo;
    private final BatallaService.Aleatorio azar;

    public EventoEconomicoService(EventoEconomicoRepositorio repo, BatallaService.Aleatorio azar) {
        this.repo = repo;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public EventoEconomicoService(EventoEconomicoRepositorio repo) {
        this(repo, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Evento activo, si lo hay. */
    public Optional<EventoActivo> activo() {
        return repo.activo();
    }

    public double ventaMult() { return activo().map(a -> a.tipo().ventaMult()).orElse(1.0); }
    public double produccionMult() { return activo().map(a -> a.tipo().produccionMult()).orElse(1.0); }
    public double impuestoMult() { return activo().map(a -> a.tipo().impuestoMult()).orElse(1.0); }
    public double curroMult() { return activo().map(a -> a.tipo().curroMult()).orElse(1.0); }

    public EventoEconomico.BolsaSesgo bolsaSesgo() {
        return activo().map(a -> a.tipo().bolsaSesgo()).orElse(EventoEconomico.BolsaSesgo.NINGUNO);
    }

    /** Lanza un evento concreto (sobrescribe el activo si lo hubiera). Devuelve el que quedó activo. */
    public EventoEconomico lanzar(EventoEconomico evento, Instant ahora) {
        repo.fijar(evento, ahora, ahora.plus(DURACION));
        return evento;
    }

    /** Lanza uno al azar del catálogo. */
    public EventoEconomico lanzarAleatorio(Instant ahora) {
        return lanzar(EventoEconomico.aleatorio(azar.next()), ahora);
    }

    /**
     * Pasada del job: si el activo caducó lo cierra (FINALIZADO); si no hay activo y el dado cae por
     * debajo de {@link #PROB_EVENTO} lanza uno aleatorio (INICIADO); en otro caso NADA.
     */
    public ResultadoTick tick(Instant ahora) {
        Optional<EventoActivo> act = repo.activo();
        if (act.isPresent()) {
            if (act.get().caducado(ahora)) {
                repo.limpiar();
                return new ResultadoTick(EstadoTick.FINALIZADO, act.get().tipo());
            }
            return ResultadoTick.nada();
        }
        if (azar.next() < PROB_EVENTO) {
            return new ResultadoTick(EstadoTick.INICIADO, lanzarAleatorio(ahora));
        }
        return ResultadoTick.nada();
    }
}
```

- [ ] **Step 4: Run test — PASS**

Run: `sh ./mvnw -B test -Dtest=EventoEconomicoServiceTest`. Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/EventoEconomicoService.java src/test/java/com/gymprofit/bot/services/EventoEconomicoServiceTest.java
git commit -m "feat(empresas): EventoEconomicoService (multiplicadores + tick del clima)"
```

- [ ] **Step 6: REVIEW** — dispatch spec-reviewer sobre este commit (base = commit de T1): activación/caducidad correctas, neutralidad sin evento, azar bien consumido (dado antes que elección). Iterar si hay hallazgos.

---

## Task 3: Job + comando `/economia` + i18n + wiring

**Files:**
- Create: `src/main/java/com/gymprofit/bot/jobs/EventoEconomicoJob.java`
- Create: `src/main/java/com/gymprofit/bot/commands/economia/EconomiaComando.java`
- Modify: `src/main/resources/messages_es.properties` (añadir claves), `src/main/resources/messages_en.properties`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`

- [ ] **Step 1: Claves i18n (ambos idiomas)**

En `messages_es.properties` añadir (y el equivalente EN en `messages_en.properties`):

```properties
# Eventos económicos (F5)
comando.economia.desc=Consulta o lanza eventos del clima económico
comando.economia.ver.desc=Muestra el evento económico activo
comando.economia.lanzar.desc=[Staff] Lanza un evento económico (vacío = aleatorio)
comando.economia.lanzar.opcion.evento=Evento a lanzar (vacío = aleatorio)
economia.ver.titulo=🌐 Clima económico
economia.ver.sin_evento=No hay ningún evento activo ahora mismo. Todo transcurre con normalidad.
economia.ver.activo={0} **{1}**\n{2}\n⏳ Termina <t:{3}:R>
economia.lanzar.ok={0} Evento lanzado: **{1}**
economia.anuncio.inicio.titulo={0} ¡Nuevo evento económico!
economia.anuncio.inicio.cuerpo=**{0}**\n{1}\nDura {2} h.
economia.anuncio.fin.titulo=✅ Fin del evento económico
economia.anuncio.fin.cuerpo=**{0}** ha terminado. La economía vuelve a la normalidad.
# Nombre + efecto de cada evento (un par por entrada del catálogo)
evento.boom_consumo.nombre=Boom de consumo
evento.boom_consumo.efecto=Las ventas de las empresas rinden un 30 % más.
evento.recesion.nombre=Recesión
evento.recesion.efecto=Las ventas de las empresas caen un 30 %.
evento.auge_industrial.nombre=Auge industrial
evento.auge_industrial.efecto=La producción por turno de trabajo sube un 50 %.
evento.crisis_suministro.nombre=Crisis de suministro
evento.crisis_suministro.efecto=La producción por turno de trabajo baja un 50 %.
evento.subida_impuestos.nombre=Subida de impuestos
evento.subida_impuestos.efecto=El impuesto semanal de las empresas sube un 50 %.
evento.ayudas_publicas.nombre=Ayudas públicas
evento.ayudas_publicas.efecto=El impuesto semanal de las empresas baja a la mitad.
evento.mercado_alcista.nombre=Mercado alcista
evento.mercado_alcista.efecto=La bolsa tiende claramente al alza.
evento.mercado_bajista.nombre=Mercado bajista
evento.mercado_bajista.efecto=La bolsa tiende claramente a la baja.
```

EN equivalente (mismas claves, textos en inglés natural, no traducción literal). Ej: `economia.ver.sin_evento=No economic event is active right now. Business as usual.`, `evento.boom_consumo.nombre=Consumer Boom`, etc.

- [ ] **Step 2: Implementar `EconomiaComando`**

Mirror de `EmpresaComando` (subcomandos con `SubcommandData`, dispatch por `evento.getSubcommandName()`, `implements ComandoAutocompletable`, permisos de staff con `DefaultMemberPermissions`). Estructura:

- `definicion()`: `Commands.slash("economia", <desc ES>)` con localización EN; dos subcomandos:
  - `ver` (sin opciones).
  - `lanzar` con una `OptionData(OptionType.STRING, "evento", <desc>, false, true)` (no requerida, autocompletable) y `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))` a nivel de comando.
- `ejecutar(evento)`: `switch (evento.getSubcommandName())`:

```java
        Locale locale = evento.getUserLocale() == DiscordLocale.SPANISH ? Messages.ES : Messages.EN;
        switch (evento.getSubcommandName()) {
            case "ver" -> verActivo(evento, locale);
            case "lanzar" -> lanzar(evento, locale);
            default -> { /* imposible */ }
        }
```

  - `verActivo`: consulta `service.activo()`. Si vacío → embed `EmbedFactory.Tipo.ECONOMIA` con `economia.ver.sin_evento`. Si presente → `economia.ver.activo` con `emoji`, nombre (`Messages.get(locale, tipo.claveI18n()+".nombre")`), efecto (`...+".efecto")`) y `fin.getEpochSecond()` para el `<t:...:R>` de Discord. Público (no efímero, por convención de visibilidad).
  - `lanzar`: lee la opción `evento` (`getOption("evento")`); si viene, `EventoEconomico.valueOf(valor)`, si no `null` → aleatorio. Llama `service.lanzar(e, Instant.now())` o `service.lanzarAleatorio(Instant.now())`. Responde `economia.lanzar.ok`. (El anuncio en canal lo hará el propio lanzamiento vía el job en su próxima pasada NO: el comando anuncia directamente reutilizando el helper del job — ver nota.)
- `autocompletar(evento)`: si la opción enfocada es `evento`, responde con hasta 25 `Command.Choice(Messages.get(ES, tipo.claveI18n()+".nombre"), tipo.name())` de `EventoEconomico.values()`, filtrando por el texto ya escrito (`getFocusedOption().getValue()`).

**Nota de anuncio del lanzamiento manual:** para no duplicar canal-lookup, extraer el envío del anuncio a un método estático reutilizable `EventoEconomicoJob.anunciarInicio(JDA jda, EventoEconomico e)` y llamarlo desde el comando tras `lanzar`. Así el manual también publica en `💰・economía`.

Categoría: `/economia` es público (subcomando `lanzar` protegido por permisos de Discord), así que no sobreescribe `categoria()` (queda `PUBLICO`).

- [ ] **Step 3: Implementar `EventoEconomicoJob`**

Mirror de `BolsaJob` (scheduler fijo `scheduleAtFixedRate`, daemon thread) pero con `Clock` inyectable para el `Instant ahora` del tick (como `ImpuestoEmpresasJob`). Canal por nombre:

```java
package com.gymprofit.bot.jobs;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EventoEconomico;
import com.gymprofit.bot.services.EventoEconomicoService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Motor del clima económico (F5): cada {@value #INTERVALO_MIN} min llama a
 * {@link EventoEconomicoService#tick} y, según el resultado, anuncia el inicio o el fin del evento en
 * el canal {@code 💰・economía} (best-effort: sin canal o sin JDA no revienta). El {@code Clock} da el
 * {@code Instant} del tick (inyectable para tests). El anuncio de inicio se expone estático para que el
 * lanzamiento manual ({@code /economia lanzar}) reutilice el mismo envío.
 */
public final class EventoEconomicoJob {

    private static final Logger log = LoggerFactory.getLogger(EventoEconomicoJob.class);
    private static final long INTERVALO_MIN = 60;
    /** Nombre del canal donde se anuncian los eventos (lo crea /setup en la categoría VIDA). */
    static final String CANAL = "💰・economía";

    private final EventoEconomicoService service;
    private final JDA jda;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-evento-economico");
        t.setDaemon(true);
        return t;
    });

    public EventoEconomicoJob(EventoEconomicoService service, JDA jda, Clock clock) {
        this.service = service;
        this.jda = jda;
        this.clock = clock;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::tick, INTERVALO_MIN, INTERVALO_MIN, TimeUnit.MINUTES);
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    private void tick() {
        try {
            EventoEconomicoService.ResultadoTick r = service.tick(clock.instant());
            switch (r.estado()) {
                case INICIADO -> anunciarInicio(jda, r.evento());
                case FINALIZADO -> anunciarFin(jda, r.evento());
                case NADA -> { /* nada que anunciar */ }
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job del clima económico", e);
        }
    }

    /** Anuncio de inicio (reutilizado por el lanzamiento manual). Best-effort. En ES (post de canal). */
    public static void anunciarInicio(JDA jda, EventoEconomico e) {
        publicar(jda, EmbedFactory.Tipo.ECONOMIA,
                Messages.get(Messages.ES, "economia.anuncio.inicio.titulo", e.emoji()),
                Messages.get(Messages.ES, "economia.anuncio.inicio.cuerpo",
                        Messages.get(Messages.ES, e.claveI18n() + ".nombre"),
                        Messages.get(Messages.ES, e.claveI18n() + ".efecto"),
                        EventoEconomicoService.DURACION.toHours()));
    }

    private static void anunciarFin(JDA jda, EventoEconomico e) {
        publicar(jda, EmbedFactory.Tipo.ECONOMIA,
                Messages.get(Messages.ES, "economia.anuncio.fin.titulo"),
                Messages.get(Messages.ES, "economia.anuncio.fin.cuerpo",
                        Messages.get(Messages.ES, e.claveI18n() + ".nombre")));
    }

    private static void publicar(JDA jda, EmbedFactory.Tipo tipo, String titulo, String cuerpo) {
        if (jda == null) {
            return;
        }
        TextChannel canal = jda.getTextChannelsByName(CANAL, false).stream().findFirst().orElse(null);
        if (canal == null) {
            log.warn("No hay canal {} para anunciar el evento económico", CANAL);
            return;
        }
        var embed = EmbedFactory.base(tipo, Messages.ES, titulo, cuerpo).build();
        canal.sendMessageEmbeds(embed).queue(null,
                err -> log.warn("No se pudo anunciar el evento económico", err));
    }
}
```

- [ ] **Step 4: Wiring en `Main.java`**

Construir el repo/service/job cerca del bloque de la bolsa (`Main.java:625-629`), y registrar el comando. El service debe construirse **antes** que `EmpresaVentaService`, `TrabajoService`, `ImpuestoEmpresasService` y `BolsaService` (Task 4 los recibirá). Añadir:

```java
            // Clima económico global (F5): estado en BD, movido por su job; lo consultan venta, curro,
            // impuesto y bolsa. Se construye antes que esos servicios para inyectárselo.
            EventoEconomicoService eventosEconomicos =
                    new EventoEconomicoService(new EventoEconomicoRepositorio(db.dataSource()));
            comandos.add(new EconomiaComando(eventosEconomicos));
```

y, bajo `jda != null` (donde arrancan los demás jobs):

```java
            new EventoEconomicoJob(eventosEconomicos, jda,
                    Clock.system(ZoneId.of("Europe/Madrid"))).iniciar();
```

Imports: `EventoEconomicoRepositorio`, `EventoEconomicoService`, `EventoEconomicoJob`, `EconomiaComando`. (En T4 se pasa `eventosEconomicos` a los 4 servicios y se ajustan sus llamadas.)

- [ ] **Step 5: Run — build verde**

Run: `sh ./mvnw -B clean verify`. Expected: BUILD SUCCESS. (Comando y job compilan; sin test unitario propio del comando/job — se prueban en smoke, patrón del proyecto para listeners/jobs de Discord.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/jobs/EventoEconomicoJob.java src/main/java/com/gymprofit/bot/commands/economia/EconomiaComando.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): job del clima economico + /economia ver|lanzar + i18n"
```

---

## Task 4: Hooks en los consumidores — con review

Cada subtarea inyecta `EventoEconomicoService` en un servicio y aplica un multiplicador en un punto, con test de proporción (multiplicador ≠ 1 cambia el resultado) y de no-regresión (neutro = igual que hoy). En cada una, actualizar el `Main` para pasar `eventosEconomicos` al constructor.

### Task 4a: Venta (`EmpresaVentaService`)

**Files:** Modify `services/EmpresaVentaService.java`, `Main.java:497`; Test `EmpresaVentaServiceTest.java`.

- [ ] **Step 1: Test de proporción + no-regresión (falla)**

En `EmpresaVentaServiceTest`, el helper que construye el service pasa a recibir un `EventoEconomicoService` mock. Añadir:

```java
    @Test
    @DisplayName("con boom de ventas activo, el bruto rinde x1.30")
    void ventaConBoom() {
        // empresa del actor con 10 unidades, actor alto cargo (reusar el setup existente del test)
        when(eventos.ventaMult()).thenReturn(1.30);
        // ... ejecutar venta de 10 unidades (precio 50) ...
        // bruto neutro = 10*50 = 500; con x1.30 = 650
        EmpresaVentaService.Resultado r = svc().vender(ACTOR_ID, OptionalLong.of(10));
        assertEquals(650, r.bruto());
    }
```

Los tests existentes deben seguir verdes con `when(eventos.ventaMult()).thenReturn(1.0)` en el `@BeforeEach` (no-regresión).

- [ ] **Step 2: Run — FAIL (ctor no acepta eventos / bruto no escala)**

Run: `sh ./mvnw -B test -Dtest=EmpresaVentaServiceTest`. Expected: FAIL.

- [ ] **Step 3: Implementar el hook**

En `EmpresaVentaService`: añadir campo `private final EventoEconomicoService eventos;` y param al constructor. En `vender`, cambiar la línea 59:

```java
        // F5 eventos: el clima económico escala el bruto (boom/recesión de ventas) antes del impuesto.
        long bruto = Math.round(aVender * Produccion.PRECIO_UNIDAD * eventos.ventaMult());
```

- [ ] **Step 4: Actualizar `Main.java:497`**

```java
            EmpresaVentaService empresaVenta = new EmpresaVentaService(empresaRepo, eventosEconomicos);
```

- [ ] **Step 5: Run — PASS**

Run: `sh ./mvnw -B test -Dtest=EmpresaVentaServiceTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/EmpresaVentaService.java src/test/java/com/gymprofit/bot/services/EmpresaVentaServiceTest.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): el clima economico escala el bruto de venta"
```

### Task 4b: Producción + curro (`TrabajoService`)

**Files:** Modify `services/TrabajoService.java`, `Main.java:471`; Test `TrabajoServiceTest.java`.

- [ ] **Step 1: Test (falla)**

`TrabajoService` gana un 8º parámetro `EventoEconomicoService eventos` en el constructor largo (nullable, como `empresas`), y el constructor de 6 args pasa `null` para empresas y eventos. Los tests que usan el ctor de 7 args (F3/F5a) pasan a construir con 8 y un mock. Añadir:

```java
    @Test
    @DisplayName("con auge industrial, el curro produce x1.5 de mercancía")
    void produccionConAuge() {
        when(eventos.produccionMult()).thenReturn(1.5);
        when(eventos.curroMult()).thenReturn(1.0);
        // ... miembro de empresa nivel 1: unidadesPorCurro = 5+1 = 6; con x1.5 = 9 ...
        trabajoService.trabajar(DISCORD_ID, AHORA);
        verify(empresas).sumarMercancia(eq(EMPRESA_ID), eq(9L));
    }

    @Test
    @DisplayName("con recesión de curro (curroMult<1) el jugador cobra proporcionalmente menos")
    void curroConMultiplicador() {
        when(eventos.produccionMult()).thenReturn(1.0);
        when(eventos.curroMult()).thenReturn(0.5);
        // el ingreso al jugador se reduce a la mitad (verificar sobre economia.ingresar o el ResultadoWork)
    }
```

Los tests existentes: con `eventos` mock devolviendo 1.0 para ambos (o, en los que usan el ctor de 6 args, `eventos == null` → helper devuelve 1.0), resultado idéntico a hoy.

- [ ] **Step 2: Run — FAIL**

Run: `sh ./mvnw -B test -Dtest=TrabajoServiceTest`. Expected: FAIL.

- [ ] **Step 3: Implementar los hooks**

Campo + ctor:

```java
    /** Clima económico (F5): multiplica el faucet del curro y la producción. {@code null} = neutro. */
    private final EventoEconomicoService eventos;
```

Ctor largo: añadir `EventoEconomicoService eventos` como último parámetro y `this.eventos = eventos;`. Ctor corto: `this(personajes, economia, usuarios, descanso, carreras, pasivos, null, null);`.

Helpers neutros (mismo patrón que el guard de `empresas`):

```java
    private double curroMult() { return eventos != null ? eventos.curroMult() : 1.0; }
    private double produccionMult() { return eventos != null ? eventos.produccionMult() : 1.0; }
```

Curro (entre la línea 368 y la 373, sobre el `pago` ya ajustado por fatiga, antes del corte de empresa):

```java
        // F5 eventos: el clima económico escala el sueldo final ANTES del corte de empresa, de modo que
        // el 10 % al bote se calcula sobre el ingreso ya modificado (mantiene la relación jugador↔empresa).
        pago = (int) Math.round(pago * curroMult());
        long aCobrar = aplicarEmpresa(discordId, pago);
```

Producción (dentro de `aplicarEmpresa`, línea 401):

```java
            // F5a + F5 eventos: mercancía por turno, escalada por el clima económico (auge/crisis).
            long unidades = Math.round(Produccion.unidadesPorCurro(emp.get().nivel()) * produccionMult());
            empresas.sumarMercancia(emp.get().id(), unidades);
```

- [ ] **Step 4: Actualizar `Main.java:471`**

Añadir `eventosEconomicos` como último argumento del `new TrabajoService(...)`.

- [ ] **Step 5: Run — PASS**

Run: `sh ./mvnw -B test -Dtest=TrabajoServiceTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/TrabajoService.java src/test/java/com/gymprofit/bot/services/TrabajoServiceTest.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): el clima economico escala curro y produccion"
```

### Task 4c: Impuesto semanal (`ImpuestoEmpresasService`)

**Files:** Modify `services/ImpuestoEmpresasService.java`, `Main.java` (construcción del service que recibe `ImpuestoEmpresasJob`); Test `ImpuestoEmpresasServiceTest.java`.

- [ ] **Step 1: Test (falla)**

En `ImpuestoEmpresasServiceTest`, el `svc()` pasa a construir con un mock `eventos` (por defecto `impuestoMult()==1.0` en `@BeforeEach` → los 10 casos actuales siguen verdes). Añadir:

```java
    @Test
    @DisplayName("con subida de impuestos (x1.5), la cuota del impuesto sube (no la del préstamo)")
    void impuestoConSubida() {
        when(eventos.impuestoMult()).thenReturn(1.5);
        // nivel 2: impuesto base = Impuesto.cuota(2) = 5.000; x1.5 = 7.500; sin préstamo → obligación 7.500
        Empresa e = empresa(2, 20_000L, 0);
        assertEquals(7_500L, svc().evaluar(e).cuota());
    }

    @Test
    @DisplayName("el multiplicador NO toca la cuota del préstamo (contrato fijo)")
    void multiplicadorNoTocaPrestamo() {
        when(eventos.impuestoMult()).thenReturn(2.0);
        // impuesto base 5.000 x2 = 10.000 + cuotaPrestamo 6.000 = 16.000
        Empresa e = empresaConPrestamo(2, 50_000L, 0, 10_000L, 6_000L);
        assertEquals(16_000L, svc().evaluar(e).cuota());
    }
```

- [ ] **Step 2: Run — FAIL**

Run: `sh ./mvnw -B test -Dtest=ImpuestoEmpresasServiceTest`. Expected: FAIL.

- [ ] **Step 3: Implementar el hook**

Campo `private final EventoEconomicoService eventos;` + param al constructor. En `evaluar` (línea 31):

```java
        // F5 eventos: el clima económico escala SOLO el impuesto, no la cuota del préstamo (deuda = contrato fijo).
        long cuota = Math.round(Impuesto.cuota(e.nivel()) * eventos.impuestoMult()) + e.cuotaPrestamo();
```

- [ ] **Step 4: Actualizar `Main.java`**

Donde se construye el `ImpuestoEmpresasService` (el que se pasa a `ImpuestoEmpresasJob`, cerca de `Main.java:236`), añadir `eventosEconomicos` al constructor.

- [ ] **Step 5: Run — PASS**

Run: `sh ./mvnw -B test -Dtest=ImpuestoEmpresasServiceTest`. Expected: PASS (12 previos + 2 nuevos).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java src/test/java/com/gymprofit/bot/services/ImpuestoEmpresasServiceTest.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): el clima economico escala el impuesto semanal"
```

### Task 4d: Bolsa (`BolsaService`)

**Files:** Modify `services/BolsaService.java`, `Main.java:626`; Test `BolsaServiceTest.java`.

- [ ] **Step 1: Test (falla)**

`BolsaService` gana un `EventoEconomicoService eventos` en sus dos constructores. En `BolsaServiceTest`, construir con un mock `eventos` (por defecto `bolsaSesgo()==NINGUNO`). Añadir un caso: con `bolsaSesgo()==ALCISTA`, y un azar que en un mercado 50/50 daría crash pero con sesgo 0.80 da boom, el precio sube. (Reusar el `Aleatorio` inyectado del test; el `tick` consume: prob-evento, luego el dado del sesgo.)

```java
    @Test
    @DisplayName("con sesgo ALCISTA el evento de bolsa tiende a boom")
    void tickSesgoAlcista() {
        when(eventos.bolsaSesgo()).thenReturn(EventoEconomico.BolsaSesgo.ALCISTA);
        // azar: 1º < EVENTO_PROB (hay evento), 2º = 0.5 (con sesgo 0.80 → 0.5<0.80 → boom)
        // verificar que actualizarPrecio recibe precio*1.30 (redondeado, suelo 1)
    }
```

Los tests existentes: `bolsaSesgo()==NINGUNO` → comportamiento idéntico (no-regresión).

- [ ] **Step 2: Run — FAIL**

Run: `sh ./mvnw -B test -Dtest=BolsaServiceTest`. Expected: FAIL.

- [ ] **Step 3: Implementar el hook**

Añadir `private final EventoEconomicoService eventos;` y param en ambos constructores (el de test con azar y el de producción). En `tick()`, sustituir el bloque del evento (líneas 136-138):

```java
            if (azar.next() < EVENTO_PROB) {
                // F5 eventos: si el clima impone sesgo, inclina el 50/50 boom/crash de la bolsa.
                double umbralBoom = switch (eventos.bolsaSesgo()) {
                    case ALCISTA -> 0.80;
                    case BAJISTA -> 0.20;
                    case NINGUNO -> 0.50;
                };
                boolean boom = azar.next() < umbralBoom;
                nuevo = Math.max(1, Math.round(p.precio() * (boom ? 1.30 : 0.70)));
            } else {
```

- [ ] **Step 4: Actualizar `Main.java:626`**

```java
            BolsaService bolsaService = new BolsaService(
                    new BolsaRepositorio(db.dataSource()), economiaRepo, usuarios, eventosEconomicos);
```

(Ajustar según cuál de los dos constructores use Main; añadir `eventosEconomicos` como parámetro extra.)

- [ ] **Step 5: Run — PASS**

Run: `sh ./mvnw -B test -Dtest=BolsaServiceTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/BolsaService.java src/test/java/com/gymprofit/bot/services/BolsaServiceTest.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): el clima economico sesga la bolsa (alcista/bajista)"
```

- [ ] **Step 7: REVIEW** — dispatch spec-reviewer sobre los 4 commits de la Task 4 (base = commit de T3): cada hook aplica el multiplicador en el punto correcto, el redondeo no rompe suelos/topes, la no-regresión con neutro es real, y el impuesto NO escala la cuota del préstamo. Iterar si hay hallazgos.

---

## Task 5: Documentación + verificación final

**Files:** Modify `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

- [ ] **Step 1: ADR-024 en `docs/decisions.md`**

Añadir tras ADR-023 (comprobar que es el último). Contenido: estado aceptada/implementada (F5 eventos); contexto (economía estable sin variabilidad); decisión (clima global, un evento a la vez, duración fija, catálogo balanceado 4/4, 5 palancas por multiplicador con default neutro, job auto + `/economia lanzar` manual, anuncio en `💰・economía`, reusa/hace visible el evento de la bolsa); consecuencias (migración V35 fila única; nuevos `EventoEconomico`/`EventoActivo`/repo/service/job/comando; 5 hooks re-tocan la lógica de dinero con review; el interés/antiinflación se mantiene por el catálogo simétrico; fuera: eventos por rama, encadenados, historial).

- [ ] **Step 2: `docs/architecture.md`**

Añadir viñeta "**Empresas (Fase 5 — eventos económicos)**" tras la de F5d; actualizar "Migraciones Flyway V6–V34" → **V6–V35** y añadir "eventos económicos" a la lista de temas; nota del `EventoEconomicoJob` y de los 5 hooks.

- [ ] **Step 3: `CHANGELOG.md`**

Bajo `[Sin publicar] / Añadido`, entrada F5 eventos económicos (`/economia`, migración `V35`).

- [ ] **Step 4: READMEs**

`README.md` y `README.en.md`: añadir `/economia` (ver · lanzar) a la lista de comandos de economía.

- [ ] **Step 5: `clean verify` final**

Run: `sh ./mvnw -B clean verify`. Expected: BUILD SUCCESS, sin fallos. Anotar el recuento de tests (baseline 589 + los nuevos).

- [ ] **Step 6: Commit**

```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5 eventos economicos — ADR-024, architecture, changelog y READMEs"
```

- [ ] **Step 7: Push + aviso de despliegue**

`git push origin main`. Avisar al usuario: **desplegar = reiniciar bot** (V35 + comando `/economia` + `EventoEconomicoJob` + los 5 hooks). Añade un slash command → reiniciar para re-registrarlo; **no** requiere `/setup` (el canal `💰・economía` ya existe). Smoke: ver el `## Despliegue` de la spec.

---

## Notas de implementación

- **Orden de dependencias en Main:** `EventoEconomicoService` se construye antes que `EmpresaVentaService`, `TrabajoService`, `ImpuestoEmpresasService` y `BolsaService`. No hay ciclo (el service solo depende de su repo + azar).
- **Coste en BD:** cada getter de multiplicador hace un `SELECT` de fila única. En los hooks frecuentes (curro) es un extra por turno, indexado por PK; aceptable. Si algún día molesta, cachear con TTL corto (YAGNI ahora).
- **Redondeo:** `Math.round` en venta/curro/producción/impuesto (coherente con `ingresoEmpresa`/`mover`); suelo de precio 1 en la bolsa se mantiene.
- **i18n:** toda clave nueva va en ES **y** EN. El anuncio de canal va en ES (post de canal, patrón de F5b); las respuestas a usuario respetan el locale del que invoca.
- **Tests de comando/job:** no se testean unitariamente (dependen de JDA en vivo), como el resto de comandos/jobs del proyecto; se cubren en el smoke test manual. La lógica testeable vive en el service y en los hooks.
