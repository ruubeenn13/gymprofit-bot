package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifica el mapeo nivel → rango. */
class RangoTest {

    @Test
    void elRangoSeEligePorUmbralDeNivel() {
        assertEquals(Rango.NOVATO, Rango.para(0));
        assertEquals(Rango.NOVATO, Rango.para(9));
        assertEquals(Rango.HABITUAL, Rango.para(10));
        assertEquals(Rango.HABITUAL, Rango.para(24));
        assertEquals(Rango.VETERANO, Rango.para(25));
        assertEquals(Rango.LEYENDA, Rango.para(50));
        assertEquals(Rango.LEYENDA, Rango.para(999));
    }
}
