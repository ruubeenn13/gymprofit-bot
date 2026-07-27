package com.gymprofit.bot.db;

/**
 * Una pregunta de trivia ya resuelta al idioma pedido (F4). El repositorio elige es/en al leerla. La letra
 * {@code correcta} es la de la opción buena (A-D).
 *
 * @param id id
 * @param categoria una de las declaradas en {@code trivia_preguntas} (FITNESS | NUTRICION)
 * @param dificultad FACIL|MEDIA|DIFICIL
 * @param pregunta enunciado en el idioma pedido
 * @param opciones las 4 opciones (orden A,B,C,D)
 * @param correcta letra A-D
 */
public record TriviaPregunta(long id, String categoria, String dificultad, String pregunta,
                             java.util.List<String> opciones, char correcta) {}
