package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
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
 * {@code /lock}: bloquea la escritura de {@code @everyone} en un canal (el actual si no se indica
 * otro). Solo altos cargos. Se registra en {@code bot-logs}.
 */
public final class LockComando implements Comando {

    private static final String NOMBRE = "lock";

    private final ConfigServidorService config;

    public LockComando(ConfigServidorService config) {
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData canal = new OptionData(OptionType.CHANNEL, "canal",
                Messages.get(Messages.ES, "comando.lock.opcion.canal"), false)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.lock.opcion.canal"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.lock.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.lock.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.lock.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOptions(canal);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        GuildChannel canal = evento.getOption("canal") != null
                ? evento.getOption("canal").getAsChannel() : evento.getGuildChannel();

        evento.deferReply(true).queue();
        ModHelper.aplicarBloqueo((IPermissionContainer) canal, true, "Lock por moderación");
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "lock.titulo"),
                Messages.get(locale, "lock.hecho", canal.getAsMention()));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
