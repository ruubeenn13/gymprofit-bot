package com.gymprofit.bot.services;

/**
 * Acción de gestión que un Directivo (o el Dueño) propone y los altos cargos de la empresa votan.
 * Se persiste por nombre en la columna {@code empresa_propuestas.tipo}.
 */
public enum TipoPropuesta {
    /** Cambiar el rango de un miembro; requiere {@code rango_nuevo} en la propuesta. */
    CAMBIAR_RANGO,
    /** Expulsar a un miembro de la empresa (lo saca de {@code empresa_miembros}). */
    SACAR,
    /** Despedir a un miembro (variante con consecuencias económicas de F2). */
    DESPEDIR
}
