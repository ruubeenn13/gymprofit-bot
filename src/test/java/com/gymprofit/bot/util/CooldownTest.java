package com.gymprofit.bot.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el cooldown por clave: primer uso permitido, bloqueo dentro de la ventana, permiso
 * de nuevo pasada la ventana e independencia entre claves distintas.
 */
class CooldownTest {

    @Test
    void permiteYBloqueaSegunLaVentana() {
        Cooldown cooldown = new Cooldown(Duration.ofSeconds(60));

        assertTrue(cooldown.intentar(1L, 1_000L), "Primer uso permitido");
        assertFalse(cooldown.intentar(1L, 30_000L), "Dentro de los 60 s: bloqueado");
        assertFalse(cooldown.intentar(1L, 60_999L), "Justo antes de cumplir la ventana: bloqueado");
        assertTrue(cooldown.intentar(1L, 61_000L), "Cumplida la ventana: permitido");
    }

    @Test
    void clavesDistintasSonIndependientes() {
        Cooldown cooldown = new Cooldown(Duration.ofSeconds(60));

        assertTrue(cooldown.intentar(1L, 1_000L));
        assertTrue(cooldown.intentar(2L, 1_000L), "Otra clave no comparte cooldown");
    }
}
