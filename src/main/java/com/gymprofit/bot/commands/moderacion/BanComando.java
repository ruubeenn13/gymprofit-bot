package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
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
import java.util.concurrent.TimeUnit;

/**
 * {@code /ban}: banea a un miembro (no puede volver). Opcionalmente borra sus mensajes de los últimos
 * N días (0-7). Solo altos cargos.
 */
public final class BanComando implements Comando {

    private static final String NOMBRE = "ban";
    private static final int MAX_BORRAR_DIAS = 7;

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public BanComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.ban.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ban.opcion.usuario"));
        OptionData motivo = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.ban.opcion.motivo"), false).setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ban.opcion.motivo"));
        OptionData borrar = new OptionData(OptionType.INTEGER, "borrar_dias",
                Messages.get(Messages.ES, "comando.ban.opcion.borrar"), false)
                .setRequiredRange(0, MAX_BORRAR_DIAS)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ban.opcion.borrar"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ban.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ban.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ban.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOptions(usuario, motivo, borrar);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member actor = evento.getMember();
        if (!ModHelper.esAltoCargo(actor)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        User objetivo = evento.getOption("usuario").getAsUser();
        Member objetivoMiembro = evento.getOption("usuario").getAsMember();
        if (!ModHelper.puedeModerar(actor, objetivoMiembro)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.nopuedes"))).setEphemeral(true).queue();
            return;
        }
        String motivo = evento.getOption("motivo") != null
                ? evento.getOption("motivo").getAsString() : null;
        int borrarDias = evento.getOption("borrar_dias") != null
                ? evento.getOption("borrar_dias").getAsInt() : 0;

        evento.deferReply(true).queue();
        evento.getGuild().ban(objetivo, borrarDias, TimeUnit.DAYS)
                .reason(motivo == null ? "Ban" : motivo).queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivo.getIdLong(),
                actor.getIdLong(), "BAN", motivo, null, null);

        String desc = Messages.get(locale, "ban.hecho", objetivo.getAsMention());
        if (motivo != null) {
            desc += "\n" + Messages.get(locale, "warn.motivo", motivo);
        }
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "ban.titulo"), desc);
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
