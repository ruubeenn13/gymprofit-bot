package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.CombateService.EstadoEquipar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica equipar/desequipar (validaciones, ranura correcta) y el cálculo del poder de combate. */
class CombateServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final CombateService servicio =
            new CombateService(personajes, inventario, usuarios);

    /** Personaje base con arma/armadura opcionales (resto de campos irrelevantes para el test). */
    private static Personaje personaje(int fuerza, int resistencia, String arma, String armadura) {
        return new Personaje(1L, fuerza, resistencia, 0, 100, 100, null, null, arma, armadura,
                null, 0, null);
    }

    @Test
    void equiparArmaQueTienesLaFijaEnLaRanuraArma() {
        when(inventario.cantidad(1L, "espada")).thenReturn(1);
        var r = servicio.equipar(1L, "espada");
        assertEquals(EstadoEquipar.OK, r.estado());
        assertEquals(CombateService.RANURA_ARMA, r.ranura());
        assertEquals(23, r.valor()); // espada = ataque 23
        verify(personajes).fijarEquipo(1L, "arma", "espada");
    }

    @Test
    void equiparArmaduraLaFijaEnLaRanuraArmadura() {
        when(inventario.cantidad(1L, "placas")).thenReturn(1);
        var r = servicio.equipar(1L, "placas");
        assertEquals(EstadoEquipar.OK, r.estado());
        assertEquals(CombateService.RANURA_ARMADURA, r.ranura());
        assertEquals(14, r.valor()); // placas = defensa 14
        verify(personajes).fijarEquipo(1L, "armadura", "placas");
    }

    @Test
    void noEquipaLoQueNoTienes() {
        when(inventario.cantidad(anyLong(), anyString())).thenReturn(0);
        assertEquals(EstadoEquipar.NO_TIENE, servicio.equipar(1L, "espada").estado());
        verify(personajes, never()).fijarEquipo(anyLong(), anyString(), any());
    }

    @Test
    void noEquipaUnConsumible() {
        assertEquals(EstadoEquipar.NO_EQUIPABLE, servicio.equipar(1L, "batido").estado());
    }

    @Test
    void noEquipaItemInexistente() {
        assertEquals(EstadoEquipar.NO_EXISTE, servicio.equipar(1L, "nave").estado());
    }

    @Test
    void desequiparLiberaLaRanuraSiHabiaAlgo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(0, 0, "espada", null));
        assertTrue(servicio.desequipar(1L, "arma"));
        verify(personajes).fijarEquipo(eq(1L), eq("arma"), isNull());
    }

    @Test
    void desequiparRanuraVaciaNoHaceNada() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(0, 0, null, null));
        assertFalse(servicio.desequipar(1L, "arma"));
        verify(personajes, never()).fijarEquipo(anyLong(), anyString(), any());
    }

    @Test
    void desequiparRanuraInvalidaLanza() {
        assertThrows(IllegalArgumentException.class, () -> servicio.desequipar(1L, "casco"));
    }

    @Test
    void poderCombateSumaStatsYEquipo() {
        // fuerza 5 + resistencia 3 + espada(23) + placas(14) = 45
        assertEquals(45, CombateService.poderCombate(personaje(5, 3, "espada", "placas")));
    }

    @Test
    void poderCombateSinEquipoSoloStats() {
        assertEquals(8, CombateService.poderCombate(personaje(5, 3, null, null)));
    }

    @Test
    void hpCombateEscalaConResistencia() {
        // HP = 80 + resistencia*10
        assertEquals(80, CombateService.hpCombate(personaje(0, 0, null, null)));
        assertEquals(120, CombateService.hpCombate(personaje(0, 4, null, null)));
    }

    @Test
    void ataqueYDefensaSumanEquipo() {
        // fuerza 5 + espada(23) = 28 ; resistencia 3 + placas(14) = 17
        assertEquals(28, CombateService.ataqueDe(personaje(5, 3, "espada", "placas")));
        assertEquals(17, CombateService.defensaDe(personaje(5, 3, "espada", "placas")));
    }

    @Test
    void danoRestaDefensaAplicaFactorYTieneSuelo() {
        assertEquals(15, CombateService.dano(20, 5, 1.0));
        assertEquals(8, CombateService.dano(20, 5, 0.5));  // round(7.5)
        assertEquals(1, CombateService.dano(3, 10, 1.0));  // suelo en 1 aunque la defensa supere
    }

    @Test
    void probabilidadDeCriticoEscalaConFuerzaYSeTopa() {
        assertEquals(0.13, CombateService.probCritico(personaje(10, 0, null, null)), 1e-9);
        assertEquals(0.50, CombateService.probCritico(personaje(1000, 0, null, null)), 1e-9);
    }

    @Test
    void probabilidadDeEsquivaEscalaConCarismaYSeTopa() {
        // carisma 0 -> base 0.05 ; carisma alto -> tope 0.40
        assertEquals(0.05, CombateService.probEsquiva(
                new Personaje(1L, 0, 0, 0, 100, 100, null, null, null, null, null, 0, null)), 1e-9);
        assertEquals(0.40, CombateService.probEsquiva(
                new Personaje(1L, 0, 0, 1000, 100, 100, null, null, null, null, null, 0, null)), 1e-9);
    }

    @Test
    void catalogoTieneArmasYArmaduras() {
        long armas = Items.CATALOGO.stream().filter(i -> i.categoria() == Items.Categoria.ARMA).count();
        long armaduras = Items.CATALOGO.stream()
                .filter(i -> i.categoria() == Items.Categoria.ARMADURA).count();
        assertTrue(armas >= 12, "catálogo amplio de armas");
        assertTrue(armaduras >= 10, "catálogo amplio de armaduras");
    }
}
