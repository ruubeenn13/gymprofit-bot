package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.services.ModeracionService.AccionEscalado;
import com.gymprofit.bot.services.ModeracionService.ResultadoAviso;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
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
import java.util.concurrent.TimeUnit;

/**
 * {@code /warn}: amonesta a un miembro (solo altos cargos). Registra el aviso cifrado y aplica el
 * <b>escalado automático</b> según los avisos activos ({@link ModeracionService}): timeout 1 h,
 * timeout 24 h o ban. Todo queda en el historial y se publica en {@code bot-logs}.
 */
public final class WarnComando implements Comando {

    private static final String NOMBRE = "warn";
    private static final String RAZON_ESCALADO = "Escalado automático por acumulación de avisos";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public WarnComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.warn.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warn.opcion.usuario"));
        OptionData motivo = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.warn.opcion.motivo"), false)
                .setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warn.opcion.motivo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.warn.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.warn.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warn.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
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
        User objetivo = evento.getOption("usuario").getAsUser();
        Member objetivoMiembro = evento.getOption("usuario").getAsMember();
        if (!ModHelper.puedeModerar(actor, objetivoMiembro)) {
            evento.reply(Messages.get(locale, "mod.nopuedes")).setEphemeral(true).queue();
            return;
        }
        String motivo = evento.getOption("motivo") != null
                ? evento.getOption("motivo").getAsString() : null;

        evento.deferReply(true).queue();
        Guild guild = evento.getGuild();
        ResultadoAviso resultado = moderacion.avisar(
                guild.getIdLong(), objetivo.getIdLong(), actor.getIdLong(), motivo);
        String escalado = aplicarEscalado(guild, objetivoMiembro, objetivo, actor.getIdLong(),
                resultado.accion(), locale);

        MessageEmbed embed = construirEmbed(locale, objetivo, resultado, motivo, escalado);
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(guild, config, embed);
    }

    /** Aplica el escalón (si lo hay) y lo registra en el historial. Devuelve el texto para el embed. */
    private String aplicarEscalado(Guild guild, Member objetivoMiembro, User objetivo,
                                   long moderadorId, AccionEscalado accion, Locale locale) {
        switch (accion) {
            case TIMEOUT_1H -> {
                aplicarTimeout(guild, objetivoMiembro, objetivo, moderadorId,
                        ModeracionService.TIMEOUT_1H_SEG);
                return Messages.get(locale, "warn.escalado.timeout1h");
            }
            case TIMEOUT_24H -> {
                aplicarTimeout(guild, objetivoMiembro, objetivo, moderadorId,
                        ModeracionService.TIMEOUT_24H_SEG);
                return Messages.get(locale, "warn.escalado.timeout24h");
            }
            case BAN -> {
                guild.ban(objetivo, 0, TimeUnit.SECONDS).reason(RAZON_ESCALADO).queue();
                moderacion.registrar(guild.getIdLong(), objetivo.getIdLong(), moderadorId,
                        "BAN", RAZON_ESCALADO, null, null);
                return Messages.get(locale, "warn.escalado.ban");
            }
            default -> {
                return null;
            }
        }
    }

    private void aplicarTimeout(Guild guild, Member objetivoMiembro, User objetivo,
                                long moderadorId, long segundos) {
        if (objetivoMiembro != null) {
            objetivoMiembro.timeoutFor(Duration.ofSeconds(segundos)).reason(RAZON_ESCALADO).queue();
        }
        moderacion.registrar(guild.getIdLong(), objetivo.getIdLong(), moderadorId,
                "TIMEOUT", RAZON_ESCALADO, null, segundos);
    }

    private MessageEmbed construirEmbed(Locale locale, User objetivo, ResultadoAviso resultado,
                                        String motivo, String escalado) {
        String desc = Messages.get(locale, "warn.hecho",
                objetivo.getAsMention(), resultado.warnsActivos());
        if (motivo != null) {
            desc += "\n" + Messages.get(locale, "warn.motivo", motivo);
        }
        if (escalado != null) {
            desc += "\n" + escalado;
        }
        return EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "warn.titulo"), desc).build();
    }
}
