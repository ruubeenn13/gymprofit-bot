package com.gymprofit.bot.commands;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Listener central de slash commands: registra las definiciones en cada servidor al arrancar y
 * enruta cada interacción a su {@link Comando}.
 *
 * <p>Los comandos se registran <b>por servidor</b> ({@code guild.updateCommands}) porque se
 * propagan al instante (los globales tardan hasta 1&nbsp;h), lo que encaja con el ciclo de la
 * Fase 1 sobre el servidor de pruebas.</p>
 */
public final class RouterComandos extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RouterComandos.class);

    private final Map<String, Comando> comandosPorNombre;
    private final List<SlashCommandData> definiciones;

    /**
     * @param comandos comandos a servir; se indexan por su nombre de definición
     */
    public RouterComandos(List<Comando> comandos) {
        Map<String, Comando> mapa = new java.util.HashMap<>();
        List<SlashCommandData> defs = new ArrayList<>();
        for (Comando comando : comandos) {
            SlashCommandData definicion = comando.definicion();
            mapa.put(definicion.getName(), comando);
            defs.add(definicion);
        }
        this.comandosPorNombre = Map.copyOf(mapa);
        this.definiciones = List.copyOf(defs);
    }

    /** Registra (sobrescribe) el set de comandos en cada servidor en cuanto está listo. */
    @Override
    public void onGuildReady(GuildReadyEvent evento) {
        evento.getGuild().updateCommands().addCommands(definiciones).queue(
                ok -> log.info("Registrados {} comandos en el servidor {}",
                        definiciones.size(), evento.getGuild().getName()),
                error -> log.error("Error registrando comandos en el servidor {}",
                        evento.getGuild().getName(), error));
    }

    /** Enruta la interacción al comando correspondiente y aísla sus errores. */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        Comando comando = comandosPorNombre.get(evento.getName());
        if (comando == null) {
            log.warn("Slash command desconocido recibido: /{}", evento.getName());
            return;
        }
        try {
            comando.ejecutar(evento);
        } catch (RuntimeException e) {
            log.error("Error ejecutando /{}", evento.getName(), e);
            Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
            String mensaje = Messages.get(locale, "comando.error.generico");
            var embed = EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, mensaje);
            if (evento.isAcknowledged()) {
                evento.getHook().sendMessageEmbeds(embed).setEphemeral(true).queue();
            } else {
                evento.replyEmbeds(embed).setEphemeral(true).queue();
            }
        }
    }
}
