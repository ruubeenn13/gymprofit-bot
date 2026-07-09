package com.gymprofit.bot;

import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fija el contrato de intents privilegiados del bot (SPEC §4). Si alguien los cambia sin
 * actualizar el Developer Portal, el bot fallaría al conectar; este test deja el conjunto
 * documentado y verificado sin abrir una conexión real a Discord.
 */
class DiscordBotTest {

    @Test
    void habilitaSoloLosDosIntentsPrivilegiadosNecesarios() {
        assertEquals(2, DiscordBot.PRIVILEGED_INTENTS.size(),
                "Solo deben activarse los intents estrictamente necesarios (principio de mínimo privilegio)");
        assertTrue(DiscordBot.PRIVILEGED_INTENTS.contains(GatewayIntent.GUILD_MEMBERS),
                "GUILD_MEMBERS es necesario para bienvenida y auto-roles");
        assertTrue(DiscordBot.PRIVILEGED_INTENTS.contains(GatewayIntent.MESSAGE_CONTENT),
                "MESSAGE_CONTENT es necesario para XP por mensaje y auto-mod");
    }
}
