package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
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
 * {@code /unlock}: reabre la escritura de {@code @everyone} en un canal previamente bloqueado (el
 * actual si no se indica otro). Solo altos cargos. Se registra en {@code bot-logs}.
 */
public final class UnlockComando implements Comando {

    private static final String NOMBRE = "unlock";

    private final ConfigServidorService config;

    public UnlockComando(ConfigServidorService config) {
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData canal = new OptionData(OptionType.CHANNEL, "canal",
                Messages.get(Messages.ES, "comando.unlock.opcion.canal"), false)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unlock.opcion.canal"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.unlock.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.unlock.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unlock.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOptions(canal);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        GuildChannel canal = evento.getOption("canal") != null
                ? evento.getOption("canal").getAsChannel() : evento.getGuildChannel();

        evento.deferReply(true).queue();
        ModHelper.aplicarBloqueo((IPermissionContainer) canal, false, "Unlock por moderación");
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "unlock.titulo"),
                Messages.get(locale, "unlock.hecho", canal.getAsMention()));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
