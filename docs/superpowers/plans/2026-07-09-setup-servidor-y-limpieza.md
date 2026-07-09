# Setup del servidor y limpieza — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir `/setup` (monta roles, categorías, canales y permisos del servidor, purga los mensajes recientes y autorrellena `config_servidor`) y `/limpiar` (purga los últimos N mensajes del canal), ambos solo para staff.

**Architecture:** El blueprint del servidor vive como **datos puros** en `SetupServidorPlan` (sin JDA, testeable). `LimpiezaService` encapsula la purga. Dos comandos (`commands/admin/SetupComando`, `commands/moderacion/LimpiarComando`) ejecutan contra JDA y reutilizan `ConfigServidorService` y `EmbedFactory`. Se registran en `Main` cuando hay BD.

**Tech Stack:** Java 21, JDA 5, JUnit 5, i18n `ResourceBundle`. Build: `mvnw.cmd verify` con `JAVA_HOME=~/.jdks/ms-21.0.11`.

**Nota de entorno (todos los comandos de build):**
```bash
export JAVA_HOME="$HOME/.jdks/ms-21.0.11" && export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Estructura de archivos

- Crear `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java` — blueprint (datos).
- Crear `src/main/java/com/gymprofit/bot/services/LimpiezaService.java` — purga + `normalizar`.
- Crear `src/main/java/com/gymprofit/bot/commands/moderacion/LimpiarComando.java`.
- Crear `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`.
- Modificar `src/main/java/com/gymprofit/bot/Main.java` — registrar ambos comandos (bloque `db != null`).
- Modificar `messages_es.properties` y `messages_en.properties` — claves nuevas.
- Test `src/test/java/com/gymprofit/bot/services/SetupServidorPlanTest.java`.
- Test `src/test/java/com/gymprofit/bot/services/LimpiezaServiceTest.java`.
- Test `src/test/java/com/gymprofit/bot/commands/moderacion/LimpiarComandoTest.java`.
- Modificar `README.md`, `README.en.md`, `CHANGELOG.md`.

---

## Task 1: Blueprint del servidor (`SetupServidorPlan`)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java`
- Test: `src/test/java/com/gymprofit/bot/services/SetupServidorPlanTest.java`

