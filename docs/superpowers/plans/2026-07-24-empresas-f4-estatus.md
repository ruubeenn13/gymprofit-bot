# Empresas Fase 4 — Estatus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar estatus visible a las empresas con `/empresa ranking` (tabla de prestigio) y un canal privado por empresa (permisos de miembro, sin roles), para todas las empresas, con creación perezosa.

**Architecture:** Sobre F1–F3. Una función pura de prestigio + una consulta de ranking en el repo alimentan un embed. El canal privado reutiliza el patrón `GremioCanal`/`GremioComando` (best-effort, sin roles): toda la lógica de canal vive en la capa comando/listener (necesita el `Guild` de JDA); `EmpresaService`/`EmpresaGestionService` siguen guild-agnostic. Nueva columna `empresas.canal_id` (V30) para persistir el canal y permitir creación perezosa.

**Tech Stack:** Java 21, JDA 5, JDBC (HikariCP), Flyway, JUnit 5 + Mockito, Testcontainers (MySQL).

**Spec:** `docs/superpowers/specs/2026-07-24-empresas-f4-estatus-design.md`.

**Precondición:** F3 desplegada y funcionando (regla de fases del `CLAUDE.md`). No empezar a implementar hasta que el usuario confirme el despliegue+smoke de F3.

**Convenciones del repo (obligatorias):** dominio en español; i18n en `messages_es.properties` + `messages_en.properties` (SIEMPRE ambos, nunca hardcodear texto visible); embeds solo por `EmbedFactory`; cabecera Javadoc por archivo + Javadoc en métodos públicos no triviales + comentarios inline del *porqué*; un test por lógica nueva; migraciones Flyway (no tocar BD a mano). Build: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify` (PowerShell; usar SIEMPRE `clean` — sin él maven-shade revienta con ZipException; no lanzar dos builds a la vez). En PowerShell 5.1, `-Dtest=A,B` va entrecomillado: `.\mvnw.cmd "-Dtest=A,B" test`. Baseline: **525 tests**.

## File Structure

- **Create** `src/main/resources/db/migration/V30__empresa_canal.sql` — columna `canal_id`.
- **Create** `src/main/java/com/gymprofit/bot/services/Prestigio.java` — función pura del score.
- **Create** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaCanal.java` — helper de canal (espejo de `GremioCanal`).
- **Modify** `src/main/java/com/gymprofit/bot/db/Empresa.java` — campo `Long canalId`.
- **Modify** `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — `SELECT_EMPRESA`/`mapearEmpresa`/`deMiembro` con `canal_id`; `fijarCanal`; `ranking`; record `EmpresaRanking`.
- **Modify** `src/main/java/com/gymprofit/bot/services/EmpresaService.java` — método `ranking(limite)` (usa `Prestigio` + `repo.ranking`).
- **Modify** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — subcomando `ranking`; hooks de canal en `fundar`/`info`/`disolver`/`gestionar`; método `ensureCanal`.
- **Modify** `src/main/java/com/gymprofit/bot/events/EmpresaBotonesListener.java` — hooks de canal en `resolver`/`votar`/`disolver`.
- **Modify** `src/main/resources/messages_es.properties`, `messages_en.properties` — claves nuevas.
- **Modify** docs: `docs/decisions.md` (ADR-019), `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.
- **Test** `src/test/java/com/gymprofit/bot/services/PrestigioTest.java` (nuevo); `EmpresaRepositorioTest` (ranking, Testcontainers) — crea el archivo si no existe siguiendo el patrón de los tests de repos con `@Testcontainers`.

---

### Task 1: Migración V30 + `canal_id` + `Prestigio` + `ranking` en el repo

**Files:**
- Create: `src/main/resources/db/migration/V30__empresa_canal.sql`
- Create: `src/main/java/com/gymprofit/bot/services/Prestigio.java`
- Test: `src/test/java/com/gymprofit/bot/services/PrestigioTest.java`
- Modify: `src/main/java/com/gymprofit/bot/db/Empresa.java`
- Modify: `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java`

- [ ] **Step 1: Migración V30**

