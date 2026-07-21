package com.gymprofit.bot.services;

import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;
import net.dv8tion.jda.api.Permission;

import java.util.ArrayList;
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
     * Override de permisos de un rol en un canal, declarado en el plan. El rol se referencia por
     * <b>nombre</b> (se resuelve a ID en {@code /setup}); {@code allow}/{@code deny} son máscaras
     * de permisos de JDA.
     *
     * @param rolNombre nombre exacto del rol (o {@link #EVERYONE} para @everyone)
     * @param allow     permisos concedidos
     * @param deny      permisos denegados
     */
    public record PermisoRol(String rolNombre, long allow, long deny) {
    }

    /** Nombre reservado que, en un {@link PermisoRol}, representa al rol público {@code @everyone}. */
    public static final String EVERYONE = "@everyone";

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
                            String introKey, String topic, List<String> etiquetas,
                            List<PermisoRol> permisos) {

        /** Copia de este canal añadiéndole la descripción (topic). Mantiene el resto igual. */
        public CanalPlan conTopic(String topic) {
            return new CanalPlan(nombre, tipo, soloLectura, slowmodeSegundos, claveConfig,
                    limiteVoz, introKey, topic, etiquetas, permisos);
        }

        /** Copia marcando el canal como solo-lectura: {@code @everyone} no escribe, Staff sí. */
        public CanalPlan conSoloLectura() {
            return new CanalPlan(nombre, tipo, true, slowmodeSegundos, claveConfig,
                    limiteVoz, introKey, topic, etiquetas, permisos);
        }

        /** Copia añadiendo un override que <b>concede</b> {@code perms} al rol {@code rol}. */
        public CanalPlan permite(String rol, Permission... perms) {
            return conPermiso(new PermisoRol(rol, Permission.getRaw(perms), 0L));
        }

        /** Copia añadiendo un override que <b>niega</b> {@code perms} al rol {@code rol}. */
        public CanalPlan niega(String rol, Permission... perms) {
            return conPermiso(new PermisoRol(rol, 0L, Permission.getRaw(perms)));
        }

        private CanalPlan conPermiso(PermisoRol permiso) {
            List<PermisoRol> lista = new ArrayList<>(permisos);
            lista.add(permiso);
            return new CanalPlan(nombre, tipo, soloLectura, slowmodeSegundos, claveConfig,
                    limiteVoz, introKey, topic, etiquetas, List.copyOf(lista));
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
            new RolPlan("▬▬ EXPERIENCIA ▬▬", null, 0x2B2D31),
            new RolPlan("🌱 Principiante", null, 0x82C91E),
            new RolPlan("💪 Intermedio", null, 0xF59F00),
            new RolPlan("🔥 Avanzado", null, 0xD9480F),
            new RolPlan("▬▬ IDIOMA ▬▬", null, 0x2B2D31),
            new RolPlan("🇪🇸 Español", null, 0xE03131),
            new RolPlan("🇬🇧 English", null, 0x1971C2),
            new RolPlan("▬▬ NOTIFICACIONES ▬▬", null, 0x2B2D31),
            new RolPlan("📣 Avisos", null, 0xE67E22),
            new RolPlan("🎯 Retos", null, 0x9B59B6),
            new RolPlan("📅 Eventos", null, 0x5865F2),
            new RolPlan("🎁 Sorteos", null, 0xEB459E),
            new RolPlan("🤝 Miembro", null, 0x2ECC71),
            new RolPlan("🔇 Silenciado", null, 0x607D8B)
    );

    private static CanalPlan texto(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, null, null, List.of(), List.of());
    }

    private static CanalPlan texto(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, 0, clave, 0, introKey, null, List.of(), List.of());
    }

    private static CanalPlan info(String nombre, TipoCanal clave) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, null, null, List.of(), List.of());
    }

    private static CanalPlan info(String nombre, TipoCanal clave, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, true, 0, clave, 0, introKey, null, List.of(), List.of());
    }

    private static CanalPlan voz(String nombre, int limite) {
        return new CanalPlan(nombre, TipoCanalDiscord.VOZ, false, 0, null, limite, null, null, List.of(), List.of());
    }

    private static CanalPlan escenario(String nombre) {
        return new CanalPlan(nombre, TipoCanalDiscord.ESCENARIO, false, 0, null, 0, null, null, List.of(), List.of());
    }

    private static CanalPlan slow(String nombre, int segundos, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.TEXTO, false, segundos, null, 0, introKey, null, List.of(), List.of());
    }

    /** Canal de foro (publicaciones con título/imagen/descripción). El topic se pone con conTopic. */
    private static CanalPlan foro(String nombre, TipoCanal clave, String... etiquetas) {
        return new CanalPlan(nombre, TipoCanalDiscord.FORO, false, 0, clave, 0, null, null, List.of(etiquetas), List.of());
    }

    /** Canal de media (galería de imágenes) con sus etiquetas. */
    private static CanalPlan media(String nombre, TipoCanal clave, String... etiquetas) {
        return new CanalPlan(nombre, TipoCanalDiscord.MEDIA, false, 0, clave, 0, null, null, List.of(etiquetas), List.of());
    }

    /**
     * Canal de anuncios (News, seguible). Admite pin de intro como un canal de texto. Se crea
     * <b>solo-lectura</b>: publica el staff y {@code @everyone} solo lee.
     */
    private static CanalPlan anuncios(String nombre, String introKey) {
        return new CanalPlan(nombre, TipoCanalDiscord.ANUNCIOS, true, 0, null, 0, introKey, null, List.of(), List.of());
    }

    /** Categorías y canales del servidor (F1–F4). Nombres decorados con «›» como separador. */
    public static final List<CategoriaPlan> CATEGORIAS = List.of(
            // Contadores en vivo. Canales de voz bloqueados (nadie conecta): solo se lee el nombre,
            // que EstadisticasService renombra periódicamente. Se localizan por PREFIJO de nombre,
            // por eso el prefijo debe coincidir con las constantes del servicio. Van arriba del todo.
            new CategoriaPlan("▬▬ 📊 SERVER STATS ▬▬", false, false, List.of(
                    voz("🔥 XP repartido: …", 0),
                    voz("🏆 Nº1: …", 0),
                    voz("🚀 Boosts: …", 0),
                    voz("🔊 En voz: …", 0),
                    voz("🎯 Reto: …", 0),
                    voz("⏳ Evento: …", 0))),
            // EVENTOS va arriba (encima de INFORMACIÓN), por decisión del usuario.
            new CategoriaPlan("▬▬ 🎤 EVENTOS ▬▬", false, false, List.of(
                    // Canales News: el staff anuncia y hace @mención al rol opt-in (📅 Eventos /
                    // 🎁 Sorteos). Todos ven los posts; solo se menciona a quien tenga el rol.
                    anuncios("📅・eventos", "intro.eventos")
                            .conTopic("Quedadas, directos y eventos en vivo. Ping al rol 📅 Eventos."),
                    anuncios("🎁・sorteos", "intro.sorteos")
                            .conTopic("Sorteos y premios de la comunidad. Ping al rol 🎁 Sorteos."),
                    // Canal de escenario (Stage): ponentes hablan, oyentes levantan la mano.
                    escenario("🎤 Escenario")
                            .permite("🎙️ Ponente", Permission.VOICE_SPEAK))),
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
                    foro("❓・faq", null, "General", "XP y niveles", "Economía", "App", "Cuenta")
                            .conTopic("Preguntas frecuentes. Busca por etiqueta o abre un post con tu duda."),
                    anuncios("📣・anuncios", "intro.anuncios")
                            .conTopic("Novedades importantes del servidor y del equipo."),
                    anuncios("📲・novedades-app", "intro.novedadesapp")
                            .conTopic("Actualizaciones y cambios de la app GymProFit."),
                    info("📱・redes-sociales", null, "intro.redes")
                            .conTopic("Síguenos en Instagram, TikTok, YouTube y más."))),
            new CategoriaPlan("▬▬ 💬 COMUNIDAD ▬▬", false, false, List.of(
                    texto("💬・general", null, "intro.general")
                            .conTopic("Charla general. Preséntate, pregunta y conoce a la gente. 💬"),
                    texto("👋・presentaciones", null, "intro.presentaciones")
                            .conTopic("¿Nuevo por aquí? Cuéntanos quién eres y tus objetivos."),
                    texto("😂・memes", null, "intro.memes")
                            .conTopic("Memes de gym y humor fit. Sin pasarse. 😂"),
                    texto("🎮・gaming", null, "intro.gaming")
                            .conTopic("Gaming, partidas y quedadas para jugar."),
                    texto("🎵・música", null, "intro.musica")
                            .conTopic("Comparte y descubre música para entrenar. 🎵"),
                    texto("🎧・off-topic", null, "intro.offtopic")
                            .conTopic("Todo lo que no encaje en el resto de canales."))),
            new CategoriaPlan("▬▬ 🏋️ FITNESS ▬▬", false, false, List.of(
                    // El job diario publica aquí a las 08:00; la intro explica el horario y /ejercicio-dia.
                    texto("🗓️・ejercicio-del-día", TipoCanal.EJERCICIO_DIA, "intro.ejerciciodia")
                            .conTopic("El ejercicio o reto del día. ¡A moverse! 🗓️"),
                    media("📈・progresos", null, "Antes/Después", "Marca (PR)", "Medidas", "Otro")
                            .conTopic("Comparte tus progresos y marcas (antes/después). Una foto por publicación."),
                    media("📸・fotos", null, "Entreno", "Comida", "Material", "Gym")
                            .conTopic("Fotos de entrenos, comidas y avances."),
                    foro("🍎・nutrición", null, "Receta", "Plan", "Duda", "Suplementación")
                            .conTopic("Recetas, planes y dudas de alimentación. Abre un post y etiquétalo.")
                            .permite("🍎 Nutricionista", Permission.MESSAGE_MANAGE, Permission.MANAGE_THREADS),
                    foro("📚・rutinas", null, "Push", "Pull", "Pierna", "Full-body", "Cardio", "Movilidad")
                            .conTopic("Comparte rutinas y splits. Un post por rutina, con su etiqueta.")
                            .permite("🧑‍🏫 Coach", Permission.MESSAGE_MANAGE, Permission.MANAGE_THREADS),
                    foro("❓・dudas", null, "Técnica", "Material", "Lesión", "Resuelto")
                            .conTopic("Pregunta sobre entrenamiento y técnica. Marca 'Resuelto' al cerrar.")
                            .permite("🧑‍🏫 Coach", Permission.MESSAGE_MANAGE, Permission.MANAGE_THREADS))),
            new CategoriaPlan("▬▬ 🎮 GAMIFICACIÓN ▬▬", false, false, List.of(
                    info("🏆・logros", TipoCanal.LOGROS, "intro.logros")
                            .conTopic("Logros desbloqueados por la comunidad (los publica el bot). 🏆"),
                    info("📊・ranking", null, "intro.ranking")
                            .conTopic("Clasificación de XP y niveles del servidor (la publica el bot)."),
                    texto("🧠・trivia", null, "intro.proximamente")
                            .conTopic("Preguntas de fitness para ganar XP. (Próximamente)")
                            .conSoloLectura(),
                    texto("⚔️・duelos", null, "intro.proximamente")
                            .conTopic("Rétate con otros miembros. (Próximamente)")
                            .conSoloLectura(),
                    texto("🎯・retos", null, "intro.proximamente")
                            .conTopic("Retos semanales para subir de nivel. (Próximamente)"),
                    slow("🤖・comandos-bot", 5, "intro.comandos")
                            .conTopic("Usa aquí los comandos de GymProBot. Slowmode activo."))),
            // Simulador de vida (RPG de ficción). Empieza con la guía y el canal de economía;
            // se irán añadiendo canales (tienda, mercado, banco, casino, gremios) por fases.
            new CategoriaPlan("▬▬ 🎮 VIDA ▬▬", false, false, List.of(
                    info("📖・cómo-jugar", null, "intro.simulador")
                            .conTopic("Cómo funciona el simulador de vida: personaje, dinero y trabajos."),
                    texto("💰・economía", null, "intro.economia")
                            .conTopic("Tu vida en el servidor: /perfil, /daily, /balance y más. 🪙"),
                    // Aventura (COMBAT): equipo, mundos desbloqueables y bestiario.
                    texto("⚔️・combate", null, "intro.combate")
                            .conTopic("Equípate y prepárate para la aventura: /equipar, /mundos, /monstruos. ⚔️"),
                    texto("🗺️・mundos", null, "intro.mundos")
                            .conTopic("Los mundos del RPG y su progreso: /mundos. 🗺️"),
                    texto("📖・bestiario", null, "intro.bestiario")
                            .conTopic("Todos los monstruos por mundo y dificultad: /monstruos. 📖"),
                    // Economía social/riesgo (mercado, casino, bolsa) y comunidad del RPG.
                    texto("📈・bolsa", null, "intro.bolsa")
                            .conTopic("Bolsa de ficción: /bolsa, /invertir, /cartera. 📈"))),
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
            new CategoriaPlan("▬▬ 🔒 STAFF ▬▬", true, true, List.of(
                    texto("🛠️・staff-chat", null, "intro.staffchat")
                            .conTopic("Coordinación interna del equipo."),
                    texto("🤖・bot-logs", TipoCanal.BOT_LOGS)
                            .conTopic("Registro de eventos del bot."),
                    texto("📋・moderación", null, "intro.moderacioncanal")
                            .conTopic("Alertas de moderación y AutoMod."),
                    foro("📥・reportes", null, "Pendiente", "En curso", "Resuelto", "Descartado")
                            .conTopic("Reportes de miembros. Un post por caso; etiqueta su estado."),
                    texto("🗄️・logs-tickets", null, "intro.logstickets")
                            .conTopic("Histórico de tickets cerrados."),
                    voz("🔒 Voz staff", 0))),
            new CategoriaPlan("▬▬ 🎫 TICKETS ▬▬", true, true, List.of())
    );
}
