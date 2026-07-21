package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /frase}: una frase motivadora al azar del banco bilingüe (32 sembradas en la V2, todas
 * de categoría MOTIVACION: sin opciones). Respuesta pública (regla 13) con cooldown de 30 s por
 * usuario: es barato de repetir y sin freno empapelaría el canal.
 */
public final class FraseComando implements Comando {

    private static final String NOMBRE = "frase";

    private final FraseRepositorio frases;
    private final Cooldown cooldown;

    public FraseComando(FraseRepositorio frases, Cooldown cooldown) {
        this.frases = frases;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.frase.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.frase.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.frase.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // El cooldown se comprueba antes de tocar la BD; el aviso va efímero (regla 13: ruido).
        if (!cooldown.intentar(evento.getUser().getIdLong(), System.currentTimeMillis())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.FRASE, locale,
                    Messages.get(locale, "frase.cooldown"))).setEphemeral(true).queue();
            return;
        }
        Optional<Frase> frase = frases.aleatoria();
        if (frase.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.FRASE, locale,
                    Messages.get(locale, "frase.vacia"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(construirEmbed(locale, frase.get())).queue();
    }

    /** Embed de la frase: cita en el idioma del usuario y autor si lo tiene. Estático para test. */
    static MessageEmbed construirEmbed(Locale locale, Frase frase) {
        String cita = "*«" + frase.texto(locale) + "»*";
        if (frase.autor() != null) {
            cita += "\n— **" + frase.autor() + "**";
        }
        return EmbedFactory.base(EmbedFactory.Tipo.FRASE, locale,
                Messages.get(locale, "frase.titulo"), cita).build();
    }
}
