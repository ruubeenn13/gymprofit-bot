/**
 * Acceso a la BD del bot (Aiven MySQL, database {@code gymprofit_bot}): repositorios JDBC
 * sobre HikariCP y migraciones Flyway en {@code src/main/resources/db/migration}.
 *
 * <p>Cualquier cambio de esquema es una migración nueva ({@code V2__...}, {@code V3__...});
 * nunca se edita una migración ya aplicada ni se toca la BD a mano. Ver la skill
 * {@code .claude/skills/nueva-migracion}. La tabla de vinculación {@code discord_links}
 * vive en la BD de la API, no aquí.</p>
 */
package com.gymprofit.bot.db;
