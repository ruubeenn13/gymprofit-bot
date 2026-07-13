package com.gymprofit.bot.commands.privacidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /borrar-mis-datos}: pide confirmación (botón) y, al confirmarla, borra todos los datos del
 * usuario en {@code gymprofit_bot} (derecho al olvido, RGPD). El borrado real lo hace
 * {@code BorrarDatosListener} al pulsar el botón.
 */
public final class BorrarMisDatosComando implements Comando {

    private static final String NOMBRE = "borrar-mis-datos";
    /** customId del botón de confirmación (lo maneja BorrarDatosListener). */
    public static final String BOTON_CONFIRMAR = "privacidad:borrar:confirmar";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.borrarmisdatos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.borrarmisdatos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.borrarmisdatos.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "borrar.aviso.titulo"),
                Messages.get(locale, "borrar.aviso.texto")).build();
        evento.replyEmbeds(embed)
                .addActionRow(Button.danger(BOTON_CONFIRMAR, Messages.get(locale, "borrar.boton")))
                .setEphemeral(true)
                .queue();
    }
}
