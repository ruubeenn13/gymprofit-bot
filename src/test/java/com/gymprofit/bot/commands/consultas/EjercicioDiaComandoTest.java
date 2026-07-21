package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el embed del ejercicio del día: naranja de marca, ficha completa y frase al pie. */
class EjercicioDiaComandoTest {

    private static final EjercicioDTO EJERCICIO = new EjercicioDTO(7, "Sentadilla",
            "El básico de pierna", "PIERNAS", "Cuádriceps", "PRINCIPIANTE",
            "http://img/7.png", null, "Baja controlado…", 9, "Peso corporal", true);
    private static final Frase FRASE =
            new Frase(1, "Progreso, no perfección.", "Progress, not perfection.", null);

    @Test
    void llevaFichaFraseYColorDeMarca() {
        MessageEmbed embed = EjercicioDiaComando.construirEmbed(Messages.ES, EJERCICIO, FRASE);
        // Máscara del canal alfa: getColorRaw() devuelve ARGB (misma convención que EmbedFactoryTest).
        assertEquals(0xFF6A00, embed.getColorRaw() & 0xFFFFFF); // naranja MARCA (SPEC §7)
        assertTrue(embed.getTitle().contains(Messages.get(Messages.ES, "ejerciciodia.titulo")));
        assertTrue(embed.getDescription().contains("Sentadilla"));
        assertEquals("http://img/7.png", embed.getImage().getUrl());
        assertTrue(embed.getFields().stream()
                .anyMatch(f -> f.getValue() != null && f.getValue().contains("Progreso")));
    }

    @Test
    void sinFraseElEmbedSigueEntero() {
        MessageEmbed embed = EjercicioDiaComando.construirEmbed(Messages.EN, EJERCICIO, null);
        assertTrue(embed.getDescription().contains("Sentadilla"));
    }
}
