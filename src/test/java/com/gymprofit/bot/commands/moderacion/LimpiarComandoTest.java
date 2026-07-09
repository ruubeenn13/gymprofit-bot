package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.services.LimpiezaService;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la definición de {@code /limpiar}: nombre, descripción y opción {@code cantidad}.
 */
class LimpiarComandoTest {

    @Test
    void definicionTieneNombreYOpcionCantidad() {
        SlashCommandData d = new LimpiarComando(new LimpiezaService()).definicion();
        assertEquals("limpiar", d.getName());
        assertFalse(d.getDescription().isBlank());
        assertTrue(d.getOptions().stream().anyMatch(o -> o.getName().equals("cantidad")),
                "debe tener la opción cantidad");
    }
}
