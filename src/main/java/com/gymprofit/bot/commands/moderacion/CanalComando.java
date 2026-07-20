package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /canal} con subcomandos (lock, unlock, lockdown, unlockdown, slowmode). Consolida el control
 * de canales en un solo comando para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>Solo altos cargos. {@code lock}/{@code unlock} actúan sobre un canal (el actual si no se indica
 * otro); {@code lockdown}/{@code unlockdown} sobre <b>todos</b> los canales de texto (anti-raid);
 * {@code slowmode} fija los segundos entre mensajes (0 lo desactiva). Todo se registra en
 * {@code bot-logs}.
 */
public final class CanalComando implements Comando {

    private static final String NOMBRE = "canal";

    private final ConfigServidorService config;

    public CanalComando(ConfigServidorService config) {
        this.config = config;
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.canal.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.canal.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.canal.familia"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addSubcommands(
                        sub("lock", "comando.lock.descripcion")
                                .addOptions(canal("comando.lock.opcion.canal", ChannelType.TEXT, ChannelType.NEWS)),
                        sub("unlock", "comando.unlock.descripcion")
                                .addOptions(canal("comando.unlock.opcion.canal", ChannelType.TEXT, ChannelType.NEWS)),
                        sub("lockdown", "comando.lockdown.descripcion"),
                        sub("unlockdown", "comando.unlockdown.descripcion"),
                        sub("slowmode", "comando.slowmode.descripcion")
                                .addOptions(segundos(), canal("comando.slowmode.opcion.canal", ChannelType.TEXT)));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData canal(String claveDesc, ChannelType... tipos) {
        return new OptionData(OptionType.CHANNEL, "canal", Messages.get(Messages.ES, claveDesc), false)
                .setChannelTypes(tipos)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData segundos() {
        return new OptionData(OptionType.INTEGER, "segundos",
                Messages.get(Messages.ES, "comando.slowmode.opcion.segundos"), true)
                .setRequiredRange(0, TextChannel.MAX_SLOWMODE)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.slowmode.opcion.segundos"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        String sub = evento.getSubcommandName() == null ? "lock" : evento.getSubcommandName();
        switch (sub) {
            case "lock" -> bloquear(evento, locale, true);
            case "unlock" -> bloquear(evento, locale, false);
            case "lockdown" -> bloqueoTotal(evento, locale, true);
            case "unlockdown" -> bloqueoTotal(evento, locale, false);
            case "slowmode" -> slowmode(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /** Bloquea o desbloquea la escritura de {@code @everyone} en un canal concreto. */
    private void bloquear(SlashCommandInteractionEvent evento, Locale locale, boolean bloquear) {
        GuildChannel canal = evento.getOption("canal") != null
                ? evento.getOption("canal").getAsChannel() : evento.getGuildChannel();

        evento.deferReply(true).queue();
        String razon = bloquear ? "Lock por moderación" : "Unlock por moderación";
        ModHelper.aplicarBloqueo((IPermissionContainer) canal, bloquear, razon);
        String prefijo = bloquear ? "lock" : "unlock";
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, prefijo + ".titulo"),
                Messages.get(locale, prefijo + ".hecho", canal.getAsMention()));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }

    /** Bloquea o desbloquea todos los canales de texto del servidor (anti-raid). */
    private void bloqueoTotal(SlashCommandInteractionEvent evento, Locale locale, boolean bloquear) {
        evento.deferReply(true).queue();
        List<TextChannel> canales = evento.getGuild().getTextChannels();
        String razon = bloquear ? "Lockdown (anti-raid)" : "Unlockdown";
        for (TextChannel canal : canales) {
            ModHelper.aplicarBloqueo(canal, bloquear, razon);
        }
        String prefijo = bloquear ? "lockdown" : "unlockdown";
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, prefijo + ".titulo"),
                Messages.get(locale, prefijo + ".hecho", canales.size()));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }

    private void slowmode(SlashCommandInteractionEvent evento, Locale locale) {
        int segundos = evento.getOption("segundos").getAsInt();
        GuildChannel base = evento.getOption("canal") != null
                ? evento.getOption("canal").getAsChannel() : evento.getGuildChannel();
        if (!(base instanceof TextChannel canal)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "slowmode.solotexto"))).setEphemeral(true).queue();
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
