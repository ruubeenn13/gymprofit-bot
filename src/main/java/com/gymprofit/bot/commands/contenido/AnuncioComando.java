package com.gymprofit.bot.commands.contenido;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.EnumSet;
import java.util.Locale;

/**
 * {@code /anuncio}: publica un anuncio con marca en {@code 📣・anuncios} y, opcionalmente, menciona al
 * rol {@code 📣 Avisos}. Solo altos cargos.
 */
public final class AnuncioComando implements Comando {

    private static final String NOMBRE = "anuncio";
    private static final String CANAL = "📣・anuncios";
    private static final String ROL_AVISOS = "📣 Avisos";

    @Override
    public SlashCommandData definicion() {
        OptionData titulo = new OptionData(OptionType.STRING, "titulo",
                Messages.get(Messages.ES, "comando.anuncio.opcion.titulo"), true).setMaxLength(256)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.anuncio.opcion.titulo"));
        OptionData mensaje = new OptionData(OptionType.STRING, "mensaje",
                Messages.get(Messages.ES, "comando.anuncio.opcion.mensaje"), true).setMaxLength(4000)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.anuncio.opcion.mensaje"));
        OptionData imagen = new OptionData(OptionType.STRING, "imagen",
                Messages.get(Messages.ES, "comando.anuncio.opcion.imagen"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.anuncio.opcion.imagen"));
        OptionData ping = new OptionData(OptionType.BOOLEAN, "ping",
                Messages.get(Messages.ES, "comando.anuncio.opcion.ping"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.anuncio.opcion.ping"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.anuncio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.anuncio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.anuncio.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(titulo, mensaje, imagen, ping);
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        TextChannel canal = evento.getGuild().getTextChannelsByName(CANAL, false)
                .stream().findFirst().orElse(null);
        if (canal == null) {
            evento.reply(Messages.get(locale, "contenido.sincanal", CANAL))
                    .setEphemeral(true).queue();
            return;
        }
        String titulo = evento.getOption("titulo").getAsString();
        String mensaje = evento.getOption("mensaje").getAsString();
        boolean ping = evento.getOption("ping") != null && evento.getOption("ping").getAsBoolean();

        var builder = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale, titulo, mensaje);
        if (evento.getOption("imagen") != null) {
            builder.setImage(evento.getOption("imagen").getAsString());
        }
        MessageEmbed embed = builder.build();

        evento.deferReply(true).queue();
        var accion = canal.sendMessageEmbeds(embed);
        Role avisos = evento.getGuild().getRolesByName(ROL_AVISOS, false).stream().findFirst().orElse(null);
        if (ping && avisos != null) {
            accion = accion.setContent(avisos.getAsMention())
                    .setAllowedMentions(EnumSet.of(MentionType.ROLE));
        }
        accion.queue(
                ok -> evento.getHook().sendMessage(Messages.get(locale, "anuncio.publicado")).queue(),
                err -> evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue());
    }
}
