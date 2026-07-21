package com.gymprofit.bot.api.dtos;

/** Cuerpo de {@code POST /auth/refresh}: el refresh token opaco guardado en memoria. */
public record RefreshDTO(String refreshToken) { }