- [ ] **Step 1: Escribir el test que falla**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;
import com.gymprofit.bot.services.SetupServidorPlan.CanalPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CategoriaPlan;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupServidorPlanTest {

    @Test
    void hayCategoriasYCanalesEsperados() {
        assertTrue(SetupServidorPlan.CATEGORIAS.size() >= 7, "al menos 7 categorías");
        long canales = SetupServidorPlan.CATEGORIAS.stream()
                .mapToLong(c -> c.canales().size()).sum();
        assertTrue(canales >= 20, "al menos 20 canales en total");
    }

    @Test
    void nombresDeCanalUnicos() {
        Set<String> vistos = new HashSet<>();
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                assertTrue(vistos.add(ch.nombre()), "nombre de canal duplicado: " + ch.nombre());
            }
        }
    }

    @Test
    void todosLosCanalesDeConfigEstanMapeadosUnaVez() {
        Set<TipoCanal> mapeados = EnumSet.noneOf(TipoCanal.class);
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                if (ch.claveConfig() != null) {
                    assertTrue(mapeados.add(ch.claveConfig()),
                            "clave de config duplicada: " + ch.claveConfig());
                }
            }
        }
        assertEquals(EnumSet.allOf(TipoCanal.class), mapeados,
                "cada canal configurable aparece exactamente una vez");
    }

    @Test
    void losCuatroObjetivosTienenRol() {
        Set<Objetivo> objetivos = EnumSet.noneOf(Objetivo.class);
        SetupServidorPlan.ROLES.stream()
                .filter(r -> r.objetivo() != null)
                .forEach(r -> objetivos.add(r.objetivo()));
        assertEquals(EnumSet.allOf(Objetivo.class), objetivos);
    }
}
```

- [ ] **Step 2: Ejecutar el test para verlo fallar**

Run: `./mvnw.cmd -q -Dtest=SetupServidorPlanTest test`
Expected: FAIL de compilación (no existe `SetupServidorPlan`).

- [ ] **Step 3: Implementar el blueprint**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;

import java.util.List;

/**
 * Blueprint (datos, sin JDA) del servidor que monta {@code /setup}: roles, categorías y canales
 * con sus permisos y su mapeo a {@code config_servidor}. Al ser datos puros es testeable sin
 * conexión a Discord.
 */
public final class SetupServidorPlan {

    /** Tipo de canal a crear. */
    public enum TipoCanalDiscord { TEXTO, VOZ }

    /**
     * @param nombre           nombre del canal (con emoji y separador)
     * @param tipo             texto o voz
     * @param soloLectura      si {@code @everyone} no puede escribir (canales informativos)
     * @param slowmodeSegundos slowmode en segundos (0 = ninguno)
     * @param claveConfig      canal de config que representa, o {@code null}
     * @param limiteVoz        límite de usuarios en canal de voz (0 = sin límite)
     */
    public record CanalPlan(String nombre, TipoCanalDiscord tipo, boolean soloLectura,
                            int slowmodeSegundos, TipoCanal claveConfig, int limiteVoz) {
    }

    /**
     * @param nombre    nombre de la categoría
     * @param oculta    si {@code @everyone} no la ve
     * @param soloStaff si además el rol Staff sí la ve (categorías privadas de staff)
     * @param canales   canales de la categoría
     */
    public record CategoriaPlan(String nombre, boolean oculta, boolean soloStaff,
                                List<CanalPlan> canales) {
    }

    /**
     * @param nombre   nombre del rol (con emoji)
     * @param objetivo objetivo de entrenamiento que representa, o {@code null}
     * @param colorRgb color RGB (0 = sin color)
     */
    public record RolPlan(String nombre, Objetivo objetivo, int colorRgb) {
    }

    private SetupServidorPlan() {
    }

    /** Roles a crear (el bot los crea sin permisos; el owner ajusta jerarquía/permisos). */
    public static final List<RolPlan> ROLES = List.of(
            new RolPlan("▬▬ EQUIPO ▬▬", null, 0x2B2D31),
            new RolPlan("👑 Fundador", null, 0xF1C40F),
            new RolPlan("🛡️ Admin", null, 0xC0392B),
            new RolPlan("🧹 Staff", null, 0x3498DB),
            new RolPlan("🧑‍🏫 Coach", null, 0x16A085),
            new RolPlan("🍎 Nutricionista", null, 0x27AE60),
            new RolPlan("🤖 Bots", null, 0x99AAB5),
            new RolPlan("▬▬ COMUNIDAD ▬▬", null, 0x2B2D31),
            new RolPlan("🎥 Creador", null, 0x8E44AD),
            new RolPlan("🥇 Campeón del mes", null, 0xF1C40F),
            new RolPlan("📲 Vinculado", null, 0x1ABC9C),
            new RolPlan("🎂 Cumpleaños", null, 0xFF80AB),
            new RolPlan("🏅 Leyenda", null, 0xE8B84B),
            new RolPlan("🏅 Veterano", null, 0xD4A03A),
            new RolPlan("🏅 Habitual", null, 0xB8862B),
            new RolPlan("🏅 Novato", null, 0x95A5A6),
            new RolPlan("▬▬ OBJETIVOS ▬▬", null, 0x2B2D31),
            new RolPlan("💪 Fuerza", Objetivo.FUERZA, 0xE74C3C),
            new RolPlan("🏃 Cardio", Objetivo.CARDIO, 0x3498DB),
            new RolPlan("⚖️ Pérdida de peso", Objetivo.PERDIDA_PESO, 0x1E8E4A),
            new RolPlan("🌟 General", Objetivo.GENERAL, 0xFF6A00),
            new RolPlan("▬▬ NOTIFICACIONES ▬▬", null, 0x2B2D31),
            new RolPlan("📣 Avisos", null, 0xE67E22),
            new RolPlan("🎯 Retos", null, 0x9B59B6),
            new RolPlan("🤝 Miembro", null, 0x2ECC71),
            new RolPlan("🔇 Silenciado", null, 0x607D8B)
    );

    private static CanalPlan texto(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0);
    }

    private static CanalPlan info(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0);
    }

    private static CanalPlan voz(String nombre, int limite) {
        return new CanalPlan(nombre, TipoCanalDiscord.VOZ, false, 0, null, limite);
    }

    private static CanalPlan slow(String nombre, int segundos) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, segundos, null, 0);
    }

    /** Categorías y canales del servidor (F1–F4). */
    public static final List<CategoriaPlan> CATEGORIAS = List.of(
            new CategoriaPlan("📢 INFORMACIÓN", false, false, List.of(
                    info("👋・bienvenidas", TipoCanal.BIENVENIDA),
                    info("🚀・empieza-aquí", null),
                    info("📜・reglas", null),
                    info("🗺️・cómo-funciona", null),
                    info("❓・faq", null),
                    info("📣・anuncios", null),
                    info("📲・novedades-app", null),
                    info("📱・redes-sociales", null))),
            new CategoriaPlan("💬 COMUNIDAD", false, false, List.of(
                    texto("💬・general", null),
                    texto("👋・presentaciones", null),
                    texto("😂・memes", null),
                    texto("🎮・gaming", null),
                    texto("🎵・música", null),
                    texto("🎧・off-topic", null))),
            new CategoriaPlan("🏋️ FITNESS", false, false, List.of(
                    texto("🗓️・ejercicio-del-día", TipoCanal.EJERCICIO_DIA),
                    texto("📈・progresos", null),
                    texto("📸・fotos", null),
                    texto("🍎・nutrición", null),
                    texto("📚・rutinas", null),
                    texto("❓・dudas", null))),
            new CategoriaPlan("🎮 GAMIFICACIÓN", false, false, List.of(
                    texto("🏆・logros", TipoCanal.LOGROS),
                    texto("📊・ranking", null),
                    texto("🪙・economía", null),
                    texto("🧠・trivia", null),
                    texto("⚔️・duelos", null),
                    texto("🎯・retos", null),
                    slow("🤖・comandos-bot", 5))),
            new CategoriaPlan("🛎️ AYUDA", false, false, List.of(
                    texto("💡・sugerencias", TipoCanal.SUGERENCIAS),
                    texto("🎫・soporte", TipoCanal.SOPORTE))),
            new CategoriaPlan("🔊 VOZ", false, false, List.of(
                    voz("🔊 General", 0),
                    voz("🎮 Gaming", 0),
                    voz("🎮 Gaming 2", 0),
                    voz("🎵 Música", 0),
                    voz("🛋️ Chill", 0),
                    voz("👥 Privada", 4),
                    voz("🎥 Directo", 0),
                    voz("💤 AFK", 0))),
            new CategoriaPlan("🔒 STAFF", true, true, List.of(
                    texto("🛠️・staff-chat", null),
                    texto("🤖・bot-logs", TipoCanal.BOT_LOGS),
                    texto("📋・moderación", null),
                    texto("📥・reportes", null),
                    texto("🗄️・logs-tickets", null),
                    voz("🔒 Voz staff", 0))),
            new CategoriaPlan("🎫 TICKETS", true, true, List.of())
    );
}
```

