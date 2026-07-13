package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Warn;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /warns}: lista los avisos de un miembro (solo altos cargos). Muestra la página más reciente
 * (hasta {@value #POR_PAGINA}) con el total; los motivos se descifran al leerlos.
 */
public final class WarnsComando implements Comando {

    private static final String NOMBRE = "warns";
    /** Avisos por página (paginación por botones pendiente; de momento la página más reciente). */
    private static final int POR_PAGINA = 10;

    private final ModeracionService moderacion;

    public WarnsComando(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.warns.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warns.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.warns.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.warns.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.warns.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        User objetivo = evento.getOption("usuario").getAsUser();

        evento.deferReply(true).queue();
        long usuarioId = objetivo.getIdLong();
        int activos = moderacion.contarWarnsActivos(usuarioId);
        int total = moderacion.contarWarnsTotales(usuarioId);
        List<Warn> avisos = moderacion.listarWarns(usuarioId, POR_PAGINA, 0);

        String desc = total == 0
                ? Messages.get(locale, "warns.vacio")
                : formatear(locale, avisos, activos, total);
        var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "warns.titulo", objetivo.getName()), desc).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
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
}
