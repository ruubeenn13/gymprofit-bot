package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * {@code /ejercicio-dia}: el ejercicio de hoy. Si el job de las 8:00 aún no corrió, la
 * elección se crea aquí mismo ({@link EjercicioDiaService#deHoy()}): comando y publicación
 * siempre coinciden. Respuesta pública, naranja de marca, con frase motivadora al pie.
 */
public final class EjercicioDiaComando implements Comando {

    private static final String NOMBRE = "ejercicio-dia";

    private final EjercicioDiaService eleccion;
    private final EjercicioService ejercicios;
    private final FraseRepositorio frases;
    private final ExecutorService executor;

    public EjercicioDiaComando(EjercicioDiaService eleccion, EjercicioService ejercicios,
                               FraseRepositorio frases, ExecutorService executor) {
        this.eleccion = eleccion;
        this.ejercicios = ejercicios;
        this.frases = frases;
        this.executor = executor;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ejerciciodia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ejerciciodia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejerciciodia.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply().queue(); // la API puede estar despertando (~50 s)
        // Todo el trabajo (API + BD) va al executor propio: nunca en el hilo del gateway.
        CompletableFuture.runAsync(() -> {
            try {
                EjercicioDia dia = eleccion.deHoy();
                EjercicioDTO ficha = ejercicios.porId(dia.ejercicioId(), locale.getLanguage());
                Frase frase = frases.aleatoria().orElse(null);
                evento.getHook().editOriginalEmbeds(construirEmbed(locale, ficha, frase)).queue();
            } catch (ApiException e) {
                // El «pensando…» público se borra y el aviso va efímero (regla 13).
                evento.getHook().deleteOriginal().queue();
                evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.EJERCICIO,
                                locale, Messages.get(locale, "ejercicios.error")))
                        .setEphemeral(true).queue();
            }
        }, executor);
    }

    /**
     * Embed del ejercicio del día (lo comparten el comando y el job): título de marca, la ficha
     * completa reutilizando los campos de {@code /ejercicios} y la frase motivadora al pie.
     *
     * @param locale    idioma del destinatario
     * @param ejercicio ficha ya localizada que devuelve la API
     * @param frase     puede ser {@code null} (banco vacío): el post sale igual, sin cita
     * @return el embed listo para publicar
     */
    public static MessageEmbed construirEmbed(Locale locale, EjercicioDTO ejercicio, Frase frase) {
        // Se parte de la ficha estándar y se re-etiqueta como post del día: mismo contenido,
        // color/emoji de marca (SPEC §7: el ejercicio del día es naranja).
        MessageEmbed ficha = EjerciciosComando.construirFicha(locale, ejercicio);
        EmbedBuilder builder = new EmbedBuilder(ficha);
        builder.setColor(EmbedFactory.Categoria.MARCA.color());
        builder.setTitle(EmbedFactory.Tipo.EJERCICIO.emoji() + "  "
                + Messages.get(locale, "ejerciciodia.titulo"));
        // El nombre del ejercicio deja de ser el título, así que encabeza la descripción para no
        // perderse: el título ahora identifica el post, no el ejercicio.
        builder.setDescription("## " + ejercicio.nombre()
                + (ficha.getDescription() == null ? "" : "\n" + ficha.getDescription()));
        if (frase != null) {
            String cita = "*«" + frase.texto(locale) + "»*";
            if (frase.autor() != null) {
                cita += " — " + frase.autor();
            }
            builder.addField(Messages.get(locale, "ejerciciodia.campo.motivacion"), cita, false);
        }
        return builder.build();
    }
}
