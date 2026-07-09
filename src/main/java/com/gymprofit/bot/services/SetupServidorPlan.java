package com.gymprofit.bot.services;

import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;

import java.util.List;

/**
 * Blueprint (datos, sin JDA) del servidor que monta {@code /setup}: roles, categorías y canales
 * con sus permisos, su mapeo a {@code config_servidor} y sus mensajes fijados de ayuda. Al ser
 * datos puros es testeable sin conexión a Discord.
 *
 * <p>Enfoque realista: es una <b>comunidad</b> (charlar, jugar, compartir progreso), no un
 * gimnasio en voz. Los canales de fitness son de conversación; la voz es de hangout/gaming.</p>
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
     * @param introKey         prefijo i18n del mensaje fijado de ayuda ({@code <key>.titulo}/
     *                         {@code <key>.desc}), o {@code null} si el canal no lleva pin
     */
    public record CanalPlan(String nombre, TipoCanalDiscord tipo, boolean soloLectura,
                            int slowmodeSegundos, TipoCanal claveConfig, int limiteVoz,
                            String introKey) {
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
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, null);
    }

    private static CanalPlan texto(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, introKey);
    }

    private static CanalPlan info(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, null);
    }

    private static CanalPlan info(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, introKey);
    }

    private static CanalPlan voz(String nombre, int limite) {
        return new CanalPlan(nombre, TipoCanalDiscord.VOZ, false, 0, null, limite, null);
    }

    private static CanalPlan slow(String nombre, int segundos, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, segundos, null, 0, introKey);
    }

    /** Categorías y canales del servidor (F1–F4). Nombres decorados con «›» como separador. */
    public static final List<CategoriaPlan> CATEGORIAS = List.of(
            new CategoriaPlan("📢 › INFORMACIÓN", false, false, List.of(
                    info("👋・bienvenidas", TipoCanal.BIENVENIDA),
                    info("🚀・empieza-aquí", null, "intro.empieza"),
                    info("🎭・roles", null, "panel.roles"),
                    info("📜・reglas", null, "intro.reglas"),
                    info("🗺️・cómo-funciona", null, "intro.como"),
                    info("❓・faq", null, "intro.faq"),
                    info("📣・anuncios", null, "intro.anuncios"),
                    info("📲・novedades-app", null),
                    info("📱・redes-sociales", null, "intro.redes"))),
            new CategoriaPlan("💬 › COMUNIDAD", false, false, List.of(
                    texto("💬・general", null, "intro.general"),
                    texto("👋・presentaciones", null, "intro.presentaciones"),
                    texto("😂・memes", null),
                    texto("🎮・gaming", null),
                    texto("🎵・música", null),
                    texto("🎧・off-topic", null))),
            new CategoriaPlan("🏋️ › FITNESS", false, false, List.of(
                    texto("🗓️・ejercicio-del-día", TipoCanal.EJERCICIO_DIA),
                    texto("📈・progresos", null, "intro.progresos"),
                    texto("📸・fotos", null),
                    texto("🍎・nutrición", null, "intro.nutricion"),
                    texto("📚・rutinas", null, "intro.rutinas"),
                    texto("❓・dudas", null, "intro.dudas"))),
            new CategoriaPlan("🎮 › GAMIFICACIÓN", false, false, List.of(
                    texto("🏆・logros", TipoCanal.LOGROS),
                    texto("📊・ranking", null),
                    texto("🪙・economía", null, "intro.proximamente"),
                    texto("🧠・trivia", null, "intro.proximamente"),
                    texto("⚔️・duelos", null, "intro.proximamente"),
                    texto("🎯・retos", null, "intro.proximamente"),
                    slow("🤖・comandos-bot", 5, "intro.comandos"))),
            new CategoriaPlan("🛎️ › AYUDA", false, false, List.of(
                    texto("💡・sugerencias", TipoCanal.SUGERENCIAS),
                    texto("🎫・soporte", TipoCanal.SOPORTE, "intro.soporte"))),
            new CategoriaPlan("🔊 › VOZ", false, false, List.of(
                    voz("🔊 General", 0),
                    voz("🎮 Gaming", 0),
                    voz("🎮 Gaming 2", 0),
                    voz("🎵 Música", 0),
                    voz("🛋️ Chill", 0),
                    voz("👥 Privada", 4),
                    voz("🎥 Directo", 0),
                    voz("💤 AFK", 0))),
            new CategoriaPlan("🔒 › STAFF", true, true, List.of(
                    texto("🛠️・staff-chat", null),
                    texto("🤖・bot-logs", TipoCanal.BOT_LOGS),
                    texto("📋・moderación", null),
                    texto("📥・reportes", null),
                    texto("🗄️・logs-tickets", null),
                    voz("🔒 Voz staff", 0))),
            new CategoriaPlan("🎫 › TICKETS", true, true, List.of())
    );
}