- [ ] **Step 4: Ejecutar el test para verlo pasar**

Run: `./mvnw.cmd -q -Dtest=SetupServidorPlanTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java src/test/java/com/gymprofit/bot/services/SetupServidorPlanTest.java
git commit -m "feat: blueprint del servidor para /setup (SetupServidorPlan)"
```

---

## Task 2: Servicio de limpieza (`LimpiezaService`)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/LimpiezaService.java`
- Test: `src/test/java/com/gymprofit/bot/services/LimpiezaServiceTest.java`

- [ ] **Step 1: Escribir el test que falla**

```java
package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimpiezaServiceTest {

    @Test
    void normalizarAcotaElRango() {
        assertEquals(1, LimpiezaService.normalizar(0), "por debajo del mínimo -> 1");
        assertEquals(1, LimpiezaService.normalizar(-5));
        assertEquals(50, LimpiezaService.normalizar(50));
        assertEquals(1000, LimpiezaService.normalizar(5000), "por encima del máximo -> 1000");
        assertEquals(1000, LimpiezaService.normalizar(1000));
    }
}
```

- [ ] **Step 2: Ejecutar el test para verlo fallar**

Run: `./mvnw.cmd -q -Dtest=LimpiezaServiceTest test`
Expected: FAIL de compilación (no existe `LimpiezaService`).

- [ ] **Step 3: Implementar el servicio**

