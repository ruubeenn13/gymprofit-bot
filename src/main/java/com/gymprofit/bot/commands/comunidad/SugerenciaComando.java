package com.gymprofit.bot.commands.comunidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.SugerenciaService;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /sugerencia}: crea una sugerencia en el foro {@code 💡・sugerencias} con votación 👍/👎 y la
 * etiqueta «En estudio», y la guarda. Disponible para todos. La resuelve el staff con
 * {@code /sugerencia-resolver}.
 */
public final class SugerenciaComando implements Comando {

    private static final String NOMBRE = "sugerencia";
    private static final String FORO = "💡・sugerencias";
    private static final String ETIQUETA_PENDIENTE = "En estudio";
    private static final int MAX_TITULO = 90;

    private final SugerenciaService sugerencias;

    public SugerenciaComando(SugerenciaService sugerencias) {
        this.sugerencias = sugerencias;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData texto = new OptionData(OptionType.STRING, "texto",
                Messages.get(Messages.ES, "comando.sugerencia.opcion.texto"), true)
                .setMaxLength(SugerenciaService.MAX_CONTENIDO)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sugerencia.opcion.texto"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.sugerencia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.sugerencia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sugerencia.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(texto);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        ForumChannel foro = evento.getGuild().getForumChannelsByName(FORO, false)
                .stream().findFirst().orElse(null);
        if (foro == null) {
            evento.reply(Messages.get(locale, "contenido.sincanal", FORO)).setEphemeral(true).queue();
            return;
        }
        String texto = evento.getOption("texto").getAsString();
        String titulo = texto.length() > MAX_TITULO ? texto.substring(0, MAX_TITULO) + "…" : texto;

        evento.deferReply(true).queue();
        var embed = EmbedFactory.base(EmbedFactory.Tipo.SUGERENCIA, locale,
                Messages.get(locale, "sugerencia.titulo", evento.getUser().getName()), texto).build();

        var accion = foro.createForumPost(titulo, MessageCreateData.fromEmbeds(embed));
        ForumTag etiqueta = foro.getAvailableTagsByName(ETIQUETA_PENDIENTE, false)
                .stream().findFirst().orElse(null);
        if (etiqueta != null) {
            accion = accion.setTags(List.of(etiqueta));
        }
        accion.queue(post -> {
            post.getMessage().addReaction(Emoji.fromUnicode("👍")).queue();
            post.getMessage().addReaction(Emoji.fromUnicode("👎")).queue();
            long id = sugerencias.crear(evento.getUser().getIdLong(),
                    post.getThreadChannel().getIdLong(), texto);
            evento.getHook().sendMessage(Messages.get(locale, "sugerencia.creada", id)).queue();
        }, err -> evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue());
    }
}
