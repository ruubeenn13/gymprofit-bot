package com.gymprofit.bot.services;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.List;
import java.util.Map;

/**
 * Blueprint (datos, sin JDA-runtime) del <b>onboarding</b> de Discord que aplica {@code /setup}:
 * canales predeterminados y preguntas de personalización, cada opción con sus roles y/o canales.
 *
 * <p>Discord no localiza el texto del onboarding por usuario, así que los títulos y descripciones
 * van <b>bilingües</b> (ES + EN). El cuerpo JSON se construye con {@link #construirBody} a partir de
 * los mapas nombre→id que arma {@code /setup}; al ser una función pura sobre {@link DataObject} es
 * testeable sin conexión a Discord.</p>
 *
 * @see <a href="https://discord.com/developers/docs/resources/guild#modify-guild-onboarding">
 *      Modify Guild Onboarding</a>
 */
public final class OnboardingPlan {

    private OnboardingPlan() {
    }

    /** Tipo de prompt de onboarding (0 = opción múltiple; único tipo que existe hoy). */
    private static final int TIPO_OPCION_MULTIPLE = 0;

    /**
     * Modo del onboarding. 0 = por defecto (solo cuentan los canales predeterminados para los
     * mínimos); 1 = avanzado (cuentan también los de los prompts). Usamos el modo por defecto.
     */
    private static final int MODO_DEFECTO = 0;

    /**
     * Una opción de un prompt.
     *
     * @param titulo      texto de la opción (bilingüe)
     * @param descripcion descripción de la opción (bilingüe), o {@code null}
     * @param emoji       emoji unicode mostrado junto a la opción
     * @param roles       nombres de rol que asigna (los mismos del {@link SetupServidorPlan})
     * @param canales     nombres de canal a los que apunta la opción
     */
    public record Opcion(String titulo, String descripcion, String emoji,
                         List<String> roles, List<String> canales) {
    }

    /**
     * Un prompt (pregunta) del onboarding.
     *
     * @param titulo       texto de la pregunta (bilingüe)
     * @param unaSola      {@code true} si solo se puede elegir una opción
     * @param obligatoria  {@code true} si hay que responderla para completar el onboarding
     * @param opciones     opciones de la pregunta
     */
    public record Prompt(String titulo, boolean unaSola, boolean obligatoria, List<Opcion> opciones) {
    }

    /** Nombres de los canales predeterminados (Discord exige ≥7, con ≥5 escribibles por @everyone). */
    public static final List<String> CANALES_DEFECTO = List.of(
            "💬・general", "👋・presentaciones", "🎧・off-topic", "🤖・comandos-bot",
            "🗓️・ejercicio-del-día", "📣・anuncios", "📜・reglas", "🚀・empieza-aquí", "🏆・logros");

