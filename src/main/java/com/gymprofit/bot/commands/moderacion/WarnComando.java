package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Warn;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * {@code /warn} con subcomandos (poner, lista, quitar, limpiar). Consolida los avisos en un solo
 * comando para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>Solo altos cargos. {@code poner} registra el aviso cifrado y aplica el <b>escalado
 * automático</b> según los avisos activos ({@link ModeracionService}): timeout 1 h, timeout 24 h o
 * ban. Todo queda en el historial y se publica en {@code bot-logs}.
 */
public final class WarnComando implements Comando {

    private static final String NOMBRE = "warn";
    private static final String RAZON_ESCALADO = "Escalado automático por acumulación de avisos";
    /** Avisos por página (paginación por botones pendiente; de momento la página más reciente). */
    private static final int POR_PAGINA = 10;

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public WarnComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.warn.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.warn.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warn.familia"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addSubcommands(
                        sub("poner", "comando.warn.descripcion")
                                .addOptions(usuario("comando.warn.opcion.usuario"), motivo()),
                        sub("lista", "comando.warns.descripcion")
                                .addOptions(usuario("comando.warns.opcion.usuario")),
                        sub("quitar", "comando.unwarn.descripcion").addOptions(id()),
                        sub("limpiar", "comando.clearwarns.descripcion")
                                .addOptions(usuario("comando.clearwarns.opcion.usuario")));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData usuario(String claveDesc) {
        return new OptionData(OptionType.USER, "usuario", Messages.get(Messages.ES, claveDesc), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData motivo() {
        return new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.warn.opcion.motivo"), false)
                .setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warn.opcion.motivo"));
    }

    private static OptionData id() {
        return new OptionData(OptionType.INTEGER, "id",
                Messages.get(Messages.ES, "comando.unwarn.opcion.id"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unwarn.opcion.id"));
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
        String sub = evento.getSubcommandName() == null ? "lista" : evento.getSubcommandName();
        switch (sub) {
            case "poner" -> poner(evento, locale, actor);
            case "lista" -> lista(evento, locale);
            case "quitar" -> quitar(evento, locale);
            case "limpiar" -> limpiar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void poner(SlashCommandInteractionEvent evento, Locale locale, Member actor) {
        User objetivo = evento.getOption("usuario").getAsUser();
        Member objetivoMiembro = evento.getOption("usuario").getAsMember();
        if (!ModHelper.puedeModerar(actor, objetivoMiembro)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "mod.nopuedes"))).setEphemeral(true).queue();
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

    private void lista(SlashCommandInteractionEvent evento, Locale locale) {
        User objetivo = evento.getOption("usuario").getAsUser();

        evento.deferReply(true).queue();
        long usuarioId = objetivo.getIdLong();
        int activos = moderacion.contarWarnsActivos(usuarioId);
        int total = moderacion.contarWarnsTotales(usuarioId);
        List<Warn> avisos = moderacion.listarWarns(usuarioId, POR_PAGINA, 0);

        String desc = total == 0
                ? Messages.get(locale, "warns.vacio")
                : formatear(locale, avisos, activos, total);
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "warns.titulo", objetivo.getName()), desc).build()).queue();
    }

    private void quitar(SlashCommandInteractionEvent evento, Locale locale) {
        long id = evento.getOption("id").getAsLong();
        boolean revocado = moderacion.revocarWarn(id);
        String clave = revocado ? "unwarn.hecho" : "unwarn.noexiste";
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, clave, id))).setEphemeral(true).queue();
    }

    private void limpiar(SlashCommandInteractionEvent evento, Locale locale) {
        User objetivo = evento.getOption("usuario").getAsUser();

        evento.deferReply(true).queue();
        int revocados = moderacion.limpiarWarns(objetivo.getIdLong());
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "clearwarns.titulo"),
                Messages.get(locale, "clearwarns.hecho", revocados, objetivo.getAsMention())).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }

    private String formatear(Locale locale, List<Warn> avisos, int activos, int total) {
        StringBuilder sb = new StringBuilder(Messages.get(locale, "warns.resumen", activos, total))
                .append("\n\n");
        for (Warn w : avisos) {
            String estado = w.activo() ? "✅" : "❌";
            String motivo = w.motivo() != null ? w.motivo() : Messages.get(locale, "warns.sinmotivo");
            sb.append(Messages.get(locale, "warns.linea",
                    w.id(), estado, motivo, "<@" + w.moderadorId() + ">",
                    "<t:" + w.creadoEn().getEpochSecond() + ":R>")).append('\n');
        }
        return sb.toString();
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