```java
package com.gymprofit.bot.services;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Limpieza de mensajes de un canal. Purga los mensajes recientes (Discord solo permite el
 * borrado en bloque de mensajes de menos de 14 días; los más antiguos no se tocan).
 */
public final class LimpiezaService {

    /** Límites del número de mensajes a purgar en una operación. */
    public static final int MIN = 1;
    public static final int MAX = 1000;

    /** Acota la cantidad pedida al rango permitido [MIN, MAX]. */
    public static int normalizar(int cantidad) {
        return Math.max(MIN, Math.min(MAX, cantidad));
    }

    /**
     * Purga hasta {@code cantidad} mensajes recientes del canal.
     *
     * @return futuro con el número de mensajes que se intentaron borrar
     */
    public CompletableFuture<Integer> purgarReciente(MessageChannel canal, int cantidad) {
        int n = normalizar(cantidad);
        return canal.getIterableHistory().takeAsync(n).thenCompose(mensajes -> {
            List<CompletableFuture<Void>> tareas = canal.purgeMessages(mensajes);
            return CompletableFuture
                    .allOf(tareas.toArray(new CompletableFuture[0]))
                    .thenApply(v -> mensajes.size());
        });
    }
}
```

- [ ] **Step 4: Ejecutar el test para verlo pasar**

Run: `./mvnw.cmd -q -Dtest=LimpiezaServiceTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gymprofit/bot/services/LimpiezaService.java src/test/java/com/gymprofit/bot/services/LimpiezaServiceTest.java
git commit -m "feat: LimpiezaService (purga de mensajes recientes)"
```

---

## Task 3: Comando `/limpiar`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/moderacion/LimpiarComando.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`
- Test: `src/test/java/com/gymprofit/bot/commands/moderacion/LimpiarComandoTest.java`

- [ ] **Step 1: Añadir claves i18n**

En `messages_es.properties` (al final):
```properties
# --- Comando /limpiar ---
comando.limpiar.descripcion=Borra los últimos mensajes de este canal (solo staff)
comando.limpiar.opcion.cantidad=Cuántos mensajes borrar (1-1000)
limpiar.titulo=Canal limpiado
limpiar.resultado=🧹 Se han borrado **{0}** mensajes recientes. (Los de más de 14 días no se pueden borrar en bloque.)
```

En `messages_en.properties` (al final):
```properties
# --- /limpiar (clean) command ---
comando.limpiar.descripcion=Delete the latest messages in this channel (staff only)
comando.limpiar.opcion.cantidad=How many messages to delete (1-1000)
limpiar.titulo=Channel cleaned
limpiar.resultado=🧹 Deleted **{0}** recent messages. (Messages older than 14 days cannot be bulk-deleted.)
```

- [ ] **Step 2: Escribir el test que falla**

```java
package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.services.LimpiezaService;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimpiarComandoTest {

    @Test
    void definicionTieneNombreYOpcionCantidad() {
        SlashCommandData d = new LimpiarComando(new LimpiezaService()).definicion();
        assertEquals("limpiar", d.getName());
        assertFalse(d.getDescription().isBlank());
        assertTrue(d.getOptions().stream().anyMatch(o -> o.getName().equals("cantidad")),
                "debe tener la opción cantidad");
    }
}
```

- [ ] **Step 3: Ejecutar el test para verlo fallar**

Run: `./mvnw.cmd -q -Dtest=LimpiarComandoTest test`
Expected: FAIL de compilación (no existe `LimpiarComando`).

- [ ] **Step 4: Implementar el comando**

```java
package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.LimpiezaService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * {@code /limpiar}: borra los últimos N mensajes del canal actual (solo staff con
 * «Gestionar mensajes»). Reutiliza {@link LimpiezaService}. Los mensajes de más de 14 días no se
 * pueden borrar en bloque (límite de Discord).
 */
public final class LimpiarComando implements Comando {

    private static final Logger log = LoggerFactory.getLogger(LimpiarComando.class);
    private static final String NOMBRE = "limpiar";

    private final LimpiezaService limpieza;

    public LimpiarComando(LimpiezaService limpieza) {
        this.limpieza = limpieza;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.limpiar.opcion.cantidad"), true)
                .setRequiredRange(LimpiezaService.MIN, LimpiezaService.MAX)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.limpiar.opcion.cantidad"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.limpiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.limpiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.limpiar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOptions(cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        int cantidad = evento.getOption("cantidad").getAsInt();

        evento.deferReply(true).queue();
        limpieza.purgarReciente(evento.getChannel(), cantidad).whenComplete((borrados, error) -> {
            if (error != null) {
                log.error("Error limpiando el canal {}", evento.getChannel().getId(), error);
                evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue();
                return;
            }
            var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "limpiar.titulo"),
                    Messages.get(locale, "limpiar.resultado", borrados)).build();
            evento.getHook().sendMessageEmbeds(embed).queue();
        });
    }
}
```

