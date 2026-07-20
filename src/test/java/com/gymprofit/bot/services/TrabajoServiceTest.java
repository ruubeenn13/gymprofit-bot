package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.TrabajoService.EstadoWork;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el pago por turno, el bloqueo por sueño y la coherencia del catálogo de trabajos. */
class TrabajoServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final DescansoService descanso = mock(DescansoService.class);

    @Test
    void dormidoNoSePuedeCurrar() {
        when(descanso.estaDormido(1L)).thenReturn(true);
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso);

        assertEquals(EstadoWork.DORMIDO, svc.trabajar(1L, Instant.now()).estado());
        // El intento sale gratis: no cobra ni gasta energía.
        verify(personajes, never()).trabajar(anyLong(), anyInt());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void elPagoCaeDentroDelRango() {
        Random rng = new Random(7);
        for (int i = 0; i < 100; i++) {
            int pago = TrabajoService.calcularPago(80, 140, rng);
            assertTrue(pago >= 80 && pago <= 140, "pago fuera de rango: " + pago);
        }
    }

    @Test
    void losEstudiosDanBonoAlSueldoConTope() {
        assertEquals(100, TrabajoService.conBonoEstudios(100, 0));    // sin estudios
        assertEquals(110, TrabajoService.conBonoEstudios(100, 10));   // +10%
        assertEquals(125, TrabajoService.conBonoEstudios(100, 25));   // +25% (tope)
        assertEquals(125, TrabajoService.conBonoEstudios(100, 100));  // no pasa del tope
    }

    @Test
    @DisplayName("con fatiga el sueldo baja un 20 %")
    void fatigaBajaElSueldo() {
        assertEquals(80, TrabajoService.conPenalizacionFatiga(100, true));
        assertEquals(100, TrabajoService.conPenalizacionFatiga(100, false));
        // Suelo de 1 coin: currar fatigado paga menos, pero nunca sale gratis para el jefe.
        assertEquals(1, TrabajoService.conPenalizacionFatiga(1, true));
    }

    @Test
    void catalogoAmplioYConsistente() {
        assertTrue(Trabajos.CATALOGO.size() >= 25, "catálogo amplio");
        for (Trabajos t : Trabajos.CATALOGO) {
            assertTrue(t.salarioMin() > 0 && t.salarioMax() >= t.salarioMin(),
                    "rango de salario válido en " + t.id());
            assertTrue(t.energiaCoste() > 0 && t.energiaCoste() <= 100,
                    "coste de energía válido en " + t.id());
            assertTrue(Trabajos.porId(t.id()).isPresent(), "porId encuentra " + t.id());
        }
    }

    @Test
    void trabajosDeTierAltoPaganMasQueLosDeEntrada() {
        int maxTier1 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 1)
                .mapToInt(Trabajos::salarioMax).max().orElse(0);
        int minTier4 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 4)
                .mapToInt(Trabajos::salarioMin).min().orElse(0);
        assertTrue(minTier4 > maxTier1, "los trabajos de élite pagan más que los de entrada");
    }
}
