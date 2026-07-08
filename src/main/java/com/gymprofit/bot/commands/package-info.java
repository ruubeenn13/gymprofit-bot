/**
 * Slash commands: una clase por comando, agrupadas en subpaquetes por categoría.
 *
 * <p>Cada comando: registra sus localizaciones ES/EN ({@code setNameLocalization} /
 * {@code setDescriptionLocalization}), saca todo texto de {@code i18n}, crea embeds solo
 * vía {@code EmbedFactory}, aplica permisos ({@code setDefaultPermissions} para staff) y
 * cooldown si escribe en BD o llama a la API. Ver la skill {@code .claude/skills/nuevo-comando}.</p>
 */
package com.gymprofit.bot.commands;
