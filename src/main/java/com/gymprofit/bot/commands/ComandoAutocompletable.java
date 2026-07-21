package com.gymprofit.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/**
 * Contrato opcional para los comandos que ofrecen <b>autocompletado</b> en alguna de sus opciones.
 * {@link RouterComandos} enruta el evento a quien lo implemente.
 *
 * <p>Se usa cuando la lista de valores válidos depende del jugador (p. ej. los ítems con efecto
 * pasivo que <i>ese</i> jugador posee) o cuando pasa de los 25 {@code choices} que admite Discord.
 * Filtrar aquí evita la mitad de los errores antes de que ocurran.
 */
public interface ComandoAutocompletable extends Comando {

    /**
     * Responde al autocompletado. <b>Debe contestar siempre</b> (aunque sea con lista vacía) y
     * hacerlo rápido: Discord da 3 s y no admite {@code defer}, así que nada de llamadas lentas.
     */
    void autocompletar(CommandAutoCompleteInteractionEvent evento);
}
