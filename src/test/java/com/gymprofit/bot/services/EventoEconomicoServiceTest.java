package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoActivo;
import com.gymprofit.bot.db.EventoEconomicoRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests de {@link EventoEconomicoService}: multiplicadores neutros/activos y el job {@code tick}. */
class EventoEconomicoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-07-27T10:00:00Z");

    private final EventoEconomicoRepositorio repo = mock(EventoEconomicoRepositorio.class);

    /** Service con azar fijo inyectado. */
    private EventoEconomicoService svc(double azar) {
        return new EventoEconomicoService(repo, () -> azar);
    }

    @Test
    @DisplayName("sin evento activo, todos los multiplicadores son neutros")
    void neutroSinEvento() {
        when(repo.activo()).thenReturn(Optional.empty());
        EventoEconomicoService s = svc(0.0);
        assertEquals(1.0, s.ventaMult());
        assertEquals(1.0, s.produccionMult());
        assertEquals(1.0, s.impuestoMult());
        assertEquals(1.0, s.curroMult());
        assertEquals(EventoEconomico.BolsaSesgo.NINGUNO, s.bolsaSesgo());
    }

    @Test
    @DisplayName("con evento activo, el getter devuelve su multiplicador")
    void multiplicadorDelActivo() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.BOOM_CONSUMO, AHORA, AHORA.plusSeconds(10))));
        assertEquals(1.30, svc(0.0).ventaMult());
    }

    @Test
    @DisplayName("tick sin activo y azar < PROB: inicia un evento aleatorio")
    void tickInicia() {
        when(repo.activo()).thenReturn(Optional.empty());
        // 1er next() = dado (< PROB), 2º next() = elección del catálogo
        EventoEconomicoService s = new EventoEconomicoService(repo, dadoLuegoCatalogo(0.0, 0.0));
        EventoEconomicoService.ResultadoTick r = s.tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.INICIADO, r.estado());
        verify(repo).fijar(any(), eq(AHORA), eq(AHORA.plus(EventoEconomicoService.DURACION)));
    }

    @Test
    @DisplayName("tick sin activo y azar >= PROB: no pasa nada")
    void tickNada() {
        when(repo.activo()).thenReturn(Optional.empty());
        EventoEconomicoService.ResultadoTick r = svc(0.99).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.NADA, r.estado());
        verify(repo, never()).fijar(any(), any(), any());
    }

    @Test
    @DisplayName("tick con evento caducado: lo finaliza y limpia")
    void tickFinaliza() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.RECESION, AHORA.minusSeconds(100), AHORA.minusSeconds(1))));
        EventoEconomicoService.ResultadoTick r = svc(0.0).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.FINALIZADO, r.estado());
        assertEquals(EventoEconomico.RECESION, r.evento());
        verify(repo).limpiar();
    }

    @Test
    @DisplayName("tick con evento vigente: no lo toca")
    void tickVigente() {
        when(repo.activo()).thenReturn(Optional.of(
                new EventoActivo(EventoEconomico.RECESION, AHORA.minusSeconds(10), AHORA.plusSeconds(100))));
        EventoEconomicoService.ResultadoTick r = svc(0.0).tick(AHORA);
        assertEquals(EventoEconomicoService.EstadoTick.NADA, r.estado());
        verify(repo, never()).limpiar();
        verify(repo, never()).fijar(any(), any(), any());
    }

    /** Aleatorio que devuelve valores en secuencia (para tick: dado, luego elección de catálogo). */
    private static BatallaService.Aleatorio dadoLuegoCatalogo(double... valores) {
        int[] i = {0};
        return () -> valores[Math.min(i[0]++, valores.length - 1)];
    }
}
