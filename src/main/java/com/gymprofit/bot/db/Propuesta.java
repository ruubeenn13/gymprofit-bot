package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPropuesta;

import java.time.Instant;

/**
 * Una propuesta de gestión pendiente de voto (fila de {@code empresa_propuestas}): un alto cargo
 * plantea cambiar el rango, sacar o despedir a un miembro, y los demás altos cargos la votan hasta
 * que se resuelve o caduca.
 *
 * @param id           id de la propuesta
 * @param empresaId    empresa sobre la que se decide
 * @param tipo         acción propuesta (cambiar rango, sacar o despedir)
 * @param objetivoId   miembro afectado (snowflake)
 * @param rangoNuevo   rango destino en {@link TipoPropuesta#CAMBIAR_RANGO}, o {@code null} en el resto
 * @param proponenteId alto cargo que abrió la propuesta (snowflake)
 * @param creada       instante de creación
 * @param expira       instante en que caduca si no se resuelve
 * @param dato         carga extra: puesto destino en {@link TipoPropuesta#ASCENSO}, {@code null} en el resto
 */
public record Propuesta(long id, long empresaId, TipoPropuesta tipo, long objetivoId,
                        RangoEmpresa rangoNuevo, long proponenteId,
                        Instant creada, Instant expira, String dato) {
}
