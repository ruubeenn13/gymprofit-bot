package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * {@code /ejercicios [grupo] [dificultad] [buscar]}: catálogo paginado de la API (8 por página)
 * con botones ◀ ▶ y menú desplegable que abre la ficha completa. El estado viaja en el
 * {@code customId} (patrón {@code /modlogs}): los botones sobreviven a un reinicio del bot.
 * Respuesta pública (regla 13); los errores de API, efímeros y amables.
 */
public final class EjerciciosComando implements Comando {

    private static final String NOMBRE = "ejercicios";
    /** Navegación ◀ ▶: {@code ejercicios:<owner>:<pag>:<grupo>:<dif>:<q>}. */
    public static final String PREFIJO_NAV = "ejercicios:";
    /** Menú de ficha: mismos campos de estado; el value de la opción es el id. */
    public static final String PREFIJO_SEL = "ejercicios-sel:";
    /** Botón de volver de la ficha a la lista. */
    public static final String PREFIJO_VOLVER = "ejercicios-volver:";
    /**
     * Tope de la búsqueda dentro del customId. El spec pedía 40, pero con el prefijo más largo
     * (18) + snowflake (20) + página + filtros + separadores, 40 rebasa los 100 chars que admite
     * Discord; con 30 el peor caso queda en 94.
     */
    public static final int MAX_BUSQUEDA = 30;

    /** Grupos musculares del catálogo (enum de la API); se ofrecen como choices del comando. */
    private static final List<String> GRUPOS = List.of("PECHO", "ESPALDA", "PIERNAS", "HOMBROS",
            "BRAZOS", "ABDOMEN", "CARDIO", "FULLBODY");
    /** Dificultades del catálogo (enum de la API). */
    private static final List<String> DIFICULTADES =
            List.of("PRINCIPIANTE", "INTERMEDIO", "AVANZADO");

    private final EjercicioService ejercicios;
    private final ExecutorService executor;

    public EjerciciosComando(EjercicioService ejercicios, ExecutorService executor) {
        this.ejercicios = ejercicios;
        this.executor = executor;
    }

    /**
     * Estado de una consulta, codificado en el customId. {@code grupo}/{@code dificultad}/
     * {@code busqueda} usan "" como «sin filtro» (un customId no transporta nulls).
     *
     * <p>Es público porque el listener de paginación vive en otro paquete
     * ({@code events/EjerciciosPaginadorListener}) y necesita parsear y recodificar el estado.</p>
     */
    public record Filtros(long ownerId, int pagina, String grupo, String dificultad,
                          String busqueda) {

        /** Codifica con el prefijo dado, truncando la búsqueda a {@link #MAX_BUSQUEDA}. */
        public String codificar(String prefijo) {
            String q = busqueda.length() > MAX_BUSQUEDA
                    ? busqueda.substring(0, MAX_BUSQUEDA) : busqueda;
            return prefijo + ownerId + ":" + pagina + ":" + grupo + ":" + dificultad + ":" + q;
        }

        /** Cambia solo la página (para las flechas). */
        public Filtros conPagina(int nueva) {
            return new Filtros(ownerId, nueva, grupo, dificultad, busqueda);
        }

        /**
         * Parsea el estado. La búsqueda va en el último campo y el split se limita a 5 trozos:
         * los ':' que el usuario escriba en ella sobreviven.
         */
        public static Filtros parsear(String customId, String prefijo) {
            String[] p = customId.substring(prefijo.length()).split(":", 5);
            return new Filtros(Long.parseUnsignedLong(p[0]), Integer.parseInt(p[1]),
                    p[2], p[3], p[4]);
        }
    }

