package com.gymprofit.bot.jobs;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.commands.consultas.EjercicioDiaComando;
import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publica el ejercicio del día a las <b>8:00 Europe/Madrid</b> en el canal configurado de cada
 * servidor ({@code config_servidor.canal_ejercicio_dia}, lo fija {@code /setup}), en el idioma
 * del servidor (el post es para todos, no para un usuario). Sin mención de rol (spec §5.2: un
 * ping diario quema). De paso hace de despertador de la API de Render.
 *
 * <p>Se reprograma a sí mismo tras cada ejecución calculando las próximas 8:00 en hora local
 * (aguanta los cambios de horario). Si la API falla, no publica un post roto: lo registra y
 * reintenta a los 30 min; los servidores ya publicados hoy no se repiten (idempotencia por
 * guild en memoria + fila única por fecha en BD).</p>
 */
public final class EjercicioDiaJob {

    private static final Logger log = LoggerFactory.getLogger(EjercicioDiaJob.class);
    private static final LocalTime HORA_PUBLICACION = LocalTime.of(8, 0);
    private static final long REINTENTO_MIN = 30;

    private final JDA jda;
    private final EjercicioDiaService eleccion;
    private final EjercicioService ejercicios;
    private final FraseRepositorio frases;
    private final ConfigServidorRepositorio configs;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gymprobot-ejercicio-dia");
                t.setDaemon(true);
                return t;
            });

    /** Guilds ya publicados en el día en curso (evita repetir el post en los reintentos). */
    private final Set<Long> publicadosHoy = ConcurrentHashMap.newKeySet();
    private volatile LocalDate diaDePublicados;

    public EjercicioDiaJob(JDA jda, EjercicioDiaService eleccion, EjercicioService ejercicios,
                           FraseRepositorio frases, ConfigServidorRepositorio configs) {
        this.jda = jda;
        this.eleccion = eleccion;
        this.ejercicios = ejercicios;
        this.frases = frases;
        this.configs = configs;
    }

    /** Programa la primera publicación (y con ella, todas las siguientes). */
    public void iniciar() {
        programarSiguiente();
    }

    /** Detiene el scheduler (parada limpia en el shutdown hook). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /**
     * Espera desde {@code ahora} hasta las próximas 8:00 <b>locales</b> de Madrid. Si ya son
     * las 8:00 en punto o más tarde, apunta a mañana. En hora local a propósito: tras un cambio
     * de horario el post sigue saliendo a las 8:00 de reloj, no 24 h después.
     */
    static Duration esperaHastaLasOcho(ZonedDateTime ahora) {
        ZonedDateTime proxima = ahora.toLocalDate().atTime(HORA_PUBLICACION)
                .atZone(ahora.getZone());
        if (!proxima.isAfter(ahora)) {
            proxima = ahora.toLocalDate().plusDays(1).atTime(HORA_PUBLICACION)
                    .atZone(ahora.getZone());
        }
        return Duration.between(ahora, proxima);
    }

    private void programarSiguiente() {
        Duration espera = esperaHastaLasOcho(ZonedDateTime.now(EjercicioDiaService.ZONA));
        scheduler.schedule(this::tick, espera.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Ejercicio del día programado para dentro de {} min", espera.toMinutes());
    }

    private void tick() {
        // Lo primero es reprogramar mañana: un fallo publicando jamás mata el ciclo diario.
        programarSiguiente();
        publicar();
    }

    /** Publica en todos los servidores configurados. Público para invocarlo en manual/tests. */
    public void publicar() {
        LocalDate hoy = LocalDate.now(EjercicioDiaService.ZONA);
        if (!hoy.equals(diaDePublicados)) {
            publicadosHoy.clear();
            diaDePublicados = hoy;
        }
        try {
            EjercicioDia dia = eleccion.deHoy();
            for (ConfigServidor config : configs.listarConEjercicioDia()) {
                if (publicadosHoy.contains(config.guildId())) {
                    continue; // reintento: este servidor ya tiene su post de hoy
                }
                // canalEjercicioDia() es Long, pero la consulta filtra IS NOT NULL: nunca es null aquí.
                TextChannel canal = jda.getTextChannelById(config.canalEjercicioDia());
                if (canal == null) {
                    log.warn("Canal de ejercicio del día no encontrado en el guild {}",
                            config.guildId());
                    continue;
                }
                Locale locale = Messages.desdeTag(config.idioma());
                EjercicioDTO ficha = ejercicios.porId(dia.ejercicioId(), locale.getLanguage());
                Frase frase = frases.aleatoria().orElse(null);
                canal.sendMessageEmbeds(
                        EjercicioDiaComando.construirEmbed(locale, ficha, frase)).queue();
                publicadosHoy.add(config.guildId());
            }
        } catch (ApiException e) {
            // API caída/despertando: nada de posts rotos; reintento con la misma idempotencia.
            log.warn("La API no respondió para el ejercicio del día; reintento en {} min",
                    REINTENTO_MIN, e);
            scheduler.schedule(this::publicar, REINTENTO_MIN, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.error("Fallo publicando el ejercicio del día", e);
        }
    }
}