Crea `V30__empresa_canal.sql` (con comentario de cabecera SQL, como el resto de migraciones):

```sql
-- V30: canal privado por empresa (F4 estatus). NULL hasta que se crea el canal
-- (creacion perezosa: la primera accion relevante lo materializa). BIGINT = snowflake de Discord.
ALTER TABLE empresas ADD COLUMN canal_id BIGINT NULL;
```

- [ ] **Step 2: Añade `canalId` al record `Empresa`**

En `Empresa.java`, añade el parámetro al final del record y documenta en el Javadoc:

```java
// ... Javadoc de clase existente, añade:
// @param canalId id del canal privado de Discord de la empresa (F4); null si aun no se ha creado
public record Empresa(long id, String rama, long duenoId, String nombre, int nivel, long bote,
                      Instant creada, Long canalId) {
}
```

- [ ] **Step 3: Test rojo de `Prestigio`**

Crea `PrestigioTest.java`:

```java
package com.gymprofit.bot.services;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PrestigioTest {

    @Test
    void nivelDominaSobreBote() {
        long nivelAlto = Prestigio.calcular(2, 0, 0);          // 20_000
        long boteEnorme = Prestigio.calcular(1, 0, 5_000_000); // 10_000 + 5_000
        assertThat(nivelAlto).isGreaterThan(boteEnorme);
    }

    @Test
    void sumaLasTresComponentes() {
        // 3*10_000 + 4*1_000 + 2_500_000/1_000 = 30_000 + 4_000 + 2_500 = 36_500
        assertThat(Prestigio.calcular(3, 4, 2_500_000)).isEqualTo(36_500L);
    }

    @Test
    void empresaVaciaSinBoteEsCero() {
        assertThat(Prestigio.calcular(0, 0, 0)).isZero();
    }

    @Test
    void miembrosDesempatanAIgualNivel() {
        assertThat(Prestigio.calcular(5, 3, 0)).isGreaterThan(Prestigio.calcular(5, 2, 0));
    }
}
```

- [ ] **Step 4: Corre el test, verifica que NO compila/falla**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PrestigioTest" test`
Expected: FAIL — `Prestigio` no existe.

- [ ] **Step 5: Implementa `Prestigio`**

```java
package com.gymprofit.bot.services;

/**
 * Score de <b>prestigio</b> de una empresa para el ranking de estatus (F4). Funcion pura y sin estado:
 * combina nivel, tamano de plantilla y bote en un unico numero comparable.
 */
public final class Prestigio {

    private Prestigio() {
    }

    /**
     * Calcula el prestigio. El <b>nivel</b> domina (x10.000: es 0-10 y se gana quemando bote), los
     * <b>miembros</b> premian el tamano (x1.000) y el <b>bote</b> pesa poco a proposito (/1.000) para no
     * premiar acaparar dinero liquido por encima de una empresa nivelada. Pesos tunables si el balance
     * en vivo lo pide.
     */
    public static long calcular(int nivel, int numMiembros, long bote) {
        return nivel * 10_000L + numMiembros * 1_000L + bote / 1_000L;
    }
}
```

