package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoServidor;
import com.gymprofit.bot.db.EventoServidorRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import net.dv8tion.jda.api.JDA;
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
 * Mantiene al día los contadores en vivo de la categoría «SERVER STATS» que crea {@code /setup}:
 * XP repartido, Nº1 del ranking, boosts del servidor y gente conectada a voz.
 *
 * <p>Discord no permite un contador «en tiempo real» literal: renombrar un canal está limitado a
 * ~2 veces por 10 min. Por eso el servicio recalcula y renombra en un job periódico
 * ({@link #INTERVALO_MINUTOS} min) y <b>solo llama a la API si el valor cambió</b>. Los canales se
 * localizan por <b>prefijo de nombre</b> (el valor tras «:» cambia), sin persistir sus IDs.</p>
 *
 * <p>XP repartido y Nº1 salen de la BD (repositorio de usuarios); si el bot arranca sin BD esos dos
 * se omiten. Boosts y «En voz» salen de la caché estándar de JDA (no requieren intents
 * privilegiados).</p>
 */
public final class EstadisticasService {

    private static final Logger log = LoggerFactory.getLogger(EstadisticasService.class);

    /** Prefijos que identifican cada contador. Deben coincidir con {@code SetupServidorPlan}. */
    public static final String PREFIJO_XP = "🔥 XP repartido:";
    public static final String PREFIJO_TOP = "🏆 Nº1:";
    public static final String PREFIJO_BOOSTS = "🚀 Boosts:";
    public static final String PREFIJO_VOZ = "🔊 En voz:";
    public static final String PREFIJO_RETO = "🎯 Reto:";
    public static final String PREFIJO_EVENTO = "⏳ Evento:";
    /** Valor mostrado cuando un contador aún no tiene dato (reto/evento sin fijar). */
    private static final String SIN_DATO = "—";

    /** Cada cuánto se refrescan los contadores (holgado frente al rate limit de renombrado). */
    private static final long INTERVALO_MINUTOS = 6;
    /** Retardo inicial: da tiempo a que se resuelvan miembros y estados de voz tras conectar. */
    private static final long RETARDO_INICIAL_MINUTOS = 1;
    /** Máx. de caracteres del nombre del Nº1 dentro del nombre del canal (límite total 100). */
    private static final int MAX_NOMBRE_TOP = 80;

    private final JDA jda;
    /** Repositorios para los contadores que salen de BD; {@code null} si el bot arrancó sin BD. */
    private final UsuarioDiscordRepositorio usuarios;
    private final EventoServidorRepositorio eventos;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-stats");
        t.setDaemon(true);
        return t;
    });

    public EstadisticasService(JDA jda, UsuarioDiscordRepositorio usuarios,
                               EventoServidorRepositorio eventos) {
        this.jda = jda;
        this.usuarios = usuarios;
        this.eventos = eventos;
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
        renombrar(guild, PREFIJO_BOOSTS, String.valueOf(guild.getBoostCount()));
        renombrar(guild, PREFIJO_VOZ, String.valueOf(contarEnVoz(guild.getVoiceChannels())));

        if (usuarios != null) {
            renombrar(guild, PREFIJO_XP, String.valueOf(usuarios.sumaXp()));
            renombrar(guild, PREFIJO_TOP, nombreDelNumeroUno(guild));
        }

        if (eventos != null) {
            EventoServidor ev = eventos.obtener(guild.getIdLong());
            renombrar(guild, PREFIJO_RETO, ev.retoTexto() == null ? SIN_DATO : ev.retoTexto());
            renombrar(guild, PREFIJO_EVENTO, valorEvento(ev, System.currentTimeMillis() / 1000));
        }
    }

    /** Texto del contador de evento: «nombre · cuenta atrás», o «—» si no hay evento fijado. */
    static String valorEvento(EventoServidor ev, long ahoraSeg) {
        if (ev.eventoNombre() == null || ev.eventoFin() == null) {
            return SIN_DATO;
        }
        return ev.eventoNombre() + " · " + cuentaAtras(ev.eventoFin(), ahoraSeg);
    }

    /**
     * Cuenta atrás legible hasta un instante ({@code finSeg}) desde {@code ahoraSeg}, ambos en epoch
     * de segundos. Ejemplos: «en 3d 4h», «en 5h 20m», «en 12m», o «¡ya!» si ya pasó. Lógica pura.
     */
    static String cuentaAtras(long finSeg, long ahoraSeg) {
        long restante = finSeg - ahoraSeg;
        if (restante <= 0) {
            return "¡ya!";
        }
        long dias = restante / 86400;
        long horas = (restante % 86400) / 3600;
        long minutos = (restante % 3600) / 60;
        if (dias > 0) {
            return "en " + dias + "d " + horas + "h";
        }
        if (horas > 0) {
            return "en " + horas + "h " + minutos + "m";
        }
        return "en " + minutos + "m";
    }

    /** Nº de miembros conectados a algún canal de voz (excluye a nadie: cuenta todos los presentes). */
    public static int contarEnVoz(List<VoiceChannel> canales) {
        return canales.stream().mapToInt(c -> c.getMembers().size()).sum();
    }

    /**
     * Nombre a mostrar del Nº1 del ranking de XP: resuelve el miembro por su ID de Discord. Si no
     * hay ranking o el miembro ya no está en el server, devuelve un guion. Trunca a
     * {@link #MAX_NOMBRE_TOP} para no pasarse del límite de longitud del nombre de canal.
     */
    private String nombreDelNumeroUno(Guild guild) {
        List<UsuarioDiscord> top = usuarios.listarTopPorXp(1);
        if (top.isEmpty()) {
            return "—";
        }
        Member miembro = guild.getMemberById(top.get(0).discordId());
        if (miembro == null) {
            return "—";
        }
        String nombre = miembro.getEffectiveName();
        return nombre.length() > MAX_NOMBRE_TOP ? nombre.substring(0, MAX_NOMBRE_TOP) : nombre;
    }

    /**
     * Renombra el canal de voz cuyo nombre empieza por {@code prefijo} al valor dado. No hace nada
     * si el canal no existe o si el nombre ya es el correcto (evita gastar rate limit).
     */
    private void renombrar(Guild guild, String prefijo, String valor) {
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
