package com.gymprofit.bot.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mantiene al día los canales de estadísticas del servidor (los contadores en vivo de la categoría
 * «SERVER STATS» que crea {@code /setup}): miembros humanos, miembros en línea y bots.
 *
 * <p>Discord no permite un contador «en tiempo real» literal: renombrar un canal está limitado a
 * ~2 veces por 10 min. Por eso el servicio recalcula y renombra en un job periódico
 * ({@link #INTERVALO_MINUTOS} min) y <b>solo llama a la API si el número cambió</b>, para no gastar
 * rate limit. Los canales se localizan por <b>prefijo de nombre</b> (el valor tras «:» cambia), de
 * modo que no hace falta persistir sus IDs.</p>
 *
 * <p>El contador «En línea» requiere el intent privilegiado {@code GUILD_PRESENCES} y la caché
 * {@code ONLINE_STATUS}; sin ellos {@link Member#getOnlineStatus()} devuelve OFFLINE para todos y
 * el contador quedaría a 0.</p>
 */
public final class EstadisticasService {

    private static final Logger log = LoggerFactory.getLogger(EstadisticasService.class);

    /** Prefijos que identifican cada contador. Deben coincidir con {@code SetupServidorPlan}. */
    public static final String PREFIJO_MIEMBROS = "👥 Miembros:";
    public static final String PREFIJO_ONLINE = "🟢 En línea:";
    public static final String PREFIJO_BOTS = "🤖 Bots:";

    /** Cada cuánto se refrescan los contadores (holgado frente al rate limit de renombrado). */
    private static final long INTERVALO_MINUTOS = 6;
    /** Retardo inicial: da tiempo a que se resuelvan miembros y presencias tras conectar. */
    private static final long RETARDO_INICIAL_MINUTOS = 1;

    /** Conteo de una guild. Separado del renombrado para poder testear la lógica sin JDA. */
    public record Conteo(long miembros, long online, long bots) {
    }

    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-stats");
        t.setDaemon(true);
        return t;
    });

    public EstadisticasService(JDA jda) {
        this.jda = jda;
    }

    /** Arranca el job periódico. Idempotente respecto a la API (no renombra si nada cambió). */
    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::actualizarTodas,
                RETARDO_INICIAL_MINUTOS, INTERVALO_MINUTOS, TimeUnit.MINUTES);
        log.info("Job de estadísticas programado cada {} min", INTERVALO_MINUTOS);
    }

    /** Detiene el job (cierre ordenado). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /** Refresca todas las guilds; un fallo en una no corta el resto. */
    private void actualizarTodas() {
        for (Guild guild : jda.getGuilds()) {
            try {
                actualizarGuild(guild);
            } catch (RuntimeException e) {
                log.warn("No se pudieron actualizar las stats de {}", guild.getId(), e);
            }
        }
    }

    /** Recalcula los contadores de una guild y renombra sus canales de stats si existen. */
    void actualizarGuild(Guild guild) {
        Conteo conteo = contar(guild.getMembers());
        renombrar(guild, PREFIJO_MIEMBROS, conteo.miembros());
        renombrar(guild, PREFIJO_ONLINE, conteo.online());
        renombrar(guild, PREFIJO_BOTS, conteo.bots());
    }

    /**
     * Cuenta miembros humanos, humanos en línea y bots de una lista de miembros. Lógica pura
     * (sin JDA) para poder testearla. «En línea» = cualquier estado distinto de OFFLINE/UNKNOWN.
     */
    public static Conteo contar(List<Member> miembros) {
        long bots = miembros.stream().filter(m -> m.getUser().isBot()).count();
        long online = miembros.stream()
                .filter(m -> !m.getUser().isBot())
                .filter(m -> m.getOnlineStatus() != OnlineStatus.OFFLINE
                        && m.getOnlineStatus() != OnlineStatus.UNKNOWN)
                .count();
        return new Conteo(miembros.size() - bots, online, bots);
    }

    /**
     * Renombra el canal de voz cuyo nombre empieza por {@code prefijo} al valor dado. No hace nada
     * si el canal no existe o si el nombre ya es el correcto (evita gastar rate limit).
     */
    private void renombrar(Guild guild, String prefijo, long valor) {
        VoiceChannel canal = guild.getVoiceChannels().stream()
                .filter(c -> c.getName().startsWith(prefijo))
                .findFirst().orElse(null);
        if (canal == null) {
            return;
        }
        String nuevo = prefijo + " " + valor;
        if (nuevo.equals(canal.getName())) {
            return;
        }
        canal.getManager().setName(nuevo).queue(null,
                err -> log.warn("No se pudo renombrar el canal de stats «{}»", prefijo, err));
    }
}
