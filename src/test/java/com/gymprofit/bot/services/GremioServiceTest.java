package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Gremio;
import com.gymprofit.bot.db.GremioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.GremioService.EstadoCrear;
import com.gymprofit.bot.services.GremioService.EstadoMiembro;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica la lógica de gremios: fundar (con validaciones/coste), añadir, salir y disolver. */
class GremioServiceTest {

    private final GremioRepositorio repo = mock(GremioRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final GremioService servicio = new GremioService(repo, economia, usuarios);

    private static Gremio gremio(long dueno) {
        return new Gremio(1L, "Los Titanes", dueno, 99L);
    }

    @Test
    void fundarConNombreValidoYSaldoCreaElGremio() {
        when(repo.gremioDe(1L)).thenReturn(Optional.empty());
        when(repo.existeNombre("Los Titanes")).thenReturn(false);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(repo.crear("Los Titanes", 1L)).thenReturn(1L);
        var r = servicio.crear(1L, "Los Titanes");
        assertEquals(EstadoCrear.OK, r.estado());
        verify(repo).anadirMiembro(1L, 1L); // el dueño entra como miembro
    }

    @Test
    void noSeFundaSiYaEstasEnUnGremioOElNombreExiste() {
        when(repo.gremioDe(1L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoCrear.YA_EN_GREMIO, servicio.crear(1L, "Otro").estado());

        when(repo.gremioDe(1L)).thenReturn(Optional.empty());
        when(repo.existeNombre("Los Titanes")).thenReturn(true);
        assertEquals(EstadoCrear.NOMBRE_USADO, servicio.crear(1L, "Los Titanes").estado());
    }

    @Test
    void nombreCortoOSinSaldoFalla() {
        when(repo.gremioDe(1L)).thenReturn(Optional.empty());
        assertEquals(EstadoCrear.NOMBRE_INVALIDO, servicio.crear(1L, "ab").estado());

        when(repo.existeNombre(anyString())).thenReturn(false);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(EstadoCrear.SIN_SALDO, servicio.crear(1L, "Los Titanes").estado());
    }

    @Test
    void elDuenoAnadeMiembrosHastaElTope() {
        when(repo.gremioDe(1L)).thenReturn(Optional.of(gremio(1L)));
        when(repo.gremioDe(2L)).thenReturn(Optional.empty());
        when(repo.contarMiembros(1L)).thenReturn(3);
        assertEquals(EstadoMiembro.OK, servicio.anadir(1L, 2L).estado());
        verify(repo).anadirMiembro(1L, 2L);

        when(repo.contarMiembros(1L)).thenReturn(GremioService.MAX_MIEMBROS);
        assertEquals(EstadoMiembro.LLENO, servicio.anadir(1L, 2L).estado());
    }

    @Test
    void soloElDuenoAnade() {
        when(repo.gremioDe(9L)).thenReturn(Optional.of(gremio(1L))); // 9 no es el dueño (1)
        assertEquals(EstadoMiembro.NO_ERES_DUENO, servicio.anadir(9L, 2L).estado());
    }

    @Test
    void salirFuncionaSalvoParaElDueno() {
        when(repo.gremioDe(2L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoMiembro.OK, servicio.salir(2L).estado());
        verify(repo).quitarMiembro(2L);

        when(repo.gremioDe(1L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoMiembro.ERES_DUENO, servicio.salir(1L).estado());
    }

    @Test
    void disolverSoloLoHaceElDueno() {
        when(repo.gremioDe(1L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoMiembro.OK, servicio.disolver(1L).estado());
        verify(repo).eliminar(1L);

        when(repo.gremioDe(9L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoMiembro.NO_ERES_DUENO, servicio.disolver(9L).estado());
    }

    @Test
    void expulsarValidaMiembroYNoATiMismo() {
        when(repo.gremioDe(1L)).thenReturn(Optional.of(gremio(1L)));
        assertEquals(EstadoMiembro.NO_PUEDES, servicio.expulsar(1L, 1L).estado());

        when(repo.gremioDe(2L)).thenReturn(Optional.empty());
        assertEquals(EstadoMiembro.NO_MIEMBRO, servicio.expulsar(1L, 2L).estado());
        verify(repo, never()).quitarMiembro(2L);
    }
}
