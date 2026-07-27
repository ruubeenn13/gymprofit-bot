# Moderación robusta + panel de tickets (F1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** AutoMod nativo y un anti-flood/invites propio alimentan el warn/escalado interno con DM al sancionado; `/warn quitar` expone la revocación; el fallo de cifrado deja de ser silencioso; `/setup` publica el panel de tickets.

**Architecture:** Un aplicador de sanciones reusable (`AplicadorSanciones`) centraliza escalado + log + DM (extraído de `WarnComando`); dos listeners (anti-abuso propio y AutoMod→warn) lo usan con un anti-ráfaga por usuario; detectores puros testeables para flood/invite; `/warn quitar` + el panel de tickets en `/setup`.

**Tech Stack:** Java 21 + JDA 5, JUnit 5 + Mockito.

**Convenciones:** dominio español; i18n ES **y** EN (nunca hardcodear); embeds vía `EmbedFactory`/`ModHelper.embed`; header Javadoc por archivo; secrets solo por env; una clase por comando/listener.

**Build:** `export JAVA_HOME="/c/Users/ruben/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH" && sh ./mvnw -B clean verify`. Siempre `clean verify`.

**Firmas reales (verificadas):**
- `ModeracionService`: `ResultadoAviso avisar(long guildId, long usuarioId, long moderadorId, String motivo)` con `record ResultadoAviso(long warnId, int warnsActivos, AccionEscalado accion)`; `enum AccionEscalado {NINGUNA, TIMEOUT_1H, TIMEOUT_24H, BAN}`; constantes `UMBRAL_*`, `TIMEOUT_1H_SEG=3600`, `TIMEOUT_24H_SEG=86400`; `void registrar(long guildId, long usuarioId, long moderadorId, String tipo, String motivo, String apodo, Long segundos)`; `boolean revocarWarn(long id)`; `int limpiarWarns(long usuarioId)`; `int contarWarnsActivos(long)`. Ctor `(WarnRepositorio, SancionRepositorio, UsuarioDiscordRepositorio, Cifrador)`.
- `WarnComando`: métodos privados `aplicarEscalado(Guild, Member, User, long moderadorId, AccionEscalado, Locale)` y `aplicarTimeout(...)` que llaman `moderacion.registrar(...)` + JDA (`objetivoMiembro.timeoutFor(Duration)`, `guild.ban(user,0,SECONDS).reason(...)`). Constante `RAZON_ESCALADO`.
- `ModHelper`: `boolean esAltoCargo(Member)`, `boolean puedeModerar(Member actor, Member objetivo)`, `Set<String> ROLES_ALTOS`, `void registrarEnLogs(Guild, ConfigServidorService, MessageEmbed)`, `MessageEmbed embed(Locale, String, String)`.
- Listeners se registran en `Main` con `listeners.add(new XListener(...))` (bloque ~396-563, bajo `jda != null`).
- Panel de tickets: `PublicarComando.BOTON_ABRIR` (customId `ticket:abrir`), canal `🎫・soporte`; `TicketListener` ya escucha el botón. `PublicarComando.java:231-262` arma el panel.
- `/setup`: `SetupComando` con `RegistroCambios` (colector 🆕/✏️/🗑️); `SetupServidorPlan` define canales (`🎫・soporte` :301, `🗄️・logs-tickets`, BOT_LOGS :317).

---

