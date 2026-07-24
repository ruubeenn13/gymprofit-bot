# Empresas Fase 5d — Préstamos empresariales — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una empresa pide un préstamo (uno a la vez): el bote recibe el principal y lo devuelve con interés en cuotas semanales cobradas por el job de F5b; el impago cuenta como morosidad/quiebra de F5b.

**Architecture:** Columnas `empresas.deuda` y `empresas.cuota_prestamo` (V34) + una función pura `Prestamo`. `PrestamoEmpresasService` concede (principal al bote, fija deuda/cuota) y amortiza (`/empresa pagar-prestamo`). El cobro semanal se integra en `ImpuestoEmpresasService`: la obligación pasa a ser `impuesto + cuota_prestamo` con un solo gate y, al pagar, amortiza la deuda.

**Tech Stack:** Java 21, JDA 5, JDBC, Flyway, JUnit 5 + Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-24-empresas-f5d-prestamos-design.md`.

**Precondición:** F5c desplegada y funcionando.

**Convenciones (obligatorias):** dominio español; i18n ES+EN en AMBOS; embeds solo por `EmbedFactory`;
`Messages.get` = MessageFormat posicional; Javadoc cabecera + inline del *porqué*; migraciones Flyway.
Build: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify` (SIEMPRE `clean`; no
dos builds a la vez; `-Dtest=A,B` entrecomillado). Baseline: **565 tests**.

## File Structure

- **Create** `src/main/resources/db/migration/V34__empresa_prestamo.sql`
- **Create** `src/main/java/com/gymprofit/bot/services/Prestamo.java` — pura (números).
- **Create** `src/main/java/com/gymprofit/bot/services/PrestamoEmpresasService.java` — conceder/pagar.
- **Modify** `src/main/java/com/gymprofit/bot/db/Empresa.java` — `long deuda, long cuotaPrestamo`.
- **Modify** `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — lecturas + `fijarPrestamo`.
- **Modify** `src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java` — obligación + amortización.
- **Modify** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — subcomandos + info.
- **Modify** `src/main/java/com/gymprofit/bot/Main.java` — wiring del service.
- **Modify** `messages_es.properties`, `messages_en.properties`.
- **Modify** docs.

---

### Task 1: V34 + `Prestamo` + deuda/cuota en el repo

**Files:**
- Create: `src/main/resources/db/migration/V34__empresa_prestamo.sql`
- Create: `src/main/java/com/gymprofit/bot/services/Prestamo.java`
- Test: `src/test/java/com/gymprofit/bot/services/PrestamoTest.java`
- Modify: `src/main/java/com/gymprofit/bot/db/Empresa.java`, `EmpresaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java`

- [ ] **Step 1: Migración V34**
```sql
-- V34: prestamo empresarial (F5d). deuda = lo que queda por devolver (0 = sin prestamo); cuota_prestamo
-- = cuota semanal que cobra el job de F5b junto al impuesto. Uno a la vez (deuda>0 bloquea otro).
ALTER TABLE empresas
    ADD COLUMN deuda           BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cuota_prestamo  BIGINT NOT NULL DEFAULT 0;
```

- [ ] **Step 2: `Empresa` record gana `long deuda, long cuotaPrestamo`** (dos últimos parámetros, tras `contratando`; Javadoc de cada uno). Rompe los `new Empresa(...)` → Step 5.

- [ ] **Step 3: Test rojo `PrestamoTest`** (JUnit 5):
```java
package com.gymprofit.bot.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class PrestamoTest {
    @Test void limiteEsNivelPorFactor() {
        assertEquals(20_000, Prestamo.limite(1));
        assertEquals(200_000, Prestamo.limite(10));
    }
    @Test void deudaAplicaInteres() {
        assertEquals(24_000, Prestamo.deudaConInteres(20_000)); // 20000 * 1.20
    }
    @Test void cuotaEsCeilPorPlazo() {
        assertEquals(6_000, Prestamo.cuota(24_000));  // 24000 / 4
        assertEquals(2_500, Prestamo.cuota(10_000));  // 10000 / 4 = 2500
        assertEquals(3, Prestamo.cuota(9));           // ceil(9/4)=3 (redondeo hacia arriba)
    }
}
```

- [ ] **Step 4: Corre, FAIL:** `.\mvnw.cmd "-Dtest=PrestamoTest" test`

- [ ] **Step 5: Implementa `Prestamo`** (clase final, constructor privado):
```java
package com.gymprofit.bot.services;