- [ ] **Step 6: Corre el test, verifica verde**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd "-Dtest=PrestigioTest" test`
Expected: PASS (4 tests).

- [ ] **Step 7: `SELECT_EMPRESA`, `mapearEmpresa`, `deMiembro` con `canal_id`**

En `EmpresaRepositorio.java`:
- Añade `canal_id` al final de la lista de columnas de la constante `SELECT_EMPRESA` (y a la lista explícita de `deMiembro`, que enumera `e.id, e.rama, ... e.creada` → añade `, e.canal_id`).
- En `mapearEmpresa`, lee la columna como `Long` que admite null:

```java
private static Empresa mapearEmpresa(ResultSet rs) throws SQLException {
    long canalId = rs.getLong("canal_id");
    return new Empresa(
            rs.getLong("id"), rs.getString("rama"), rs.getLong("dueno_discord_id"),
            rs.getString("nombre"), rs.getInt("nivel"), rs.getLong("bote"),
            rs.getTimestamp("creada").toInstant(),
            rs.wasNull() ? null : canalId); // canal_id es NULL hasta que se crea el canal
}
```

- [ ] **Step 8: `fijarCanal`**

Añade al repo (con Javadoc):

```java
/** Persiste el id del canal privado recien creado de una empresa (creacion perezosa de F4). */
public void fijarCanal(long empresaId, long canalId) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(
                 "UPDATE empresas SET canal_id = ? WHERE id = ?")) {
        ps.setLong(1, canalId);
        ps.setLong(2, empresaId);
        ps.executeUpdate();
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo fijar el canal de la empresa " + empresaId, e);
    }
}
```

(Usa la misma clase de excepción `DatabaseException` y el mismo estilo try-with-resources que el resto del repo. Copia el patrón exacto de `incrementarBote`/`subirNivel`.)

- [ ] **Step 9: Record `EmpresaRanking` + método `ranking`**

Añade el record (arriba, junto a los demás records del repo o como archivo aparte si el repo agrupa records dentro): declara dentro de `EmpresaRepositorio` un record anidado público, o al lado. Usa este:

```java
/** Fila del ranking de empresas (F4): datos minimos para pintar la tabla de prestigio. */
public record EmpresaRanking(String nombre, String rama, int nivel, int miembros, long bote) {
}
```

Y el método (cuenta de miembros por empresa con LEFT JOIN para incluir empresas de 0 miembros):

```java
/**
 * Datos crudos de todas las empresas para el ranking de prestigio (F4): nombre, rama, nivel, bote y
 * el numero de miembros (LEFT JOIN para no perder empresas sin miembros). El orden por prestigio lo
 * pone el service con {@link com.gymprofit.bot.services.Prestigio}, no el SQL.
 */
public List<EmpresaRanking> ranking() {
    String sql = "SELECT e.nombre, e.rama, e.nivel, e.bote, COUNT(m.discord_id) AS miembros "
            + "FROM empresas e LEFT JOIN empresa_miembros m ON m.empresa_id = e.id "
            + "GROUP BY e.id, e.nombre, e.rama, e.nivel, e.bote";
    List<EmpresaRanking> lista = new ArrayList<>();
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            lista.add(new EmpresaRanking(
                    rs.getString("nombre"), rs.getString("rama"), rs.getInt("nivel"),
                    rs.getInt("miembros"), rs.getLong("bote")));
        }
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo leer el ranking de empresas", e);
    }
    return lista;
}
```

- [ ] **Step 10: Test de repo `ranking` (Testcontainers)**

En `EmpresaRepositorioTest.java` (sigue el patrón `@Testcontainers` + `@Container MySQLContainer` + Flyway migrate del resto de tests de repos con BD; si el archivo no existe, créalo copiando la infraestructura de otro `*RepositorioTest` con Testcontainers). Añade:

```java
@Test
void rankingCuentaMiembrosEIncluyeEmpresasVacias() {
    long conDos = repo.fundar("SALUD", 1L, "Alfa");     // el dueno cuenta como miembro? -> ver nota
    repo.anadirMiembro(conDos, 2L, RangoEmpresa.EMPLEADO);
    long vacia = repo.fundar("TECNICA", 3L, "Beta");
    repo.quitarMiembro(vacia, 3L); // fuerza 0 miembros para el caso LEFT JOIN

    List<EmpresaRepositorio.EmpresaRanking> r = repo.ranking();

    assertThat(r).extracting(EmpresaRepositorio.EmpresaRanking::nombre)
            .containsExactlyInAnyOrder("Alfa", "Beta");
    assertThat(r).filteredOn(e -> e.nombre().equals("Beta"))
            .singleElement().extracting(EmpresaRepositorio.EmpresaRanking::miembros).isEqualTo(0);
}
```

Nota: comprueba si `repo.fundar` inserta al dueño en `empresa_miembros` (mira su implementación); ajusta el número esperado de miembros de "Alfa" a lo que realmente inserte. El objetivo del test es (a) que el COUNT agrupa bien y (b) que una empresa sin miembros aparece con `miembros=0`.

- [ ] **Step 11: `clean verify` + commit**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS, `Tests run:` = 525 + los nuevos (PrestigioTest 4 + repo ranking 1).

```bash
git add src/main/resources/db/migration/V30__empresa_canal.sql src/main/java/com/gymprofit/bot/services/Prestigio.java src/test/java/com/gymprofit/bot/services/PrestigioTest.java src/main/java/com/gymprofit/bot/db/Empresa.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java
git commit -m "feat(empresas): V30 canal_id + prestigio y ranking en el repo"
```

---

### Task 2: `/empresa ranking` (comando + embed) + i18n

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/EmpresaService.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: `EmpresaService.ranking(limite)`**

Añade (usa `Prestigio` + `repo.ranking`; ordena descendente y recorta). Define un record de salida en el service (o reutiliza `EmpresaRanking` del repo añadiéndole el prestigio calculado — más limpio un record propio del service para no mezclar capas):

```java
/** Fila del ranking ya puntuada y ordenada para la vista (F4). */
public record FilaRanking(String nombre, String rama, int nivel, int miembros, long bote, long prestigio) {
}

