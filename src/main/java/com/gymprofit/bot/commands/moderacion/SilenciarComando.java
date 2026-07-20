package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * {@code /silenciar} con subcomandos (mute, unmute, timeout, untimeout). Consolida las dos formas de
 * callar a un miembro en un solo comando para no gastar cupo de slash commands (límite de 100 de
 * Discord).
 *
 * <p>Solo altos cargos. {@code mute} es indefinido y usa el rol {@code 🔇 Silenciado} (que
 * {@code /setup} deja sin permiso de escritura en todos los canales); {@code timeout} es el aislamiento
 * <b>nativo</b> de Discord con duración estilo {@code 30m}/{@code 2h}/{@code 1d} (máx. 28 días). Todo
 * queda en el historial y se publica en {@code bot-logs}.
 */
public final class SilenciarComando implements Comando {

    private static final String NOMBRE = "silenciar";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public SilenciarComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.silenciar.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.silenciar.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.silenciar.familia"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addSubcommands(
                        sub("mute", "comando.mute.descripcion")
                                .addOptions(usuario("comando.mute.opcion.usuario"),
                                        motivo("comando.mute.opcion.motivo")),
                        sub("unmute", "comando.unmute.descripcion")
                                .addOptions(usuario("comando.unmute.opcion.usuario")),
                        sub("timeout", "comando.timeout.descripcion")
                                .addOptions(usuario("comando.timeout.opcion.usuario"), duracion(),
                                        motivo("comando.timeout.opcion.motivo")),
                        sub("untimeout", "comando.untimeout.descripcion")
                                .addOptions(usuario("comando.untimeout.opcion.usuario")));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData usuario(String claveDesc) {
        return new OptionData(OptionType.USER, "usuario", Messages.get(Messages.ES, claveDesc), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData motivo(String claveDesc) {
        return new OptionData(OptionType.STRING, "motivo", Messages.get(Messages.ES, claveDesc), false)
                .setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData duracion() {
        return new OptionData(OptionType.STRING, "duracion",
                Messages.get(Messages.ES, "comando.timeout.opcion.duracion"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.timeout.opcion.duracion"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member actor = evento.getMember();
        if (!ModHelper.esAltoCargo(actor)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        Member objetivo = evento.getOption("usuario").getAsMember();
        User objetivoUser = evento.getOption("usuario").getAsUser();
        if (objetivo == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.noenservidor"))).setEphemeral(true).queue();
            return;
        }
        String sub = evento.getSubcommandName() == null ? "mute" : evento.getSubcommandName();
        switch (sub) {
            case "mute" -> mute(evento, locale, actor, objetivo, objetivoUser);
            case "unmute" -> unmute(evento, locale, actor, objetivo, objetivoUser);
            case "timeout" -> timeout(evento, locale, actor, objetivo, objetivoUser);
            case "untimeout" -> untimeout(evento, locale, actor, objetivo, objetivoUser);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void mute(SlashCommandInteractionEvent evento, Locale locale, Member actor,
                      Member objetivo, User objetivoUser) {
        if (!ModHelper.puedeModerar(actor, objetivo)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.nopuedes"))).setEphemeral(true).queue();
            return;
        }
        Role silenciado = ModHelper.rolSilenciado(evento.getGuild());
        if (silenciado == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mute.sinrol"))).setEphemeral(true).queue();
            return;
        }
        String motivo = motivoDe(evento);

        evento.deferReply(true).queue();
        evento.getGuild().addRoleToMember(objetivo, silenciado)
                .reason(motivo == null ? "Mute" : motivo).queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "MUTE", motivo, null, null);

        String desc = Messages.get(locale, "mute.hecho", objetivoUser.getAsMention());
        if (motivo != null) {
            desc += "\n" + Messages.get(locale, "warn.motivo", motivo);
        }
        responder(evento, ModHelper.embed(locale, Messages.get(locale, "mute.titulo"), desc));
    }

    private void unmute(SlashCommandInteractionEvent evento, Locale locale, Member actor,
                        Member objetivo, User objetivoUser) {
        Role silenciado = ModHelper.rolSilenciado(evento.getGuild());
        if (silenciado == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mute.sinrol"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(true).queue();
        evento.getGuild().removeRoleFromMember(objetivo, silenciado).reason("Unmute").queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "UNMUTE", null, null, null);

        responder(evento, ModHelper.embed(locale, Messages.get(locale, "unmute.titulo"),
                Messages.get(locale, "unmute.hecho", objetivoUser.getAsMention())));
    }

    private void timeout(SlashCommandInteractionEvent evento, Locale locale, Member actor,
                         Member objetivo, User objetivoUser) {
        if (!ModHelper.puedeModerar(actor, objetivo)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.nopuedes"))).setEphemeral(true).queue();
            return;
        }
        OptionalLong segundos = Duraciones.parsear(evento.getOption("duracion").getAsString());
        if (segundos.isEmpty() || segundos.getAsLong() > Duraciones.MAX_TIMEOUT_SEG) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "timeout.duracioninvalida"))).setEphemeral(true).queue();
            return;
        }
        String motivo = motivoDe(evento);

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
        responder(evento, ModHelper.embed(locale, Messages.get(locale, "timeout.titulo"), desc));
    }

    private void untimeout(SlashCommandInteractionEvent evento, Locale locale, Member actor,
                           Member objetivo, User objetivoUser) {
        evento.deferReply(true).queue();
        objetivo.removeTimeout().reason("Untimeout").queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "UNTIMEOUT", null, null, null);

        responder(evento, ModHelper.embed(locale, Messages.get(locale, "untimeout.titulo"),
                Messages.get(locale, "untimeout.hecho", objetivoUser.getAsMention())));
    }

    private static String motivoDe(SlashCommandInteractionEvent evento) {
        return evento.getOption("motivo") != null ? evento.getOption("motivo").getAsString() : null;
    }

    /** Publica el resultado al moderador y lo deja registrado en {@code bot-logs}. */
    private void responder(SlashCommandInteractionEvent evento, MessageEmbed embed) {
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
