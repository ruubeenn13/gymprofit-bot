package com.gymprofit.bot.api.dtos;

import java.util.List;

/** Página de resultados de la API ({@code PageDTO} de Spring): contenido + metadatos. */
public record PaginaDTO<T>(List<T> content, int page, int size, long totalElements,
                           int totalPages, boolean last) { }
