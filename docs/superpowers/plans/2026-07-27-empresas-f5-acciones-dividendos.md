# Acciones y dividendos de empresa (F5) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cualquier jugador compra participaciones (pool fijo de 100) de una empresa a un precio derivado del prestigio; el capital va al bote y cobra dividendos semanales del bote según su parte; puede revender al pool.

**Architecture:** Función pura (`Accion`) + holdings persistidos (`EmpresaAccionRepositorio`, V36) + `AccionEmpresasService` (comprar/vender/cartera/repartirDividendos, patrón atómico validar→mover→registrar) + `DividendoEmpresasJob` (semanal) + `/acciones` (comprar·vender·ver·cartera). Todo redistribuye del/al bote (antiinflación-neutral). La quiebra (FK ON DELETE CASCADE) funde el capital de los inversores.

**Tech Stack:** Java 21, JDBC (HikariCP), Flyway, JDA 5, JUnit 5 + Mockito + Testcontainers.

**Convenciones del repo (obligatorias):** dominio en español; i18n en `messages_es.properties` **y** `messages_en.properties` (nunca hardcodear); todo embed vía `EmbedFactory`; cabecera Javadoc por archivo + Javadoc en públicos no triviales + inline del *porqué*; migración Flyway para el esquema; una clase por comando; secrets solo por env.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw -B clean verify`. Testcontainers se saltan en local (Docker npipe) y solo corren en CI. **Usar siempre `clean verify`** (sin `clean`, el shade revienta con `ZipException`). PowerShell: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`.

**Firmas reales del código (verificadas, úsalas tal cual):**
- `EconomiaRepositorio`: `void ingresar(long discordId, long cantidad, String motivo)`, `boolean gastar(long discordId, long cantidad, String motivo)` (gate atómico, false si no hay saldo).
- `EmpresaRepositorio`: `Optional<Empresa> porId(long id)`, `List<MiembroEmpresa> miembros(long empresaId)` (el nº de miembros = `.size()`), `void incrementarBote(long empresaId, long cantidad)`, `boolean gastarDelBote(long empresaId, long cantidad)` (gate atómico `UPDATE … WHERE bote>=?`, false si no cubre), `List<Empresa> todas()`.
- `Prestigio.calcular(int nivel, int numMiembros, long bote)` → `long`.
- `Empresa` record: `(long id, String rama, long duenoId, String nombre, int nivel, long bote, Instant creada, Long canalId, long mercancia, int impagos, boolean contratando, long deuda, long cuotaPrestamo)`.
- `UsuarioDiscordRepositorio.obtenerOCrear(long discordId)` (llamar antes de `gastar`/`ingresar`, como hace `BolsaService`).

---

## File Structure

**Nuevos:**
- `src/main/resources/db/migration/V36__empresa_acciones.sql`
- `src/main/java/com/gymprofit/bot/services/Accion.java` — pura (POOL, FRACCION, precio, dividendo).
- `src/main/java/com/gymprofit/bot/db/EmpresaAccionRepositorio.java` — holdings JDBC + records `Accionista`/`PosicionAccion`.
- `src/main/java/com/gymprofit/bot/services/AccionEmpresasService.java` — comprar/vender/cartera/repartirDividendos.
- `src/main/java/com/gymprofit/bot/jobs/DividendoEmpresasJob.java` — job semanal.
- `src/main/java/com/gymprofit/bot/commands/economia/AccionesComando.java` — `/acciones`.
- Tests: `AccionTest`, `AccionEmpresasServiceTest`, `EmpresaAccionRepositorioTest` (Testcontainers).

**Modificados:**
- `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — línea de acciones en `info`.
- `src/main/java/com/gymprofit/bot/Main.java` — construir repo/service/job + registrar comando.
- `messages_es.properties` / `messages_en.properties` — claves de `/acciones` + avisos + info.
- Docs: `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

---

## Task 1: Migración V36 + `Accion` (pura) + repositorio

**Files:**
- Create: `src/main/resources/db/migration/V36__empresa_acciones.sql`
- Create: `src/main/java/com/gymprofit/bot/services/Accion.java`
- Create: `src/main/java/com/gymprofit/bot/db/EmpresaAccionRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/services/AccionTest.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaAccionRepositorioTest.java`

- [ ] **Step 1: Migración V36**

```sql
-- V36: participaciones de empresa (F5 acciones/dividendos). Holdings: cuántas participaciones (1..100)
-- tiene cada jugador de cada empresa. La fila se borra al llegar a 0. FK a empresas ON DELETE CASCADE:
-- al disolver (quiebra F5b o manual) las participaciones desaparecen (los inversores pierden el capital).
CREATE TABLE empresa_acciones (
    empresa_id  BIGINT NOT NULL,
    discord_id  BIGINT NOT NULL,
    cantidad    INT    NOT NULL COMMENT 'Participaciones (1..100); la fila se borra al llegar a 0',
    PRIMARY KEY (empresa_id, discord_id),
    CONSTRAINT fk_acc_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id) ON DELETE CASCADE,
    CONSTRAINT fk_acc_usuario FOREIGN KEY (discord_id) REFERENCES usuarios_discord(discord_id) ON DELETE CASCADE
);
```
Verify the referenced tables/columns exist: `empresas(id)` and `usuarios_discord(discord_id)` (other FKs in the codebase reference `usuarios_discord(discord_id)` — confirm the exact column name in an earlier migration like V27, and match it).