/**
 * Numeros puros del prestamo empresarial (F5d). Sin estado: limite por nivel, interes total y la cuota
 * semanal por plazo. El interes es un sumidero neto a medio plazo (se devuelve mas de lo prestado, y ese
 * extra sale del bote sin volver a nadie). Tunables.
 */
public final class Prestamo {

    /** Limite de principal por nivel: limite = nivel * este factor. */
    private static final long LIMITE_POR_NIVEL = 20_000L;
    /** Interes total sobre el principal (deuda = principal * (1 + INTERES)). */
    private static final double INTERES = 0.20;
    /** Semanas en las que se devuelve (numero de cuotas). */
    private static final int PLAZO_SEMANAS = 4;

    private Prestamo() {
    }

    /** Principal maximo que puede pedir una empresa de este nivel. */
    public static long limite(int nivel) {
        return nivel * LIMITE_POR_NIVEL;
    }

    /** Deuda total (principal + interes) de un prestamo de este principal. */
    public static long deudaConInteres(long principal) {
        return Math.round(principal * (1 + INTERES));
    }

    /** Cuota semanal para amortizar esta deuda en {@code PLAZO_SEMANAS} (division hacia arriba). */
    public static long cuota(long deuda) {
        return (deuda + PLAZO_SEMANAS - 1) / PLAZO_SEMANAS;
    }
}
```

- [ ] **Step 6: `PrestamoTest` verde** (3 tests).

- [ ] **Step 7: `deuda`/`cuota_prestamo` en las lecturas del repo** — añade ambas a `SELECT_EMPRESA` y al SELECT de `deMiembro`; en `mapearEmpresa`, `rs.getLong("deuda")` y `rs.getLong("cuota_prestamo")` como los dos últimos args.

- [ ] **Step 8: Arregla todos los `new Empresa(...)`** (`grep -rn "new Empresa(" src`) añadiendo `0L, 0L`.

- [ ] **Step 9: `fijarPrestamo`** en el repo (un setter para conceder/amortizar/saldar):
```java
/** Fija deuda y cuota del prestamo de una empresa (F5d): conceder, amortizar o saldar (0, 0). */
public void fijarPrestamo(long empresaId, long deuda, long cuota) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(
                 "UPDATE empresas SET deuda = ?, cuota_prestamo = ? WHERE id = ?")) {
        ps.setLong(1, deuda);
        ps.setLong(2, cuota);
        ps.setLong(3, empresaId);
        ps.executeUpdate();
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo fijar el prestamo de la empresa " + empresaId, e);
    }
}
```

- [ ] **Step 10: Test de repo (Testcontainers)** en `EmpresaRepositorioTest`: `deuda`/`cuota_prestamo` default 0; `fijarPrestamo(id, 24000, 6000)` persiste ambos; leídos en `porId`.

- [ ] **Step 11: `clean verify` + commit** (~569 tests):
```bash
git add src/main/resources/db/migration/V34__empresa_prestamo.sql src/main/java/com/gymprofit/bot/services/Prestamo.java src/test/java/com/gymprofit/bot/services/PrestamoTest.java src/main/java/com/gymprofit/bot/db/Empresa.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java
# + tests con new Empresa( tocados
git commit -m "feat(empresas): V34 prestamo (deuda/cuota) + numeros y repo"
```

---

### Task 2: `PrestamoEmpresasService` + `/empresa prestamo` + `/empresa pagar-prestamo` (LLEVA REVIEW)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/PrestamoEmpresasService.java`
- Test: `src/test/java/com/gymprofit/bot/services/PrestamoEmpresasServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java`, `Main.java`
- Modify: `messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: Test rojo `PrestamoEmpresasServiceTest`** (Mockito). `conceder(actorId, cantidad)`:
  - OK: alto cargo, deuda 0, cantidad ≤ límite → `verify(repo).incrementarBote(id, cantidad)` + `verify(repo).fijarPrestamo(id, deudaConInteres, cuota)`. Resultado OK con cifras.
  - `SIN_EMPRESA` (deMiembro vacío); `NO_AUTORIZADO` (no alto cargo); `CANTIDAD_INVALIDA` (≤0);
    `YA_TIENE_PRESTAMO` (deuda>0); `LIMITE` (cantidad > `Prestamo.limite(nivel)`). En todos: `never().incrementarBote(...)`.
  `pagar(actorId, cantidad)`:
  - OK con cantidad: amortiza `min(cantidad, deuda)` vía `gastarDelBote`, `fijarPrestamo(id, deudaNueva, cuotaNueva)`.
  - OK sin cantidad (empty): paga `min(deuda, bote)` → salda o amortiza.
  - pago que salda → `fijarPrestamo(id, 0, 0)`.
  - `SIN_DEUDA` (deuda 0); `NO_AUTORIZADO`; `SIN_EMPRESA`; `SIN_FONDOS` (`gastarDelBote` false).

- [ ] **Step 2: Corre, FAIL.**

- [ ] **Step 3: Implementa `PrestamoEmpresasService`**
```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Prestamos empresariales (F5d): conceder (el principal entra al bote, se fija deuda+cuota) y amortizar
 * (pagar del bote contra la deuda). Uno a la vez. Autorizan los altos cargos (DUENO/DIRECTIVO). El gate
 * de dinero es {@link EmpresaRepositorio#gastarDelBote}; el principal entra con {@code incrementarBote}.
 */
public final class PrestamoEmpresasService {

    public enum EstadoConceder { OK, SIN_EMPRESA, NO_AUTORIZADO, CANTIDAD_INVALIDA, YA_TIENE_PRESTAMO, LIMITE }
    public enum EstadoPago { OK, SIN_EMPRESA, NO_AUTORIZADO, SIN_DEUDA, SIN_FONDOS }

    public record ResultadoConceder(EstadoConceder estado, long principal, long deuda, long cuota, long limite) {
        static ResultadoConceder de(EstadoConceder e) { return new ResultadoConceder(e, 0, 0, 0, 0); }
    }
    public record ResultadoPago(EstadoPago estado, long pagado, long deudaRestante, long cuota) {
        static ResultadoPago de(EstadoPago e) { return new ResultadoPago(e, 0, 0, 0); }
    }

    private final EmpresaRepositorio repo;

    public PrestamoEmpresasService(EmpresaRepositorio repo) { this.repo = repo; }

    /** Concede un prestamo: el principal entra al bote y se fija la deuda (principal+interes) y su cuota. */
    public ResultadoConceder conceder(long actorId, long cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) return ResultadoConceder.de(EstadoConceder.SIN_EMPRESA);
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) return ResultadoConceder.de(EstadoConceder.NO_AUTORIZADO);
        if (cantidad <= 0) return ResultadoConceder.de(EstadoConceder.CANTIDAD_INVALIDA);
        if (emp.deuda() > 0) return ResultadoConceder.de(EstadoConceder.YA_TIENE_PRESTAMO);
        long limite = Prestamo.limite(emp.nivel());
        if (cantidad > limite) return new ResultadoConceder(EstadoConceder.LIMITE, 0, 0, 0, limite);
        long deuda = Prestamo.deudaConInteres(cantidad);
        long cuota = Prestamo.cuota(deuda);
        repo.incrementarBote(emp.id(), cantidad);   // el principal entra al bote
        repo.fijarPrestamo(emp.id(), deuda, cuota);
        return new ResultadoConceder(EstadoConceder.OK, cantidad, deuda, cuota, limite);
    }

    /** Amortiza del bote contra la deuda. {@code cantidad} vacio = pagar lo que se pueda hasta saldar. */
    public ResultadoPago pagar(long actorId, OptionalLong cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) return ResultadoPago.de(EstadoPago.SIN_EMPRESA);
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) return ResultadoPago.de(EstadoPago.NO_AUTORIZADO);
        if (emp.deuda() <= 0) return ResultadoPago.de(EstadoPago.SIN_DEUDA);
        long tope = cantidad.isPresent() ? Math.min(cantidad.getAsLong(), emp.deuda())
                                         : Math.min(emp.deuda(), emp.bote());
        if (tope <= 0) return ResultadoPago.de(EstadoPago.SIN_FONDOS);
        if (!repo.gastarDelBote(emp.id(), tope)) return ResultadoPago.de(EstadoPago.SIN_FONDOS);
        long deudaNueva = emp.deuda() - tope;
        long cuotaNueva = deudaNueva == 0 ? 0 : Math.min(emp.cuotaPrestamo(), deudaNueva);
        repo.fijarPrestamo(emp.id(), deudaNueva, cuotaNueva);
        return new ResultadoPago(EstadoPago.OK, tope, deudaNueva, cuotaNueva);
    }

    private boolean esAltoCargo(long empresaId, long actorId) {
        return repo.altosCargos(empresaId).stream().anyMatch(m -> m.discordId() == actorId);
    }
}
```

- [ ] **Step 4: Corre, PASS.**

- [ ] **Step 5: Wiring en `Main`** — `PrestamoEmpresasService empresaPrestamo = new PrestamoEmpresasService(empresaRepo);` y pásalo al `new EmpresaComando(...)` (parámetro + campo).

- [ ] **Step 6: Subcomandos en `EmpresaComando`** (patrón del subcomando `vender`, líneas ~162/190/216/710):
  - `OptionData cantidadPrestamo = new OptionData(OptionType.INTEGER, "cantidad", Messages.get(Messages.ES,"comando.empresa.prestamo.cantidad"), true).setMinValue(1);`
  - `OptionData cantidadPago = new OptionData(OptionType.INTEGER, "cantidad", Messages.get(Messages.ES,"comando.empresa.pagarprestamo.cantidad"), false).setMinValue(1);`
  - `addSubcommands(... sub("prestamo","comando.empresa.prestamo.desc").addOptions(cantidadPrestamo), sub("pagar-prestamo","comando.empresa.pagarprestamo.desc").addOptions(cantidadPago))`
  - switch: `case "prestamo" -> prestamo(evento, locale); case "pagar-prestamo" -> pagarPrestamo(evento, locale);`
  - Métodos (MessageFormat posicional; `EmbedFactory.base(Tipo.ECONOMIA, locale, titulo, desc).build()` como `vender`):
```java
private void prestamo(SlashCommandInteractionEvent evento, Locale locale) {
    long cantidad = evento.getOption("cantidad").getAsLong();
    PrestamoEmpresasService.ResultadoConceder r = prestamos.conceder(evento.getUser().getIdLong(), cantidad);
    String msg = switch (r.estado()) {
        case SIN_EMPRESA -> Messages.get(locale, "empresa.prestamo.sin_empresa");
        case NO_AUTORIZADO -> Messages.get(locale, "empresa.prestamo.no_autorizado");
        case CANTIDAD_INVALIDA -> Messages.get(locale, "empresa.prestamo.cantidad_invalida");
        case YA_TIENE_PRESTAMO -> Messages.get(locale, "empresa.prestamo.ya_tiene");
        case LIMITE -> Messages.get(locale, "empresa.prestamo.limite", r.limite());
        case OK -> Messages.get(locale, "empresa.prestamo.ok", r.principal(), r.deuda(), r.cuota());
    };
    evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
            Messages.get(locale, "empresa.prestamo.titulo"), msg).build()).queue();
}

