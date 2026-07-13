package com.gymprofit.bot.jobs;

import com.gymprofit.bot.commands.contenido.SorteoComando;
import com.gymprofit.bot.db.Sorteo;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.SorteoService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cierra los sorteos vencidos: lee las reacciones 🎉 del mensaje, elige {@code num_ganadores} al azar
 * entre los participantes (excluye bots), lo anuncia en el canal y marca el sorteo como resuelto.
 * Corre cada minuto en su propio hilo (no bloquea el gateway).
 */
public final class SorteoJob {

    private static final Logger log = LoggerFactory.getLogger(SorteoJob.class);
    private static final long INTERVALO_MIN = 1;
    /** Tope de participantes que se leen del mensaje (páginas de reacciones). */
    private static final int MAX_PARTICIPANTES = 500;

    private final JDA jda;
    private final SorteoService sorteos;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-sorteos");
        t.setDaemon(true);
        return t;
    });

    public SorteoJob(JDA jda, SorteoService sorteos) {
        this.jda = jda;
        this.sorteos = sorteos;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::resolverVencidos, INTERVALO_MIN, INTERVALO_MIN,
                TimeUnit.MINUTES);
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    /** Resuelve todos los sorteos vencidos. Público para poder invocarlo en tests/manual. */
    public void resolverVencidos() {
        try {
            for (Sorteo sorteo : sorteos.vencidos(Instant.now().getEpochSecond())) {
                try {
                    resolver(sorteo);
                } catch (RuntimeException e) {
                    log.warn("No se pudo resolver el sorteo {}", sorteo.id(), e);
                } finally {
                    sorteos.cerrar(sorteo.id()); // nunca reintentar un sorteo (evita anuncios dobles)
                }
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job de sorteos", e);
        }
    }

    private void resolver(Sorteo sorteo) {
        TextChannel canal = jda.getTextChannelById(sorteo.canalId());
        if (canal == null) {
            return;
        }
        Message mensaje = canal.retrieveMessageById(sorteo.mensajeId()).complete();
        List<Long> participantes = mensaje
                .retrieveReactionUsers(Emoji.fromUnicode(SorteoComando.EMOJI))
                .takeAsync(MAX_PARTICIPANTES).join().stream()
                .filter(u -> !u.isBot())
                .map(User::getIdLong)
                .toList();

        List<Long> ganadores = SorteoService.elegirGanadores(participantes, sorteo.numGanadores());
        String texto = ganadores.isEmpty()
                ? Messages.get(Messages.ES, "sorteo.sinparticipantes", sorteo.premio())
                : Messages.get(Messages.ES, "sorteo.ganadores",
                        ganadores.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" ")),
                        sorteo.premio());
        canal.sendMessage(MessageCreateData.fromContent(texto)).queue();
    }
}