- [ ] **Step 5: Registrar el comando en `Main`**

En `Main.iniciarDiscord`, dentro del bloque `if (db != null)`, añadir el import
`import com.gymprofit.bot.commands.moderacion.LimpiarComando;`
`import com.gymprofit.bot.services.LimpiezaService;`
y tras registrar `ConfigComando`:
```java
            LimpiezaService limpieza = new LimpiezaService();
            comandos.add(new LimpiarComando(limpieza));
```

- [ ] **Step 6: Ejecutar tests + verify**

Run: `./mvnw.cmd -q -Dtest=LimpiarComandoTest test` → PASS
Run: `./mvnw.cmd verify` → BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: comando /limpiar (purga de mensajes, solo staff)"
```

---

## Task 4: Comando `/setup`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/admin/SetupComando.java`
- Modify: `messages_es.properties`, `messages_en.properties`, `Main.java`

- [ ] **Step 1: Añadir claves i18n**

En `messages_es.properties`:
```properties
# --- Comando /setup ---
comando.setup.descripcion=Monta la estructura del servidor de GymProFit (solo admin)
setup.titulo=Servidor montado
setup.resumen=✅ Roles: **{0}** · Categorías/canales: **{1}** · Mensajes limpiados: **{2}**.\n\nRevisa los pasos manuales pendientes (Comunidad, icono, jerarquía de roles).
setup.error=Ha ocurrido un error montando el servidor. Revisa mis permisos (Gestionar roles y canales) y vuelve a intentarlo.
```

En `messages_en.properties`:
```properties
# --- /setup command ---
comando.setup.descripcion=Set up the GymProFit server structure (admin only)
setup.titulo=Server set up
setup.resumen=✅ Roles: **{0}** · Categories/channels: **{1}** · Messages cleaned: **{2}**.\n\nCheck the pending manual steps (Community, icon, role hierarchy).
setup.error=Something went wrong setting up the server. Check my permissions (Manage Roles and Channels) and try again.
```

- [ ] **Step 2: Implementar el comando**