    @Override
    public SlashCommandData definicion() {
        OptionData grupo = new OptionData(OptionType.STRING, "grupo",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.grupo"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.grupo"));
        GRUPOS.forEach(g -> grupo.addChoice(capitalizar(g), g));
        OptionData dificultad = new OptionData(OptionType.STRING, "dificultad",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.dificultad"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.dificultad"));
        DIFICULTADES.forEach(d -> dificultad.addChoice(capitalizar(d), d));
        OptionData buscar = new OptionData(OptionType.STRING, "buscar",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.buscar"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.buscar"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ejercicios.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ejercicios.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(grupo, dificultad, buscar);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Filtros filtros = new Filtros(evento.getUser().getIdLong(), 0,
                opcion(evento, "grupo"), opcion(evento, "dificultad"), opcion(evento, "buscar"));
        // deferReply antes de tocar la API: Render puede tardar ~50 s en despertar y Discord
        // solo da 3 s sin defer. La llamada va al executor propio, nunca al hilo del gateway.
        evento.deferReply().queue();
        CompletableFuture.runAsync(() -> {
            try {
                PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar(filtros.busqueda(),
                        filtros.grupo(), filtros.dificultad(), 0, locale.getLanguage());
                evento.getHook().editOriginalEmbeds(construirLista(locale, pagina, filtros))
                        .setComponents(construirComponentes(locale, pagina, filtros))
                        .queue();
            } catch (ApiException e) {
                // El «pensando…» público se borra y el aviso va efímero (regla 13).
                evento.getHook().deleteOriginal().queue();
                evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS,
                                locale, Messages.get(locale, "ejercicios.error")))
                        .setEphemeral(true).queue();
            }
        }, executor);
    }

    /** Valor de una opción opcional, con "" como «sin filtro» (lo que espera {@link Filtros}). */
    private static String opcion(SlashCommandInteractionEvent evento, String nombre) {
        var opcion = evento.getOption(nombre);
        return opcion == null ? "" : opcion.getAsString();
    }

    /** Embed de una página de la lista (azul de consulta). Estático para test y listener. */
    public static MessageEmbed construirLista(Locale locale, PaginaDTO<EjercicioDTO> pagina,
                                              Filtros filtros) {
        EmbedBuilder builder = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.titulo"));
        if (pagina.content().isEmpty()) {
            return builder.setDescription(Messages.get(locale, "ejercicios.vacio")).build();
        }
        StringBuilder desc = new StringBuilder(Messages.get(locale, "ejercicios.resumen",
                pagina.totalElements(), pagina.page() + 1, pagina.totalPages()));
        desc.append("\n");
        // La numeración es absoluta dentro del catálogo filtrado, no relativa a la página: así se
        // ve de un vistazo por dónde va uno cuando hay 100+ páginas.
        int n = pagina.page() * EjercicioService.TAMANO_PAGINA + 1;
        for (EjercicioDTO e : pagina.content()) {
            desc.append("\n**").append(n++).append(". ").append(e.nombre()).append("**")
                    .append(" — ").append(capitalizar(e.grupoMuscular()))
                    .append(" · ").append(capitalizar(e.dificultad()));
        }
        return builder.setDescription(desc.toString()).build();
    }

    /** Fila de flechas + menú de ficha; vacío si no hay resultados. Estático para el listener. */
    public static List<ActionRow> construirComponentes(Locale locale,
                                                       PaginaDTO<EjercicioDTO> pagina,
                                                       Filtros filtros) {
        if (pagina.content().isEmpty()) {
            return List.of();
        }
        Button anterior = Button.secondary(
                        filtros.conPagina(filtros.pagina() - 1).codificar(PREFIJO_NAV), "◀")
                .withDisabled(filtros.pagina() == 0);
        Button siguiente = Button.secondary(
                        filtros.conPagina(filtros.pagina() + 1).codificar(PREFIJO_NAV), "▶")
                .withDisabled(pagina.last());
        StringSelectMenu.Builder menu = StringSelectMenu.create(filtros.codificar(PREFIJO_SEL))
                // El placeholder también se traduce: el servidor es bilingüe y de lo contrario
                // un usuario en EN vería lista y ficha en inglés pero el desplegable en español.
                .setPlaceholder(Messages.get(locale, "ejercicios.menu"));
        for (EjercicioDTO e : pagina.content()) {
            // El label admite 100 chars; los nombres del catálogo caben, pero por si acaso.
            String nombre = e.nombre().length() > 100 ? e.nombre().substring(0, 100) : e.nombre();
            menu.addOption(nombre, String.valueOf(e.id()));
        }
        return List.of(ActionRow.of(anterior, siguiente), ActionRow.of(menu.build()));
    }

    /** Ficha completa de un ejercicio (imagen grande + campos). Estática para el listener. */
    public static MessageEmbed construirFicha(Locale locale, EjercicioDTO e) {
        EmbedBuilder builder = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale, e.nombre());
        if (e.descripcion() != null && !e.descripcion().isBlank()) {
            builder.setDescription(e.descripcion());
        }
        if (e.imagenUrl() != null && !e.imagenUrl().isBlank()) {
            builder.setImage(e.imagenUrl());
        }
        builder.addField(Messages.get(locale, "ejercicios.campo.grupo"),
                capitalizar(e.grupoMuscular()), true);
        if (e.musculoPrimario() != null) {
            builder.addField(Messages.get(locale, "ejercicios.campo.musculo"),
                    e.musculoPrimario(), true);
        }
        builder.addField(Messages.get(locale, "ejercicios.campo.dificultad"),
                capitalizar(e.dificultad()), true);
        if (e.caloriasQuemadas() != null) {
            builder.addField(Messages.get(locale, "ejercicios.campo.calorias"),
                    String.valueOf(e.caloriasQuemadas()), true);
        }
        if (e.equipoNecesario() != null && !e.equipoNecesario().isBlank()) {
            builder.addField(Messages.get(locale, "ejercicios.campo.equipo"),
                    e.equipoNecesario(), true);
        }
        if (e.instrucciones() != null && !e.instrucciones().isBlank()) {
            builder.addField(Messages.get(locale, "ejercicios.campo.instrucciones"),
                    truncar(e.instrucciones(), 1024), false); // límite de field de Discord
        }
        return builder.build();
    }

    /** Botón para volver de la ficha a la página de lista de la que se vino. */
    public static List<ActionRow> construirBotonVolver(Locale locale, Filtros filtros) {
        return List.of(ActionRow.of(Button.secondary(filtros.codificar(PREFIJO_VOLVER),
                Messages.get(locale, "ejercicios.volver"))));
    }

    /** {@code "PECHO"} → {@code "Pecho"} (los enums de la API gritan; el embed no). */
    private static String capitalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "—";
        }
        return valor.charAt(0) + valor.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
