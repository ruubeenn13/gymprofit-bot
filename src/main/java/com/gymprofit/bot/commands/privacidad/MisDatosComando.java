package com.gymprofit.bot.commands.privacidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PrivacidadService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * {@code /mis-datos}: entrega al usuario (efímero) un JSON con todo lo que el bot guarda de él
 * (derecho de acceso y portabilidad, RGPD). Los motivos cifrados se descifran solo para él.
 */
public final class MisDatosComando implements Comando {

    private static final String NOMBRE = "mis-datos";

    private final PrivacidadService privacidad;

    public MisDatosComando(PrivacidadService privacidad) {
        this.privacidad = privacidad;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.misdatos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.misdatos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.misdatos.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        DataObject datos = privacidad.exportar(evento.getUser().getIdLong());
        byte[] json = datos.toString().getBytes(StandardCharsets.UTF_8);
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "misdatos.texto")))
                .addFiles(FileUpload.fromData(json, "mis-datos.json"))
                .queue();
    }
}
