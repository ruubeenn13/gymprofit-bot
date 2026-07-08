/**
 * Cliente Retrofit2 + OkHttp3 + Gson hacia la API GymProFit (interfaces por dominio:
 * ejercicios, rutinas, logros, sesiones, mediciones, admin, discord).
 *
 * <p>El bot <b>nunca</b> toca la BD de la app: todo dato de la app pasa por esta capa.
 * El access token se cachea y solo se renueva ante un 401; ante un 429 se respeta
 * {@code Retry-After} con backoff (ver {@code GYMPROBOT_SPEC.md} §4.1). Toda llamada se
 * envía con {@code Accept-Language} según el idioma del servidor/usuario.</p>
 */
package com.gymprofit.bot.api;
