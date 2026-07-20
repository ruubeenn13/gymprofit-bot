package com.gymprofit.bot.commands.privacidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PrivacidadService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * {@code /privacidad} con subcomandos (info, exportar, borrar). Consolida los derechos RGPD en un solo
 * comando para no gastar cupo de slash commands (límite de 100 de Discord). Disponible para cualquier
 * usuario y siempre en efímero: son datos personales.
 *
 * <p>{@code info} explica qué guarda el bot, para qué y cuánto tiempo; {@code exportar} entrega un JSON
 * con todo lo suyo (acceso y portabilidad), descifrando los motivos solo para él; {@code borrar} pide
 * confirmación por botón y el borrado real lo hace {@code BorrarDatosListener} (derecho al olvido).
 */
public final class PrivacidadComando implements Comando {

    private static final String NOMBRE = "privacidad";
    /** customId del botón de confirmación de borrado (lo maneja BorrarDatosListener). */
    public static final String BOTON_CONFIRMAR = "privacidad:borrar:confirmar";

    private final PrivacidadService privacidad;

    public PrivacidadComando(PrivacidadService privacidad) {
        this.privacidad = privacidad;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.privacidad.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.privacidad.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.privacidad.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("info", "comando.privacidad.descripcion"),
                        sub("exportar", "comando.misdatos.descripcion"),
                        sub("borrar", "comando.borrarmisdatos.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "info" : evento.getSubcommandName();
        switch (sub) {
            case "info" -> info(evento, locale);
            case "exportar" -> exportar(evento, locale);
            case "borrar" -> borrar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void info(SlashCommandInteractionEvent evento, Locale locale) {
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                        Messages.get(locale, "privacidad.titulo"),
                        Messages.get(locale, "privacidad.texto")).build())
                .setEphemeral(true).queue();
    }

    private void exportar(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(true).queue();
        DataObject datos = privacidad.exportar(evento.getUser().getIdLong());
        byte[] json = datos.toString().getBytes(StandardCharsets.UTF_8);
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                        Messages.get(locale, "misdatos.texto")))
                .addFiles(FileUpload.fromData(json, "mis-datos.json"))
                .queue();
    }

    private void borrar(SlashCommandInteractionEvent evento, Locale locale) {
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                        Messages.get(locale, "borrar.aviso.titulo"),
                        Messages.get(locale, "borrar.aviso.texto")).build())
                .addActionRow(Button.danger(BOTON_CONFIRMAR, Messages.get(locale, "borrar.boton")))
                .setEphemeral(true)
                .queue();
    }
}
