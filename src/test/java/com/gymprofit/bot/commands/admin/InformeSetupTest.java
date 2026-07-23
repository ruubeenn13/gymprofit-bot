package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.i18n.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InformeSetupTest {

    private InformeSetup.Contexto ctx() {
        return new InformeSetup.Contexto("GymProFit", "@ruben", false, 1_700_000_000L);
    }

    @Test
    @DisplayName("sin cambios: incluye cabecera, contadores a cero y la línea 'sin cambios'")
    void sinCambios() {
        List<String> lineas = InformeSetup.lineas(new RegistroCambios(), ctx(), Messages.ES);
        String texto = String.join("\n", lineas);
        assertTrue(texto.contains("GymProFit"), "cabecera con el servidor");
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.sincambios")));
    }

    @Test
    @DisplayName("con cambios: agrupa por tipo y por categoría con los nombres")
    void conCambios() {
        RegistroCambios r = new RegistroCambios();
        r.creado(Categoria.ROL, "Coach");
        r.creado(Categoria.CANAL, "bot-logs");
        r.actualizado(Categoria.INTRO, "general");
        List<String> lineas = InformeSetup.lineas(r, ctx(), Messages.ES);
        String texto = String.join("\n", lineas);
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.nuevos")), "bloque de nuevos");
        assertTrue(texto.contains("Coach") && texto.contains("bot-logs"), "nombres de los nuevos");
        assertTrue(texto.contains(Messages.get(Messages.ES, "setup.registro.actualizados")), "bloque de actualizados");
        assertTrue(texto.contains("general"), "nombre del intro actualizado");
    }
}
