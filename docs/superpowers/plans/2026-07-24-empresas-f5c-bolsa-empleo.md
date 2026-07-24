# Empresas Fase 5c — Bolsa de empleo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un tablón público `/empleo ver` para descubrir empresas que contratan (flag opt-in por empresa, `/empleo contratar`) y solicitar entrar desde el tablón con un botón + modal de motivo, reutilizando el flujo de solicitud/aprobación de F1.

**Architecture:** Una columna `empresas.contratando` (V33) + un método `solicitarPorId` que clona la validación de `solicitar` pero recibe el id (el botón lleva id, no nombre). Un `EmpleoComando` (`ver` tablón / `contratar` toggle) y un `EmpleoListener` (`ListenerAdapter`) que abre el modal en el botón y, al enviarse, crea la SOLICITUD; el dueño la resuelve por el flujo de pendientes de F1 (intacto).

**Tech Stack:** Java 21, JDA 5, JDBC, Flyway, JUnit 5 + Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-24-empresas-f5c-bolsa-empleo-design.md`.

**Precondición:** F5b desplegada y funcionando.

**Nota Discord (refinamiento):** un slash command no puede tener a la vez invocación base y subcomandos.
Como el toggle es `/empleo contratar`, el tablón es **`/empleo ver`** (dos subcomandos: `ver` + `contratar`).

**Convenciones (obligatorias):** dominio español; i18n ES+EN en AMBOS `messages_*.properties`; embeds solo
por `EmbedFactory`; `Messages.get` = MessageFormat posicional; Javadoc cabecera + inline del *porqué*;
migraciones Flyway; una clase por slash command en `commands/`. Build:
`$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd clean verify` (SIEMPRE `clean`; no dos
builds a la vez; `-Dtest=A,B` entrecomillado). Baseline: **555 tests**.

## File Structure

- **Create** `src/main/resources/db/migration/V33__empresa_contratando.sql`
- **Create** `src/main/java/com/gymprofit/bot/commands/economia/EmpleoComando.java`
- **Create** `src/main/java/com/gymprofit/bot/events/EmpleoListener.java`
- **Modify** `src/main/java/com/gymprofit/bot/db/Empresa.java` — campo `boolean contratando`.
- **Modify** `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java` — `contratando` en lecturas; `fijarContratando`; `contratandoDeRama`.
- **Modify** `src/main/java/com/gymprofit/bot/services/EmpresaService.java` — `solicitarPorId`; toggle helper.
- **Modify** `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` — `🟢 Contratando` en `info`.
- **Modify** `src/main/java/com/gymprofit/bot/Main.java` — registrar `EmpleoComando` + `EmpleoListener`.
- **Modify** `messages_es.properties`, `messages_en.properties`.
- **Modify** docs (`decisions.md`, `architecture.md`, `CHANGELOG.md`, READMEs).

---

### Task 1: V33 + `contratando` en el repo + `solicitarPorId`

**Files:**
- Create: `src/main/resources/db/migration/V33__empresa_contratando.sql`
- Modify: `src/main/java/com/gymprofit/bot/db/Empresa.java`
- Modify: `src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java`
- Modify: `src/main/java/com/gymprofit/bot/services/EmpresaService.java`
- Test: `src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java`, `src/test/java/com/gymprofit/bot/services/EmpresaServiceTest.java`

- [ ] **Step 1: Migración V33**
```sql
-- V33: flag de "esta empresa contrata" (F5c bolsa de empleo). Opt-in: por defecto cerrada. La lista
-- /empleo solo muestra las que lo tienen activo, de la rama del que busca.
ALTER TABLE empresas ADD COLUMN contratando BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 2: `Empresa` record gana `boolean contratando`** (último parámetro, tras `impagos`; Javadoc: "abierta a solicitudes en la bolsa de empleo (F5c); false por defecto"). Rompe los `new Empresa(...)` → Step 4.