private void pagarPrestamo(SlashCommandInteractionEvent evento, Locale locale) {
    OptionMapping opt = evento.getOption("cantidad");
    OptionalLong cantidad = opt == null ? OptionalLong.empty() : OptionalLong.of(opt.getAsLong());
    PrestamoEmpresasService.ResultadoPago r = prestamos.pagar(evento.getUser().getIdLong(), cantidad);
    String msg = switch (r.estado()) {
        case SIN_EMPRESA -> Messages.get(locale, "empresa.prestamo.sin_empresa");
        case NO_AUTORIZADO -> Messages.get(locale, "empresa.prestamo.no_autorizado");
        case SIN_DEUDA -> Messages.get(locale, "empresa.prestamo.sin_deuda");
        case SIN_FONDOS -> Messages.get(locale, "empresa.prestamo.sin_fondos");
        case OK -> Messages.get(locale, "empresa.prestamo.pagado", r.pagado(), r.deudaRestante(), r.cuota());
    };
    evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
            Messages.get(locale, "empresa.prestamo.titulo"), msg).build()).queue();
}
```
`prestamos` = el campo `PrestamoEmpresasService` nuevo. Importa `OptionalLong`/`OptionMapping` si faltan.

- [ ] **Step 7: Deuda en `/empresa info`** — tras armar `cuerpo`, sin renumerar:
```java
if (e.deuda() > 0) {
    cuerpo += "\n" + Messages.get(locale, "empresa.info.deuda", e.deuda(), e.cuotaPrestamo());
}
```

- [ ] **Step 8: i18n ES**
```properties
comando.empresa.prestamo.desc=Pide un préstamo para el bote (dueño/directivo)
comando.empresa.prestamo.cantidad=Cuánto principal pedir
comando.empresa.pagarprestamo.desc=Amortiza el préstamo desde el bote (dueño/directivo)
comando.empresa.pagarprestamo.cantidad=Cuánto pagar (vacío = lo que se pueda)
empresa.prestamo.titulo=💳 Préstamo de empresa
empresa.prestamo.ok=Préstamo concedido: **{0}** 🪙 al bote. Deuda total: **{1}** 🪙 (cuota **{2}**/semana).
empresa.prestamo.limite=Tu nivel permite pedir como mucho **{0}** 🪙.
empresa.prestamo.ya_tiene=Tu empresa ya tiene un préstamo activo; sáldalo antes de pedir otro.
empresa.prestamo.cantidad_invalida=La cantidad debe ser mayor que 0.
empresa.prestamo.sin_empresa=No perteneces a ninguna empresa.
empresa.prestamo.no_autorizado=Solo el dueño o un directivo pueden gestionar préstamos.
empresa.prestamo.pagado=Amortizados **{0}** 🪙. Deuda restante: **{1}** 🪙 (cuota **{2}**/semana).
empresa.prestamo.sin_deuda=Tu empresa no tiene ningún préstamo que pagar.
empresa.prestamo.sin_fondos=El bote no tiene fondos para amortizar.
empresa.info.deuda=💳 Deuda: {0} 🪙 (cuota {1}/sem)
```

- [ ] **Step 9: i18n EN** (mismas claves, traducidas; conserva 🪙/💳; mismos placeholders).

- [ ] **Step 10: `clean verify` + commit** (paridad ES/EN a mano). LLEVA REVIEW:
```bash
git add src/main/java/com/gymprofit/bot/services/PrestamoEmpresasService.java src/test/java/com/gymprofit/bot/services/PrestamoEmpresasServiceTest.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/java/com/gymprofit/bot/Main.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(empresas): /empresa prestamo y pagar-prestamo (banco empresarial)"
```

---

### Task 3: Obligación única en `ImpuestoEmpresasService` (impuesto + cuota) — LLEVA REVIEW

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java`
- Test: `src/test/java/com/gymprofit/bot/services/ImpuestoEmpresasServiceTest.java`

