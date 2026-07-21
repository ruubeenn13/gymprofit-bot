package com.gymprofit.bot.api.dtos;

/** Cuerpo de {@code POST /auth/login}: credenciales de la cuenta de servicio (nunca loguearlas). */
public record CredencialesDTO(String username, String password) { }
