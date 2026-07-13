package com.gymprofit.bot.db;

import java.time.Instant;

/**
 * Un aviso (warn) de moderación de la tabla {@code warns}. Fuente del escalado automático (se
 * cuentan los {@code activo=true}). El {@code motivo} viaja <b>cifrado</b> entre repo y BD; el
 * servicio lo descifra al leerlo (ver {@code util/Cifrador}).
 *
 * @param id           id del aviso
 * @param discordId    usuario amonestado (snowflake)
 * @param moderadorId  staff que puso el aviso (snowflake)
 * @param motivo       razón (en claro tras pasar por el servicio; cifrada en BD), o {@code null}
 * @param activo       {@code false} si fue revocado
 * @param creadoEn     instante de creación
 */
public record Warn(long id, long discordId, long moderadorId, String motivo, boolean activo,
                   Instant creadoEn) {
}