/**
 * Top de empresas por prestigio (F4). Trae todas (son pocas), puntua cada una con
 * {@link Prestigio#calcular} y devuelve las {@code limite} de mayor prestigio, de mayor a menor.
 */
public List<FilaRanking> ranking(int limite) {
    return repo.ranking().stream()
            .map(e -> new FilaRanking(e.nombre(), e.rama(), e.nivel(), e.miembros(), e.bote(),
                    Prestigio.calcular(e.nivel(), e.miembros(), e.bote())))
            .sorted(java.util.Comparator.comparingLong(FilaRanking::prestigio).reversed())
            .limit(limite)
            .toList();
}
```

- [ ] **Step 2: Registra el subcomando `ranking`**

En `EmpresaComando.java`, dentro de `addSubcommands(...)`, añade `sub("ranking", "comando.empresa.ranking.desc")` (sin opciones). Añade al `switch (sub)`:

```java
case "ranking" -> ranking(evento, locale);
```

- [ ] **Step 3: Método `ranking` en el comando**

Añade una constante `private static final int TOP = 10;` y el método (mira la firma exacta de `EmbedFactory.base(...)` en `TopComando`/otros usos y respétala; abajo va la forma habitual `base(Tipo, locale, titulo)` + `setDescription`):

```java
/**
 * Pinta el top {@value #TOP} de empresas por prestigio (F4). Publico. Reusa el estilo de podio de
 * {@code TopComando} (medallas para el podio, numero para el resto).
 */
