package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /slowmode}: fija el modo lento (segundos entre mensajes) de un canal de texto (el actual si
 * no se indica otro). 0 lo desactiva. Solo altos cargos. Se registra en {@code bot-logs}.
 */
public final class SlowmodeComando implements Comando {

    private static final String NOMBRE = "slowmode";

    private final ConfigServidorService config;

    public SlowmodeComando(ConfigServidorService config) {
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData segundos = new OptionData(OptionType.INTEGER, "segundos",
                Messages.get(Messages.ES, "comando.slowmode.opcion.segundos"), true)
                .setRequiredRange(0, TextChannel.MAX_SLOWMODE)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.slowmode.opcion.segundos"));
        OptionData canal = new OptionData(OptionType.CHANNEL, "canal",
                Messages.get(Messages.ES, "comando.slowmode.opcion.canal"), false)
                .setChannelTypes(ChannelType.TEXT)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.slowmode.opcion.canal"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.slowmode.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.slowmode.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.slowmode.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOptions(segundos, canal);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        int segundos = evento.getOption("segundos").getAsInt();
        GuildChannel base = evento.getOption("canal") != null
                ? evento.getOption("canal").getAsChannel() : evento.getGuildChannel();
        if (!(base instanceof TextChannel canal)) {
            evento.reply(Messages.get(locale, "slowmode.solotexto")).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(true).queue();
        canal.getManager().setSlowmode(segundos).reason("Slowmode por moderación").queue();
        String desc = segundos == 0
                ? Messages.get(locale, "slowmode.desactivado", canal.getAsMention())
                : Messages.get(locale, "slowmode.hecho", canal.getAsMention(),
                        Duraciones.formatear(segundos));
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "slowmode.titulo"), desc);
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