- [ ] **Step 3: `contratando` en las lecturas del repo** — añade `contratando` a `SELECT_EMPRESA` y al SELECT de `deMiembro`; en `mapearEmpresa`, `rs.getBoolean("contratando")` como último arg.

- [ ] **Step 4: Arregla los `new Empresa(...)`** (`grep -rn "new Empresa(" src`) añadiendo `false`.

- [ ] **Step 5: `fijarContratando` + `contratandoDeRama`** en el repo:
```java
/** Marca si la empresa aparece en la bolsa de empleo (F5c). */
public void fijarContratando(long empresaId, boolean valor) {
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement("UPDATE empresas SET contratando = ? WHERE id = ?")) {
        ps.setBoolean(1, valor);
        ps.setLong(2, empresaId);
        ps.executeUpdate();
    } catch (SQLException e) {
        throw new DatabaseException("No se pudo fijar contratando de la empresa " + empresaId, e);
    }
}

/** Empresas de una rama abiertas a solicitudes (F5c), las mas fuertes primero. */
public List<Empresa> contratandoDeRama(String rama) {
    List<Empresa> lista = new ArrayList<>();
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(
                 SELECT_EMPRESA + " WHERE contratando = TRUE AND rama = ? ORDER BY nivel DESC, nombre ASC")) {
        ps.setString(1, rama);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearEmpresa(rs));
            }
        }
    } catch (SQLException e) {
        throw new DatabaseException("No se pudieron listar empresas contratando de la rama " + rama, e);
    }
    return lista;
}
```

- [ ] **Step 6: Test rojo `solicitarPorId`** en `EmpresaServiceTest` (mira cómo se testea `solicitar`: mockea `personajes.obtenerOCrear`, `repo.deMiembro`, `repo.porId`, `repo.crearPendiente`). Casos:
  - OK: personaje con trabajo de rama X, no en empresa, empresa id existe y es de rama X → `crearPendiente(id, discordId, SOLICITUD, motivo)`.
  - `SIN_TRABAJO`: personaje sin trabajo.
  - `YA_EN_EMPRESA`: `deMiembro` presente.
  - `EMPRESA_NO_EXISTE`: `porId` vacío.
  - `OTRA_RAMA`: empresa de rama distinta a la del solicitante.

- [ ] **Step 7: Implementa `solicitarPorId`** en `EmpresaService` (clon de `solicitar` pero por id; NO dupliques reglas: extrae lo común si es limpio, o replica la validación con un comentario de que sigue a `solicitar`):
```java
/**
 * Solicitud de ingreso por <b>id</b> de empresa (F5c, desde el boton del tablon /empleo). Misma
 * validacion que {@link #solicitar} por nombre: hace falta trabajo (rama), no estar ya en otra empresa,
 * la empresa existe y es de tu rama. Crea una SOLICITUD que resolvera el dueño (flujo de F1).
 */
public ResultadoIngreso solicitarPorId(long discordId, long empresaId, String motivo) {
    Personaje p = personajes.obtenerOCrear(discordId);
    if (p.trabajo() == null) {
        return ResultadoIngreso.SIN_TRABAJO;
    }
    if (repo.deMiembro(discordId).isPresent()) {
        return ResultadoIngreso.YA_EN_EMPRESA;
    }
    Optional<Empresa> empresa = repo.porId(empresaId);
    if (empresa.isEmpty()) {
        return ResultadoIngreso.EMPRESA_NO_EXISTE;
    }
    Ascensos.Rama rama = ramaDe(p.trabajo());
    if (!empresa.get().rama().equals(rama.name())) {
        return ResultadoIngreso.OTRA_RAMA;
    }
    return crearPendiente(empresa.get().id(), discordId, TipoPendiente.SOLICITUD, recortar(motivo));
}
```
(Confirma que `ResultadoIngreso` tiene `OTRA_RAMA` — sí, de F1. `crearPendiente`/`recortar`/`ramaDe` ya existen y son privados del service.)

- [ ] **Step 8: Test de repo (Testcontainers)** — `contratandoDeRama` filtra por flag y rama, ordena; `fijarContratando` persiste; `contratando` default false. Sigue el patrón `@Testcontainers` del archivo.

