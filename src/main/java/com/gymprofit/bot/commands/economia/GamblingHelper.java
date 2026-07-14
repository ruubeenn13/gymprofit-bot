package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.ApuestaService.Resultado;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Locale;

/** Utilidades comunes de los comandos de azar (opción de apuesta, cooldown y embed de resultado). */
final class GamblingHelper {

    private GamblingHelper() {
    }

    /** Opción de apuesta (entero, con los límites del servicio). */
    static OptionData opcionApuesta() {
        return new OptionData(OptionType.INTEGER, "apuesta",
                Messages.get(Messages.ES, "comando.apuesta.opcion.apuesta"), true)
                .setMinValue(ApuestaService.APUESTA_MIN)
                .setMaxValue(ApuestaService.APUESTA_MAX);
    }

    /** Comprueba el cooldown; si aún enfría, responde y devuelve {@code true}. */
    static boolean enCooldown(SlashCommandInteractionEvent evento, Cooldown cooldown, Locale locale) {
        if (!cooldown.intentar(evento.getUser().getIdLong(), System.currentTimeMillis())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "apuesta.cooldown"))).setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    /** Embed del resultado de una apuesta (o del error de validación/saldo). */
    static MessageEmbed embed(Locale locale, Resultado r, String detalle) {
        String texto = switch (r.estado()) {
            case APUESTA_INVALIDA -> Messages.get(locale, "apuesta.invalida",
                    ApuestaService.APUESTA_MIN, ApuestaService.APUESTA_MAX);
            case SIN_SALDO -> Messages.get(locale, "regalar.sinsaldo");
            case OK -> detalle + "\n" + (r.gano()
                    ? Messages.get(locale, "apuesta.ganaste", r.ganancia())
                    : Messages.get(locale, "apuesta.perdiste", -r.ganancia()));
        };
        return EmbedFactory.aviso(r.gano() ? EmbedFactory.Tipo.LOGRO : EmbedFactory.Tipo.ECONOMIA,
                locale, texto);
    }
}
