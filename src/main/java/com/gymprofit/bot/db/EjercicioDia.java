package com.gymprofit.bot.db;

import java.time.LocalDate;

/**
 * Elección del ejercicio del día (tabla {@code ejercicio_dia}, V24). Una fila por día natural
 * de Europe/Madrid; {@code ronda} cuenta las vueltas completas al catálogo.
 *
 * @param fecha       día natural
 * @param ejercicioId id del ejercicio en la API GymProFit
 * @param ronda       vuelta al catálogo (empieza en 1)
 */
public record EjercicioDia(LocalDate fecha, int ejercicioId, int ronda) { }