**Contexto:** hoy `evaluar` usa `Impuesto.cuota(nivel)` y `aplicar` en PAGA hace `gastarDelBote(cuota)` +
`fijarImpagos(0)`. El job (`ImpuestoEmpresasJob`) llama `aplicar(e)` y avisa con `r.cuota()/impagos()/falta()`
— **no hace falta tocar el job**: la obligación total viaja como `r.cuota()` y la amortización ocurre dentro
de `aplicar`.

- [ ] **Step 1: Actualiza los tests** de `ImpuestoEmpresasServiceTest` y añade los de préstamo:
  - Los casos existentes construyen `Empresa` con `cuotaPrestamo = 0` → la obligación = impuesto, siguen igual (ajusta el constructor de `Empresa` con los nuevos args `deuda=0, cuotaPrestamo=0`).
  - Nuevo `pagaObligacionConCuotaAmortiza`: empresa nivel 2 (impuesto `Impuesto.cuota(2)`), `deuda=10_000`, `cuotaPrestamo=6_000`, bote suficiente para `impuesto+6_000` → `evaluar` = PAGA con `cuota == impuesto + 6_000`; `aplicar` → `gastarDelBote(id, impuesto+6_000)` (true) + `fijarImpagos(id,0)` + `fijarPrestamo(id, 4_000, 4_000)` (deuda 10_000-6_000=4_000; cuota = min(6_000,4_000)=4_000).
  - `saldaPrestamoEnLaUltimaCuota`: `deuda=6_000`, `cuotaPrestamo=6_000` → `fijarPrestamo(id, 0, 0)`.
  - `obligacionNoCubierta_impagoNoAmortiza`: bote < impuesto+cuota → MOROSA/QUIEBRA, `never().fijarPrestamo(...)`, `never().gastarDelBote(...)` en PAGA (impago no toca la deuda).
  - `sinDeuda_comoF5b`: `deuda=0, cuotaPrestamo=0` → obligación=impuesto, `never().fijarPrestamo(...)`.

