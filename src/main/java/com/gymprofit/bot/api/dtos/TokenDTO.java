package com.gymprofit.bot.api.dtos;

import java.util.List;

/** Respuesta de {@code POST /auth/login} y {@code /auth/refresh}: tokens y roles emitidos. */
public record TokenDTO(String token, String refreshToken, String username, List<String> roles) { }
