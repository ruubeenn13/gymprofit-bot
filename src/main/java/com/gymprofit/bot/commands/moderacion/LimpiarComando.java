package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.LimpiezaService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * {@code /limpiar}: borra los últimos N mensajes del canal actual (solo staff con
 * «Gestionar mensajes»). Reutiliza {@link LimpiezaService}. Los mensajes de más de 14 días no se
 * pueden borrar en bloque (límite de Discord).
 */
public final class LimpiarComando implements Comando {

    private static final Logger log = LoggerFactory.getLogger(LimpiarComando.class);
    private static final String NOMBRE = "limpiar";

    private final LimpiezaService limpieza;

    public LimpiarComando(LimpiezaService limpieza) {
        this.limpieza = limpieza;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.limpiar.opcion.cantidad"), true)
                .setRequiredRange(LimpiezaService.MIN, LimpiezaService.MAX)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.limpiar.opcion.cantidad"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.limpiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.limpiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.limpiar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOptions(cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        int cantidad = evento.getOption("cantidad").getAsInt();

        evento.deferReply(true).queue();
        limpieza.purgarReciente(evento.getChannel(), cantidad).whenComplete((borrados, error) -> {
            if (error != null) {
                log.error("Error limpiando el canal {}", evento.getChannel().getId(), error);
                evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue();
                return;
            }
            var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                    Messages.get(locale, "limpiar.titulo"),
                    Messages.get(locale, "limpiar.resultado", borrados)).build();
            evento.getHook().sendMessageEmbeds(embed).queue();
        });
    }
}
