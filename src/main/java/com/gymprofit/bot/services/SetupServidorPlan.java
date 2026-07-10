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
    public enum TipoCanalDiscord { TEXTO, VOZ, ESCENARIO, FORO, MEDIA, ANUNCIOS }

    /**
     * @param nombre           nombre del canal (con emoji y separador)
     * @param tipo             texto o voz
     * @param soloLectura      si {@code @everyone} no puede escribir (canales informativos)
     * @param slowmodeSegundos slowmode en segundos (0 = ninguno)
     * @param claveConfig      canal de config que representa, o {@code null}
     * @param limiteVoz        límite de usuarios en canal de voz (0 = sin límite)
     * @param introKey         prefijo i18n del mensaje fijado de ayuda ({@code <key>.titulo}/
     *                         {@code <key>.desc}), o {@code null} si el canal no lleva pin
     * @param topic            descripción del canal (topic de Discord), o {@code null}. Los
     *                         canales de voz y escenario no llevan topic.
     * @param etiquetas        etiquetas del foro (solo tipo {@code FORO}); vacío en el resto
     */
    public record CanalPlan(String nombre, TipoCanalDiscord tipo, boolean soloLectura,
                            int slowmodeSegundos, TipoCanal claveConfig, int limiteVoz,
                            String introKey, String topic, List<String> etiquetas) {

        /** Copia de este canal añadiéndole la descripción (topic). Mantiene el resto igual. */
        public CanalPlan conTopic(String topic) {
            return new CanalPlan(nombre, tipo, soloLectura, slowmodeSegundos, claveConfig,
                    limiteVoz, introKey, topic, etiquetas);
        }
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
            new RolPlan("🎙️ Ponente", null, 0xE84393),
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
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, null, null, List.of());
    }

    private static CanalPlan texto(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, introKey, null, List.of());
    }

    private static CanalPlan info(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, null, null, List.of());
    }

    private static CanalPlan info(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, introKey, null, List.of());
    }

    private static CanalPlan voz(String nombre, int limite) {
        return new CanalPlan(nombre, TipoCanalDiscord.VOZ, false, 0, null, limite, null, null, List.of());
    }

    private static CanalPlan escenario(String nombre) {
        return new CanalPlan(nombre, TipoCanalDiscord.ESCENARIO, false, 0, null, 0, null, null, List.of());
    }

    private static CanalPlan slow(String nombre, int segundos, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, segundos, null, 0, introKey, null, List.of());
    }

    /** Canal de foro (publicaciones con título/imagen/descripción). El topic se pone con conTopic. */
    private static CanalPlan foro(String nombre, TipoCanal clave, String... etiquetas) {
        return new CanalPlan(nombre, TipoCanalDiscord.FORO, false, 0, clave, 0, null, null, List.of(etiquetas));
    }

    /** Canal de media (galería de imágenes). */
    private static CanalPlan media(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.MEDIA, false, 0, clave, 0, null, null, List.of());
    }

    /** Canal de anuncios (News, seguible). Admite pin de intro como un canal de texto. */
    private static CanalPlan anuncios(String nombre, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.ANUNCIOS, false, 0, null, 0, introKey, null, List.of());
    }

    /** Categorías y canales del servidor (F1–F4). Nombres decorados con «›» como separador. */
    public static final List<CategoriaPlan> CATEGORIAS = List.of(
            // Contadores en vivo. Canales de voz bloqueados (nadie conecta): solo se lee el nombre,
            // que EstadisticasService renombra periódicamente. Se localizan por PREFIJO de nombre,
            // por eso el prefijo debe coincidir con las constantes del servicio. Van arriba del todo.
            new CategoriaPlan("▬▬ 📊 SERVER STATS ▬▬", false, false, List.of(
                    voz("👥 Miembros: …", 0),
                    voz("🟢 En línea: …", 0),
                    voz("🤖 Bots: …", 0))),
            new CategoriaPlan("▬▬ 📢 INFORMACIÓN ▬▬", false, false, List.of(
                    info("👋・bienvenidas", TipoCanal.BIENVENIDA)
                            .conTopic("Damos la bienvenida a cada nuevo miembro de la comunidad. 👋"),
                    info("🚀・empieza-aquí", null, "intro.empieza")
                            .conTopic("Tu primer paso: normas, roles y cómo moverte por el servidor."),
                    info("🎭・roles", null, "panel.roles")
                            .conTopic("Elige tus roles de objetivo y de notificaciones con los menús."),
                    info("📜・reglas", null, "intro.reglas")
                            .conTopic("Normas de la comunidad. Entrar implica aceptarlas: léelas."),
                    info("🗺️・cómo-funciona", null, "intro.como")
                            .conTopic("XP, niveles, economía y retos: cómo funciona GymProFit."),
                    info("❓・faq", null, "intro.faq")
                            .conTopic("Preguntas frecuentes sobre el servidor, el bot y la app."),
                    anuncios("📣・anuncios", "intro.anuncios")
                            .conTopic("Novedades importantes del servidor y del equipo."),
                    anuncios("📲・novedades-app", null)
                            .conTopic("Actualizaciones y cambios de la app GymProFit."),
                    info("📱・redes-sociales", null, "intro.redes")
                            .conTopic("Síguenos en Instagram, TikTok, YouTube y más."))),
            new CategoriaPlan("▬▬ 💬 COMUNIDAD ▬▬", false, false, List.of(
                    texto("💬・general", null, "intro.general")
                            .conTopic("Charla general. Preséntate, pregunta y conoce a la gente. 💬"),
                    texto("👋・presentaciones", null, "intro.presentaciones")
                            .conTopic("¿Nuevo por aquí? Cuéntanos quién eres y tus objetivos."),
                    texto("😂・memes", null)
                            .conTopic("Memes de gym y humor fit. Sin pasarse. 😂"),
                    texto("🎮・gaming", null)
                            .conTopic("Gaming, partidas y quedadas para jugar."),
                    texto("🎵・música", null)
                            .conTopic("Comparte y descubre música para entrenar. 🎵"),
                    texto("🎧・off-topic", null)
                            .conTopic("Todo lo que no encaje en el resto de canales."))),
            new CategoriaPlan("▬▬ 🏋️ FITNESS ▬▬", false, false, List.of(
                    texto("🗓️・ejercicio-del-día", TipoCanal.EJERCICIO_DIA)
                            .conTopic("El ejercicio o reto del día. ¡A moverse! 🗓️"),
                    media("📈・progresos", null)
                            .conTopic("Comparte tus progresos y marcas (antes/después). Una foto por publicación."),
                    media("📸・fotos", null)
                            .conTopic("Fotos de entrenos, comidas y avances."),
                    foro("🍎・nutrición", null, "Receta", "Plan", "Duda", "Suplementación")
                            .conTopic("Recetas, planes y dudas de alimentación. Abre un post y etiquétalo."),
                    foro("📚・rutinas", null, "Push", "Pull", "Pierna", "Full-body", "Cardio", "Movilidad")
                            .conTopic("Comparte rutinas y splits. Un post por rutina, con su etiqueta."),
                    foro("❓・dudas", null, "Técnica", "Material", "Lesión", "Resuelto")
                            .conTopic("Pregunta sobre entrenamiento y técnica. Marca 'Resuelto' al cerrar."))),
            new CategoriaPlan("▬▬ 🎮 GAMIFICACIÓN ▬▬", false, false, List.of(
                    texto("🏆・logros", TipoCanal.LOGROS)
                            .conTopic("Logros desbloqueados por la comunidad. 🏆"),
                    texto("📊・ranking", null)
                            .conTopic("Clasificación de XP y niveles del servidor."),
                    texto("🪙・economía", null, "intro.proximamente")
                            .conTopic("Monedas, tienda y recompensas. (Próximamente)"),
                    texto("🧠・trivia", null, "intro.proximamente")
                            .conTopic("Preguntas de fitness para ganar XP. (Próximamente)"),
                    texto("⚔️・duelos", null, "intro.proximamente")
                            .conTopic("Rétate con otros miembros. (Próximamente)"),
                    texto("🎯・retos", null, "intro.proximamente")
                            .conTopic("Retos semanales para subir de nivel. (Próximamente)"),
                    slow("🤖・comandos-bot", 5, "intro.comandos")
                            .conTopic("Usa aquí los comandos de GymProBot. Slowmode activo."))),
            new CategoriaPlan("▬▬ 🛎️ AYUDA ▬▬", false, false, List.of(
                    foro("💡・sugerencias", TipoCanal.SUGERENCIAS,
                            "En estudio", "Aprobada", "Rechazada", "Implementada")
                            .conTopic("Propón mejoras: título, imagen y descripción. Vota con reacciones."),
                    texto("🎫・soporte", TipoCanal.SOPORTE, "intro.soporte")
                            .conTopic("¿Necesitas ayuda? Abre un ticket con el equipo."))),
            new CategoriaPlan("▬▬ 🔊 VOZ ▬▬", false, false, List.of(
                    // Lobby Join-To-Create: al entrar, el bot crea una sala temporal propia (lógica
                    // pendiente). Sustituye a la antigua "Privada" fija de 4.
                    voz("➕ Crear sala", 0),
                    voz("🔊 General", 0),
                    voz("🎮 Gaming", 0),
                    voz("🎮 Gaming 2", 0),
                    voz("🎵 Música", 0),
                    voz("🛋️ Chill", 0),
                    voz("🎥 Directo", 0),
                    voz("💤 AFK", 0))),
            new CategoriaPlan("▬▬ 🎤 EVENTOS ▬▬", false, false, List.of(
                    // Canal de escenario (Stage): ponentes hablan, oyentes levantan la mano.
                    escenario("🎤 Escenario"))),
            new CategoriaPlan("▬▬ 🔒 STAFF ▬▬", true, true, List.of(
                    texto("🛠️・staff-chat", null)
                            .conTopic("Coordinación interna del equipo."),
                    texto("🤖・bot-logs", TipoCanal.BOT_LOGS)
                            .conTopic("Registro de eventos del bot."),
                    texto("📋・moderación", null)
                            .conTopic("Alertas de moderación y AutoMod."),
                    texto("📥・reportes", null)
                            .conTopic("Reportes de miembros pendientes de revisar."),
                    texto("🗄️・logs-tickets", null)
                            .conTopic("Histórico de tickets cerrados."),
                    voz("🔒 Voz staff", 0))),
            new CategoriaPlan("▬▬ 🎫 TICKETS ▬▬", true, true, List.of())
    );
}
