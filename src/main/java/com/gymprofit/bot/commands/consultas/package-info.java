/**
 * Comandos de consulta a contenidos de GymProFit: el catálogo de ejercicios de la API
 * ({@code /ejercicios}), el ejercicio del día ({@code /ejercicio-dia}) y el banco de frases
 * motivadoras ({@code /frase}). No hablan HTTP: delegan en {@code services/} y pintan embeds.
 *
 * <p>El trabajo asíncrono (todo lo que toca la API o la BD tras un defer) y su manejo de errores
 * están centralizados en {@link com.gymprofit.bot.commands.consultas.ConsultaAsincrona}, que usan
 * también los componentes de {@code events/EjerciciosPaginadorListener}.</p>
 */
package com.gymprofit.bot.commands.consultas;
