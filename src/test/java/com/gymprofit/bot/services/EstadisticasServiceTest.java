package com.gymprofit.bot.services;

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
}
