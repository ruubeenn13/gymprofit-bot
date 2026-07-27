package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuotaTest {

    @Test
    @DisplayName("relativa: cuota justa = 1.0; líder > 1; monopolio y total 0 = 1.0")
    void relativa() {
        assertEquals(1.0, Cuota.relativa(100, 200, 2), 1e-9);
        assertEquals(1.5, Cuota.relativa(150, 200, 2), 1e-9);
        assertEquals(1.0, Cuota.relativa(100, 100, 1), 1e-9);
        assertEquals(1.0, Cuota.relativa(0, 0, 3), 1e-9);
    }

    @Test
    @DisplayName("factorVenta: neutro en 1.0; clamp a MIN/MAX; prima/penalización intermedias")
    void factorVenta() {
        assertEquals(1.0, Cuota.factorVenta(1.0), 1e-9);
        assertEquals(1.125, Cuota.factorVenta(1.5), 1e-9);
        assertEquals(0.875, Cuota.factorVenta(0.5), 1e-9);
        assertEquals(1.25, Cuota.factorVenta(3.0), 1e-9);
        assertEquals(0.75, Cuota.factorVenta(-5.0), 1e-9);
    }
}