- [ ] **Step 2: Corre, FAIL** (los nuevos fallan; los viejos deben seguir compilando tras ajustar el constructor).

- [ ] **Step 3: Modifica `ImpuestoEmpresasService`**:
  - `evaluar`: `long cuota = Impuesto.cuota(e.nivel()) + e.cuotaPrestamo();` (obligación total). El resto igual (comparaciones con `e.bote()`, impagos, falta).
  - `aplicar` PAGA, tras `gastarDelBote(r.cuota())` == true y `fijarImpagos(0)`, **amortiza si hay deuda**:
```java
if (repo.gastarDelBote(e.id(), r.cuota())) {
    repo.fijarImpagos(e.id(), 0);
    if (e.deuda() > 0) {
        long deudaNueva = e.deuda() - Math.min(e.cuotaPrestamo(), e.deuda());
        long cuotaNueva = deudaNueva == 0 ? 0 : Math.min(e.cuotaPrestamo(), deudaNueva);
        repo.fijarPrestamo(e.id(), deudaNueva, cuotaNueva);
    }
    return r;
}
```
  - `recaerEnImpago` y las ramas MOROSA/QUIEBRA: **no** amortizan (no se paga, no se toca la deuda). Sin cambios salvo que `falta = cuota - bote` ya usa la obligación total (correcto).
  - Actualiza el Javadoc de clase: "obligacion semanal = impuesto + cuota del prestamo (F5d); al pagar, amortiza la deuda".