private void ranking(SlashCommandInteractionEvent evento, Locale locale) {
    List<EmpresaService.FilaRanking> top = empresa.ranking(TOP);
    if (top.isEmpty()) {
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "empresa.ranking.titulo"))
                .setDescription(Messages.get(locale, "empresa.ranking.vacio")).build()).queue();
        return;
    }
    StringBuilder sb = new StringBuilder();
    int puesto = 1;
    for (EmpresaService.FilaRanking f : top) {
        String medalla = switch (puesto) {
            case 1 -> "🥇"; // 🥇
            case 2 -> "🥈"; // 🥈
            case 3 -> "🥉"; // 🥉
            default -> "**" + puesto + ".**";
        };
        sb.append(Messages.get(locale, "empresa.ranking.fila")
                .replace("{medalla}", medalla)
                .replace("{nombre}", f.nombre())
                .replace("{rama}", Messages.get(locale, "rama." + f.rama().toLowerCase(Locale.ROOT)))
                .replace("{nivel}", String.valueOf(f.nivel()))
                .replace("{miembros}", String.valueOf(f.miembros()))
                .replace("{bote}", String.valueOf(f.bote())))
                .append('\n');
        puesto++;
    }
    evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
            Messages.get(locale, "empresa.ranking.titulo"))
            .setDescription(sb.toString()).build()).queue();
}
```

Nota: si `EmbedFactory.base` tiene otra firma (p. ej. devuelve `MessageEmbed` ya construido, o toma descripción), adáptalo a como lo usan `TopComando` y los demás métodos de `EmpresaComando` — NO inventes API.

- [ ] **Step 4: i18n ES**

En `messages_es.properties`:

```properties
comando.empresa.ranking.desc=Ranking de empresas por prestigio
empresa.ranking.titulo=🏆 Ranking de empresas
empresa.ranking.fila={medalla} **{nombre}** — {rama} · Nv.{nivel} · {miembros} miembros · 💰 {bote}
empresa.ranking.vacio=Aún no hay ninguna empresa fundada. ¡Sé el primero!
```

- [ ] **Step 5: i18n EN**

En `messages_en.properties`:

```properties
comando.empresa.ranking.desc=Company prestige ranking
empresa.ranking.titulo=🏆 Company ranking
empresa.ranking.fila={medalla} **{nombre}** — {rama} · Lv.{nivel} · {miembros} members · 💰 {bote}
empresa.ranking.vacio=No company has been founded yet. Be the first!
```

- [ ] **Step 6: `clean verify` + commit**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS. (Verifica a mano que no falta ninguna clave i18n en un idioma: no debe haber claves en ES que no estén en EN.)

```bash
git add src/main/java/com/gymprofit/bot/services/EmpresaService.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(empresas): /empresa ranking (top de prestigio)"
```

---

### Task 3: Canal privado — `EmpresaCanal` + `ensureCanal` + hooks (LLEVA REVIEW)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaCanal.java`
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java`
- Modify: `src/main/java/com/gymprofit/bot/events/EmpresaBotonesListener.java`

**Contexto:** copia el patrón EXACTO de `GremioCanal.java` + cómo lo cablea `GremioComando.java` (líneas 88 crear, 130 añadir, 148/166 quitar, 182 eliminar). Todo es **best-effort**: si el bot no puede gestionar canales, se registra y se sigue. `EmpresaBotonesListener` y `EmpresaComando` YA tienen acceso al `Guild` vía `evento.getGuild()`.

- [ ] **Step 1: `EmpresaCanal` (espejo de `GremioCanal`)**

Crea `EmpresaCanal.java` idéntico en estructura a `GremioCanal`, cambiando solo el prefijo del canal a `🏢・` y el nombre de la clase/logger:

```java
package com.gymprofit.bot.commands.economia;

import java.util.EnumSet;
import java.util.function.LongConsumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestion del canal privado de una empresa (F4) con permisos <b>de miembro</b> (sin rol, para no gastar
 * el cupo de roles). El canal deniega la vista a {@code @everyone} y la concede a cada miembro. Todo es
 * best-effort: si el bot no puede gestionar canales, se registra y se sigue (la empresa existe igual).
 */
final class EmpresaCanal {

    private static final Logger log = LoggerFactory.getLogger(EmpresaCanal.class);

