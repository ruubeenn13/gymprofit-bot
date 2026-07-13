package com.gymprofit.bot.commands.comunidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.SugerenciaService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
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
 * {@code /sugerencia-resolver}: el staff acepta o rechaza una sugerencia por su id; actualiza el
 * estado en BD y la etiqueta del post del foro (Aprobada/Rechazada). Solo altos cargos.
 */
public final class SugerenciaResolverComando implements Comando {

    private static final String NOMBRE = "sugerencia-resolver";

    private final SugerenciaService sugerencias;

    public SugerenciaResolverComando(SugerenciaService sugerencias) {
        this.sugerencias = sugerencias;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData id = new OptionData(OptionType.INTEGER, "id",
                Messages.get(Messages.ES, "comando.sugres.opcion.id"), true).setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sugres.opcion.id"));
        OptionData estado = new OptionData(OptionType.STRING, "estado",
                Messages.get(Messages.ES, "comando.sugres.opcion.estado"), true)
                .addChoice("aceptada", "ACEPTADA")
                .addChoice("rechazada", "RECHAZADA")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sugres.opcion.estado"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.sugres.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.sugres.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sugres.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(id, estado);
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        long id = evento.getOption("id").getAsLong();
        String estado = evento.getOption("estado").getAsString(); // ACEPTADA | RECHAZADA

        var sugerencia = sugerencias.buscar(id);
        if (sugerencia.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "sugres.noexiste", id))).setEphemeral(true).queue();
            return;
        }
        boolean resuelta = sugerencias.resolver(id, estado, evento.getUser().getIdLong());
        if (!resuelta) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "sugres.yaresuelta", id))).setEphemeral(true).queue();
            return;
        }
        aplicarEtiqueta(evento, sugerencia.get().mensajeId(), estado);

        String clave = "ACEPTADA".equals(estado) ? "sugres.aceptada" : "sugres.rechazada";
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, clave, id))).setEphemeral(true).queue();
    }

    /** Aplica la etiqueta del foro (Aprobada/Rechazada) al post, si el hilo sigue accesible. */
    private void aplicarEtiqueta(SlashCommandInteractionEvent evento, long threadId, String estado) {
        ThreadChannel hilo = evento.getGuild().getThreadChannelById(threadId);
        if (hilo == null || !(hilo.getParentChannel() instanceof ForumChannel foro)) {
            return; // hilo archivado o inaccesible: el estado ya quedó en BD
        }
        String nombreEtiqueta = "ACEPTADA".equals(estado) ? "Aprobada" : "Rechazada";
        ForumTag etiqueta = foro.getAvailableTagsByName(nombreEtiqueta, false)
                .stream().findFirst().orElse(null);
        if (etiqueta != null) {
            hilo.getManager().setAppliedTags(List.of(etiqueta)).queue(null, error -> { });
        }
    }
}
