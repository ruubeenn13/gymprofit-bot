package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de mundos del RPG de combate (datos, en código). Cada mundo agrupa a sus monstruos
 * (ver {@link Monstruos}) y se recorre en orden: se DESBLOQUEA al derrotar al jefe del mundo
 * anterior (lógica en {@link MundoService}). El {@code nivelRequerido} es un gate informativo/de
 * seguridad que se comprobará al pelear (COMBAT-3); el desbloqueo por progreso es lo que abre el
 * mundo en la navegación. El nombre/descripcion salen de i18n con clave {@code mundo.<id>}.
 *
 * @param id             identificador estable (clave i18n y columna {@code progreso_mundos.mundo})
 * @param emoji          emoji para los embeds
 * @param orden          posición en la ruta (1 = primero, siempre desbloqueado)
 * @param nivelRequerido nivel de servidor (XP) recomendado para pelear en él
 */
public record Mundos(String id, String emoji, int orden, int nivelRequerido) {

    /** Catálogo completo, ordenado por {@link #orden}. */
    public static final List<Mundos> CATALOGO = List.of(
            new Mundos("bosque",         "🌲", 1, 0),
            new Mundos("cueva",          "🕸️", 2, 3),
            new Mundos("pantano",        "🐸", 3, 6),
            new Mundos("desierto",       "🏜️", 4, 10),
            new Mundos("montana_helada", "🏔️", 5, 15),
            new Mundos("volcan",         "🌋", 6, 20),
            new Mundos("ciudad_ruinas",  "🏚️", 7, 28),
            new Mundos("reino_sombrio",  "🌑", 8, 38));

    /** Busca un mundo por id. */
    public static Optional<Mundos> porId(String id) {
        return CATALOGO.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Mundo inmediatamente anterior en la ruta, o vacío si este es el primero. */
    public Optional<Mundos> anterior() {
        return CATALOGO.stream().filter(m -> m.orden() == orden - 1).findFirst();
    }
}
