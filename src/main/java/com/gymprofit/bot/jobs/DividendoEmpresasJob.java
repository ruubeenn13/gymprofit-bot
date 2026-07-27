package com.gymprofit.bot.jobs;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.AccionEmpresasService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reparte los <b>dividendos semanales</b> de las participaciones de empresa (F5) los <b>jueves a las
 * 02:00 Europe/Madrid</b>: por cada empresa calcula el pot ({@code FRACCION_DIVIDENDO} del bote) y lo
 * reparte proporcionalmente entre sus accionistas ({@link AccionEmpresasService#repartirDividendos}).
 * Toda la regla vive en el service; el job solo la dispara en su franja y avisa del resultado en el
 * canal privado de cada empresa.
 *
 * <p>El jueves (y no el lunes del impuesto, {@code ImpuestoEmpresasJob}) es a propósito: reparte la
 * carga sobre el bote a lo largo de la semana en vez de acumular dos sumideros el mismo día, y da tiempo
 * a que la nómina diaria y las ventas repongan el bote entre medias.</p>
 *
 * <p>Se reprograma a sí mismo tras cada ejecución calculando el próximo jueves 02:00 en hora local
 * (aguanta los cambios de horario), igual que {@code ImpuestoEmpresasJob}. El reparto de cada empresa va
 * envuelto en su propio try/catch: un fallo en una no tumba el reparto del resto (best-effort). El aviso
 * al canal es best-effort también.</p>
 */
public final class DividendoEmpresasJob {

    private static final Logger log = LoggerFactory.getLogger(DividendoEmpresasJob.class);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalTime HORA = LocalTime.of(2, 0);
    private static final DayOfWeek DIA = DayOfWeek.THURSDAY;

    private final EmpresaRepositorio empresas;
    private final AccionEmpresasService service;
    private final JDA jda;
    private final Clock clock;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gymprobot-dividendo-empresas");
                t.setDaemon(true);
                return t;
            });

    public DividendoEmpresasJob(EmpresaRepositorio empresas, AccionEmpresasService service,
                                JDA jda, Clock clock) {
        this.empresas = empresas;
        this.service = service;
        this.jda = jda;
        this.clock = clock;
    }

    /** Programa el primer reparto (y con él, todos los siguientes). */
    public void iniciar() {
        programarSiguiente();
    }

    /** Detiene el scheduler (parada limpia en el shutdown hook). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /**
     * Espera desde {@code ahora} hasta el próximo jueves a las 02:00 <b>locales</b>. Si ya pasó ese
     * instante este jueves (o no es jueves), apunta al jueves que viene. En hora local a propósito: tras
     * un cambio de horario el reparto sigue saliendo a las 02:00 de reloj. Package-private para el test.
     */
    static Duration esperaHastaProximoJueves(ZonedDateTime ahora) {
        ZonedDateTime prox = ahora.toLocalDate().atTime(HORA).atZone(ahora.getZone())
                .with(TemporalAdjusters.nextOrSame(DIA));
        if (!prox.isAfter(ahora)) {
            prox = prox.plusWeeks(1);
        }
        return Duration.between(ahora, prox);
    }

    private void programarSiguiente() {
        Duration espera = esperaHastaProximoJueves(ZonedDateTime.now(clock.withZone(ZONA)));
        scheduler.schedule(this::tick, espera.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Dividendos de empresas programados para dentro de {} min", espera.toMinutes());
    }

    private void tick() {
        // Lo primero es reprogramar la semana que viene: un fallo repartiendo jamás mata el ciclo semanal.
        programarSiguiente();
        repartir();
    }

    /** Reparte dividendos a todas las empresas. Público para invocarlo en manual/tests. */
    public void repartir() {
        List<Empresa> todas;
        try {
            todas = empresas.todas();
        } catch (RuntimeException e) {
            log.error("No se pudieron listar empresas; reparto de dividendos abortado", e);
            return;
        }
        for (Empresa e : todas) {
            try {
                // Cada empresa aislada: si una peta (BD, datos raros), las demás cobran igual.
                AccionEmpresasService.ResultadoDividendo r = service.repartirDividendos(e);
                avisar(e, r);
            } catch (RuntimeException ex) {
                log.warn("Fallo repartiendo dividendos de la empresa {}", e.id(), ex);
            }
        }
    }

    /**
     * Aviso al canal privado de la empresa (F4, si existe). Solo se manda cuando hubo pago: si no hay
     * accionistas o no toca nada, no hay nada que anunciar. En ES porque es un post de canal, no la
     * respuesta a un usuario. Best-effort: sin canal o con JDA aún sin conectar no revienta nada.
     */
    private void avisar(Empresa e, AccionEmpresasService.ResultadoDividendo r) {
        if (jda == null || e.canalId() == null
                || r.estado() != AccionEmpresasService.EstadoDividendo.PAGADO) {
            return;
        }
        TextChannel canal = jda.getTextChannelById(e.canalId());
        if (canal == null) {
            return;
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, Messages.ES,
                Messages.get(Messages.ES, "empresa.dividendo.titulo"),
                Messages.get(Messages.ES, "empresa.dividendo.cuerpo", r.total(), r.accionistas()))
                .build();
        canal.sendMessageEmbeds(embed).queue(null, err ->
                log.warn("No se pudo avisar del dividendo a la empresa {}", e.id(), err));
    }
}