- [ ] **Step 2: `AccionTest` (write first, FAIL)**

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccionTest {

    @Test
    @DisplayName("precio = prestigio / 100, con suelo 1")
    void precio() {
        assertEquals(150, Accion.precioParticipacion(15_000)); // 15000/100
        assertEquals(1, Accion.precioParticipacion(0));         // suelo
        assertEquals(1, Accion.precioParticipacion(99));        // 99/100=0 → suelo 1
    }

    @Test
    @DisplayName("dividendo = floor(pot * participaciones / 100)")
    void dividendo() {
        assertEquals(250, Accion.dividendoDe(1_000, 25)); // 1000*25/100
        assertEquals(0, Accion.dividendoDe(1_000, 0));
        assertEquals(1_000, Accion.dividendoDe(1_000, 100)); // pool completo
        assertEquals(9, Accion.dividendoDe(99, 10));        // floor(990/100)=9
    }
}
```

- [ ] **Step 3: Run — FAIL** (`sh ./mvnw -B test -Dtest=AccionTest`, `Accion` no existe).

- [ ] **Step 4: `Accion.java`**

```java
package com.gymprofit.bot.services;

/**
 * Números puros de las participaciones de empresa (F5 acciones/dividendos). Una empresa tiene un pool
 * fijo de {@link #POOL} participaciones (= porcentaje directo); el precio de cada una deriva del prestigio
 * de la empresa ({@link #precioParticipacion}) y el dividendo de cada accionista es su fracción del pool
 * sobre el pot semanal ({@link #dividendoDe}). Sin estado ni dependencias: testeable.
 */
public final class Accion {

    private Accion() {}

    /** Participaciones totales de cualquier empresa (= porcentaje: 1 participación = 1 %). */
    public static final int POOL = 100;

    /** Fracción del bote que forma el pot de dividendos cada semana (sumidero modesto: ver antiinflación). */
    public static final double FRACCION_DIVIDENDO = 0.05;

    /** Precio de una participación: el valor (prestigio) repartido entre el pool, con suelo 1. */
    public static long precioParticipacion(long prestigio) {
        return Math.max(1, prestigio / POOL);
    }

    /** Dividendo de un accionista: su fracción del pool sobre el pot (redondeo a la baja). */
    public static long dividendoDe(long pot, int participaciones) {
        return (long) Math.floor((double) pot * participaciones / POOL);
    }
}
```

- [ ] **Step 5: Run — PASS** (`sh ./mvnw -B test -Dtest=AccionTest`).

- [ ] **Step 6: `EmpresaAccionRepositorio.java`** (JDBC, mirror `BolsaRepositorio` style — try-with-resources, `DatabaseException`)

```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de las participaciones de empresa (F5). Tabla {@code empresa_acciones}: cuántas
 * participaciones tiene cada jugador de cada empresa. {@code fijar} hace upsert (borra la fila al llegar a
 * 0). {@code vendidasDe} suma el pool colocado de una empresa; {@code accionistas} y {@code carteraDe} son
 * las dos vistas que necesitan el reparto de dividendos y el comando de cartera.
 */
public final class EmpresaAccionRepositorio {

    /** Un accionista de una empresa (para el reparto de dividendos). */
    public record Accionista(long discordId, int cantidad) {}

    /** Una posición del jugador: participaciones que tiene en una empresa (para la cartera). */
    public record PosicionAccion(long empresaId, int cantidad) {}

    private final DataSource dataSource;

    public EmpresaAccionRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Participaciones que tiene un jugador en una empresa (0 si ninguna). */
    public int participacionesDe(long empresaId, long discordId) {
        String sql = "SELECT cantidad FROM empresa_acciones WHERE empresa_id = ? AND discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cantidad") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo participaciones de " + discordId, e);
        }
    }

    /** Participaciones vendidas (colocadas) de una empresa: SUM del pool. */
    public int vendidasDe(long empresaId) {
        String sql = "SELECT COALESCE(SUM(cantidad), 0) AS total FROM empresa_acciones WHERE empresa_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo participaciones vendidas de " + empresaId, e);
        }
    }

    /** Accionistas de una empresa (para repartir dividendos). */
    public List<Accionista> accionistas(long empresaId) {
        String sql = "SELECT discord_id, cantidad FROM empresa_acciones WHERE empresa_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Accionista> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new Accionista(rs.getLong("discord_id"), rs.getInt("cantidad")));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo accionistas de " + empresaId, e);
        }
    }

    /** Cartera de participaciones del jugador (empresas donde tiene algo). */
    public List<PosicionAccion> carteraDe(long discordId) {
        String sql = "SELECT empresa_id, cantidad FROM empresa_acciones WHERE discord_id = ? ORDER BY empresa_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PosicionAccion> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new PosicionAccion(rs.getLong("empresa_id"), rs.getInt("cantidad")));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la cartera de acciones de " + discordId, e);
        }
    }

    /** Fija las participaciones (upsert); borra la fila si {@code cantidad <= 0}. */
    public void fijar(long empresaId, long discordId, int cantidad) {
        String sql = cantidad <= 0
                ? "DELETE FROM empresa_acciones WHERE empresa_id = ? AND discord_id = ?"
                : "INSERT INTO empresa_acciones (empresa_id, discord_id, cantidad) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE cantidad = VALUES(cantidad)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            if (cantidad > 0) {
                ps.setInt(3, cantidad);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error fijando participaciones de " + discordId, e);
        }
    }
}
```

- [ ] **Step 7: `EmpresaAccionRepositorioTest` (Testcontainers)** — mirror the container scaffolding of `EmpresaRepositorioTest.java` exactly (needs an empresa row + a usuarios_discord row to satisfy the FKs before inserting participaciones — check how EmpresaRepositorioTest seeds those). Cases:
```java
    @Test @DisplayName("sin filas, participaciones y vendidas son 0")
    void vacio() {
        assertEquals(0, repo.participacionesDe(EMPRESA_ID, USER_ID));
        assertEquals(0, repo.vendidasDe(EMPRESA_ID));
    }

    @Test @DisplayName("fijar upsert + vendidasDe suma el pool")
    void fijarYSuma() {
        repo.fijar(EMPRESA_ID, USER_ID, 30);
        repo.fijar(EMPRESA_ID, OTHER_ID, 20);
        assertEquals(30, repo.participacionesDe(EMPRESA_ID, USER_ID));
        assertEquals(50, repo.vendidasDe(EMPRESA_ID));
        repo.fijar(EMPRESA_ID, USER_ID, 40); // upsert, no duplica
        assertEquals(60, repo.vendidasDe(EMPRESA_ID));
    }

    @Test @DisplayName("fijar a 0 borra la fila")
    void borra() {
        repo.fijar(EMPRESA_ID, USER_ID, 10);
        repo.fijar(EMPRESA_ID, USER_ID, 0);
        assertEquals(0, repo.participacionesDe(EMPRESA_ID, USER_ID));
    }

    @Test @DisplayName("borrar la empresa (CASCADE) borra las participaciones")
    void cascade() {
        repo.fijar(EMPRESA_ID, USER_ID, 10);
        // borrar la fila de empresas (usar el EmpresaRepositorio.disolver o un DELETE directo del test)
        empresaRepo.disolver(EMPRESA_ID);
        assertEquals(0, repo.vendidasDe(EMPRESA_ID));
    }