## Task 1: `AplicadorSanciones` (escalado + log + DM) + endurecer cifrado + `WarnComando` delega — con review

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/AplicadorSanciones.java` (JDA-aware; si el proyecto separa gateway de service, ponlo donde encaje — mira dónde vive código JDA+lógica; `commands/moderacion/` junto a `ModHelper` es válido).
- Modify: `src/main/java/com/gymprofit/bot/services/ModeracionService.java` (`ResultadoAviso.motivoGuardado`)
- Modify: `src/main/java/com/gymprofit/bot/commands/moderacion/WarnComando.java` (delegar)
- Modify: `messages_es.properties` / `messages_en.properties` (claves de DM + aviso de cifrado)
- Modify: `Main.java` (construir `AplicadorSanciones`, pasarlo a `WarnComando`)
- Test: `src/test/java/com/gymprofit/bot/services/AplicadorSancionesTest.java`, y actualizar `ModeracionServiceTest`.

- [ ] **Step 1: `ResultadoAviso.motivoGuardado` (test first).** En `ModeracionServiceTest` añadir: con `Cifrador` que cifra OK → `avisar(...).motivoGuardado() == true`; con `Cifrador` que devuelve null (sin `BOT_CRYPTO_KEY`) → `motivoGuardado() == false` y no lanza. (Mira cómo el test actual construye el `Cifrador`; si es final/no mockeable, usa un `Cifrador` real con y sin clave, o un stub.) Run → FAIL.

- [ ] **Step 2: implementar el flag.** Leer `ModeracionService.avisar` (líneas ~61-67) y el bloque de cifrado (~136-158). Cambiar `ResultadoAviso` a `record ResultadoAviso(long warnId, int warnsActivos, AccionEscalado accion, boolean motivoGuardado)`. En `avisar`: calcular `boolean guardado = (motivo == null) || (cif != null);` (motivo cifrado correctamente, o no había motivo que guardar) y pasarlo al record. Ajustar todos los sitios que construyen `ResultadoAviso`. Run ModeracionServiceTest → PASS (y los tests de escalado existentes siguen verdes: si construyen el record por posición, hay que añadirles el 4º arg — actualízalos).

- [ ] **Step 3: `AplicadorSancionesTest` (write first, FAIL).** Mockito con JDA mockeado (`Guild`, `Member`, `User`, `PrivateChannel`, `RestAction`). Mock `ModeracionService moderacion` y `ConfigServidorService config`. Casos:
  - `avisar` devuelve `AccionEscalado.NINGUNA` → no timeout/ban, sí `registrarEnLogs`, sí abre DM (`verify(user).openPrivateChannel()`).
  - devuelve `TIMEOUT_1H` → `verify(member).timeoutFor(Duration.ofSeconds(3600))` + `moderacion.registrar(...,"TIMEOUT",...)`.
  - devuelve `BAN` → `verify(guild).ban(user, 0, TimeUnit.SECONDS)` + `registrar(...,"BAN",...)`.
  - DM que falla (el `queue` de error) no rompe (la sanción se completa igual).
  - `motivoGuardado=false` → el `Resultado` refleja que hay que avisar del cifrado.
  Usa `when(...).thenReturn(mock(RestAction.class))` y `doNothing`/lenient para los `.queue()`. Mira `WarnComandoTest`/`ModlogsPaginadorListener` u otro test JDA del repo para el patrón de mockear `RestAction`/`AuditableRestAction`/`openPrivateChannel`.

- [ ] **Step 4: implementar `AplicadorSanciones`.** Mover `aplicarEscalado`/`aplicarTimeout` de `WarnComando` aquí (adaptados a no depender de la interacción). Esqueleto:
```java
public final class AplicadorSanciones {
    private static final Logger log = LoggerFactory.getLogger(AplicadorSanciones.class);
    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public AplicadorSanciones(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    /** Resultado para el llamante: el aviso base, el texto del escalón aplicado (o null) y si hay que avisar del cifrado. */
    public record Resultado(ModeracionService.ResultadoAviso aviso, String escaladoTexto) {}

    /**
     * Aplica un aviso completo: registra el warn (escalado incluido), lo ejecuta en Discord (timeout/ban),
     * lo loguea en bot-logs y avisa al usuario por DM (best-effort). Reusable desde el comando y desde los
     * listeners automáticos (moderadorId = staff o el propio bot).
     */
    public Resultado aplicar(Guild guild, Member objetivo, long moderadorId, String motivo) {
        var r = moderacion.avisar(guild.getIdLong(), objetivo.getIdLong(), moderadorId, motivo);
        String escalado = aplicarEscalado(guild, objetivo, r.accion());
        registrarEnLogs(guild, objetivo.getUser(), motivo, r, escalado);
        avisarPorDm(guild, objetivo.getUser(), motivo, r, escalado);
        return new Resultado(r, escalado);
    }
    // aplicarEscalado(Guild, Member, AccionEscalado) → aplica timeout/ban con RAZON_ESCALADO + moderacion.registrar; devuelve la clave i18n del escalón o null
    // registrarEnLogs(...) → ModHelper.embed + ModHelper.registrarEnLogs (en ES, post de canal)
    // avisarPorDm(...) → objetivo.getUser().openPrivateChannel().flatMap(c -> c.sendMessageEmbeds(...)).queue(null, err -> log.debug("DM cerrado", err))  // best-effort, en ES
}
```
El DM en ES (post directo, comunidad ES-first): claves `sancion.dm.aviso` / `sancion.dm.timeout` / `sancion.dm.ban` con el motivo y el nombre del server. `RAZON_ESCALADO` reúsala (muévela a una constante compartida o duplica el literal con la misma i18n). Registrar en logs igual que hoy.

- [ ] **Step 5: run — AplicadorSancionesTest PASS.**

- [ ] **Step 6: `WarnComando` delega.** Sustituir su `aplicarEscalado`/`aplicarTimeout` internos por una llamada al `AplicadorSanciones` inyectado (constructor gana el aplicador; Main lo pasa). El flujo del comando: valida (permiso, jerarquía con `ModHelper.puedeModerar`) → `aplicador.aplicar(guild, objetivoMiembro, evento.getUser().getIdLong(), motivo)` → construye el embed de respuesta con `resultado.escaladoTexto()` y, si `!resultado.aviso().motivoGuardado()`, añade el aviso de cifrado (efímero o como línea del embed de respuesta del staff). Los tests de `WarnComando` (si existen) se actualizan; si no hay, el smoke lo cubre.

- [ ] **Step 7: i18n (ambos idiomas).** Claves nuevas: `sancion.dm.aviso`, `sancion.dm.timeout`, `sancion.dm.ban`, `warn.cifrado.sin_clave` (aviso al moderador). EN natural.

- [ ] **Step 8: Main wiring.** Construir `AplicadorSanciones aplicador = new AplicadorSanciones(moderacion, configService);` donde ya se construye `moderacion`/`WarnComando`, y pasarlo a `WarnComando`. Import.

- [ ] **Step 9: `sh ./mvnw -B clean verify` → BUILD SUCCESS.**

- [ ] **Step 10: Commit** `git commit -am "feat(moderacion): AplicadorSanciones reusable (escalado + log + DM) + aviso de cifrado"`

- [ ] **Step 11: REVIEW** (base = commit de la spec): el escalado aplicado coincide con el de antes (no cambia el comportamiento de `/warn`), el DM es best-effort y no rompe, el flag de cifrado se propaga, la jerarquía se respeta.

---

## Task 2: detector puro + listener anti-flood/anti-invites — con review

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/DeteccionAbuso.java` (puro)
- Create: `src/main/java/com/gymprofit/bot/events/AntiAbusoListener.java`
- Modify: `Main.java` (registrar el listener), `messages_es/en.properties` (motivos i18n si aplican)
- Test: `src/test/java/com/gymprofit/bot/services/DeteccionAbusoTest.java`

- [ ] **Step 1: `DeteccionAbusoTest` (write first).**
```java
    @Test @DisplayName("flood: N mensajes en la ventana → true; espaciados → false")
    void flood() {
        // FLOOD_MSGS=5, FLOOD_SEG=7. timestamps en epoch millis.
        long t = 1_000_000L;
        // 5 mensajes en 3 s → flood
        assertTrue(DeteccionAbuso.esFlood(java.util.List.of(t, t+500, t+1000, t+1500, t+2000), t+2000));
        // 5 mensajes repartidos en 20 s → no flood
        assertFalse(DeteccionAbuso.esFlood(java.util.List.of(t, t+5000, t+10000, t+15000, t+20000), t+20000));
    }

    @Test @DisplayName("invite de Discord detectado; texto normal no")
    void invite() {
        assertTrue(DeteccionAbuso.tieneInviteDiscord("únete a discord.gg/abc123"));
        assertTrue(DeteccionAbuso.tieneInviteDiscord("https://discord.com/invite/xyz"));
        assertFalse(DeteccionAbuso.tieneInviteDiscord("mira este vídeo youtube.com/watch?v=1"));
        assertFalse(DeteccionAbuso.tieneInviteDiscord("hablamos en el server"));
    }
```

- [ ] **Step 2: run — FAIL.**

- [ ] **Step 3: `DeteccionAbuso.java`** (puro):
```java
public final class DeteccionAbuso {
    private DeteccionAbuso() {}
    public static final int FLOOD_MSGS = 5;
    public static final long FLOOD_SEG = 7;
    private static final java.util.regex.Pattern INVITE =
            java.util.regex.Pattern.compile("(?i)(discord\\.gg/|discord(app)?\\.com/invite/)\\S+");

    /** ¿Los timestamps (epoch millis) contienen ≥ FLOOD_MSGS mensajes dentro de los últimos FLOOD_SEG s hasta {@code ahora}? */
    public static boolean esFlood(java.util.List<Long> timestamps, long ahora) {
        long desde = ahora - FLOOD_SEG * 1000;
        long enVentana = timestamps.stream().filter(t -> t >= desde && t <= ahora).count();
        return enVentana >= FLOOD_MSGS;
    }

    /** ¿El texto contiene una invitación de servidor de Discord? */
    public static boolean tieneInviteDiscord(String texto) {
        return texto != null && INVITE.matcher(texto).find();
    }
}
```

- [ ] **Step 4: run — PASS.**

- [ ] **Step 5: `AntiAbusoListener`** (extends `ListenerAdapter`, `onMessageReceived`). Lógica:
  - Ignorar: `evento.getAuthor().isBot()`, mensajes fuera de guild (DM), y **staff** (`ModHelper.esAltoCargo(evento.getMember())`).
  - Mantener un historial de timestamps por usuario (mapa acotado en memoria; usa el patrón de `Cooldown`/`ReintentoRegistro`; poda entradas viejas). Añadir el timestamp del mensaje; si `DeteccionAbuso.esFlood(historial, ahora)` → es flood.
  - Si es flood O `DeteccionAbuso.tieneInviteDiscord(contenido)`: `evento.getMessage().delete().queue(null, e->{})` (best-effort) y, tras el **anti-ráfaga** (un `Cooldown` de 30 s por usuario; si aún no ha pasado, solo borrar sin warn), `aplicador.aplicar(guild, miembro, jda.getSelfUser().getIdLong(), motivo)` con motivo `Messages.get(Messages.ES, "moderacion.motivo.flood"/".invite")`.
  - Requiere el intent `MESSAGE_CONTENT` (ya activo).
  - Constructor recibe `AplicadorSanciones aplicador` (+ lo que necesite para staff/config).
- [ ] **Step 6: i18n** `moderacion.motivo.flood`, `moderacion.motivo.invite` (ES+EN).
- [ ] **Step 7: Main** `listeners.add(new AntiAbusoListener(aplicador));` bajo `jda != null`.
- [ ] **Step 8: `clean verify` → BUILD SUCCESS.**
- [ ] **Step 9: Commit** `feat(moderacion): anti-flood y anti-invites propio (borra + warn con anti-rafaga)`
- [ ] **Step 10: REVIEW** — borra + sanciona en vivo: exención de staff/bots real, anti-ráfaga evita 10 warns por 10 mensajes, historial acotado (no fuga de memoria), best-effort en el borrado.

---

## Task 3: listener AutoMod → warn interno

**Files:** Create `src/main/java/com/gymprofit/bot/events/AutoModWarnListener.java`; Modify `Main.java`.

- [ ] **Step 1: confirmar el evento JDA.** En JDA 5.6.1, el evento de ejecución de AutoMod es (confirmar por import/compilación) `net.dv8tion.jda.api.events.automod.AutoModExecutionEvent`; expone `getUserIdLong()`/`getUser()` o el miembro, la `AutoModResponse`/acción y el `AutoModRule`/nombre. Ábrelo (autocompletar / `javap` del jar en `~/.m2`) y usa los getters reales.
- [ ] **Step 2: `AutoModWarnListener`** (extends `ListenerAdapter`, `onAutoModExecution` o el nombre real). Lógica:
  - Ignorar bots y staff (si el evento da el `Member`; si solo da userId, resuelve `guild.retrieveMemberById` o salta el filtro de staff y confía en que el staff no dispara AutoMod).
  - **Anti-ráfaga:** `Cooldown` 30 s por usuario; si <30 s desde el último auto-warn, solo `log.debug` y salir.
  - Si pasa: `aplicador.aplicar(guild, miembro, jda.getSelfUser().getIdLong(), Messages.get(Messages.ES, "moderacion.motivo.automod", nombreRegla))`.
  - Constructor recibe `AplicadorSanciones` (+ `JDA`/self id si hace falta).
- [ ] **Step 3: i18n** `moderacion.motivo.automod` (ES `AutoMod: {0}` / EN `AutoMod: {0}`).
- [ ] **Step 4: Main** `listeners.add(new AutoModWarnListener(aplicador));`.
- [ ] **Step 5: `clean verify` → BUILD SUCCESS.**
- [ ] **Step 6: Commit** `feat(moderacion): las infracciones de AutoMod cuentan como warn interno (anti-rafaga)`

---

## Task 4: `/warn quitar` (revocación individual)

**Files:** Modify `WarnComando.java` (subcomando), `messages_es/en.properties`.

- [ ] **Step 1:** ¿`/warn` ya es una familia con subcomandos? (la auditoría lo sugiere: `/warn` [poner·lista·quitar·limpiar]). Si `quitar` ya existe como subcomando, comprobar si realmente llama a `revocarWarn` individual; **si ya está, esta tarea se reduce a verificarlo y se marca hecha**. Si NO existe, añadir el subcomando `quitar` con opciones `usuario` (requerida) e `id` (entero, opcional): con `id` → `moderacion.revocarWarn(id)`; sin `id` → revocar el warn activo más reciente del usuario (añade un método al service/repo si no hay forma de obtener el último id, o documenta que `id` es requerido). Permiso de moderación como el resto de `/warn`. Responder con el resultado + `ModHelper.registrarEnLogs`.
- [ ] **Step 2: i18n** claves del subcomando (desc + resultado ok/no encontrado), ES+EN.
- [ ] **Step 3: `clean verify` → BUILD SUCCESS.**
- [ ] **Step 4: Commit** `feat(moderacion): /warn quitar revoca un aviso individual` (o `docs`/`test` si ya existía y solo se verifica).

---

## Task 5: panel de tickets en `/setup`

**Files:** Modify `SetupComando.java` (publicar el panel), posiblemente `PublicarComando.java` (extraer el armado del panel a un punto reutilizable), `messages` si hace falta.

- [ ] **Step 1:** Localizar cómo `PublicarComando` arma el panel de ticket (`:231-262`, embed + `Button` con customId `ticket:abrir`). Extraer ese armado (embed + ActionRow del botón) a un método reutilizable (p. ej. estático en `PublicarComando` o un helper de tickets) para no duplicar el `customId`/estilo.
- [ ] **Step 2:** En `SetupComando`, tras crear/asegurar el canal `🎫・soporte`, publicar el panel de forma **idempotente**: buscar en los mensajes fijados del canal un mensaje del bot que ya tenga el botón `ticket:abrir`; si no existe, enviarlo y fijarlo, y anotarlo en `RegistroCambios` (🆕). Si ya existe, no republicar (ni contar cambio). Hazlo tanto en `/setup` como en `/setup desde_cero` (que recrea todo). Mira cómo `/setup` ya publica/fija otros mensajes (welcome, intros) para reusar el patrón de "solo si difiere/no existe".
- [ ] **Step 3: `clean verify` → BUILD SUCCESS.**
- [ ] **Step 4: Commit** `feat(tickets): /setup publica el panel del boton de ticket en soporte`

---

## Task 6: Documentación + verificación final

**Files:** `docs/decisions.md`, `docs/architecture.md`, `CHANGELOG.md`, `README.md`, `README.en.md`.

- [ ] **Step 1: ADR-027** (tras ADR-026; confirmar). Decisión: `AplicadorSanciones` centraliza escalado+log+DM; AutoMod y un anti-flood/invites propio alimentan el warn/escalado con anti-ráfaga; endurecer el fallo de cifrado; `/warn quitar`; panel de tickets en `/setup`. Consecuencias: nuevos listeners `AntiAbusoListener`/`AutoModWarnListener`, `DeteccionAbuso` pura, `ResultadoAviso.motivoGuardado`; sin migración; fuera de alcance lo no elegido.
- [ ] **Step 2: `docs/architecture.md`** — `events/` suma los 2 listeners; nota del `AplicadorSanciones` compartido y de que `/setup` publica el panel de tickets. Sin cambio de migraciones.
- [ ] **Step 3: `CHANGELOG.md`** — entrada del módulo (AutoMod→warn, anti-flood/invites propio, DM al sancionado, `/warn quitar`, panel de tickets en setup).
- [ ] **Step 4: READMEs** — `/warn` suma `quitar`; nota del anti-flood/invites y del auto-registro de AutoMod; ES+EN.
- [ ] **Step 5: `clean verify` final** — BUILD SUCCESS; anotar recuento de tests.
- [ ] **Step 6: Commit** `docs(moderacion): moderacion robusta + panel de tickets — ADR-027, architecture, changelog y READMEs`
- [ ] **Step 7: Push + aviso.** El controlador hace `git push` y avisa: **desplegar = reiniciar bot + re-ejecutar `/setup`** (el panel de tickets). Necesita permiso de moderación (timeout/ban) y AutoMod creado (lo hace `/setup`). Smoke en la spec.

---

## Notas de implementación

- **Un solo camino de sanción:** comando y listeners pasan SIEMPRE por `AplicadorSanciones.aplicar` → escalado, log y DM idénticos. No dupliques la aplicación de timeout/ban.
- **Anti-ráfaga:** cada llamador automático (los 2 listeners) protege con un `Cooldown` de 30 s por usuario ANTES de sancionar; el aplicador no lo conoce (sigue siendo "aplica un aviso").
- **Best-effort:** borrar mensajes y abrir DMs se hace con `.queue(null, err->...)`; un fallo (mensaje ya borrado, DM cerrado) nunca tumba la sanción.
- **DM en ES** (post directo, comunidad ES-first); respuestas de comando según locale del invocador.
- **Memoria del flood:** historial de timestamps por usuario acotado y podado (no fuga); estado volátil, no se persiste.
- **Sin migración** en todo el módulo.
- **Tests:** la lógica pura (`DeteccionAbuso`, flag de cifrado) y `AplicadorSanciones` (Mockito) se testean; los listeners y `/setup` se cubren en smoke (dependen de JDA vivo).
