package com.gymprofit.bot.commands.contenido;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.SorteoService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * {@code /sorteo}: crea un sorteo en {@code 🎁・sorteos}. Publica un embed con reacción 🎉 (participar),
 * menciona al rol {@code 🎁 Sorteos} y lo guarda; el {@code SorteoJob} elige ganadores al vencer.
 * Solo altos cargos.
 */
public final class SorteoComando implements Comando {

    /** Emoji de participación del sorteo (compartido con el job). */
    public static final String EMOJI = "🎉";
    private static final String NOMBRE = "sorteo";
    private static final String CANAL = "🎁・sorteos";
    private static final String ROL = "🎁 Sorteos";
    private static final int MAX_GANADORES = 20;

    private final SorteoService sorteos;

    public SorteoComando(SorteoService sorteos) {
        this.sorteos = sorteos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData premio = new OptionData(OptionType.STRING, "premio",
                Messages.get(Messages.ES, "comando.sorteo.opcion.premio"), true)
                .setMaxLength(SorteoService.MAX_PREMIO)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sorteo.opcion.premio"));
        OptionData duracion = new OptionData(OptionType.STRING, "duracion",
                Messages.get(Messages.ES, "comando.sorteo.opcion.duracion"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sorteo.opcion.duracion"));
        OptionData ganadores = new OptionData(OptionType.INTEGER, "ganadores",
                Messages.get(Messages.ES, "comando.sorteo.opcion.ganadores"), false)
                .setRequiredRange(1, MAX_GANADORES)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sorteo.opcion.ganadores"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.sorteo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.sorteo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sorteo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(premio, duracion, ganadores);
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
        OptionalLong segundos = Duraciones.parsear(evento.getOption("duracion").getAsString());
        if (segundos.isEmpty()) {
            evento.reply(Messages.get(locale, "timeout.duracioninvalida")).setEphemeral(true).queue();
            return;
        }
        TextChannel canal = evento.getGuild().getTextChannelsByName(CANAL, false)
                .stream().findFirst().orElse(null);
        if (canal == null) {
            evento.reply(Messages.get(locale, "contenido.sincanal", CANAL)).setEphemeral(true).queue();
            return;
        }
        String premio = evento.getOption("premio").getAsString();
        int ganadores = evento.getOption("ganadores") != null
                ? evento.getOption("ganadores").getAsInt() : 1;
        long finEpoch = Instant.now().getEpochSecond() + segundos.getAsLong();

        evento.deferReply(true).queue();
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.RETO, locale,
                Messages.get(locale, "sorteo.titulo"),
                Messages.get(locale, "sorteo.cuerpo", premio, ganadores,
                        "<t:" + finEpoch + ":R>", EMOJI)).build();

        Role rol = evento.getGuild().getRolesByName(ROL, false).stream().findFirst().orElse(null);
        var accion = canal.sendMessageEmbeds(embed);
        if (rol != null) {
            accion = accion.setContent(rol.getAsMention()).setAllowedMentions(EnumSet.of(MentionType.ROLE));
        }
        long finalFin = finEpoch;
        accion.queue(msg -> {
            msg.addReaction(Emoji.fromUnicode(EMOJI)).queue();
            sorteos.crear(evento.getGuild().getIdLong(), canal.getIdLong(), msg.getIdLong(),
                    premio, ganadores, evento.getUser().getIdLong(), finalFin);
            evento.getHook().sendMessage(Messages.get(locale, "sorteo.creado")).queue();
        }, err -> evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue());
    }
}
