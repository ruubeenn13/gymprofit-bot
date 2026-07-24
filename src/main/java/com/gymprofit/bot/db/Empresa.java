package com.gymprofit.bot.db;

import java.time.Instant;

/**
 * Una empresa (fila de {@code empresas}): entidad tipo gremio ligada a una rama de carrera.
 * {@code nivel} y {@code bote} existen desde F1 pero solo cobran sentido con la economía de F2.
 *
 * @param id      id de la empresa
 * @param rama    rama de carrera a la que pertenece (id del catálogo services/Ascensos)
 * @param duenoId fundador/dueño (snowflake)
 * @param nombre  nombre visible (único dentro de la rama)
 * @param nivel   nivel de la empresa (economía de F2; 1 al fundar)
 * @param bote    fondo común acumulado (economía de F2; 0 al fundar)
 * @param creada  instante de fundación
 * @param canalId id del canal privado de Discord (F4); null si aún no se ha creado
 */
public record Empresa(long id, String rama, long duenoId, String nombre, int nivel, long bote,
                      Instant creada, Long canalId) {
}