- [ ] **Step 9: `clean verify` + commit** (~559 tests):
```bash
git add src/main/resources/db/migration/V33__empresa_contratando.sql src/main/java/com/gymprofit/bot/db/Empresa.java src/main/java/com/gymprofit/bot/db/EmpresaRepositorio.java src/main/java/com/gymprofit/bot/services/EmpresaService.java src/test/java/com/gymprofit/bot/db/EmpresaRepositorioTest.java src/test/java/com/gymprofit/bot/services/EmpresaServiceTest.java
# + tests con new Empresa( tocados
git commit -m "feat(empresas): V33 contratando + solicitarPorId (bolsa de empleo)"
```

---

### Task 2: `/empleo ver` + `/empleo contratar` + `🟢 Contratando` en info

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/economia/EmpleoComando.java`
- Modify: `src/main/java/com/gymprofit/bot/services/EmpresaService.java` (toggle)
- Modify: `src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java` (info)
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `messages_es.properties`, `messages_en.properties`

- [ ] **Step 1: Toggle en `EmpresaService`** (o donde encaje; reusa la comprobación de alto cargo como `EmpresaVentaService.esAltoCargo` con `repo.altosCargos`):
```java
/** Resultado de alternar el flag de contratacion (F5c). */
public enum ResultadoContratar { ABIERTA, CERRADA, SIN_EMPRESA, NO_AUTORIZADO }