- [ ] **Step 4: Corre, PASS** (nuevos + viejos verdes).

- [ ] **Step 5: (opcional) aviso** — la clave `empresa.impuesto.morosa/quiebra` dice "cuota semanal de {0}"; ahora {0} es la obligación total (impuesto + préstamo), lo cual es correcto. No hace falta cambiar el texto; si quieres precisión, puedes matizar la clave para decir "obligación" en vez de "cuota" (ES+EN) — opcional, no rompe nada.

- [ ] **Step 6: `clean verify` + commit** (LLEVA REVIEW):
```bash
git add src/main/java/com/gymprofit/bot/services/ImpuestoEmpresasService.java src/test/java/com/gymprofit/bot/services/ImpuestoEmpresasServiceTest.java
git commit -m "feat(empresas): la obligacion semanal incluye la cuota del prestamo"
```

---

### Task 4: Documentación + verify final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: ADR-023** (comprueba que el último es 022):
```markdown
## ADR-023 — préstamos empresariales

**Estado:** aceptada e implementada (Fase 5d).

**Contexto.** Una empresa joven con el bote seco no tenía forma de conseguir liquidez para pagar sus
obligaciones o subir de nivel.

**Decisión.** Un préstamo por empresa (uno a la vez): `/empresa prestamo <cantidad>` (altos cargos) mete
el principal al bote y fija una deuda `principal × 1,20` a devolver en **cuotas semanales**
(`ceil(deuda / 4)`). El cobro se integra en el job de F5b: la **obligación semanal** pasa a ser
`impuesto + cuota_prestamo` con un solo gate `gastarDelBote`; al pagar, amortiza la deuda; si el bote no
cubre la obligación, cuenta como impago (morosidad → quiebra de F5b). `/empresa pagar-prestamo` amortiza
antes de tiempo. Límite `nivel × 20.000`. Números en `Prestamo`.

**Consecuencias.** Migración V34 (`empresas.deuda`, `empresas.cuota_prestamo`). El interés es un sumidero
neto a medio plazo. `ImpuestoEmpresasService` pasa a evaluar la obligación total. Varios préstamos,
refinanciación y avales quedan fuera.
```

