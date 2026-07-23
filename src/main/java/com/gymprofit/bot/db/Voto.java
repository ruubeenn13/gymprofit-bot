package com.gymprofit.bot.db;

/**
 * Un voto de un alto cargo sobre una propuesta (fila de {@code empresa_votos}). Cada votante vota una
 * sola vez por propuesta (PK compuesta {@code propuesta_id, votante_discord_id}).
 *
 * @param propuestaId propuesta votada
 * @param votanteId   alto cargo que emite el voto (snowflake)
 * @param si          sentido del voto: {@code true} = a favor (Sí), {@code false} = en contra (No)
 */
public record Voto(long propuestaId, long votanteId, boolean si) {
}
