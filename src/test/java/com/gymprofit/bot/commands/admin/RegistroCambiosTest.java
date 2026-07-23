package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.commands.admin.RegistroCambios.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroCambiosTest {

    @Test
    @DisplayName("registro vacío: no hubo cambios y contadores a cero")
    void vacio() {
        RegistroCambios r = new RegistroCambios();
        assertFalse(r.huboCambios());
        assertEquals(0, r.cuenta(Tipo.CREADO));
        assertTrue(r.entradas().isEmpty());
    }

    @Test
    @DisplayName("cuenta por tipo y orden de inserción preservado")
    void cuentaYOrden() {
        RegistroCambios r = new RegistroCambios();
        r.creado(Categoria.ROL, "Coach");
        r.actualizado(Categoria.CANAL, "general");
        r.creado(Categoria.CANAL, "bot-logs");
        r.eliminado(Categoria.CANAL, "viejo");

        assertTrue(r.huboCambios());
        assertEquals(2, r.cuenta(Tipo.CREADO));
        assertEquals(1, r.cuenta(Tipo.ACTUALIZADO));
        assertEquals(1, r.cuenta(Tipo.ELIMINADO));
        assertEquals(4, r.entradas().size());
        assertEquals("Coach", r.entradas().get(0).nombre());
        assertEquals(Tipo.ELIMINADO, r.entradas().get(3).tipo());
    }
}
