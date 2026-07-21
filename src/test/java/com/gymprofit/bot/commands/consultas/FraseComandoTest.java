package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el embed de {@code /frase}: idioma correcto y autor solo cuando lo hay. */
class FraseComandoTest {

    private static final Frase CON_AUTOR =
            new Frase(1, "No cuentes los días.", "Do not count the days.", "Muhammad Ali");
    private static final Frase ANONIMA =
            new Frase(2, "Progreso, no perfección.", "Progress, not perfection.", null);

    @Test
    void muestraElTextoDelIdiomaPedido() {
        MessageEmbed es = FraseComando.construirEmbed(Messages.ES, CON_AUTOR);
        assertTrue(es.getDescription().contains("No cuentes los días."));
        MessageEmbed en = FraseComando.construirEmbed(Messages.EN, CON_AUTOR);
        assertTrue(en.getDescription().contains("Do not count the days."));
    }

    @Test
    void incluyeElAutorSoloSiExiste() {
        assertTrue(FraseComando.construirEmbed(Messages.ES, CON_AUTOR)
                .getDescription().contains("Muhammad Ali"));
        assertFalse(FraseComando.construirEmbed(Messages.ES, ANONIMA)
                .getDescription().contains("—"));
    }
}
