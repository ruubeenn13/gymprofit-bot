package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /kick}: expulsa a un miembro del servidor (puede volver con invitación). Solo altos cargos. */
public final class KickComando implements Comando {

    private static final String NOMBRE = "kick";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public KickComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.kick.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.kick.opcion.usuario"));
        OptionData motivo = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.kick.opcion.motivo"), false).setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.kick.opcion.motivo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.kick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.kick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.kick.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
                .addOptions(usuario, motivo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member actor = evento.getMember();
        if (!ModHelper.esAltoCargo(actor)) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        Member objetivo = evento.getOption("usuario").getAsMember();
        User objetivoUser = evento.getOption("usuario").getAsUser();
        if (objetivo == null) {
            evento.reply(Messages.get(locale, "mod.noenservidor")).setEphemeral(true).queue();
            return;
        }
        if (!ModHelper.puedeModerar(actor, objetivo)) {
            evento.reply(Messages.get(locale, "mod.nopuedes")).setEphemeral(true).queue();
            return;
        }
        String motivo = evento.getOption("motivo") != null
                ? evento.getOption("motivo").getAsString() : null;

        evento.deferReply(true).queue();
        objetivo.kick().reason(motivo == null ? "Kick" : motivo).queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "KICK", motivo, null, null);

        String desc = Messages.get(locale, "kick.hecho", objetivoUser.getAsMention());
        if (motivo != null) {
            desc += "\n" + Messages.get(locale, "warn.motivo", motivo);
        }
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "kick.titulo"), desc);
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
