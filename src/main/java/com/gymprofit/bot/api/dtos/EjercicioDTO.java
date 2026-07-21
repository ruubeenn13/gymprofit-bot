package com.gymprofit.bot.api.dtos;

/** Ficha completa de un ejercicio del catálogo (localizada por la API vía Accept-Language). */
public record EjercicioDTO(Integer id, String nombre, String descripcion, String grupoMuscular,
                           String musculoPrimario, String dificultad, String imagenUrl,
                           String imagenUrl2, String instrucciones, Integer caloriasQuemadas,
                           String equipoNecesario, Boolean activo) { }
