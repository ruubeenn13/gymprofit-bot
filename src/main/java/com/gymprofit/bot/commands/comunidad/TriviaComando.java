package com.gymprofit.bot.commands.comunidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.TriviaPregunta;
import com.gymprofit.bot.db.TriviaRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TriviaService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /trivia} con subcomandos (jugar, ranking): el juego de trivia fitness de la Fase 4. Toda la
 * lógica (elegir pregunta no respondida, validar la respuesta one-shot y pagar el premio) vive en
 * {@link TriviaService}; aquí solo se pinta la pregunta con sus botones y el ranking de aciertos.
 *
 * <p>Visibilidad: {@code jugar} es <b>efímero</b> (es un quiz personal: la pregunta y sus opciones solo
 * las ve quien juega, para que nadie copie la respuesta ni se le fastidie el suyo); {@code ranking} es
 * <b>público</b> (la competición se ve a la vista del canal). El botón pulsado lo resuelve
 * {@link com.gymprofit.bot.events.TriviaListener}, que edita el propio mensaje efímero con el desenlace.
 */
public final class TriviaComando implements Comando {

    private static final String NOMBRE = "trivia";
    /** Número de jugadores que se listan en el ranking de aciertos. */
    private static final int TOP = 10;
    /** Discord corta la etiqueta de un botón a 80 caracteres: tope al que se recorta cada opción. */
    private static final int MAX_ETIQUETA = 80;
    /** Prefijo del customId de responder: {@code trivia:resp:<preguntaId>:<letra>}. */
    public static final String BOTON_RESP = "trivia:resp";
    /**
     * Categorías ofrecidas como choice de {@code jugar}. El value es el nombre almacenado en
     * {@code trivia_preguntas.categoria} (mayúsculas); la etiqueta visible sale de
     * {@code trivia.categoria.<lowercase>}. Que se ofrezca una categoría sin preguntas no es problema:
     * el service devuelve «sin preguntas» y el comando lo avisa.
     */
    private static final String[] CATEGORIAS = {
            "FITNESS", "NUTRICION", "ENTRENAMIENTO", "ANATOMIA", "SUPLEMENTACION", "CULTURA_FITNESS"
    };

    private final TriviaService trivia;

    public TriviaComando(TriviaService trivia) {
        this.trivia = trivia;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData categoria = new OptionData(OptionType.STRING, "categoria",
                Messages.get(Messages.ES, "comando.trivia.opcion.categoria"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trivia.opcion.categoria"));
        for (String cat : CATEGORIAS) {
            categoria.addChoice(
                    Messages.get(Messages.ES, "trivia.categoria." + cat.toLowerCase(Locale.ROOT)), cat);
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.trivia.desc"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.trivia.desc"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trivia.desc"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("jugar", "comando.trivia.jugar.desc").addOptions(categoria),
                        sub("ranking", "comando.trivia.ranking.desc"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        boolean es = locale == Messages.ES;
        String sub = evento.getSubcommandName() == null ? "jugar" : evento.getSubcommandName();
        switch (sub) {
            case "jugar" -> jugar(evento, locale, es);
            case "ranking" -> ranking(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.TRIVIA, locale,
                    Messages.get(locale, "trivia.error"))).setEphemeral(true).queue();
        }
    }

    /**
     * Sirve la siguiente pregunta no respondida (opcionalmente de una categoría), en <b>efímero</b>: el
     * enunciado y las cuatro opciones como botones A-D. La resolución del botón la hace el listener. Si no
     * quedan preguntas se avisa (también efímero).
     */
    private void jugar(SlashCommandInteractionEvent evento, Locale locale, boolean es) {
        OptionMapping catOpt = evento.getOption("categoria");
        Optional<String> categoria = catOpt == null ? Optional.empty() : Optional.of(catOpt.getAsString());
        Optional<TriviaPregunta> pOpt = trivia.siguiente(evento.getUser().getIdLong(), categoria, es);
        if (pOpt.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.TRIVIA, locale,
                    Messages.get(locale, "trivia.sin_preguntas"))).setEphemeral(true).queue();
            return;
        }
        TriviaPregunta p = pOpt.get();
        String catVisible = Messages.get(locale, "trivia.categoria." + p.categoria().toLowerCase(Locale.ROOT));
        String titulo = Messages.get(locale, "trivia.pregunta.titulo", catVisible);
        String cuerpo = Messages.get(locale, "trivia.pregunta.cuerpo", p.pregunta(), catVisible, p.dificultad());
        List<Button> botones = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            char letra = (char) ('A' + i);
            String etiqueta = letra + ") " + p.opciones().get(i);
            if (etiqueta.length() > MAX_ETIQUETA) {
                etiqueta = etiqueta.substring(0, MAX_ETIQUETA);
            }
            botones.add(Button.primary(BOTON_RESP + ":" + p.id() + ":" + letra, etiqueta));
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.TRIVIA, locale, titulo, cuerpo).build())
                .setComponents(ActionRow.of(botones))
                .setEphemeral(true).queue();
    }

    /**
     * Pinta el top {@value #TOP} de jugadores por aciertos (F4). <b>Público</b> (la competición se ve a la
     * vista del canal). Reusa el estilo de podio del resto de rankings (medallas para el podio, número para
     * el resto). El id de usuario viaja como texto en {@code <@id>} para que MessageFormat no le meta
     * separadores de millar y rompa la mención.
     */
    private void ranking(SlashCommandInteractionEvent evento, Locale locale) {
        List<TriviaRepositorio.FilaTrivia> top = trivia.ranking(TOP);
        if (top.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.TRIVIA, locale,
                    Messages.get(locale, "trivia.ranking.titulo"),
                    Messages.get(locale, "trivia.ranking.vacio")).build()).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        int puesto = 1;
        for (TriviaRepositorio.FilaTrivia f : top) {
            sb.append(Messages.get(locale, "trivia.ranking.fila",
                    medalla(puesto), String.valueOf(f.discordId()), f.aciertos()));
            sb.append('\n');
            puesto++;
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.TRIVIA, locale,
                Messages.get(locale, "trivia.ranking.titulo"), sb.toString().strip()).build()).queue();
    }

    /** Medalla del podio (oro/plata/bronce) o el número de puesto en negrita (mismo criterio que TopComando). */
    private static String medalla(int puesto) {
        return switch (puesto) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "**" + puesto + ".**";
        };
    }
}
