package com.gymprofit.bot.jobs;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el cálculo de la espera hasta las 8:00 de Europe/Madrid, incluido el salto de día
 * y el cambio de hora (el cálculo va en hora local: tras un cambio DST sigue siendo a las 8:00
 * de reloj de pared, no 24 h exactas después), y la idempotencia diaria de la publicación.
 */
class EjercicioDiaJobTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void antesDeLasOchoEsperaHastaHoy() {
        ZonedDateTime seisAm = ZonedDateTime.of(2026, 7, 20, 6, 30, 0, 0, MADRID);
        assertEquals(Duration.ofMinutes(90), EjercicioDiaJob.esperaHastaLasOcho(seisAm));
    }

    @Test
    void despuesDeLasOchoEsperaHastaManana() {
        ZonedDateTime nueveAm = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(23), EjercicioDiaJob.esperaHastaLasOcho(nueveAm));
    }

    @Test
    void justoALasOchoProgramaParaManana() {
        ZonedDateTime lasOcho = ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(24), EjercicioDiaJob.esperaHastaLasOcho(lasOcho));
    }

    @Test
    void elCambioDeHoraMantieneLasOchoLocales() {
        // Noche del 24 al 25 de octubre de 2026: a las 03:00 se retrasa a las 02:00 (25 h de día).
        ZonedDateTime vispera = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(25), EjercicioDiaJob.esperaHastaLasOcho(vispera));
    }

    /**
     * Un reintendo tras publicar no repite el post: el guild ya publicado hoy se salta (la BD
     * garantiza el mismo ejercicio, pero el mensaje se enviaría dos veces sin esta guarda).
     */
    @Test
    void noRepiteElPostEnElMismoDia() {
        JDA jda = mock(JDA.class);
        TextChannel canal = mock(TextChannel.class, RETURNS_DEEP_STUBS);
        when(jda.getTextChannelById(anyLong())).thenReturn(canal);

        EjercicioDiaService eleccion = mock(EjercicioDiaService.class);
        when(eleccion.deHoy()).thenReturn(new EjercicioDia(LocalDate.of(2026, 7, 20), 7, 1));

        EjercicioService ejercicios = mock(EjercicioService.class);
        when(ejercicios.porId(anyInt(), anyString())).thenReturn(new EjercicioDTO(
                7, "Press banca", "Empuje horizontal", "Pecho", "Pectoral mayor", "Media",
                null, null, "Baja y sube", 120, "Barra", Boolean.TRUE));

        FraseRepositorio frases = mock(FraseRepositorio.class);
        when(frases.aleatoria()).thenReturn(Optional.empty());

        ConfigServidorRepositorio configs = mock(ConfigServidorRepositorio.class);
        when(configs.listarConEjercicioDia()).thenReturn(List.of(new ConfigServidor(
                42L, "es", null, 555L, null, null, null, null, null, null, null, null)));

        EjercicioDiaJob job = new EjercicioDiaJob(jda, eleccion, ejercicios, frases, configs);
        job.publicar();
        job.publicar(); // simula el reintento del mismo día
        verify(canal, times(1)).sendMessageEmbeds(any(MessageEmbed.class));
    }
}