    /** Permisos que se conceden a cada miembro en el canal de su empresa (ver + escribir + historial). */
    private static final EnumSet<Permission> PERMISOS = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);

    private EmpresaCanal() {
    }

    /** Crea el canal privado y devuelve su id por callback (solo si se creo). */
    static void crear(Guild guild, String nombre, long duenoId, LongConsumer onCreado) {
        guild.createTextChannel("🏢・" + nombre)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(duenoId, PERMISOS, null)
                .queue(canal -> onCreado.accept(canal.getIdLong()),
                        err -> log.warn("No se pudo crear el canal de la empresa {}", nombre, err));
    }

    /** Da acceso al canal a un miembro. */
    static void anadir(Guild guild, Long canalId, long miembroId) {
        TextChannel canal = canal(guild, canalId);
        if (canal == null) {
            return;
        }
        guild.retrieveMemberById(miembroId).queue(
                m -> canal.upsertPermissionOverride(m).grant(PERMISOS).queue(null,
                        e -> log.warn("No se pudo dar acceso a {} en el canal de empresa {}", miembroId, canalId, e)),
                e -> log.warn("No se pudo recuperar al miembro {} para el canal de empresa {}", miembroId, canalId, e));
    }

    /** Quita el acceso al canal a un miembro. */
    static void quitar(Guild guild, Long canalId, long miembroId) {
        TextChannel canal = canal(guild, canalId);
        if (canal == null) {
            return;
        }
        guild.retrieveMemberById(miembroId).queue(m -> {
            var ov = canal.getPermissionOverride(m);
            if (ov != null) {
                ov.delete().queue(null, e -> log.warn("No se pudo quitar a {} del canal de empresa {}", miembroId, canalId, e));
            }
        }, e -> log.warn("No se pudo recuperar al miembro {} para quitarlo del canal {}", miembroId, canalId, e));
    }

    /** Borra el canal de la empresa. */
    static void eliminar(Guild guild, Long canalId) {
        TextChannel canal = canal(guild, canalId);
        if (canal != null) {
            canal.delete().queue(null, e -> log.warn("No se pudo borrar el canal de empresa {}", canalId, e));
        }
    }

    /** Resuelve el canal por id, o null si no existe / id nulo. */
    private static TextChannel canal(Guild guild, Long canalId) {
        return canalId == null ? null : guild.getTextChannelById(canalId);
    }
}
```

**IMPORTANTE:** lee `GremioCanal.java` real antes de escribir esto y copia sus imports/firmas exactos (la versión de arriba puede diferir en detalles de la API de JDA de tu versión — el `GremioCanal` del repo es la fuente de verdad de las firmas correctas).

- [ ] **Step 2: `ensureCanal` en `EmpresaComando`**

Añade un helper privado que materializa el canal perezosamente y resincroniza a todos los miembros:

```java
/**
 * Garantiza que la empresa tiene canal privado (F4, creacion perezosa): si {@code canalId} es null, lo
 * crea, persiste su id y resincroniza a TODOS los miembros actuales. Cubre las empresas fundadas antes
 * de F4. Best-effort: si el bot no puede crear canales, no pasa nada (se reintenta la proxima vez).
 */
