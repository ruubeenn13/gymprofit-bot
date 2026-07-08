/**
 * {@code EmbedFactory} central: <b>única</b> vía para crear embeds (prohibido construirlos
 * a mano en comandos o listeners).
 *
 * <p>Garantiza las reglas visuales de {@code GYMPROBOT_SPEC.md} §7: color por categoría,
 * un solo emoji identificador en el título, footer {@code "GymProBot • GymProFit"} +
 * timestamp, y paginación con botones cuando no cabe. Ver la skill
 * {@code .claude/skills/nuevo-embed}.</p>
 */
package com.gymprofit.bot.embeds;
