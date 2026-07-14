package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifica que la rareza se deriva bien del ítem (por stat de combate o por precio). */
class RarezaTest {

    private static Rareza de(String itemId) {
        return Rareza.de(Items.porId(itemId).orElseThrow());
    }

    @Test
    void armasSeClasificanPorAtaque() {
        assertEquals(Rareza.COMUN, de("puno"));              // ataque 2
        assertEquals(Rareza.RARO, de("espada_corta"));       // ataque 10
        assertEquals(Rareza.EPICO, de("arco"));              // ataque 27
        assertEquals(Rareza.LEGENDARIO, de("espada_legendaria")); // ataque 75
    }

    @Test
    void armadurasSeClasificanPorDefensa() {
        assertEquals(Rareza.COMUN, de("ropa"));              // defensa 1
        assertEquals(Rareza.LEGENDARIO, de("armadura_divina")); // defensa 55
    }

    @Test
    void elRestoSeClasificaPorPrecio() {
        assertEquals(Rareza.COMUN, de("fruta"));             // precio 20
        assertEquals(Rareza.RARO, de("movil"));              // precio 600
        assertEquals(Rareza.LEGENDARIO, de("mansion"));      // precio 500000
    }
}
