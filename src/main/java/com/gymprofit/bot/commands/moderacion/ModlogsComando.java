package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Sancion;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /modlogs}: historial completo de moderación de un miembro (avisos, mutes, timeouts, kicks,
 * bans, nicks), paginado con botones. Solo altos cargos. La construcción de cada página es estática
 * para reutilizarla desde el listener de paginación.
 */
public final class ModlogsComando implements Comando {

    private static final String NOMBRE = "modlogs";
    /** Prefijo de los customId de los botones de paginación: {@code modlogs:<userId>:<page>}. */
    public static final String PREFIJO_BOTON = "modlogs:";

    private final ModeracionService moderacion;

    public ModlogsComando(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.modlogs.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.modlogs.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.modlogs.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.modlogs.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.modlogs.descripcion"))
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

        long guildId = evento.getGuild().getIdLong();
        long usuarioId = objetivo.getIdLong();
        int total = moderacion.contarSanciones(guildId, usuarioId);
        MessageEmbed embed = construirEmbed(moderacion, locale, objetivo.getName(),
                guildId, usuarioId, 0, total);
        var mensaje = evento.getHook().sendMessageEmbeds(embed);
        List<ActionRow> filas = construirBotones(usuarioId, 0, total);
        if (!filas.isEmpty()) {
            mensaje = mensaje.setComponents(filas);
        }
        mensaje.queue();
    }

    /** Nº total de páginas para {@code total} sanciones. */
    public static int totalPaginas(int total) {
        return Math.max(1, (int) Math.ceil(total / (double) ModHelper.SANCIONES_POR_PAGINA));
    }

    /** Construye el embed de una página del historial (reutilizado por el listener de botones). */
    public static MessageEmbed construirEmbed(ModeracionService moderacion, Locale locale,
                                              String nombre, long guildId, long usuarioId,
                                              int pagina, int total) {
        String titulo = Messages.get(locale, "modlogs.titulo", nombre);
        if (total == 0) {
            return ModHelper.embed(locale, titulo, Messages.get(locale, "modlogs.vacio"));
        }
        List<Sancion> sanciones = moderacion.modlogs(guildId, usuarioId,
                ModHelper.SANCIONES_POR_PAGINA, pagina * ModHelper.SANCIONES_POR_PAGINA);
        String desc = Messages.get(locale, "modlogs.resumen",
                        total, pagina + 1, totalPaginas(total))
                + "\n\n" + ModHelper.formatearSanciones(locale, sanciones);
        return ModHelper.embed(locale, titulo, desc);
    }

    /** Fila de botones ◀ ▶ para paginar, o vacía si cabe todo en una página. */
    public static List<ActionRow> construirBotones(long usuarioId, int pagina, int total) {
        int paginas = totalPaginas(total);
        if (total <= ModHelper.SANCIONES_POR_PAGINA) {
            return List.of();
        }
        Button anterior = Button.secondary(PREFIJO_BOTON + usuarioId + ":" + (pagina - 1), "◀")
                .withDisabled(pagina == 0);
        Button siguiente = Button.secondary(PREFIJO_BOTON + usuarioId + ":" + (pagina + 1), "▶")
                .withDisabled(pagina >= paginas - 1);
        return List.of(ActionRow.of(anterior, siguiente));
    }
}
