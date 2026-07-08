/**
 * Tareas programadas: ejercicio del día (8:00), retos semanales (lunes 9:00), sync de
 * logros, etc.
 *
 * <p><b>Zona horaria:</b> todos los jobs fijan explícitamente {@code Europe/Madrid}; los
 * contenedores de Render corren en UTC, así que no se confía en la TZ del sistema
 * (ver {@code GYMPROBOT_SPEC.md} §3). Los fallos de job se reportan al canal
 * {@code #bot-logs} (§14).</p>
 */
package com.gymprofit.bot.jobs;