- [ ] **Step 2: `docs/architecture.md`** — viñeta F5d tras F5c; migraciones a **V6–V34**; nota de que el job de F5b cobra la obligación (impuesto + cuota).

- [ ] **Step 3: `CHANGELOG.md`** — bajo `### Añadido`, encima de F5c:
```markdown
- **Empresas (Fase 5d)** (`/empresa prestamo · pagar-prestamo`): préstamos empresariales. La empresa pide
  liquidez al bote y la devuelve con interés en cuotas semanales cobradas con el impuesto; el impago cuenta
  para la quiebra. Migración `V34`.
```

- [ ] **Step 4: READMEs** — `/empresa` suma `prestamo · pagar-prestamo`.

- [ ] **Step 5: `clean verify` final + commit**
```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5d prestamos — ADR-023, architecture, changelog y READMEs"
```

---

## Despliegue (tras cerrar las 4 tasks)

**Reiniciar bot** (V34 + subcomandos `prestamo`/`pagar-prestamo` + la obligación en el job semanal). Añade
slash commands → reiniciar; **no** requiere `/setup`. Smoke: `/empresa prestamo` (bote↑, deuda en `info`);
`/empresa pagar-prestamo` (amortiza/salda); invocar el cobro semanal a mano (`ImpuestoEmpresasJob.cobrar()`,
público) y comprobar que descuenta impuesto+cuota, baja la deuda, y que sin bote suficiente cuenta como
impago (F5b).

## Notas de riesgo (para el review de T2 y T3)

- **T2**: el principal entra con `incrementarBote` y la deuda se fija justo después; el pago usa
  `gastarDelBote` como gate (nunca amortiza sin descontar). Un préstamo a la vez (`deuda>0` bloquea).
- **T3**: la obligación única no debe romper el comportamiento sin deuda (F5b puro). La amortización solo
  ocurre en PAGA con gasto exitoso; el impago nunca toca la deuda. `fijarPrestamo` solo se llama si había
  deuda (evita escrituras inútiles).
