package com.gymprofit.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Contrato de un slash command. Cada comando vive en su propia clase dentro de
 * {@code commands/} (subpaquete por categoría) y expone su definición (para registrarla en
 * Discord) y su lógica de ejecución. La lógica de negocio no trivial debe delegarse en un
 * service de {@code services/}.
 */
public interface Comando {

    /**
     * Definición del comando (nombre, descripción y localizaciones ES/EN, opciones, permisos).
     * La usa {@link RouterComandos} para registrarlo en cada servidor.
     */
    SlashCommandData definicion();

    /**
     * Ejecuta el comando ante una interacción de Discord.
     */
    void ejecutar(SlashCommandInteractionEvent evento);
}
