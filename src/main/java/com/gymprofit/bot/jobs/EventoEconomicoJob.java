package com.gymprofit.bot.jobs;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EventoEconomico;
import com.gymprofit.bot.services.EventoEconomicoService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Motor del clima económico (F5): cada {@value #INTERVALO_MIN} min llama a
 * {@link EventoEconomicoService#tick} y, según el resultado, anuncia el inicio o el fin del evento en
 * el canal {@code 💰・economía} (best-effort: sin canal o sin JDA no revienta). El {@code Clock} da el
 * {@code Instant} del tick (inyectable para tests). El anuncio de inicio se expone estático para que el
 * lanzamiento manual ({@code /economia lanzar}) reutilice el mismo envío.
 */
public final class EventoEconomicoJob {

    private static final Logger log = LoggerFactory.getLogger(EventoEconomicoJob.class);
    private static final long INTERVALO_MIN = 60;
    /** Nombre del canal donde se anuncian los eventos (lo crea /setup en la categoría VIDA). */
    static final String CANAL = "💰・economía";

    private final EventoEconomicoService service;
    private final JDA jda;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-evento-economico");
        t.setDaemon(true);
        return t;
    });

    public EventoEconomicoJob(EventoEconomicoService service, JDA jda, Clock clock) {
        this.service = service;
        this.jda = jda;
        this.clock = clock;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::tick, INTERVALO_MIN, INTERVALO_MIN, TimeUnit.MINUTES);
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    private void tick() {
        try {
            EventoEconomicoService.ResultadoTick r = service.tick(clock.instant());
            switch (r.estado()) {
                case INICIADO -> anunciarInicio(jda, r.evento());
                case FINALIZADO -> anunciarFin(jda, r.evento());
                case NADA -> { /* nada que anunciar */ }
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job del clima económico", e);
        }
    }

    /** Anuncio de inicio (reutilizado por el lanzamiento manual). Best-effort. En ES (post de canal). */
    public static void anunciarInicio(JDA jda, EventoEconomico e) {
        publicar(jda, EmbedFactory.Tipo.ECONOMIA,
                Messages.get(Messages.ES, "economia.anuncio.inicio.titulo", e.emoji()),
                Messages.get(Messages.ES, "economia.anuncio.inicio.cuerpo",
                        Messages.get(Messages.ES, e.claveI18n() + ".nombre"),
                        Messages.get(Messages.ES, e.claveI18n() + ".efecto"),
                        EventoEconomicoService.DURACION.toHours()));
    }

    private static void anunciarFin(JDA jda, EventoEconomico e) {
        publicar(jda, EmbedFactory.Tipo.ECONOMIA,
                Messages.get(Messages.ES, "economia.anuncio.fin.titulo"),
                Messages.get(Messages.ES, "economia.anuncio.fin.cuerpo",
                        Messages.get(Messages.ES, e.claveI18n() + ".nombre")));
    }

    private static void publicar(JDA jda, EmbedFactory.Tipo tipo, String titulo, String cuerpo) {
        if (jda == null) {
            return;
        }
        TextChannel canal = jda.getTextChannelsByName(CANAL, false).stream().findFirst().orElse(null);
        if (canal == null) {
            log.warn("No hay canal {} para anunciar el evento económico", CANAL);
            return;
        }
        var embed = EmbedFactory.base(tipo, Messages.ES, titulo, cuerpo).build();
        canal.sendMessageEmbeds(embed).queue(null,
                err -> log.warn("No se pudo anunciar el evento económico", err));
    }
}
