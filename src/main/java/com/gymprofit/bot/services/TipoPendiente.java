package com.gymprofit.bot.services;

/** Dirección de una pertenencia pendiente: la empresa invita, o el jugador solicita. */
public enum TipoPendiente {
    /** La empresa invitó al jugador; la resuelve el jugador (aceptar/rechazar). */
    INVITACION,
    /** El jugador solicitó entrar (con motivo); la resuelve el dueño (aprobar/rechazar). */
    SOLICITUD
}
