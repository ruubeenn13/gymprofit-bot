package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.EncantarService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica /encantar: subir nivel (coste creciente, tope), aplicar efectos y validaciones. */
class EncantarServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final EncantarService servicio = new EncantarService(personajes, economia, usuarios);

    private static Personaje conArma(String arma, int nivel) {
        return new Personaje(1L, 0, 0, 0, 100, 100, null, null, arma, null, null, nivel, null);
    }

    @Test
    void costeDeNivelCreceConElNivel() {
        assertEquals(500, EncantarService.costeNivel(0));
        assertEquals(2000, EncantarService.costeNivel(3));
    }

    @Test
    void subirNivelConArmaYSaldoLoSube() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma("espada", 0));
        when(economia.gastar(eq(1L), eq(500L), anyString())).thenReturn(true);
        var r = servicio.subirNivel(1L);
        assertEquals(Estado.OK, r.estado());
        assertEquals(1, r.nivelNuevo());
        assertEquals(500, r.coste());
        verify(personajes).subirNivelArma(1L);
    }

    @Test
    void noSeSubeNivelSinArma() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma(null, 0));
        assertEquals(Estado.SIN_ARMA, servicio.subirNivel(1L).estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    void noSeSubeNivelPasadoElMaximo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma("espada", EncantarService.NIVEL_MAX));
        assertEquals(Estado.NIVEL_MAXIMO, servicio.subirNivel(1L).estado());
        verify(personajes, never()).subirNivelArma(anyLong());
    }

    @Test
    void sinSaldoNoSubeNivel() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma("espada", 0));
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(Estado.SIN_SALDO, servicio.subirNivel(1L).estado());
        verify(personajes, never()).subirNivelArma(anyLong());
    }

    @Test
    void aplicarEncantoConArmaYSaldoLoFija() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma("espada", 0));
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        var r = servicio.aplicarEncanto(1L, "llama");
        assertEquals(Estado.OK, r.estado());
        assertEquals("llama", r.encantoId());
        verify(personajes).fijarEncanto(1L, "llama");
    }

    @Test
    void encantoInexistenteFalla() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma("espada", 0));
        assertEquals(Estado.ENCANTO_NO_EXISTE, servicio.aplicarEncanto(1L, "arcoiris").estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }
}