```java
package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.LimpiezaService;
import com.gymprofit.bot.services.SetupServidorPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CanalPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CategoriaPlan;
import com.gymprofit.bot.services.SetupServidorPlan.RolPlan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/**
 * {@code /setup}: monta la estructura del servidor (roles, categorías, canales y permisos según
 * {@link SetupServidorPlan}), purga los mensajes recientes de los canales existentes y
 * autorrellena {@code config_servidor}. Idempotente: reutiliza lo que ya exista (por nombre).
 * Solo admin. Se ejecuta en el hilo de la interacción con llamadas bloqueantes ({@code complete}).
 */
public final class SetupComando implements Comando {

    private static final Logger log = LoggerFactory.getLogger(SetupComando.class);
    private static final String NOMBRE = "setup";

    private final ConfigServidorService config;
    private final LimpiezaService limpieza;

    public SetupComando(ConfigServidorService config, LimpiezaService limpieza) {
        this.config = config;
        this.limpieza = limpieza;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.setup.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.setup.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.setup.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Guild guild = evento.getGuild();
        evento.deferReply(true).queue();

        try {
            int limpiados = purgarCanalesExistentes(guild);
            Role staff = crearRoles(guild);
            int canales = crearCategoriasYCanales(guild, staff);

            var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                    Messages.get(locale, "setup.titulo"),
                    Messages.get(locale, "setup.resumen",
                            SetupServidorPlan.ROLES.size(), canales, limpiados)).build();
            evento.getHook().sendMessageEmbeds(embed).queue();
        } catch (RuntimeException e) {
            log.error("Error en /setup en el servidor {}", guild.getId(), e);
            evento.getHook().sendMessage(Messages.get(locale, "setup.error")).queue();
        }
    }

    /** Purga mensajes recientes de todos los canales de texto existentes; devuelve cuántos. */
    private int purgarCanalesExistentes(Guild guild) {
        int total = 0;
        for (TextChannel canal : guild.getTextChannels()) {
            try {
                total += limpieza.purgarReciente(canal, LimpiezaService.MAX).join();
            } catch (RuntimeException e) {
                log.warn("No se pudo limpiar el canal {}", canal.getId(), e);
            }
        }
        return total;
    }

    /** Crea (o reutiliza) los roles; devuelve el rol Staff para los permisos de categoría. */
    private Role crearRoles(Guild guild) {
        Role staff = null;
        for (RolPlan plan : SetupServidorPlan.ROLES) {
            Role rol = guild.getRolesByName(plan.nombre(), false).stream().findFirst().orElse(null);
            if (rol == null) {
                var accion = guild.createRole().setName(plan.nombre());
                if (plan.colorRgb() != 0) {
                    accion = accion.setColor(new Color(plan.colorRgb()));
                }
                rol = accion.complete();
            }
            if (plan.objetivo() != null) {
                config.fijarRol(guild.getIdLong(), plan.objetivo(), rol.getIdLong());
            }
            if ("🧹 Staff".equals(plan.nombre())) {
                staff = rol;
            }
        }
        return staff;
    }

    /** Crea (o reutiliza) categorías y canales con permisos; devuelve cuántos canales hay en el plan. */
    private int crearCategoriasYCanales(Guild guild, Role staff) {
        int canales = 0;
        Role everyone = guild.getPublicRole();

        for (CategoriaPlan catPlan : SetupServidorPlan.CATEGORIAS) {
            Category categoria = guild.getCategoriesByName(catPlan.nombre(), false)
                    .stream().findFirst().orElse(null);
            if (categoria == null) {
                var accion = guild.createCategory(catPlan.nombre());
                if (catPlan.oculta()) {
                    accion = accion.addRolePermissionOverride(everyone.getIdLong(),
                            0L, Permission.VIEW_CHANNEL.getRawValue());
                    if (catPlan.soloStaff() && staff != null) {
                        accion = accion.addRolePermissionOverride(staff.getIdLong(),
                                Permission.VIEW_CHANNEL.getRawValue(), 0L);
                    }
                }
                categoria = accion.complete();
            }

            for (CanalPlan chPlan : catPlan.canales()) {
                canales++;
                crearCanal(guild, categoria, chPlan, everyone);
            }
        }
        return canales;
    }

    /** Crea (o reutiliza) un canal bajo su categoría, sincroniza permisos y aplica extras. */
    private void crearCanal(Guild guild, Category categoria, CanalPlan chPlan, Role everyone) {
        GuildChannel existente = guild.getChannels().stream()
                .filter(c -> c.getName().equals(chPlan.nombre()))
                .findFirst().orElse(null);
        if (existente != null) {
            aplicarConfig(guild, existente, chPlan);
            return;
        }

        GuildChannel creado;
        if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.VOZ) {
            var accionVoz = categoria.createVoiceChannel(chPlan.nombre());
            if (chPlan.limiteVoz() > 0) {
                accionVoz = accionVoz.setUserlimit(chPlan.limiteVoz());
            }
            creado = accionVoz.complete();
        } else {
            var accion = categoria.createTextChannel(chPlan.nombre());
            if (chPlan.slowmodeSegundos() > 0) {
                accion = accion.setSlowmode(chPlan.slowmodeSegundos());
            }
            TextChannel tc = accion.complete();
            // Sincroniza permisos con la categoría (hereda ocultación/staff).
            tc.getManager().sync(categoria).complete();
            if (chPlan.soloLectura()) {
                tc.upsertPermissionOverride(everyone)
                        .deny(Permission.MESSAGE_SEND).complete();
            }
            creado = tc;
        }
        aplicarConfig(guild, creado, chPlan);
    }

    /** Si el canal representa un canal de config, lo guarda en config_servidor. */
    private void aplicarConfig(Guild guild, GuildChannel canal, CanalPlan chPlan) {
        if (chPlan.claveConfig() != null) {
            config.fijarCanal(guild.getIdLong(), chPlan.claveConfig(), canal.getIdLong());
        }
    }
}
```

