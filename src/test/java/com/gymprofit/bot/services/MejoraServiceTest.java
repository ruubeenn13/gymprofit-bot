package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.MejoraRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.MejoraService.EstadoMejora;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica la compra de nodos del árbol: prerrequisito, saldo, duplicados y catálogo. */
class MejoraServiceTest {

    private final MejoraRepositorio mejoras = mock(MejoraRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final MejoraService servicio =
            new MejoraService(mejoras, economia, personajes, usuarios);

    @Test
    void comprarRaizAplicaBonus() {
        when(economia.gastar(eq(1L), anyLong(), eq("mejora"))).thenReturn(true);
        var r = servicio.comprar(1L, "fuerza1"); // sin prereq
        assertEquals(EstadoMejora.OK, r.estado());
        verify(mejoras).comprar(1L, "fuerza1");
        verify(personajes).sumarAtributo(1L, "fuerza", 2);
    }

    @Test
    void nodoBloqueadoSinPrerrequisito() {
        when(mejoras.tiene(1L, "fuerza1")).thenReturn(false); // no tiene el prereq
        var r = servicio.comprar(1L, "fuerza2");
        assertEquals(EstadoMejora.BLOQUEADO, r.estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    void noSeCompraDosVeces() {
        when(mejoras.tiene(1L, "fuerza1")).thenReturn(true);
        assertEquals(EstadoMejora.YA_TIENES, servicio.comprar(1L, "fuerza1").estado());
    }

    @Test
    void sinSaldoNoAplica() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        var r = servicio.comprar(1L, "carisma1");
        assertEquals(EstadoMejora.SIN_SALDO, r.estado());
        verify(personajes, never()).sumarAtributo(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void nodoInexistente() {
        assertEquals(EstadoMejora.NO_EXISTE, servicio.comprar(1L, "magia9").estado());
    }

    @Test
    void arbolCoherente() {
        assertEquals(12, Mejoras.CATALOGO.size());
        for (Mejoras m : Mejoras.CATALOGO) {
            assertTrue(m.precio() > 0 && m.valor() > 0, "coste y bonus válidos en " + m.id());
            if (m.prereq() != null) {
                assertTrue(Mejoras.porId(m.prereq()).isPresent(), "prereq válido en " + m.id());
            }
        }
    }
}
