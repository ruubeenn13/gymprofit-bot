package com.gymprofit.bot.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el parseo y formateo de duraciones cortas. */
class DuracionesTest {

    @Test
    void parseaUnidadesSimples() {
        assertEquals(30, Duraciones.parsear("30s").getAsLong());
        assertEquals(1800, Duraciones.parsear("30m").getAsLong());
        assertEquals(7200, Duraciones.parsear("2h").getAsLong());
        assertEquals(86_400, Duraciones.parsear("1d").getAsLong());
    }

    @Test
    void parseaCombinaciones() {
        assertEquals(86_400 + 2 * 3600 + 30 * 60, Duraciones.parsear("1d2h30m").getAsLong());
        assertEquals(90, Duraciones.parsear("1m30s").getAsLong());
    }

    @Test
    void rechazaFormatosInvalidos() {
        for (String malo : new String[]{"", "  ", "abc", "10x", "1h30", "h", "-5m", "0s"}) {
            assertFalse(Duraciones.parsear(malo).isPresent(), "debe rechazar: " + malo);
        }
        assertTrue(Duraciones.parsear(null).isEmpty());
    }

    @Test
    void formateaOmitiendoCeros() {
        assertEquals("1h", Duraciones.formatear(3600));
        assertEquals("1m 30s", Duraciones.formatear(90));
        assertEquals("1d 2h", Duraciones.formatear(86_400 + 2 * 3600));
        assertEquals("0s", Duraciones.formatear(0));
    }

    @Test
    void mayusculasYEspaciosSeNormalizan() {
        OptionalLong r = Duraciones.parsear("  2H  ");
        assertTrue(r.isPresent());
        assertEquals(7200, r.getAsLong());
    }
}