/** Alterna si la empresa del actor aparece en la bolsa de empleo. Solo DUENO/DIRECTIVO. */
public ResultadoContratar alternarContratando(long actorId) {
    Optional<Empresa> emp = repo.deMiembro(actorId);
    if (emp.isEmpty()) {
        return ResultadoContratar.SIN_EMPRESA;
    }
    boolean altoCargo = repo.altosCargos(emp.get().id()).stream().anyMatch(m -> m.discordId() == actorId);
    if (!altoCargo) {
        return ResultadoContratar.NO_AUTORIZADO;
    }
    boolean nuevo = !emp.get().contratando();
    repo.fijarContratando(emp.get().id(), nuevo);
    return nuevo ? ResultadoContratar.ABIERTA : ResultadoContratar.CERRADA;
}
```

- [ ] **Step 2: `EmpleoComando`** (`implements Comando`) con dos subcomandos `ver` y `contratar`. Constructor recibe `EmpresaService` y lo necesario para resolver la rama del invocador (`PersonajeRepositorio` + `Trabajos`/`Ascensos`, o un método del service; mira cómo `TrabajoComando`/`EmpresaService` resuelven la rama de un jugador y reúsalo). `data()` (o el método de definición del proyecto) declara los subcomandos con sus descripciones i18n. `ejecutar` enruta:
  - `contratar` → `empresa.alternarContratando(user)`, responde `empleo.contratar.abierta/cerrada/sin_empresa/no_autorizado`.
  - `ver` → resuelve la rama del invocador; sin trabajo → `empleo.sin_trabajo`; luego `repo.contratandoDeRama(rama)`:
    - vacío → `empleo.tablon.vacio`.
    - si el invocador ya está en una empresa (`deMiembro` presente) → lista SIN botones + nota `empleo.ya_en_empresa`.
    - si no → por cada empresa, una fila (`empleo.tablon.fila` con nombre/nivel/miembros/bote, MessageFormat posicional) y un botón **Solicitar** (`Button.primary("empleo:solicitar:" + emp.id(), Messages.get(locale,"empleo.solicitar.boton"))`). Embed `STATS`; trocea con `util/Embeds.partirEnBloques` si excede 4096, y reparte los botones en `ActionRow` (máx 5 botones por fila, 5 filas por mensaje → si hay muchas empresas, limita a las primeras N y avísalo, o pagina; para F5c basta un tope razonable, p. ej. 25, con nota si se recorta — **NO** dejes un recorte silencioso).
  - **Categoría del comando:** declara la categoría como el resto de `commands/economia` (revisa `Comando.categoria()` en `EmpresaComando`).

- [ ] **Step 3: Constante del customId** — en `EmpleoComando` o `EmpleoListener`, `public static final String BOTON_SOLICITAR = "empleo:solicitar";` (customId = `empleo:solicitar:<empresaId>`). Reúsala en el listener.

- [ ] **Step 4: `🟢 Contratando` en `/empresa info`** — en `EmpresaComando.info`, tras armar el cuerpo (variable `cuerpo`), añade condicional (sin renumerar):
```java
if (e.contratando()) {
    cuerpo += "\n" + Messages.get(locale, "empresa.info.contratando");
}
```

- [ ] **Step 5: Registro en `Main`** — `comandos.add(new EmpleoComando(...))` junto a `EmpresaComando`; el `EmpleoListener` se añade en la Task 3.

- [ ] **Step 6: i18n ES**
```properties
comando.empleo.desc=Bolsa de empleo: encuentra empresas que contratan
comando.empleo.ver.desc=Ver las empresas de tu rama que contratan
comando.empleo.contratar.desc=Abre o cierra tu empresa a solicitudes (dueño/directivo)
empleo.tablon.titulo=💼 Bolsa de empleo — {0}
empleo.tablon.fila=**{0}** · Nv.{1} · {2} miembros · 💰 {3} 🪙
empleo.tablon.vacio=No hay ninguna empresa de tu rama contratando ahora mismo.
empleo.sin_trabajo=Elige un trabajo primero: sin rama no hay empresa a la que entrar.
empleo.ya_en_empresa=Ya perteneces a una empresa, no puedes solicitar entrar en otra.
empleo.contratar.abierta=🟢 Tu empresa ahora **aparece** en la bolsa de empleo.
empleo.contratar.cerrada=🔴 Tu empresa ya **no aparece** en la bolsa de empleo.
empleo.contratar.sin_empresa=No perteneces a ninguna empresa.
empleo.contratar.no_autorizado=Solo el dueño o un directivo pueden abrir o cerrar las solicitudes.
empleo.solicitar.boton=Solicitar
empleo.solicitar.modal.titulo=Solicitar entrada
empleo.solicitar.modal.motivo=Motivo (opcional)
empleo.solicitar.enviada=✅ Solicitud enviada. El dueño de la empresa la revisará.
empleo.tablon.recorte=Mostrando las primeras {0} empresas.
empresa.info.contratando=🟢 Contratando
```

- [ ] **Step 7: i18n EN** (mismas claves, traducidas; `{0}` rama en el título, etc.).

- [ ] **Step 8: `clean verify` + commit** (paridad ES/EN a mano):
```bash
git add src/main/java/com/gymprofit/bot/commands/economia/EmpleoComando.java src/main/java/com/gymprofit/bot/services/EmpresaService.java src/main/java/com/gymprofit/bot/commands/economia/EmpresaComando.java src/main/java/com/gymprofit/bot/Main.java src/main/resources/messages_es.properties src/main/resources/messages_en.properties
git commit -m "feat(empresas): /empleo (tablon + toggle contratar) y contratando en info"
```

---

### Task 3: `EmpleoListener` — botón → modal → `solicitarPorId` (LLEVA REVIEW)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/events/EmpleoListener.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- (i18n ya añadido en Task 2)

**Contexto:** NO hay modales aún en el repo — este es el primero. Patrón JDA 5 abajo. El botón
`empleo:solicitar:<id>` abre un modal con el mismo `id`; al enviarse, se lee el motivo y se llama a
`solicitarPorId`. La resolución posterior (aprobar/rechazar) es el flujo de F1, **no se toca**.

- [ ] **Step 1: `EmpleoListener`** (`extends ListenerAdapter`), con `EmpresaService`:
```java
package com.gymprofit.bot.events;
// imports: ListenerAdapter, ButtonInteractionEvent, ModalInteractionEvent, Modal, TextInput,
// TextInputStyle, ActionRow, Messages, Locale, EmpresaService, ResultadoIngreso, EmpleoComando...

