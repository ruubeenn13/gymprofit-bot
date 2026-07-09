package com.gymprofit.bot.commands.general;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la definición del comando {@code /ping}: nombre correcto, descripción no vacía y
 * localizaciones ES/EN presentes (SPEC §8), sin necesidad de conexión a Discord.
 */
class PingComandoTest {

    @Test
    void definicionTieneNombreYLocalizaciones() {
        SlashCommandData definicion = new PingComando().definicion();

        assertEquals("ping", definicion.getName());
        assertFalse(definicion.getDescription().isBlank(), "La descripción por defecto no puede ir vacía");

        Map<DiscordLocale, String> descripciones = definicion.getDescriptionLocalizations().toMap();
        assertTrue(descripciones.containsKey(DiscordLocale.SPANISH), "Falta la descripción en español");
        assertTrue(descripciones.containsKey(DiscordLocale.ENGLISH_US), "Falta la descripción en inglés");
    }
}
