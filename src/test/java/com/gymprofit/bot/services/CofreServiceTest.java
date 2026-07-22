package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.CofreService.Estado;
import com.gymprofit.bot.services.CofreService.Resultado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica la apertura de cofres (premios por peso, conversión a coins) y el invariante de sumidero. */
class CofreServiceTest {

    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private CofreService svc(double azar) {
        return new CofreService(inventario, economia, personajes, usuarios, () -> azar);
    }

    private static Personaje conArma(String arma) {
        return new Personaje(1L, 0, 0, 0, 100, 100, null, null, arma, null, null, 0, null, 0, 0);
    }

    @Test
    void noSePuedeAbrirLoQueNoTienes() {
        when(inventario.quitar(1L, "cofre_comun", 1)).thenReturn(false);
        assertEquals(Estado.NO_TIENE, svc(0.0).abrir(1L, "cofre_comun", 1).estado());
    }

    @Test
    void abrirDaUnPremioPorCofreYPagaCoins() {
        when(inventario.quitar(1L, "cofre_comun", 1)).thenReturn(true);
        // azar 0.0 -> primer premio de cofre_comun (coins 30-80) y cantidad mínima (30)
        Resultado r = svc(0.0).abrir(1L, "cofre_comun", 1);
        assertEquals(Estado.OK, r.estado());
        assertEquals(1, r.premios().size());
        assertEquals(Cofres.Tipo.COINS, r.premios().get(0).tipo());
        assertEquals(30, r.premios().get(0).coins());
        verify(economia).ingresar(eq(1L), eq(30L), anyString());
    }

    @Test
    void unPremioDeItemSeAnadeAlInventario() {
        when(inventario.quitar(1L, "cofre_comun", 1)).thenReturn(true);
        // azar 0.5 -> objetivo 50/100 -> cae en "cafe" (tramo [45,65))
        Resultado r = svc(0.5).abrir(1L, "cofre_comun", 1);
        assertEquals(Cofres.Tipo.ITEM, r.premios().get(0).tipo());
        assertEquals("cafe", r.premios().get(0).ref());
        verify(inventario).anadir(1L, "cafe", 1);
    }

    @Test
    void unEncantamientoSinArmaSeConvierteEnCoins() {
        when(inventario.quitar(1L, "cofre_raro", 1)).thenReturn(true);
        when(personajes.obtenerOCrear(1L)).thenReturn(conArma(null)); // sin arma
        // azar 0.85 -> objetivo 85/100 -> "afilado" (ENCANTO, tramo [84,89))
        Resultado r = svc(0.85).abrir(1L, "cofre_raro", 1);
        var premio = r.premios().get(0);
        assertEquals(Cofres.Tipo.COINS, premio.tipo());
        assertTrue(premio.fallback());
        assertEquals(750, premio.coins()); // afilado precio 1500 * 0.5
    }

    @Test
    void todoCofreEsUnSumidero() {
        // Invariante anti-inflación: el valor esperado (coins extraíbles) es menor que el precio.
        // El gear obtenido es utilidad, no coins nuevos, así que el sumidero real es aún mayor.
        for (Cofres c : Cofres.CATALOGO) {
            long precio = Items.porId(c.itemId()).orElseThrow().precio();
            double ev = CofreService.valorEsperado(c);
            assertTrue(ev < precio, c.itemId() + ": EV " + ev + " debe ser < precio " + precio);
        }
    }
}
