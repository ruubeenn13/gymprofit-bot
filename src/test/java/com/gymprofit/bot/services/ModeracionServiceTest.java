package com.gymprofit.bot.services;

import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.services.ModeracionService.AccionEscalado;
import com.gymprofit.bot.services.ModeracionService.ResultadoAviso;
import com.gymprofit.bot.util.Cifrador;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el escalado por umbrales y que {@code avisar} persiste, cifra y cuenta correctamente. */
class ModeracionServiceTest {

    private final WarnRepositorio warns = mock(WarnRepositorio.class);
    private final SancionRepositorio sanciones = mock(SancionRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final Cifrador cifrador = new Cifrador(Cifrador.generarClaveBase64());
    private final ModeracionService servicio =
            new ModeracionService(warns, sanciones, usuarios, cifrador);

    @Test
    void escaladoRespetaLosUmbrales() {
        assertEquals(AccionEscalado.NINGUNA, ModeracionService.escalado(0));
        assertEquals(AccionEscalado.NINGUNA, ModeracionService.escalado(2));
        assertEquals(AccionEscalado.TIMEOUT_1H, ModeracionService.escalado(3));
        assertEquals(AccionEscalado.TIMEOUT_1H, ModeracionService.escalado(4));
        assertEquals(AccionEscalado.TIMEOUT_24H, ModeracionService.escalado(5));
        assertEquals(AccionEscalado.TIMEOUT_24H, ModeracionService.escalado(6));
        assertEquals(AccionEscalado.BAN, ModeracionService.escalado(7));
        assertEquals(AccionEscalado.BAN, ModeracionService.escalado(12));
    }

    @Test
    void avisarInsertaCuentaYDevuelveEscalado() {
        when(warns.insertar(anyLong(), anyLong(), any())).thenReturn(42L);
        when(warns.contarActivos(100L)).thenReturn(5);

        ResultadoAviso r = servicio.avisar(1L, 100L, 200L, "spam de menciones");

        assertEquals(42L, r.warnId());
        assertEquals(5, r.warnsActivos());
        assertEquals(AccionEscalado.TIMEOUT_24H, r.accion());
        verify(usuarios).obtenerOCrear(100L);          // garantiza la FK
        verify(sanciones).insertar(eq(1L), eq(100L), eq(200L), eq("WARN"), any(), isNull(), isNull());
    }

    @Test
    void avisarGuardaElMotivoCifradoNoEnClaro() {
        when(warns.insertar(anyLong(), anyLong(), any())).thenAnswer(inv -> {
            String motivoGuardado = inv.getArgument(2);
            // El motivo que llega al repo NO debe ser el texto plano.
            assertNotEquals("texto sensible", motivoGuardado);
            assertEquals("texto sensible", cifrador.descifrar(motivoGuardado));
            return 1L;
        });
        when(warns.contarActivos(100L)).thenReturn(1);

        servicio.avisar(1L, 100L, 200L, "texto sensible");
        verify(warns).insertar(eq(100L), eq(200L), any());
    }

    @Test
    void sinClaveNoPersisteElTextoLibre() {
        ModeracionService sinCifrado = new ModeracionService(
                warns, sanciones, usuarios, new Cifrador(""));
        when(warns.insertar(anyLong(), anyLong(), isNull())).thenReturn(1L);
        when(warns.contarActivos(100L)).thenReturn(1);

        sinCifrado.avisar(1L, 100L, 200L, "no debe guardarse");

        // Con el cifrado deshabilitado, el motivo llega como null (degradado seguro).
        verify(warns).insertar(eq(100L), eq(200L), isNull());
    }
}