/**
 * Bolsa de empleo (F5c): el boton "Solicitar" del tablon /empleo abre un modal que pide el motivo; al
 * enviarlo se crea la SOLICITUD por id (misma validacion que /empresa solicitar) que resolvera el dueño
 * por el flujo de pendientes de F1. Primer uso de modales del bot.
 */
public final class EmpleoListener extends ListenerAdapter {

    private static final String PREFIJO = EmpleoComando.BOTON_SOLICITAR + ":"; // "empleo:solicitar:"
    private static final String INPUT_MOTIVO = "motivo";

    private final EmpresaService empresa;

    public EmpleoListener(EmpresaService empresa) { this.empresa = empresa; }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO)) { return; }
        Locale locale = /* idioma del guild como en el resto de listeners */;
        long empresaId = Long.parseLong(id.substring(PREFIJO.length()));
        TextInput motivo = TextInput.create(INPUT_MOTIVO,
                Messages.get(locale, "empleo.solicitar.modal.motivo"), TextInputStyle.PARAGRAPH)
                .setRequired(false).setMaxLength(300).build();
        Modal modal = Modal.create(PREFIJO + empresaId, Messages.get(locale, "empleo.solicitar.modal.titulo"))
                .addComponents(ActionRow.of(motivo)).build();
        evento.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent evento) {
        String id = evento.getModalId();
        if (!id.startsWith(PREFIJO)) { return; }
        Locale locale = /* idioma del guild */;
        long empresaId = Long.parseLong(id.substring(PREFIJO.length()));
        var campo = evento.getValue(INPUT_MOTIVO);
        String motivo = campo == null ? "" : campo.getAsString();
        EmpresaService.ResultadoIngreso r = empresa.solicitarPorId(evento.getUser().getIdLong(), empresaId, motivo);
        String msg = switch (r) {
            case OK -> Messages.get(locale, "empleo.solicitar.enviada");
            case SIN_TRABAJO -> Messages.get(locale, "empleo.sin_trabajo");
            case YA_EN_EMPRESA -> Messages.get(locale, "empleo.ya_en_empresa");
            case OTRA_RAMA -> Messages.get(locale, "empresa.ingreso.otra_rama"); // clave de error de F1
            case EMPRESA_NO_EXISTE -> Messages.get(locale, /* clave de error F1 de empresa inexistente */);
            case YA_PENDIENTE -> Messages.get(locale, /* clave F1 de ya-hay-pendiente */);
            case ES_MISMO -> Messages.get(locale, /* clave F1 */);
        };
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.<TIPO>, locale, msg)).setEphemeral(true).queue();
    }
}
```
**Ajusta:** (a) cómo se obtiene el `Locale` del guild — copia el patrón EXACTO de `EmpresaBotonesListener` (mira cómo saca el idioma). (b) Las claves i18n de error de `ResultadoIngreso` que ya usa `EmpresaComando` al mostrar el resultado de `/empresa solicitar` — **reúsalas** (grep `empresa.ingreso.` / cómo mapea `ResultadoIngreso` a texto en `EmpresaComando`), no inventes nuevas. (c) El `EmbedFactory.Tipo`/método real para un aviso efímero (mira cómo responde `EmpresaBotonesListener`). (d) El switch sobre `ResultadoIngreso` debe ser exhaustivo (todas las constantes del enum).

- [ ] **Step 2: Registro en `Main`** — `listeners.add(new EmpleoListener(empresaService));` junto a `EmpresaBotonesListener`.

- [ ] **Step 3: Verificar que JDA reparte modales** — `EmpresaBotonesListener` se añade como `ListenerAdapter` directo (no por el router); `EmpleoListener` igual → `onModalInteraction` llega solo. Confirma que en `Main` los listeners se registran en el `JDABuilder`/`addEventListeners` como los demás.

- [ ] **Step 4: `clean verify`** — BUILD SUCCESS, 559 tests (la interacción real de botón/modal no se testea en unidad → **pendiente de smoke test en vivo**; deja constancia). Si escribes algún test unitario del listener, que sea del mapeo `ResultadoIngreso`→clave (puro), no de JDA.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/gymprofit/bot/events/EmpleoListener.java src/main/java/com/gymprofit/bot/Main.java
git commit -m "feat(empresas): boton + modal de solicitud en la bolsa de empleo"
```

