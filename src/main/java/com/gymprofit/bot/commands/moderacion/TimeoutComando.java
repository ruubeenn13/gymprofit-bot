package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.util.Duraciones;
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

import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * {@code /timeout}: aísla temporalmente a un miembro con el timeout nativo de Discord (no puede
 * escribir ni hablar). Duración estilo {@code 30m}/{@code 2h}/{@code 1d} (máx. 28 días). Solo altos
 * cargos.
 */
public final class TimeoutComando implements Comando {

    private static final String NOMBRE = "timeout";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public TimeoutComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.timeout.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.timeout.opcion.usuario"));
        OptionData duracion = new OptionData(OptionType.STRING, "duracion",
                Messages.get(Messages.ES, "comando.timeout.opcion.duracion"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.timeout.opcion.duracion"));
        OptionData motivo = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.timeout.opcion.motivo"), false).setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.timeout.opcion.motivo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.timeout.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.timeout.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.timeout.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOptions(usuario, duracion, motivo);
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
        OptionalLong segundos = Duraciones.parsear(evento.getOption("duracion").getAsString());
        if (segundos.isEmpty() || segundos.getAsLong() > Duraciones.MAX_TIMEOUT_SEG) {
            evento.reply(Messages.get(locale, "timeout.duracioninvalida")).setEphemeral(true).queue();
            return;
        }
        String motivo = evento.getOption("motivo") != null
                ? evento.getOption("motivo").getAsString() : null;

        evento.deferReply(true).queue();
        long seg = segundos.getAsLong();
        objetivo.timeoutFor(Duration.ofSeconds(seg)).reason(motivo == null ? "Timeout" : motivo).queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "TIMEOUT", motivo, null, seg);

        String desc = Messages.get(locale, "timeout.hecho",
                objetivoUser.getAsMention(), Duraciones.formatear(seg));
        if (motivo != null) {
            desc += "\n" + Messages.get(locale, "warn.motivo", motivo);
        }
        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "timeout.titulo"), desc);
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
