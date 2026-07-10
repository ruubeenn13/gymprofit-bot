package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoServidor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica la lógica pura de los contadores en vivo sin abrir una conexión a Discord:
 * {@link EstadisticasService#contarEnVoz(List)} suma los miembros de todos los canales de voz.
 */
class EstadisticasServiceTest {

    private static VoiceChannel canalConMiembros(int n) {
        VoiceChannel canal = mock(VoiceChannel.class);
        when(canal.getMembers()).thenReturn(java.util.Collections.nCopies(n, mock(Member.class)));
        return canal;
    }

    @Test
    void sumaLosMiembrosDeTodosLosCanalesDeVoz() {
        List<VoiceChannel> canales = List.of(
                canalConMiembros(3),
                canalConMiembros(0),
                canalConMiembros(2));

        assertEquals(5, EstadisticasService.contarEnVoz(canales));
    }

    @Test
    void sinNadieEnVozDaCero() {
        List<VoiceChannel> canales = List.of(
                canalConMiembros(0),
                canalConMiembros(0));

        assertEquals(0, EstadisticasService.contarEnVoz(canales));
    }

    @Test
    void listaVaciaDeCanalesDaCero() {
        assertEquals(0, EstadisticasService.contarEnVoz(List.of()));
    }

    @Test
    void cuentaAtrasFormateaDiasHorasYMinutos() {
        assertEquals("en 3d 4h", EstadisticasService.cuentaAtras(3 * 86400 + 4 * 3600, 0));
        assertEquals("en 5h 20m", EstadisticasService.cuentaAtras(5 * 3600 + 20 * 60, 0));
        assertEquals("en 12m", EstadisticasService.cuentaAtras(12 * 60, 0));
    }

    @Test
    void cuentaAtrasYaPasadaDaYa() {
        assertEquals("¡ya!", EstadisticasService.cuentaAtras(100, 100));
        assertEquals("¡ya!", EstadisticasService.cuentaAtras(50, 100));
    }

    @Test
    void valorEventoComponeNombreYCuentaAtras() {
        EventoServidor ev = new EventoServidor(1L, null, "Reto de dominadas", 3600L);
        assertEquals("Reto de dominadas · en 1h 0m", EstadisticasService.valorEvento(ev, 0));
    }

    @Test
    void valorEventoSinEventoDaGuion() {
        assertEquals("—", EstadisticasService.valorEvento(EventoServidor.vacio(1L), 0));
    }
}