private void ensureCanal(Guild guild, Empresa emp) {
    if (guild == null || emp.canalId() != null) {
        return;
    }
    EmpresaCanal.crear(guild, emp.nombre(), emp.duenoId(), canalId -> {
        repo.fijarCanal(emp.id(), canalId);
        // Resincroniza a todos los miembros actuales (el dueno ya tiene override en la creacion).
        for (MiembroEmpresa m : repo.miembros(emp.id())) {
            if (m.discordId() != emp.duenoId()) {
                EmpresaCanal.anadir(guild, canalId, m.discordId());
            }
        }
    });
}
```

(`EmpresaComando` ya tiene el campo `repo` (`EmpresaRepositorio`). `Empresa`, `MiembroEmpresa`, `Guild` ya se importan o impórtalos.)

- [ ] **Step 3: Hook en `fundar`**

En `EmpresaComando.fundar`, tras un fundar OK (donde tienes el `empresaId` de `SalidaFundar`), crea el canal inmediatamente. Recupera la empresa recién creada (`repo.porId(empresaId)`) o llama a `EmpresaCanal.crear` directo con el nombre y el usuario:

```java
// tras SalidaFundar OK (ya hay Guild): crea el canal y persiste su id. Best-effort.
Guild guild = evento.getGuild();
if (guild != null) {
    EmpresaCanal.crear(guild, nombre, evento.getUser().getIdLong(),
            canalId -> repo.fijarCanal(salida.empresaId(), canalId));
}
```

(Ajusta `nombre`/`salida` a los identificadores reales del método. El dueño es quien funda → `evento.getUser().getIdLong()`.)

- [ ] **Step 4: Hook en `info`**

Al principio de `EmpresaComando.info`, tras resolver la empresa del invocador (el `Empresa`/`InfoEmpresa` que ya obtiene), llama a `ensureCanal(evento.getGuild(), emp)` **solo cuando el invocador mira su PROPIA empresa** (no cuando busca otra por nombre — solo materializamos el canal de la empresa a la que perteneces). Coloca la llamada donde ya tengas el objeto `Empresa` del miembro.

- [ ] **Step 5: Hook en `disolver` (comando + listener)**

`disolver` se confirma por botón (`EmpresaBotonesListener.disolver`). Antes de disolver, capta el `canalId`; tras OK, elimínalo. En el punto donde se ejecuta el disolver definitivo (mira si es `EmpresaComando.disolver` que abre confirmación y `EmpresaBotonesListener.disolver` que ejecuta):

```java
// justo antes de empresa.disolver(...), guarda el canal a borrar:
Long canalId = empresa.infoDe(userId).map(i -> i.empresa().canalId()).orElse(null);
ResultadoDisolver r = empresa.disolver(userId);
if (r == ResultadoDisolver.OK && evento.getGuild() != null && canalId != null) {
    EmpresaCanal.eliminar(evento.getGuild(), canalId);
}
```

(Adapta `empresa.infoDe`/el getter real del `canalId` y el `userId` al código existente.)

- [ ] **Step 6: Hook en ingreso (`EmpresaBotonesListener.resolver`)**

En `resolver`, cuando `ResultadoResolver` es `ACEPTADO`, el objetivo entra en la empresa: materializa el canal si hace falta y añade al nuevo miembro. Necesitas la `Empresa` del que acepta (o del pendiente). Tras el `ACEPTADO`:

```java
if (r == ResultadoResolver.ACEPTADO && evento.getGuild() != null) {
    empresa.infoDe(nuevoMiembroId).ifPresent(info -> {
        Empresa emp = info.empresa();
        if (emp.canalId() == null) {
            // materializa perezosamente y sincroniza a todos (incluye al recien llegado)
            EmpresaCanal.crear(evento.getGuild(), emp.nombre(), emp.duenoId(), canalId -> {
                repo.fijarCanal(emp.id(), canalId);
                for (MiembroEmpresa m : repo.miembros(emp.id())) {
                    if (m.discordId() != emp.duenoId()) {
                        EmpresaCanal.anadir(evento.getGuild(), canalId, m.discordId());
                    }
                }
            });
        } else {
            EmpresaCanal.anadir(evento.getGuild(), emp.canalId(), nuevoMiembroId);
        }
    });
}
```

(`EmpresaBotonesListener` necesitará el `EmpresaRepositorio repo` — pásalo por el constructor y actualiza el `new EmpresaBotonesListener(...)` en `Main.java`. `nuevoMiembroId` = el `discordId` del que acepta/es aprobado; sácalo del pendiente o del evento según el flujo real de `resolver`.)

- [ ] **Step 7: Hook en sacar/despedir (dueño directo y voto)**

`SACAR`/`DESPEDIR` quitan al objetivo de la empresa. Hay dos caminos:
- **Dueño directo** en `EmpresaComando.gestionar`: tras `ResultadoGestion.EJECUTADA` y solo si `tipo == SACAR || tipo == DESPEDIR`, `EmpresaCanal.quitar(guild, canalId, objetivoId)`.
- **Voto aprobado** en `EmpresaBotonesListener.votar`: tras `ResultadoVoto.APROBADA_EJECUTADA`, necesitas el `tipo` y el `objetivo` de la propuesta. La propuesta ya se leyó para ejecutar; si el listener no los tiene a mano, exponlos (el `votar` del service podría devolverlos, o el listener recupera la propuesta por id antes de votar). Solo para `SACAR`/`DESPEDIR`.

Para el `canalId` del objetivo: es el de la empresa del actor/propuesta (`repo.porId(empresaId).map(Empresa::canalId)` o vía `empresa.infoDe(actorId)`). Representativo (camino directo):

```java
if (r == ResultadoGestion.EJECUTADA && (tipo == TipoPropuesta.SACAR || tipo == TipoPropuesta.DESPEDIR)
        && evento.getGuild() != null) {
    empresa.infoDe(actorId).map(i -> i.empresa().canalId()).ifPresent(canalId ->
            EmpresaCanal.quitar(evento.getGuild(), canalId, objetivoId));
}
```

(No añadas canal-sync para `CAMBIAR_RANGO` ni `ASCENSO`.)

- [ ] **Step 8: `clean verify`**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS, 525 + nuevos, 0 fallos. La interacción real con JDA no se testea en unidad (best-effort) → queda pendiente de smoke test en vivo; deja constancia en el commit/resumen.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/gymprofit/bot/commands/economia/EmpresaCanal.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/java/com/gymprofit/bot/events/EmpresaBotonesListener.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): canal privado por empresa con creacion perezosa"
```