```
Adapt seeding/ids to the existing test's helpers. `disolver` exists on `EmpresaRepositorio`; if the test doesn't already build one, do a direct `DELETE FROM empresas WHERE id=?` via the datasource to prove CASCADE.

- [ ] **Step 8: Run — build verde** (`sh ./mvnw -B clean verify`; repo test skipped locally, `AccionTest` green).

- [ ] **Step 9: Commit**
```bash
git add src/main/resources/db/migration/V36__empresa_acciones.sql src/main/java/com/gymprofit/bot/services/Accion.java src/main/java/com/gymprofit/bot/db/EmpresaAccionRepositorio.java src/test/java/com/gymprofit/bot/services/AccionTest.java src/test/java/com/gymprofit/bot/db/EmpresaAccionRepositorioTest.java
git commit -m "feat(empresas): V36 participaciones de empresa (Accion pura + repo)"
```

---

## Task 2: `AccionEmpresasService` (comprar/vender/cartera) + `/acciones` + info — con review

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/AccionEmpresasService.java`
- Create: `src/main/java/com/gymprofit/bot/commands/economia/AccionesComando.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` (línea de acciones en `info`)
- Modify: `messages_es.properties`, `messages_en.properties`, `Main.java`
- Test: `src/test/java/com/gymprofit/bot/services/AccionEmpresasServiceTest.java`

- [ ] **Step 1: `AccionEmpresasServiceTest` (write first, FAIL)**

