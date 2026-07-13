package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /unban}: levanta el baneo de un usuario por su id (solo altos cargos). */
public final class UnbanComando implements Comando {

    private static final String NOMBRE = "unban";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public UnbanComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData id = new OptionData(OptionType.STRING, "usuario_id",
                Messages.get(Messages.ES, "comando.unban.opcion.id"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unban.opcion.id"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.unban.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.unban.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unban.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOptions(id);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member actor = evento.getMember();
        if (!ModHelper.esAltoCargo(actor)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        long usuarioId;
        try {
            usuarioId = Long.parseUnsignedLong(evento.getOption("usuario_id").getAsString().trim());
        } catch (NumberFormatException e) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "unban.idinvalido"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(true).queue();
        evento.getGuild().unban(UserSnowflake.fromId(usuarioId)).reason("Unban").queue(
                ok -> {
                    moderacion.registrar(evento.getGuild().getIdLong(), usuarioId,
                            actor.getIdLong(), "UNBAN", null, null, null);
                    MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "unban.titulo"),
                            Messages.get(locale, "unban.hecho", "<@" + usuarioId + ">"));
                    evento.getHook().sendMessageEmbeds(embed).queue();
                    ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
                },
                error -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "unban.noestaba"))).queue());
    }
}
