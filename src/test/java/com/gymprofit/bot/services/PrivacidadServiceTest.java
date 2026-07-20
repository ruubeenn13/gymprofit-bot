package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.DescansoRepositorio;
import com.gymprofit.bot.db.Sancion;
import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.Warn;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.services.PrivacidadService.ResultadoBorrado;
import com.gymprofit.bot.util.Cifrador;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el borrado (olvido) y el export descifrado de datos de usuario. */
class PrivacidadServiceTest {

    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final WarnRepositorio warns = mock(WarnRepositorio.class);
    private final SancionRepositorio sanciones = mock(SancionRepositorio.class);
    private final DescansoRepositorio descanso = mock(DescansoRepositorio.class);
    private final Cifrador cifrador = new Cifrador(Cifrador.generarClaveBase64());
    private final PrivacidadService servicio =
            new PrivacidadService(usuarios, warns, sanciones, descanso, cifrador);

    /** Por defecto nadie tiene fila de descanso; el test que lo necesita la simula. */
    PrivacidadServiceTest() {
        when(descanso.buscar(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void borrarEliminaUsuarioYSanciones() {
        when(usuarios.borrar(100L)).thenReturn(true);
        when(sanciones.borrarTodasDelUsuario(100L)).thenReturn(3);

        ResultadoBorrado r = servicio.borrar(100L);

        assertTrue(r.existiaUsuario());
        assertEquals(3, r.sanciones());
        verify(usuarios).borrar(100L);
        verify(sanciones).borrarTodasDelUsuario(100L);
    }

    @Test
    void exportarDescifraLosMotivos() {
        when(usuarios.buscar(100L)).thenReturn(Optional.of(
                new UsuarioDiscord(100L, 250, 3, 40, 5, null, "es", false)));
        when(warns.listarPorUsuario(eq(100L), anyInt(), anyInt())).thenReturn(List.of(
                new Warn(1L, 100L, 200L, cifrador.cifrar("spam"), true, Instant.now())));
        when(sanciones.listarTodasDelUsuario(eq(100L), anyInt())).thenReturn(List.of(
                new Sancion(1L, 9L, 100L, 200L, "BAN", cifrador.cifrar("raid"), null, null, Instant.now())));

        DataObject json = servicio.exportar(100L);

        assertEquals(250, json.getObject("gamificacion").getInt("xp"));
        assertEquals(1, json.getArray("avisos").length());
        assertEquals("spam", json.getArray("avisos").getObject(0).getString("motivo"));
        assertEquals("raid", json.getArray("sanciones").getObject(0).getString("motivo"));
    }

    @Test
    void exportarIncluyeElDescanso() {
        when(usuarios.buscar(anyLong())).thenReturn(Optional.empty());
        when(warns.listarPorUsuario(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(sanciones.listarTodasDelUsuario(anyLong(), anyInt())).thenReturn(List.of());
        when(descanso.buscar(100L)).thenReturn(Optional.of(new DescansoEstado(100L, null,
                Instant.parse("2026-07-15T08:00:00Z"), 2, LocalDate.of(2026, 7, 15), null)));

        DataObject json = servicio.exportar(100L);

        assertEquals(2, json.getObject("descanso").getInt("consumidos_hoy"));
        assertEquals("2026-07-15T08:00:00Z",
                json.getObject("descanso").getString("ultimo_despertar"));
    }

    @Test
    void exportarSinUsuarioDevuelveColeccionesVacias() {
        when(usuarios.buscar(anyLong())).thenReturn(Optional.empty());
        when(warns.listarPorUsuario(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(sanciones.listarTodasDelUsuario(anyLong(), anyInt())).thenReturn(List.of());

        DataObject json = servicio.exportar(999L);

        assertEquals(0, json.getArray("avisos").length());
        assertEquals(0, json.getArray("sanciones").length());
        // Quien nunca ha dormido no tiene fila: el export no la inventa (ni la crea).
        assertFalse(json.hasKey("descanso"));
    }
}