    /** Preguntas del onboarding, en orden. */
    public static final List<Prompt> PROMPTS = List.of(
            new Prompt("🌍 Idioma / Language", true, true, List.of(
                    new Opcion("🇪🇸 Español", "Hablo español / I speak Spanish", "🇪🇸",
                            List.of("🇪🇸 Español"), List.of()),
                    new Opcion("🇬🇧 English", "I speak English / Hablo inglés", "🇬🇧",
                            List.of("🇬🇧 English"), List.of()))),
            new Prompt("🎯 ¿Cuál es tu objetivo? / Your goal?", true, true, List.of(
                    new Opcion("💪 Fuerza", "Levantar más, ganar músculo y potencia", "💪",
                            List.of("💪 Fuerza"), List.of("📚・rutinas")),
                    new Opcion("🏃 Cardio", "Resistencia, correr y salud cardiovascular", "🏃",
                            List.of("🏃 Cardio"), List.of("📚・rutinas")),
                    new Opcion("⚖️ Pérdida de peso", "Definir, quemar grasa y sentirte mejor", "⚖️",
                            List.of("⚖️ Pérdida de peso"), List.of("🍎・nutrición")),
                    new Opcion("🌟 General", "Fitness completo sin un foco único", "🌟",
                            List.of("🌟 General"), List.of()))),
            new Prompt("🏋️ ¿Tu nivel en el gym? / Your level?", true, false, List.of(
                    new Opcion("🌱 Principiante", "Empezando o menos de 1 año entrenando", "🌱",
                            List.of("🌱 Principiante"), List.of("❓・dudas")),
                    new Opcion("💪 Intermedio", "1–3 años, técnica ya asentada", "💪",
                            List.of("💪 Intermedio"), List.of()),
                    new Opcion("🔥 Avanzado", "+3 años, entreno serio y constante", "🔥",
                            List.of("🔥 Avanzado"), List.of()))),
            new Prompt("📣 ¿Qué avisos quieres? / Notifications?", false, false, List.of(
                    new Opcion("📣 Anuncios", "Novedades importantes del servidor", "📣",
                            List.of("📣 Avisos"), List.of()),
                    new Opcion("🎯 Retos", "Aviso cuando empieza un reto", "🎯",
                            List.of("🎯 Retos"), List.of()),
                    new Opcion("📅 Eventos", "Quedadas, directos y eventos en vivo", "📅",
                            List.of("📅 Eventos"), List.of()),
                    new Opcion("🎁 Sorteos", "Te avisamos de cada sorteo", "🎁",
                            List.of("🎁 Sorteos"), List.of()))),
            new Prompt("📌 ¿Qué te interesa? / Interests?", false, false, List.of(
                    new Opcion("🏋️ Rutinas", "Planes de entreno y divisiones", "🏋️",
                            List.of(), List.of("📚・rutinas")),
                    new Opcion("🍎 Nutrición", "Dietas, recetas y suplementación", "🍎",
                            List.of(), List.of("🍎・nutrición")),
                    new Opcion("📈 Progresos", "Comparte tu antes/después y PRs", "📈",
                            List.of(), List.of("📈・progresos")),
                    new Opcion("🎯 Retos", "Únete a los retos de la comunidad", "🎯",
                            List.of(), List.of("🎯・retos")))));

    /**
     * Construye el cuerpo JSON del {@code PUT /guilds/{id}/onboarding}. Resuelve los nombres de rol
     * y canal a sus IDs con los mapas dados; las referencias que no existan se omiten. A cada prompt
     * y opción se le asigna un id numérico secuencial temporal (Discord lo reasigna).
     *
     * @param rolesIds   nombre de rol → id
     * @param canalesIds nombre de canal → id
     * @return {@link DataObject} listo para enviar como cuerpo del PUT
     */
    public static DataObject construirBody(Map<String, Long> rolesIds, Map<String, Long> canalesIds) {
        DataArray defecto = DataArray.empty();
        for (String canal : CANALES_DEFECTO) {
            Long id = canalesIds.get(canal);
            if (id != null) {
                defecto.add(Long.toUnsignedString(id));
            }
        }

        DataArray prompts = DataArray.empty();
        long siguienteId = 1;
        for (Prompt prompt : PROMPTS) {
            DataArray opciones = DataArray.empty();
            for (Opcion opcion : prompt.opciones()) {
                DataArray roleIds = idsDe(opcion.roles(), rolesIds);
                DataArray channelIds = idsDe(opcion.canales(), canalesIds);
                opciones.add(DataObject.empty()
                        .put("id", Long.toString(siguienteId++))
                        .put("title", opcion.titulo())
                        .put("description", opcion.descripcion())
                        .put("emoji_name", opcion.emoji())
                        .put("role_ids", roleIds)
                        .put("channel_ids", channelIds));
            }
            prompts.add(DataObject.empty()
                    .put("id", Long.toString(siguienteId++))
                    .put("type", TIPO_OPCION_MULTIPLE)
                    .put("title", prompt.titulo())
                    .put("single_select", prompt.unaSola())
                    .put("required", prompt.obligatoria())
                    .put("in_onboarding", true)
                    .put("options", opciones));
        }

        return DataObject.empty()
                .put("enabled", true)
                .put("mode", MODO_DEFECTO)
                .put("default_channel_ids", defecto)
                .put("prompts", prompts);
    }

    /** Resuelve una lista de nombres a un {@link DataArray} de IDs (como string), omitiendo faltantes. */
    private static DataArray idsDe(List<String> nombres, Map<String, Long> mapa) {
        DataArray ids = DataArray.empty();
        for (String nombre : nombres) {
            Long id = mapa.get(nombre);
            if (id != null) {
                ids.add(Long.toUnsignedString(id));
            }
        }
        return ids;
    }
}