- [ ] **Step 3: Registrar el comando en `Main`**

En `Main.iniciarDiscord`, tras crear `configService` y `limpieza`, añadir imports
`import com.gymprofit.bot.commands.admin.SetupComando;`
y:
```java
            comandos.add(new SetupComando(configService, limpieza));
```
(Asegurarse de que `LimpiezaService limpieza` de la Task 3 se declara antes y se reutiliza.)

- [ ] **Step 4: Verify**

Run: `./mvnw.cmd verify`
Expected: BUILD SUCCESS (los tests existentes siguen pasando; `/setup` no tiene test unitario porque su ejecución es 100% JDA — queda smoke test manual).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: comando /setup (monta el servidor y autoconfigura)"
```

---

## Task 5: Documentación

**Files:**
- Modify: `README.md`, `README.en.md`, `CHANGELOG.md`

- [ ] **Step 1: Añadir a la tabla de comandos de `README.md`**

```markdown
| `/setup` | Monta la estructura del servidor (roles, canales, permisos) y autoconfigura (solo admin) |
| `/limpiar <cantidad>` | Borra los últimos N mensajes del canal (solo staff) |
```

- [ ] **Step 2: Añadir a `README.en.md`**

```markdown
| `/setup` | Set up the server structure (roles, channels, permissions) and auto-config (admin only) |
| `/limpiar <amount>` | Delete the latest N messages in the channel (staff only) |
```

- [ ] **Step 3: Añadir entrada a `CHANGELOG.md`** (bajo `### Añadido`)

```markdown
- **Administración del servidor (F1)**: `/setup` monta roles, categorías y canales (F1–F4) con
  permisos según `SetupServidorPlan`, purga los mensajes recientes existentes y autorrellena
  `config_servidor` (solo admin). `/limpiar <cantidad>` purga los últimos N mensajes del canal
  (solo staff), vía `LimpiezaService`. Tests `SetupServidorPlanTest`, `LimpiezaServiceTest`,
  `LimpiarComandoTest`. Ejecución en vivo de `/setup` y `/limpiar` pendiente de smoke test manual.
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: /setup y /limpiar en README (ES/EN) y CHANGELOG"
```

---

## Task 6: Smoke test manual (en el servidor de pruebas)

- [ ] Arrancar el bot con BD y token (Docker MySQL en 3307 + run config del IDE).
- [ ] Ejecutar `/setup` → verificar que crea roles, categorías y canales, y que `/config ver`
  muestra bienvenida/ejercicio/logros/sugerencias/soporte/bot-logs y los 4 roles ya mapeados.
- [ ] Re-ejecutar `/setup` → no duplica (idempotente).
- [ ] Escribir varios mensajes en un canal y `/limpiar cantidad:10` → confirma los borrados.
- [ ] Completar los pasos manuales del spec: nombre + icono, activar Comunidad, Onboarding con
  roles opt-in, AutoMod nativo, subir jerarquía de Fundador/Admin/Staff y darles permisos.
- [ ] Designar `💤 AFK` como canal AFK del servidor (Ajustes → Descripción general → Canal AFK)
  y ordenar los roles separadores (`▬▬ … ▬▬`) entre cada grupo (arrastre manual).
- [ ] Pegar textos de `📜・reglas` y `🗺️・cómo-funciona`.

---

## Self-review

- **Cobertura del spec:** roles (Task 1), categorías/canales/permisos (Task 1 + 4), purga en setup
  (Task 4 `purgarCanalesExistentes`), `/limpiar` (Task 3), autoconfig (Task 4 `aplicarConfig`/roles),
  pasos manuales (Task 6). Silenciado: sus overrides globales quedan como refinamiento manual (el
  rol se crea en Task 1); si se quiere automatizar, es una ampliación posterior — anotado aquí para
  no darlo por hecho.
- **Placeholders:** ninguno; todo el código va completo.
- **Consistencia de tipos:** `TipoCanal`/`Objetivo` reutilizados de `ConfigServidorService`;
  `normalizar`, `purgarReciente`, `MIN`/`MAX` coinciden entre servicio, comando y tests.