**Esta task lleva review** (permisos de canal + sincronización de estado entre BD y Discord).

---

### Task 4: Documentación + verify final

**Files:**
- Modify: `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: `docs/decisions.md` — ADR-019** (comprueba que el último es 018):

```markdown
## ADR-019 — estatus de empresas

**Estado:** aceptada e implementada (Fase 4).

**Contexto.** Tras la economía (F3) las empresas necesitaban un motivo para competir y un espacio propio.

**Decisión.** Un **ranking de prestigio** (`/empresa ranking`) ordena las empresas por un score puro
`nivel*10.000 + miembros*1.000 + bote/1.000` (el nivel domina; el bote pesa poco a propósito para no
premiar acaparar). Cada empresa tiene un **canal privado** por permisos de miembro (sin rol, como los
gremios), para **todas** las empresas y con **creación perezosa**: si `canal_id` es null, la primera
acción relevante (fundar, ingreso o `/empresa info`) lo crea y sincroniza a los miembros actuales. Toda
la lógica de canal vive en la capa comando/listener (necesita el `Guild`); los services siguen
guild-agnostic. Best-effort: sin permisos, se registra y se sigue.

**Consecuencias.** Migración V30 (columna `canal_id` en `empresas`). Los cosméticos comprables se
difieren a una mini-fase posterior. La competición por rama (cuota de mercado) queda para F5.
```

- [ ] **Step 2: `docs/architecture.md`** — añade la viñeta F4 al bloque de empresas (tras la de F3) y sube el rango de migraciones a **V6–V30** (añade "estatus (ranking + canal privado)" a la lista):

```markdown
- **Empresas (Fase 4)**: estatus. `/empresa ranking` ordena por un prestigio puro (nivel, miembros,
  bote) y cada empresa tiene un **canal privado** por permisos de miembro (sin rol), para todas y con
  creación perezosa (lo materializa la primera acción relevante). La lógica de canal vive en la capa
  comando/listener; los services siguen guild-agnostic. Migración V30 (`canal_id`).
```

- [ ] **Step 3: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`, encima de la entrada de F3:

```markdown
- **Empresas (Fase 4)** (`/empresa ranking`): tabla de prestigio de las empresas (nivel + miembros +
  bote) y un **canal privado** por empresa (permisos de miembro, sin roles), creado perezosamente para
  todas. Migración `V30`.
```

- [ ] **Step 4: READMEs** — en `README.md` y `README.en.md`, añade `ranking` a la lista de subcomandos de `/empresa` (que ya tiene `… propuestas · mejorar · ascender`).

- [ ] **Step 5: `clean verify` final + commit**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS. Pega el `Tests run:`.

```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 4 estatus — ADR-019, architecture, changelog y READMEs"
```

---

## Despliegue (tras cerrar las 4 tasks)

**Reiniciar bot** (aplica V30 + subcomando `ranking` + hooks de canal). **No** requiere `/setup`.
Smoke test en vivo: `/empresa ranking`; materialización del canal de una empresa vieja al correr
`/empresa info`; sincronización de acceso al entrar (invitación/solicitud), salir (sacar/despedir) y
disolver. Confirma que el bot tiene permiso **Gestionar canales** en el servidor.

## Notas de riesgo

- **Sincronización BD↔Discord best-effort:** si el bot pierde el permiso de gestionar canales, el
  `canal_id` puede quedar apuntando a un canal que ya no existe o no sincronizar. Es aceptado (como en
  gremios): los helpers tratan el canal inexistente como no-op. No se persigue consistencia fuerte.
- **`ensureCanal` en `info`** solo para la empresa propia del invocador, no al buscar otras por nombre.