**Esta task lleva review** (nuevo camino de ingreso por id + primer modal + reuso de la validación de F1).

---

### Task 4: Documentación + verify final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`

- [ ] **Step 1: ADR-022** (comprueba que el último es 021):
```markdown
## ADR-022 — bolsa de empleo de empresas

**Estado:** aceptada e implementada (Fase 5c).

**Contexto.** Entrar en una empresa exigía conocer su nombre (`/empresa solicitar <nombre>`); no había
forma de descubrir quién contrata.

**Decisión.** Un flag opt-in `contratando` por empresa (lo alterna un alto cargo con `/empleo contratar`)
y un tablón `/empleo ver` que lista las empresas contratando **de la rama del que mira**, cada una con un
botón que abre un **modal** de motivo y crea una SOLICITUD por id (`solicitarPorId`, misma validación que
`solicitar`). La resolución la hace el dueño por el flujo de pendientes de F1, intacto. Es el primer uso
de modales del bot.

**Consecuencias.** Migración V33 (`empresas.contratando`). Nuevos `EmpleoComando` y `EmpleoListener`. El
control de entrada es el flag on/off (las empresas no tienen tope de tamaño). Vacantes por puesto/plazas
quedan fuera.
```

- [ ] **Step 2: `docs/architecture.md`** — viñeta F5c tras F5b; migraciones a **V6–V33**; menciona `EmpleoComando`/`EmpleoListener` si la sección de comandos lo pide.

- [ ] **Step 3: `CHANGELOG.md`** — bajo `## [Sin publicar]` / `### Añadido`, encima de F5b:
```markdown
- **Empresas (Fase 5c)** (`/empleo`): bolsa de empleo. Las empresas se marcan como contratando y los
  jugadores ven en `/empleo ver` las de su rama y solicitan entrar con un botón. Migración `V33`.
```

- [ ] **Step 4: READMEs** — añade `/empleo` a la lista de comandos en `README.md` y `README.en.md`.

- [ ] **Step 5: `clean verify` final + commit**
```bash
git add docs/decisions.md docs/architecture.md CHANGELOG.md README.md README.en.md
git commit -m "docs(empresas): fase 5c bolsa de empleo — ADR-022, architecture, changelog y READMEs"
```

---

## Despliegue (tras cerrar las 4 tasks)

**Reiniciar bot** (V33 + comandos `/empleo` + `EmpleoListener`). Añade slash commands nuevos → reiniciar
para re-registrarlos; **no** requiere `/setup`. Smoke test: un alto cargo abre su empresa con `/empleo
contratar`; otro jugador de la misma rama la ve en `/empleo ver`, pulsa Solicitar, rellena el motivo, y el
dueño aprueba (flujo F1) con alta en el canal privado; comprobar sin-trabajo y ya-en-empresa; `🟢
Contratando` en `/empresa info`.

## Notas de riesgo (para el review de la Task 3)

- **`solicitarPorId` no debe duplicar reglas** de `solicitar`: mismas comprobaciones (trabajo, no en otra,
  rama, existe), mismo `ResultadoIngreso`. La única diferencia es id vs nombre.
- **Modal**: primer uso; el `customId` del modal replica el del botón (`empleo:solicitar:<id>`) para
  arrastrar el id. `getValue` puede ser null si el campo va vacío (es opcional) → tratar como "".
- **Switch exhaustivo** sobre `ResultadoIngreso` en el listener (que no compile si falta una constante).
- **No recortar en silencio** el tablón: si hay más empresas que el tope de botones, avísalo con
  `empleo.tablon.recorte`.