Mock `EmpresaRepositorio repo`, `EconomiaRepositorio economia`, `EmpresaAccionRepositorio accRepo`, `UsuarioDiscordRepositorio usuarios`. Helper to build an `Empresa` (mirror how other tests build the record — 13 fields; use nivel/miembros/bote that give a known prestigio). Precio de test: para `Empresa` nivel 1, 0 miembros extra, bote conocido, `Prestigio.calcular(1,1,bote)`; para simplificar, stub `repo.miembros(EMP)` con una lista de tamaño fijo. Cases:
```java
    @Test @DisplayName("comprar OK: cobra al jugador, sube el bote, fija participaciones")
    void comprarOk() {
        // prestigio tal que precio = 100 (ej. nivel/miembros/bote elegidos); comprar 10 → coste 1000
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(/*nivel*/1, /*bote*/ ...)));
        when(repo.miembros(EMP)).thenReturn(List.of(/* 1 miembro */));
        when(accRepo.vendidasDe(EMP)).thenReturn(0);
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(0);
        when(economia.gastar(eq(ACTOR), eq(1_000L), anyString())).thenReturn(true);

        AccionEmpresasService.ResultadoCompra r = svc().comprar(ACTOR, EMP, 10);
        assertEquals(AccionEmpresasService.EstadoCompra.OK, r.estado());
        verify(economia).gastar(ACTOR, 1_000L, "acciones_comprar:" + EMP);
        verify(repo).incrementarBote(EMP, 1_000L);
        verify(accRepo).fijar(EMP, ACTOR, 10);
    }

    @Test @DisplayName("comprar más que el pool libre → SIN_PARTICIPACIONES_LIBRES")
    void comprarTope() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(...)));
        when(accRepo.vendidasDe(EMP)).thenReturn(95);
        AccionEmpresasService.ResultadoCompra r = svc().comprar(ACTOR, EMP, 10); // 95+10 > 100
        assertEquals(AccionEmpresasService.EstadoCompra.SIN_PARTICIPACIONES_LIBRES, r.estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test @DisplayName("comprar sin saldo → SIN_SALDO, no toca el bote")
    void comprarSinSaldo() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(...)));
        when(accRepo.vendidasDe(EMP)).thenReturn(0);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        AccionEmpresasService.ResultadoCompra r = svc().comprar(ACTOR, EMP, 10);
        assertEquals(AccionEmpresasService.EstadoCompra.SIN_SALDO, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(accRepo, never()).fijar(anyLong(), anyLong(), anyInt());
    }

    @Test @DisplayName("comprar cantidad<=0 o empresa inexistente → error sin efectos")
    void comprarValidaciones() {
        // CANTIDAD_INVALIDA (0) y NO_EXISTE (porId vacío)
    }

    @Test @DisplayName("vender OK: gate del bote, ingresa al jugador, baja participaciones")
    void venderOk() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(...))); // precio 100
        when(repo.miembros(EMP)).thenReturn(List.of(/*1*/));
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(10);
        when(repo.gastarDelBote(EMP, 500L)).thenReturn(true); // vender 5 * 100
        AccionEmpresasService.ResultadoVenta r = svc().vender(ACTOR, EMP, 5);
        assertEquals(AccionEmpresasService.EstadoVenta.OK, r.estado());
        verify(repo).gastarDelBote(EMP, 500L);
        verify(economia).ingresar(ACTOR, 500L, "acciones_vender:" + EMP);
        verify(accRepo).fijar(EMP, ACTOR, 5); // 10-5
    }

    @Test @DisplayName("vender más de lo que tienes → SIN_PARTICIPACIONES")
    void venderSinParticipaciones() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(...)));
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(3);
        AccionEmpresasService.ResultadoVenta r = svc().vender(ACTOR, EMP, 5);
        assertEquals(AccionEmpresasService.EstadoVenta.SIN_PARTICIPACIONES, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
    }

    @Test @DisplayName("vender pero el bote no cubre → EMPRESA_SIN_FONDOS")
    void venderSinFondos() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa(...)));
        when(repo.miembros(EMP)).thenReturn(List.of(/*1*/));
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(10);
        when(repo.gastarDelBote(eq(EMP), anyLong())).thenReturn(false);
        AccionEmpresasService.ResultadoVenta r = svc().vender(ACTOR, EMP, 5);
        assertEquals(AccionEmpresasService.EstadoVenta.EMPRESA_SIN_FONDOS, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
        verify(accRepo, never()).fijar(anyLong(), anyLong(), anyInt());
    }
```
Pin the prestigio so precio=100 deterministically: pick nivel/bote so `Prestigio.calcular(nivel, miembros.size(), bote)/100 == 100` (e.g. `Prestigio.calcular(1, 1, bote)` = `10000 + 1000 + bote/1000`; for precio 100 → prestigio 10000 → not reachable with nivel 1 since base 11000. Instead choose values that give a round prestigio and compute the expected coste from it — don't force 100; compute `precioEsperado = Prestigio.calcular(nivel, miembros, bote)/100` in the test and assert `coste = cantidad*precioEsperado`). Keep it deterministic by controlling nivel, `miembros` list size, and bote.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: `AccionEmpresasService.java`**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaAccionRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;

/**
 * Participaciones de empresa (F5): comprar/vender contra el pool fijo de la empresa y repartir dividendos
 * del bote. Todo es redistribución (comprar mueve coins jugador→bote; vender y dividendo, bote→jugador):
 * cero creación. El precio de la participación flota con el prestigio ({@link Accion#precioParticipacion}).
 * Los movimientos siguen el patrón atómico del resto (validar → gate del dinero → registrar): nunca dejan
 * estado a medias.
 */
public final class AccionEmpresasService {

    public enum EstadoCompra { OK, NO_EXISTE, CANTIDAD_INVALIDA, SIN_PARTICIPACIONES_LIBRES, SIN_SALDO }
    public enum EstadoVenta { OK, NO_EXISTE, CANTIDAD_INVALIDA, SIN_PARTICIPACIONES, EMPRESA_SIN_FONDOS }

    public record ResultadoCompra(EstadoCompra estado, int cantidad, long precio, long coste) {
        static ResultadoCompra de(EstadoCompra e) { return new ResultadoCompra(e, 0, 0, 0); }
    }
    public record ResultadoVenta(EstadoVenta estado, int cantidad, long precio, long valor) {
        static ResultadoVenta de(EstadoVenta e) { return new ResultadoVenta(e, 0, 0, 0); }
    }

    private final EmpresaRepositorio repo;
    private final EmpresaAccionRepositorio accRepo;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public AccionEmpresasService(EmpresaRepositorio repo, EmpresaAccionRepositorio accRepo,
                                 EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this.repo = repo;
        this.accRepo = accRepo;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Precio actual de una participación de la empresa (prestigio / pool, suelo 1). */
    public long precioActual(Empresa e) {
        long prestigio = Prestigio.calcular(e.nivel(), repo.miembros(e.id()).size(), e.bote());
        return Accion.precioParticipacion(prestigio);
    }

    /** Compra {@code cantidad} participaciones: cobra al jugador y el capital entra al bote. */
    public ResultadoCompra comprar(long actorId, long empresaId, int cantidad) {
        if (cantidad <= 0) return ResultadoCompra.de(EstadoCompra.CANTIDAD_INVALIDA);
        Optional<Empresa> empOpt = repo.porId(empresaId);
        if (empOpt.isEmpty()) return ResultadoCompra.de(EstadoCompra.NO_EXISTE);
        Empresa emp = empOpt.get();
        int libres = Accion.POOL - accRepo.vendidasDe(empresaId);
        if (cantidad > libres) return ResultadoCompra.de(EstadoCompra.SIN_PARTICIPACIONES_LIBRES);
        long precio = precioActual(emp);
        long coste = precio * cantidad;
        usuarios.obtenerOCrear(actorId);
        // Gate del dinero primero: si no hay saldo, nada más ocurre.
        if (!economia.gastar(actorId, coste, "acciones_comprar:" + empresaId)) {
            return ResultadoCompra.de(EstadoCompra.SIN_SALDO);
        }
        repo.incrementarBote(empresaId, coste); // el capital entra al bote
        int nuevas = accRepo.participacionesDe(empresaId, actorId) + cantidad;
        accRepo.fijar(empresaId, actorId, nuevas);
        return new ResultadoCompra(EstadoCompra.OK, cantidad, precio, coste);
    }

    /** Vende {@code cantidad} participaciones de vuelta al pool: la empresa paga del bote a precio actual. */
    public ResultadoVenta vender(long actorId, long empresaId, int cantidad) {
        if (cantidad <= 0) return ResultadoVenta.de(EstadoVenta.CANTIDAD_INVALIDA);
        Optional<Empresa> empOpt = repo.porId(empresaId);
        if (empOpt.isEmpty()) return ResultadoVenta.de(EstadoVenta.NO_EXISTE);
        Empresa emp = empOpt.get();
        int tienes = accRepo.participacionesDe(empresaId, actorId);
        if (tienes < cantidad) return ResultadoVenta.de(EstadoVenta.SIN_PARTICIPACIONES);
        long precio = precioActual(emp);
        long valor = precio * cantidad;
        // Gate del bote (atómico UPDATE ... WHERE bote>=valor): si no cubre, no vende.
        if (!repo.gastarDelBote(empresaId, valor)) {
            return ResultadoVenta.de(EstadoVenta.EMPRESA_SIN_FONDOS);
        }
        usuarios.obtenerOCrear(actorId);
        economia.ingresar(actorId, valor, "acciones_vender:" + empresaId);
        accRepo.fijar(empresaId, actorId, tienes - cantidad); // borra si 0
        return new ResultadoVenta(EstadoVenta.OK, cantidad, precio, valor);
    }
}
```
(The `cartera` view and `repartirDividendos` are added in later steps/tasks — see Task 3. Add `cartera` here if the command needs it now; the `/acciones cartera` subcommand does. Add a `cartera(long actorId)` method returning positions valued at current price, iterating `accRepo.carteraDe(actorId)` and `repo.porId` per empresa. Keep it simple and read-only.)

Add this `cartera` method to the service (needed by the command in this task):
```java
    /** Cartera de participaciones del jugador, valorada a precio actual por empresa. */
    public java.util.List<PosicionVista> cartera(long actorId) {
        java.util.List<PosicionVista> vistas = new java.util.ArrayList<>();
        for (EmpresaAccionRepositorio.PosicionAccion pos : accRepo.carteraDe(actorId)) {
            Optional<Empresa> emp = repo.porId(pos.empresaId());
            if (emp.isEmpty()) continue; // empresa disuelta: la posición ya no existe (defensivo)
            long precio = precioActual(emp.get());
            vistas.add(new PosicionVista(emp.get().nombre(), pos.cantidad(), precio, precio * pos.cantidad()));
        }
        return vistas;
    }

    /** Una posición para pintar la cartera: empresa, participaciones, precio actual y valor. */
    public record PosicionVista(String empresa, int cantidad, long precio, long valor) {}
```

- [ ] **Step 4: Run — service tests PASS** (`sh ./mvnw -B test -Dtest=AccionEmpresasServiceTest`).

- [ ] **Step 5: i18n (ambos idiomas).** Añadir a `messages_es.properties` (y equivalente natural EN):
```properties
# F5 — acciones y dividendos de empresa: comando /acciones
comando.acciones.desc=Invierte en empresas: compra participaciones y cobra dividendos
comando.acciones.comprar.desc=Compra participaciones de una empresa
comando.acciones.vender.desc=Vende participaciones a la empresa
comando.acciones.ver.desc=Muestra las participaciones y el precio de una empresa
comando.acciones.cartera.desc=Muestra tus participaciones en todas las empresas
comando.acciones.opcion.empresa=Empresa
comando.acciones.opcion.cantidad=Cuántas participaciones
acciones.comprar.ok={0} Has comprado **{1}** participaciones de **{2}** por **{3}** 🪙 ({4} 🪙/u).
acciones.vender.ok={0} Has vendido **{1}** participaciones de **{2}** por **{3}** 🪙 ({4} 🪙/u).
acciones.ver.titulo=📈 {0}
acciones.ver.cuerpo=Participaciones vendidas: **{0}/100**\nPrecio: **{1}** 🪙/participación\nTuyas: **{2}** ({3} 🪙)
acciones.cartera.titulo=📈 Tu cartera de empresas
acciones.cartera.vacia=No tienes participaciones en ninguna empresa.
acciones.cartera.linea={0}: **{1}** part. · **{2}** 🪙
acciones.cartera.total=\nValor total: **{0}** 🪙
acciones.error.no_existe=❌ Esa empresa no existe.
acciones.error.cantidad=❌ La cantidad debe ser 1 o más.
acciones.error.sin_libres=❌ No quedan tantas participaciones libres en esa empresa.
acciones.error.sin_saldo=❌ No tienes saldo suficiente.
acciones.error.sin_participaciones=❌ No tienes tantas participaciones de esa empresa.
acciones.error.empresa_sin_fondos=❌ La empresa no tiene bote suficiente para recomprar ahora.
empresa.info.acciones=📈 Acciones: {0}/100 (precio {1} 🪙)
```
Añade la clave `empresa.info.acciones` y las de `acciones.*` en **ambos** ficheros. EN natural.

- [ ] **Step 6: `AccionesComando.java`.** Mirror `EconomiaComando`/`EmpresaComando`: `/acciones` con 4 subcomandos, `implements ComandoAutocompletable`. Estructura:
- `definicion()`: `Commands.slash("acciones", <desc ES>)` localizada EN; subcomandos:
  - `comprar` / `vender`: opción `empresa` (STRING, requerida, `autocomplete=true`) + opción `cantidad` (INTEGER, requerida, `setMinValue(1)`).
  - `ver`: opción `empresa` (STRING, requerida, autocomplete).
  - `cartera`: sin opciones.
- `ejecutar`: resolver locale con `Messages.desdeTag(evento.getUserLocale().getLocale())` (patrón correcto del proyecto). `switch(getSubcommandName())`:
  - `comprar`/`vender`: parsear `empresaId = Long.parseLong(getOption("empresa").getAsString())` (el value del autocomplete es el id), `cantidad = getOption("cantidad").getAsInt()`. Llamar al service; `switch` **exhaustivo** sobre el `Estado*` → responder con la clave i18n correspondiente (OK público con cifras; errores efímeros con `setEphemeral(true)`).
  - `ver`: `empresaId`, `repo.porId` → si vacío error; si no, `precioActual` + `vendidasDe` + `participacionesDe(actor)` → embed `EmbedFactory.Tipo.ECONOMIA` `acciones.ver.*`. Público.
  - `cartera`: `service.cartera(actor)` → si vacía `acciones.cartera.vacia`; si no, líneas + total (trocear con `util/Embeds` si hiciera falta, como otros comandos). Público.
- `autocompletar`: opción `empresa` → `repo.todas()` filtradas por el texto tecleado (nombre contiene, case-insensitive), `Command.Choice(nombre, String.valueOf(id))`, tope 25, **responde siempre**.
- El comando necesita `EmpresaRepositorio`, `EmpresaAccionRepositorio` y `AccionEmpresasService` (pásalos por constructor; el service ya tiene repo/accRepo, así que puedes exponer en el service getters/lecturas para `ver`, o pasar los repos al comando para las lecturas de `ver`/autocomplete). Elige lo que menos superficie cree; lo natural: el comando recibe `AccionEmpresasService` (compra/venta/cartera/precioActual) + `EmpresaRepositorio` (porId/todas para ver/autocomplete) + `EmpresaAccionRepositorio` (vendidasDe/participacionesDe para ver). Category default PUBLICO.

- [ ] **Step 7: Línea de acciones en `/empresa info`.** En `EmpresaComando`, donde se pinta el cuerpo de `info` (busca la clave `empresa.info.*` y cómo se construyen los placeholders), añade la línea `empresa.info.acciones` con `vendidasDe(empresaId)` y `precioActual`. El comando necesitará acceso a `EmpresaAccionRepositorio` y al cálculo de precio: inyecta lo mínimo (p. ej. `AccionEmpresasService` para `precioActual` + `EmpresaAccionRepositorio` para `vendidasDe`). Si `/empresa info` usa una clave única con N placeholders, añade 2 placeholders más y ajústalos en ES+EN; si concatena líneas, añade una línea. Sigue el patrón que ya exista en ese método (F5a añadió almacén, F5d añadió deuda — imítalo).

- [ ] **Step 8: Wiring en `Main.java`.** Construir `EmpresaAccionRepositorio accionRepo = new EmpresaAccionRepositorio(db.dataSource());` y `AccionEmpresasService accionService = new AccionEmpresasService(empresaRepo, accionRepo, economiaRepo, usuarios);` en `iniciarDiscord` (cerca del resto de servicios de empresa). Registrar `comandos.add(new AccionesComando(accionService, empresaRepo, accionRepo));`. Pasar lo que necesite `EmpresaComando` para su nueva línea de info (accionService/accionRepo) al construir `EmpresaComando`. Imports.

- [ ] **Step 9: Run — `sh ./mvnw -B clean verify` → BUILD SUCCESS.**

- [ ] **Step 10: Commit**
```bash
git add -A
git commit -m "feat(empresas): /acciones comprar|vender|ver|cartera + acciones en info"
```

- [ ] **Step 11: REVIEW** — spec-reviewer sobre este commit (base = commit de T1): dinero (capital al bote en comprar, gate `gastarDelBote` en vender, tope del pool, precio = prestigio/100), i18n ES+EN completo, `/empresa info` intacto salvo la línea nueva, switches exhaustivos. Iterar si hay hallazgos.

---

## Task 3: dividendos (`repartirDividendos` + `DividendoEmpresasJob`) — con review

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/AccionEmpresasService.java`
- Create: `src/main/java/com/gymprofit/bot/jobs/DividendoEmpresasJob.java`
- Modify: `messages_es.properties`, `messages_en.properties`, `Main.java`
- Test: `src/test/java/com/gymprofit/bot/services/AccionEmpresasServiceTest.java` (añadir casos)

- [ ] **Step 1: tests de `repartirDividendos` (write first, FAIL)**
```java
    @Test @DisplayName("reparte el pot proporcional; la parte no vendida se queda; gate único")
    void repartirProporcional() {
        // bote 100.000 → pot = floor(0.05*100000) = 5.000
        // accionistas: ACTOR 25 part, OTHER 15 part (40 vendidas; 60 no vendidas se quedan)
        Empresa e = empresa(/*id EMP, bote*/ 100_000L, ...);
        when(accRepo.accionistas(EMP)).thenReturn(List.of(
                new EmpresaAccionRepositorio.Accionista(ACTOR, 25),
                new EmpresaAccionRepositorio.Accionista(OTHER, 15)));
        // pago ACTOR = floor(5000*25/100)=1250; OTHER = floor(5000*15/100)=750; total=2000
        when(repo.gastarDelBote(EMP, 2_000L)).thenReturn(true);

        AccionEmpresasService.ResultadoDividendo r = svc().repartirDividendos(e);
        assertEquals(AccionEmpresasService.EstadoDividendo.PAGADO, r.estado());
        assertEquals(2_000L, r.total());
        verify(repo).gastarDelBote(EMP, 2_000L);
        verify(economia).ingresar(ACTOR, 1_250L, "dividendo:" + EMP);
        verify(economia).ingresar(OTHER, 750L, "dividendo:" + EMP);
    }

    @Test @DisplayName("sin accionistas o bote 0 → NADA, no toca el bote")
    void repartirNada() {
        when(accRepo.accionistas(EMP)).thenReturn(List.of());
        AccionEmpresasService.ResultadoDividendo r = svc().repartirDividendos(empresa(100_000L, ...));
        assertEquals(AccionEmpresasService.EstadoDividendo.NADA, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
    }

    @Test @DisplayName("carrera: el bote bajó entre calcular y cobrar → NADA, no paga a nadie")
    void repartirCarrera() {
        Empresa e = empresa(100_000L, ...);
        when(accRepo.accionistas(EMP)).thenReturn(List.of(
                new EmpresaAccionRepositorio.Accionista(ACTOR, 25)));
        when(repo.gastarDelBote(eq(EMP), anyLong())).thenReturn(false);
        AccionEmpresasService.ResultadoDividendo r = svc().repartirDividendos(e);
        assertEquals(AccionEmpresasService.EstadoDividendo.NADA, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }
```

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: implementar `repartirDividendos` en el service**
```java
    public enum EstadoDividendo { PAGADO, NADA }
    /** Resultado de un reparto: total pagado y nº de accionistas cobrados. */
    public record ResultadoDividendo(EstadoDividendo estado, long total, int accionistas) {
        static ResultadoDividendo nada() { return new ResultadoDividendo(EstadoDividendo.NADA, 0, 0); }
    }

    /**
     * Reparte dividendos de una empresa: pot = {@code floor(FRACCION_DIVIDENDO * bote)}; cada accionista
     * cobra {@code floor(pot * sus_part / 100)}. El total se descuenta con un único gate atómico
     * {@code gastarDelBote}; la parte no vendida nunca sale del bote. Si no hay accionistas, ni pot, ni
     * el bote cubre el total (carrera), no paga a nadie.
     */
    public ResultadoDividendo repartirDividendos(Empresa e) {
        var accionistas = accRepo.accionistas(e.id());
        if (accionistas.isEmpty() || e.bote() <= 0) return ResultadoDividendo.nada();
        long pot = (long) Math.floor(Accion.FRACCION_DIVIDENDO * e.bote());
        if (pot <= 0) return ResultadoDividendo.nada();
        long total = 0;
        for (var a : accionistas) {
            total += Accion.dividendoDe(pot, a.cantidad());
        }
        if (total <= 0) return ResultadoDividendo.nada();
        // Gate único: descuenta el total del bote de una vez; si el bote bajó (carrera), no paga nada.
        if (!repo.gastarDelBote(e.id(), total)) return ResultadoDividendo.nada();
        for (var a : accionistas) {
            long pago = Accion.dividendoDe(pot, a.cantidad());
            if (pago > 0) {
                usuarios.obtenerOCrear(a.discordId());
                economia.ingresar(a.discordId(), pago, "dividendo:" + e.id());
            }
        }
        return new ResultadoDividendo(EstadoDividendo.PAGADO, total, accionistas.size());
    }
```

- [ ] **Step 4: Run — service tests PASS.**

- [ ] **Step 5: i18n del aviso (ambos idiomas)**
```properties
empresa.dividendo.titulo=📈 Dividendos repartidos
empresa.dividendo.cuerpo=Se han repartido **{0}** 🪙 en dividendos entre **{1}** accionistas.
```

- [ ] **Step 6: `DividendoEmpresasJob.java`** — mirror `ImpuestoEmpresasJob` exactly (Clock, ZONA Europe/Madrid, self-reschedule via `TemporalAdjusters`, per-empresa try/catch, best-effort aviso al canal privado F4). Differences: **DIA = THURSDAY, HORA = 02:00**; método público `repartir()` (equivalente a `cobrar()`); por cada empresa llama `service.repartirDividendos(e)` y avisa **solo si `PAGADO`** con las claves `empresa.dividendo.*` (Tipo `EmbedFactory.Tipo.ECONOMIA`, en ES, best-effort al `e.canalId()`). Reprograma primero, reparte después (patrón de `ImpuestoEmpresasJob.tick`). Constructor `(EmpresaRepositorio empresas, AccionEmpresasService service, JDA jda, Clock clock)`.

- [ ] **Step 7: Wiring en `Main.java`.** Bajo el bloque de jobs (`jda != null`), junto a `ImpuestoEmpresasJob`:
```java
            new DividendoEmpresasJob(empresaRepoParaJob, accionServiceParaJob, jda,
                    Clock.system(ZoneId.of("Europe/Madrid"))).iniciar();
```
El `AccionEmpresasService` del job puede ser una instancia propia si el scope no ve la de `iniciarDiscord` (como se hizo con `ImpuestoEmpresasService`/`EventoEconomicoService`: construir `new AccionEmpresasService(new EmpresaRepositorio(db.dataSource()), new EmpresaAccionRepositorio(db.dataSource()), economiaRepo, usuarios)` en el scope del job). El service no tiene estado (todo en BD), así que una instancia propia es correcta.

- [ ] **Step 8: Run — `sh ./mvnw -B clean verify` → BUILD SUCCESS.**

- [ ] **Step 9: Commit**
```bash
git add -A
git commit -m "feat(empresas): dividendos semanales de acciones (job jueves 02:00)"
```

- [ ] **Step 10: REVIEW** — spec-reviewer sobre este commit (base = commit de T2): reparto proporcional con floor, gate único `gastarDelBote(total)`, la parte no vendida se queda, la carrera no paga, el job reprograma antes de repartir y avisa solo en PAGADO. Iterar si hay hallazgos.

---

## Task 4: Documentación + verificación final

**Files:** Modify `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

- [ ] **Step 1: ADR-025 en `docs/decisions.md`** (tras ADR-024; confirmar que es el último). Estilo ADR-021..024. Contenido: estado aceptada/implementada (F5 acciones); contexto (las empresas no tenían capital externo ni los no-miembros forma de participar en su economía); decisión (pool fijo de 100 participaciones, precio = prestigio/100, comprar mete capital al bote, dividendos semanales del bote por fracción del pool con `DividendoEmpresasJob` los jueves 02:00, vender recompra la empresa a precio actual, quiebra funde el capital vía FK CASCADE); consecuencias (migración V36; nuevos `Accion`/repo/`AccionEmpresasService`/job/`AccionesComando`; redistribución pura antiinflación-neutral, `FRACCION_DIVIDENDO` modesta; fuera: mercado secundario, precio por oferta/demanda, dividendo declarado por el dueño, voto por acciones).

- [ ] **Step 2: `docs/architecture.md`** — viñeta "**Empresas (Fase 5 — acciones y dividendos)**" tras la de eventos económicos; bump "Migraciones Flyway V6–V35" → **V6–V36** + añadir "acciones" a la lista de temas; añadir `DividendoEmpresasJob` a la descripción de `jobs/`.

- [ ] **Step 3: `CHANGELOG.md`** — bajo `[Sin publicar] / Añadido`, primer bullet: F5 acciones/dividendos (`/acciones`, migración `V36`).

- [ ] **Step 4: READMEs** — `README.md` y `README.en.md`: añadir `/acciones` (comprar · vender · ver · cartera) a la lista de comandos de economía (cerca de `/bolsa` y del clima económico).

- [ ] **Step 5: `clean verify` final** — `sh ./mvnw -B clean verify` → BUILD SUCCESS. Anotar recuento de tests.

- [ ] **Step 6: Commit**
```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5 acciones y dividendos — ADR-025, architecture, changelog y READMEs"
```

- [ ] **Step 7: Push + aviso de despliegue.** El controlador hace `git push origin main` y avisa: **desplegar = reiniciar bot** (V36 + `/acciones` + `DividendoEmpresasJob` + línea en `/empresa info`). Añade slash command → reiniciar para re-registrarlo; **no** requiere `/setup`. Smoke: ver el `## Despliegue` de la spec.

---

## Notas de implementación

- **Orden de dinero (atómico):** comprar = gate `economia.gastar` → `incrementarBote` → `fijar`; vender = gate `repo.gastarDelBote` → `economia.ingresar` → `fijar`; dividendo = calcular total → gate `gastarDelBote(total)` → `ingresar` a cada uno. El gate SIEMPRE antes de registrar/pagar.
- **Precio snapshot:** el precio se calcula una vez por operación (antes de mover dinero); comprar cambia el bote después, pero eso afecta a operaciones futuras, no a la actual. Correcto.
- **`usuarios.obtenerOCrear`** antes de `gastar`/`ingresar`, como `BolsaService`.
- **Redondeo:** precio `prestigio/100` (entero, suelo 1); dividendo `floor(pot*part/100)`. El total repartido ≤ pot; la parte no vendida se queda en el bote sin código extra.
- **Quiebra:** la FK `ON DELETE CASCADE` a `empresas(id)` borra las participaciones al `disolver`; no hay reembolso (riesgo real). El test de repo lo comprueba.
- **i18n:** toda clave en ES **y** EN. Avisos de canal en ES (post de canal, patrón F5b); respuestas a usuario según locale del invocador (`Messages.desdeTag`).
- **Tests de comando/job:** no se testean unitariamente (dependen de JDA/Clock vivo), como el resto del proyecto; se cubren en el smoke. La lógica testeable vive en `Accion` y `AccionEmpresasService`.
